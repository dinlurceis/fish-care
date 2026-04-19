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
        
        // Ép Google DNS (8.8.8.8) SAU KHI đã có DHCP → IP không bị 255.255.255.255
        IPAddress dns1(8, 8, 8, 8);
        IPAddress dns2(8, 8, 4, 4);
        WiFi.config(WiFi.localIP(), WiFi.gatewayIP(), WiFi.subnetMask(), dns1, dns2);
        
        Serial.printf("\n[NetworkTask] ✓ WiFi kết nối: %s\n", WiFi.SSID().c_str());
        Serial.printf("[NetworkTask] IP: %s | DNS: 8.8.8.8\n", WiFi.localIP().toString().c_str());
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
    
    fbConfig.database_url = FIREBASE_HOST;
    fbConfig.signer.tokens.legacy_token = FIREBASE_DB_SECRET;
    
    // Timeout ngắn để SSL không block lâu hơn 60s WDT
    fbConfig.timeout.serverResponse = 6000;    // 6s
    fbConfig.timeout.socketConnection = 5000;  // 5s TCP
    fbConfig.timeout.sslHandshake = 5000;      // 5s SSL handshake
    
    Firebase.begin(&fbConfig, nullptr);
    Firebase.reconnectNetwork(true);
    
    s_FirebaseReady = true;
    Serial.printf("[NetworkTask] ✓ Firebase init xong | Database: %s\n", FIREBASE_HOST);
}

// ─────────────────────────────────────────────────────────
//  HÀM ĐỒ THỐNG KÊ DỮ LIỆU LÊN FIREBASE
// ─────────────────────────────────────────────────────────

// Forward declaration
void checkAndPushAlerts(const SensorData_t& data);

void syncSensorDataToFirebase(const SensorData_t& data) {
    // Log trạng thái để debug
    if (!s_FirebaseReady) return;
    
    bool ntpSynced = (time(nullptr) > 24 * 3600);
    if (!ntpSynced) {
        static unsigned long lastNtpMsg = 0;
        if (millis() - lastNtpMsg > 30000) {
            Serial.println("[NetworkTask] ⏳ NTP chưa sync - Gửi data với timestamp mặc định...");
            lastNtpMsg = millis();
        }
    }
    
    // Đẩy dữ liệu cảm biến Real-time lên Firebase
    bool ok = true;
    ok &= Firebase.setFloat(fbData, "/aquarium/temperature", data.temperature);
    ok &= Firebase.setFloat(fbData, "/aquarium/water_quality", data.tds);
    ok &= Firebase.setInt(fbData,   "/aquarium/ts300b", data.turbidity);
    ok &= Firebase.setFloat(fbData, "/aquarium/weight", data.weight);
    
    static int dnsErrorCount = 0;
    if (ok) {
        dnsErrorCount = 0;
        Serial.printf("[NetworkTask] ✓ Firebase sync OK: Temp=%.1f, TDS=%.1f\n", data.temperature, data.tds);
    } else {
        String reason = fbData.errorReason();
        Serial.printf("[NetworkTask] ⚠️ Firebase sync FAIL: %s\n", reason.c_str());
        
        // Nếu lỗi DNS liên tục -> báo WiFi reset ở loop chính
        if (reason.indexOf("DNS") != -1 || reason.indexOf("connection refused") != -1) {
            dnsErrorCount++;
            if (dnsErrorCount > 5) {
                Serial.println("[NetworkTask] 🚨 DNS Failed liên tục -> Yêu cầu reconnect WiFi!");
                WiFi.disconnect(); 
                dnsErrorCount = 0;
            }
        }
    }
    
    // ── Ghi lịch sử biểu đồ (Chart) vào /tds_logs mỗi 60 giây ──
    static unsigned long s_lastChartSync = 0;
    if (millis() - s_lastChartSync > 60000 || s_lastChartSync == 0) {
        time_t now = time(nullptr);
        if (now > 24 * 3600) { // NTP đã sync
            long long tsMs = (long long)now * 1000LL;
            String path = "/tds_logs/" + String(tsMs);
            Firebase.setFloat(fbData, path, data.tds);
            Serial.printf("[NetworkTask] ✓ Chart Saved: ts=%lld, tds=%.1f\n", tsMs, data.tds);
            s_lastChartSync = millis();
        }
    }
}

// ─────────────────────────────────────────────────────────
//  HÀM ĐỌC LỆNH TỪ FIREBASE
// ─────────────────────────────────────────────────────────

void pollCommandsFromFirebase() {
    if (!s_FirebaseReady || !Firebase.ready() || !xQueue_FeedCommands || !xQueue_AutoCommands) {
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
        xQueueSend(xQueue_FeedCommands, &cmd, 0);
        xQueueSend(xQueue_AutoCommands, &cmd, 0);
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
            .type = CMD_THUCAN_AUTO,
            .value = feedState ? 1.0f : 0.0f,
            .timestamp = millis()
        };
        xQueueSend(xQueue_FeedCommands, &cmd, 0);
        xQueueSend(xQueue_AutoCommands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_THUCAN_AUTO: state=%d\n", feedState);
    }
    // Gram mode
    else if (feedMode == "gram") {
        CommandData_t cmd = {
            .type = CMD_THUCAN_GRAM,
            .value = targetGram,
            .timestamp = millis()
        };
        xQueueSend(xQueue_FeedCommands, &cmd, 0);
        xQueueSend(xQueue_AutoCommands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_THUCAN_GRAM: target=%.1fg\n", targetGram);
    }
    // Manual mode
    else if (feedMode == "manual") {
        CommandData_t cmd = {
            .type = CMD_THUCAN_MANUAL,
            .value = feedState ? 1.0f : 0.0f,
            .timestamp = millis()
        };
        xQueueSend(xQueue_FeedCommands, &cmd, 0);
        xQueueSend(xQueue_AutoCommands, &cmd, 0);
        Serial.printf("[NetworkTask] CMD_THUCAN_MANUAL: state=%d\n", feedState);
    }
}

// ─────────────────────────────────────────────────────────
//  TASK LOOP CHÍNH
// ─────────────────────────────────────────────────────────

void networkTaskLoop(void* unused) {
    Serial.println("[NetworkTask] Bắt đầu khởi tạo...");
    // NetworkTask KHÔNG đăng ký vào WDT vì Firebase SSL có thể block lâu
    
    static bool ntpConfigured = false;
    static unsigned long lastFirebaseRetry = 0;
    static unsigned long lastSensorSync = 0;
    
    for (;;) {
        
        // ════════════════════════════════════════
        //  1. KIỂM TRA & KẾT NỐI WIFI
        // ════════════════════════════════════════
        
        if (WiFi.status() != WL_CONNECTED) {
            isWiFiConnected = false;
            if (millis() - s_LastRetryTime > s_RetryDelay) {
                Serial.printf("[NetworkTask] ⚠️  WiFi đứt - Retry sau %lums...\n", s_RetryDelay);
                connectWiFi();
                s_LastRetryTime = millis();
                // Reset Firebase khi WiFi reconnect
                s_FirebaseReady = false;
                uint32_t newDelay = s_RetryDelay * 2;
                s_RetryDelay = (newDelay > WIFI_RETRY_MAX_MS) ? WIFI_RETRY_MAX_MS : newDelay;
            }
        } else {
            isWiFiConnected = true;
            
            // ════════════════════════════════════════
            //  2. NTP - Non-blocking: gọi 1 lần rồi thôi
            // ════════════════════════════════════════
            
            if (!ntpConfigured) {
                // Thêm nhiều NTP server để tăng khả năng thành công trên 4G
                configTime(7 * 3600, 0, "time.google.com", "pool.ntp.org", "time.nist.gov");
                ntpConfigured = true;
                Serial.println("[NetworkTask] 🌐 Đã yêu cầu NTP sync (background)...");
            }
            
            // ════════════════════════════════════════
            //  3. KHỞI TẠO / RETRY FIREBASE
            // ════════════════════════════════════════
            
            if (!s_FirebaseReady) {
                initFirebase();
                lastFirebaseRetry = millis();
            } else if (!Firebase.ready()) {
                // Firebase mất kết nối → retry sau 15 giây
                if (millis() - lastFirebaseRetry > 15000) {
                    Serial.println("[NetworkTask] ⚠️  Firebase mất kết nối - re-init...");
                    s_FirebaseReady = false;  // Trigger re-init ở vòng lặp tiếp theo
                    lastFirebaseRetry = millis();
                }
            }
            
            // ════════════════════════════════════════
            //  4 & 5. SYNC & POLL (chỉ khi Firebase sẵn sàng)
            // ════════════════════════════════════════
            
            if (s_FirebaseReady && Firebase.ready()) {
                // Sync sensor data mỗi 2 giây
                if (millis() - lastSensorSync > 2000) {
                    SensorData_t sensorData = {0};
                    if (xQueuePeek(xQueue_SensorData, &sensorData, 0) == pdPASS) {
                        syncSensorDataToFirebase(sensorData);
                    }
                    lastSensorSync = millis();
                }
                
                // Poll lệnh từ Firebase mỗi vòng lặp
                pollCommandsFromFirebase();
            }
        }
        
        // ───── Debug Serial ─────
        static unsigned long lastDebug = 0;
        if (millis() - lastDebug > 5000) {
            Serial.printf("[NetworkTask] WiFi=%s | Firebase=%s | Time=%s\n",
                          isWiFiConnected ? "ON" : "OFF",
                          Firebase.ready() ? "OK" : "DOWN",
                          time(nullptr) > 24*3600 ? "SYNCED" : "NO_NTP");
            lastDebug = millis();
        }
        
        vTaskDelay(pdMS_TO_TICKS(800));
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
        10240,                    // Stack size (Tăng lên 10KB cho SSL)
        nullptr,                  // Parameter
        priority,                 // Priority (4 = Highest)
        &s_TaskHandle,            // Handle output
        1                         // Core 1 (tránh block IDLE0 trên Core 0)
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


