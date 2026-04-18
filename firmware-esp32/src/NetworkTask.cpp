#include "NetworkTask.h"
#include <WiFi.h>
#include <FirebaseESP32.h>
#include <esp_task_wdt.h>
#include <time.h>
#include "secrets.h"  // ← Load credentials từ file ẩn

// ============================================================
//  NETWORKTASK - Kết nối WiFi, Firebase, Retry logic
//  Chịu trách nhiệm: Hoàng
//  Chi tiết: WiFi retry exponential, Firebase sync, WDT
// ============================================================

// ─────────────────────────────────────────────────────────
//  FIREBASE SDK - GLOBAL (chia sẻ giữa NetworkTask & FeedingTask)
// ─────────────────────────────────────────────────────────
FirebaseData fbData;
FirebaseConfig fbConfig;
FirebaseAuth fbAuth;

// ─────────────────────────────────────────────────────────
//  TASK HANDLE
// ─────────────────────────────────────────────────────────
TaskHandle_t s_TaskHandle = nullptr;

namespace {

// WiFi & Firebase credentials được load từ include/secrets.h
// (File này được .gitignore để bảo vệ - không push lên GitHub)

// ─────────────────────────────────────────────────────────
//  TRẠNG THÁI KẾT NỐI
// ─────────────────────────────────────────────────────────
bool s_WiFiConnected = false;
bool s_FirebaseReady = false;

// WiFi retry: exponential backoff (2s → 4s → 8s → ... → max 30s)
unsigned long s_LastRetryTime = 0;
unsigned long s_RetryDelay = 2000;  // Bắt đầu 2 giây

// ─────────────────────────────────────────────────────────
//  HÀM KẾT NỐI WIFI
// ─────────────────────────────────────────────────────────

void connectWiFi() {
    Serial.println("[NetworkTask] Cố gắng kết nối WiFi...");
    
    // Cách 1: Thử SSID thứ nhất
    WiFi.begin(WIFI_SSID_1, WIFI_PASS_1);
    Serial.printf("[NetworkTask] Thử: '%s'\n", WIFI_SSID_1);
    
    int retries = 0;
    while (WiFi.status() != WL_CONNECTED && retries < 20) {
        delay(500);
        Serial.print(".");
        retries++;
    }
    
    // Nếu thất bại, thử SSID thứ hai
    if (WiFi.status() != WL_CONNECTED) {
        WiFi.begin(WIFI_SSID_2, WIFI_PASS_2);
        Serial.printf("\n[NetworkTask] Thử: '%s'\n", WIFI_SSID_2);
        
        retries = 0;
        while (WiFi.status() != WL_CONNECTED && retries < 20) {
            delay(500);
            Serial.print(".");
            retries++;
        }
    }
    
    if (WiFi.status() == WL_CONNECTED) {
        s_WiFiConnected = true;
        s_RetryDelay = 2000;  // Reset retry delay
        Serial.printf("\n[NetworkTask] ✓ WiFi kết nối: %s\n", WiFi.SSID().c_str());
        Serial.printf("[NetworkTask] IP: %s\n", WiFi.localIP().toString().c_str());
    } else {
        s_WiFiConnected = false;
        Serial.println("\n[NetworkTask] ✗ Không kết nối được WiFi");
    }
}

// ─────────────────────────────────────────────────────────
//  HÀM KHỞI TẠO FIREBASE
// ─────────────────────────────────────────────────────────

void initFirebase() {
    Serial.println("[NetworkTask] Khởi tạo Firebase...");
    
    // Cấu hình Firebase
    fbConfig.database_url = FIREBASE_HOST;
    fbConfig.api_key = FIREBASE_API_KEY;
    fbAuth.user.email = USER_EMAIL;
    fbAuth.user.password = USER_PASSWORD;
    
    // Bắt đầu Firebase
    Firebase.begin(&fbConfig, &fbAuth);
    Firebase.reconnectNetwork(true);
    
    s_FirebaseReady = true;
    Serial.println("[NetworkTask] ✓ Firebase khởi tạo xong");
}

// ─────────────────────────────────────────────────────────
//  HÀM ĐỒ THỐNG KÊ DỮ LIỆU LÊN FIREBASE
// ─────────────────────────────────────────────────────────

void syncSensorDataToFirebase(const SensorData_t& data) {
    if (!s_FirebaseReady || !Firebase.ready()) {
        return;
    }
    
    // Đẩy dữ liệu cảm biến lên Firebase
    Firebase.setFloat(fbData, "/aquarium/temperature", data.temperature);
    Firebase.setFloat(fbData, "/aquarium/water_quality", data.tds);
    Firebase.setInt(fbData, "/aquarium/ts300b", data.turbidity);
    Firebase.setFloat(fbData, "/aquarium/weight", data.weight);  // ← Luôn ghi weight
    
    // Debug
    Serial.printf("[NetworkTask] Firebase sync: Temp=%.1f, TDS=%.1f, Turbidity=%d, Weight=%.1f\n",
                  data.temperature, data.tds, data.turbidity, data.weight);
}

// ─────────────────────────────────────────────────────────
//  HÀM ĐỌC LỆNH TỪ FIREBASE
// ─────────────────────────────────────────────────────────

void pollCommandsFromFirebase() {
    if (!s_FirebaseReady || !Firebase.ready() || !xQueue_Commands) {
        return;
    }
    
    // ── Đọc lệnh Oxy (guồng) ──
    if (Firebase.getBool(fbData, "/aquarium/control/guong")) {
        bool oxyState = fbData.boolData();
        CommandData_t cmd = {
            .type = oxyState ? CMD_GUONG_ON : CMD_GUONG_OFF,
            .value = 0,
            .timestamp = millis()
        };
        xQueueSend(xQueue_Commands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_GUONG: %s\n", oxyState ? "ON" : "OFF");
    }
    
    // ── Đọc lệnh Feeding (cám) ──
    String feedMode = "";
    if (Firebase.getString(fbData, "/aquarium/control/thucan/mode")) {
        feedMode = fbData.stringData();
    }
    
    bool feedState = false;
    if (Firebase.getBool(fbData, "/aquarium/control/thucan/state")) {
        feedState = fbData.boolData();
    }
    
    float targetGram = 0;
    if (Firebase.getFloat(fbData, "/aquarium/control/thucan/target_gram")) {
        targetGram = fbData.floatData();
    }
    
    // Auto mode
    if (feedMode == "auto") {
        CommandData_t cmd = {
            .type = feedState ? CMD_THUCAN_AUTO : CMD_THUCAN_AUTO,  // Trạng thái ON/OFF qua state
            .value = feedState ? 1.0f : 0.0f,
            .timestamp = millis()
        };
        xQueueSend(xQueue_Commands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_THUCAN_AUTO: state=%d\n", feedState);
    }
    // Gram mode
    else if (feedMode == "gram") {
        CommandData_t cmd = {
            .type = CMD_THUCAN_GRAM,
            .value = targetGram,  // Gram amount
            .timestamp = millis()
        };
        xQueueSend(xQueue_Commands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_THUCAN_GRAM: target=%.1fg\n", targetGram);
    }
    // Manual mode
    else if (feedMode == "manual") {
        CommandData_t cmd = {
            .type = CMD_THUCAN_MANUAL,
            .value = feedState ? 1.0f : 0.0f,
            .timestamp = millis()
        };
        xQueueSend(xQueue_Commands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_THUCAN_MANUAL: state=%d\n", feedState);
    }
}

// ─────────────────────────────────────────────────────────
//  TASK LOOP CHÍNH
// ─────────────────────────────────────────────────────────

void networkTaskLoop(void* unused) {
    // ───── Khởi tạo ─────
    Serial.println("[NetworkTask] Bắt đầu khởi tạo...");
    
    // Đăng ký vào Watchdog
    esp_task_wdt_add(NULL);
    
    // ───── Vòng lặp chính ─────
    for (;;) {
        
        // ════════════════════════════════════════
        //  1. KIỂM TRA & KẾT NỐI WIFI
        // ════════════════════════════════════════
        
        if (WiFi.status() != WL_CONNECTED) {
            // WiFi đứt - cố tái kết nối
            if (millis() - s_LastRetryTime > s_RetryDelay) {
                Serial.printf("[NetworkTask] ⚠️  WiFi đứt - Retry sau %lums...\n", s_RetryDelay);
                
                connectWiFi();
                s_LastRetryTime = millis();
                
                // Exponential backoff: tăng delay lên
                uint32_t newDelay = s_RetryDelay * 2;
                s_RetryDelay = (newDelay > WIFI_RETRY_MAX_MS) ? WIFI_RETRY_MAX_MS : newDelay;
            }
            
            // Set flag WiFi DOWN cho AutomationTask (edge logic)
            isWiFiConnected = false;
            
        } else {
            // WiFi còn kết nối
            isWiFiConnected = true;
            
            // ════════════════════════════════════════
            //  2. KHỞI TẠO TIME NTP (lần đầu tiên)
            // ════════════════════════════════════════
            
            static bool timeConfigured = false;
            if (!timeConfigured) {
                configTime(7 * 3600, 0, "pool.ntp.org");  // UTC+7 (Vietnam)
                // Chờ time được sync (timeout 5 giây)
                int syncRetries = 0;
                while (time(nullptr) < 24 * 3600 && syncRetries < 50) {  // < 1 ngày sau epoch = chưa sync
                    delay(100);
                    syncRetries++;
                }
                if (time(nullptr) > 24 * 3600) {
                    timeConfigured = true;
                    Serial.printf("[NetworkTask] ✓ NTP time synced: %lu\n", time(nullptr));
                } else {
                    Serial.println("[NetworkTask] ⚠️  NTP time sync timeout");
                }
            }
            
            // ════════════════════════════════════════
            //  3. KHỞI TẠO FIREBASE (lần đầu tiên)
            // ════════════════════════════════════════
            
            if (!s_FirebaseReady) {
                initFirebase();
            }
            
            // ════════════════════════════════════════
            //  4. ĐỒ THỐNG KÊ DỮ LIỆU CẢM BIẾN
            // ════════════════════════════════════════
            
            SensorData_t sensorData = {0};
            if (xQueuePeek(xQueue_SensorData, &sensorData, 0) == pdPASS) {
                syncSensorDataToFirebase(sensorData);
            }
            
            // ════════════════════════════════════════
            //  5. ĐỌC LỆNH TỪ FIREBASE
            // ════════════════════════════════════════
            
            pollCommandsFromFirebase();
        }
        
        // ───── Feed Watchdog (bảo vệ NTask treo) ─────
        // Nếu NTask treo quá 20s mạch sẽ tự reset
        esp_task_wdt_reset();
        
        // ───── Debug Serial ─────
        Serial.printf("[NetworkTask] WiFi=%s | Firebase=%s\n",
                      s_WiFiConnected ? "ON" : "OFF",
                      s_FirebaseReady && Firebase.ready() ? "OK" : "DOWN");
        
        // ───── Delay ─────
        vTaskDelay(pdMS_TO_TICKS(2000));  // Check mỗi 2 giây
    }
}

}  // namespace

// ============================================================
//  HÀM PUBLIC
// ============================================================

void NetworkTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[NetworkTask_init] Tạo FreeRTOS task...");
    
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        networkTaskLoop,          // Hàm task
        "NetworkTask",            // Tên task
        stackSize,                // Stack size (8KB vì Firebase dùng bộ nhớ)
        nullptr,                  // Parameter
        priority,                 // Priority (4 = Highest)
        &s_TaskHandle,            // Handle output
        0                         // Core 0 (WiFi)
    );
    
    if (xReturned == pdPASS) {
        Serial.println("[NetworkTask_init] ✓ Task tạo thành công");
    } else {
        Serial.println("[NetworkTask_init] ✗ Lỗi tạo task!");
    }
}

bool NetworkTask_IsWiFiConnected() {
    return s_WiFiConnected;
}

bool NetworkTask_IsFirebaseConnected() {
    return s_FirebaseReady && Firebase.ready();
}

void NetworkTask_LogFeedHistory(float grams, const String& mode, const String& timeStr) {
    if (!s_FirebaseReady || !Firebase.ready()) {
        Serial.printf("[NetworkTask] ⚠️  Firebase not ready, skipping log write\n");
        return;
    }
    
    // Đọc counter hiện tại từ /logs/counter
    int logCounter = 1;
    if (Firebase.getInt(fbData, "/logs/counter")) {
        logCounter = fbData.intData();
    }
    
    // Tạo path cho log mới: /logs/log{counter}
    String basePath = "/logs/log" + String(logCounter);
    
    // Ghi thông tin cho ăn
    if (Firebase.setFloat(fbData, basePath + "/gram", grams) &&
        Firebase.setString(fbData, basePath + "/mode", mode) &&
        Firebase.setString(fbData, basePath + "/time", timeStr)) {
        
        // Tăng counter cho log tiếp theo
        Firebase.setInt(fbData, "/logs/counter", logCounter + 1);
        
        Serial.printf("[NetworkTask] ✓ Feed log saved: log%d (%.1fg, %s, %s)\n", 
                     logCounter, grams, mode.c_str(), timeStr.c_str());
    } else {
        Serial.printf("[NetworkTask] ⚠️  Failed to write feed log: %s\n", fbData.errorReason().c_str());
    }
}
