#include "NetworkTask.h"
#include "TaskDelay.h"
#include <WiFi.h>
#include <WiFiManager.h>      // Thư viện quản lý WiFi thông minh
#include <FirebaseESP32.h>
#include <esp_task_wdt.h>
#include <time.h>
#include "secrets.h"          // Chỉ chứa FIREBASE_HOST và FIREBASE_API_KEY / SECRET

//  NETWORKTASK - WiFiManager, Firebase Stream, Task Communication
//  Kiến trúc: Event-Driven (Firebase Stream) + JSON Batch Push

//  FIREBASE SDK - GLOBAL
FirebaseData fbData;        // Dùng riêng để đẩy dữ liệu lên (Upload)
FirebaseData streamData;    // Dùng RIÊNG để hứng dữ liệu đẩy xuống (Stream) - CHỐNG CRASH
FirebaseConfig fbConfig;
FirebaseAuth fbAuth;

extern SemaphoreHandle_t xMutex_Firebase;
extern QueueHandle_t xQueue_AutoCommands;
extern QueueHandle_t xQueue_FeedCommands;
extern QueueHandle_t xQueue_SensorData;

//  TRẠNG THÁI KẾT NỐI
bool s_WiFiConnected = false;
bool s_FirebaseReady = false;
bool s_FirebaseStreamStarted = false;
TaskHandle_t s_TaskHandle = nullptr;

namespace {

//  1. HÀM KẾT NỐI WIFI TỰ ĐỘNG (WIFIMANAGER)
void connectWiFiManager() {
    Serial.println("[NetworkTask] Bắt đầu khởi động WiFiManager...");
    
    WiFiManager wm;
    
    // Đặt Timeout là 3 phút (180 giây). 
    // Nếu quá 3 phút không ai kết nối vào WiFi ảo để cài đặt, ESP32 sẽ bỏ qua để chạy code Offline
    wm.setConfigPortalTimeout(180); 
    
    // Đặt giao diện tối cho ngầu (Tùy chọn)
    wm.setClass("invert");

    // Khởi tạo trạm phát WiFi ảo
    // Tên WiFi: FishCare_AP  |  Mật khẩu: 12345678
    Serial.println("[NetworkTask] Đang tìm WiFi cũ... Nếu không thấy sẽ phát WiFi 'FishCare_AP'");
    bool res = wm.autoConnect("FishCare_AP", "12345678"); 
    //wm.resetSettings();
    if (!res) {
        Serial.println("[NetworkTask] ⚠️ Hết thời gian cài đặt WiFi hoặc lỗi. Chuyển sang chế độ OFFLINE!");
        s_WiFiConnected = false;
    } else {
        s_WiFiConnected = true;
        // Ép DNS Google để Firebase kết nối ổn định hơn, không bị lỗi phân giải tên miền
        IPAddress dns1(8, 8, 8, 8);
        IPAddress dns2(8, 8, 4, 4);
        WiFi.config(WiFi.localIP(), WiFi.gatewayIP(), WiFi.subnetMask(), dns1, dns2);
        
        Serial.printf("\n[NetworkTask] ✓ Kết nối thành công tới WiFi: %s\n", WiFi.SSID().c_str());
        Serial.printf("[NetworkTask] IP: %s\n", WiFi.localIP().toString().c_str());
    }
}

//  2. CALLBACK: HỨNG SỰ KIỆN TỪ FIREBASE STREAM
void firebaseCallback(StreamData data) {
   if (data.eventType() == "put" || data.eventType() == "patch") {
        String path = data.dataPath();
        
        // Bỏ qua các tín hiệu rác hoặc node gốc
        if (path == "/") return; 
        
        Serial.printf("[Firebase_Stream] Lệnh mới tới! Path: %s\n", path.c_str());

        // Lệnh Guồng Oxy
        if (path.indexOf("guong") != -1) {
            bool oxyState = data.boolData();
            CommandData_t cmd = {
                .type = oxyState ? CMD_GUONG_ON : CMD_GUONG_OFF,
                .value = 0,
                .timestamp = millis()
            };
            xQueueSend(xQueue_AutoCommands, &cmd, 0);
            Serial.printf("[Stream] CMD_GUONG: %s\n", oxyState ? "ON" : "OFF");
        }
        
        // Lệnh Thức ăn
        else if (path.indexOf("mode") != -1) {
            String mode = data.stringData();
            CommandData_t cmd = {.timestamp = millis()};
            if (mode == "auto") { cmd.type = CMD_THUCAN_AUTO; cmd.value = 1.0f; }
            else if (mode == "gram") { cmd.type = CMD_THUCAN_GRAM; cmd.value = 0; }
            else if (mode == "manual") { cmd.type = CMD_THUCAN_MANUAL; cmd.value = 0; }
            
            xQueueSend(xQueue_FeedCommands, &cmd, 0);
            Serial.printf("[Stream] CMD_THUCAN_MODE: %s\n", mode.c_str());
        }
        else if (path.indexOf("target_gram") != -1) {
            CommandData_t cmd = {
                .type = CMD_THUCAN_GRAM,
                .value = data.floatData(),
                .timestamp = millis()
            };
            xQueueSend(xQueue_FeedCommands, &cmd, 0);
            Serial.printf("[Stream] CMD_THUCAN_GRAM: %.1fg\n", data.floatData());
        }
        else if (path.indexOf("state") != -1 && path.indexOf("thucan") != -1) {
            CommandData_t cmd = {
                .type = CMD_THUCAN_MANUAL,
                .value = data.boolData() ? 1.0f : 0.0f,
                .timestamp = millis()
            };
            xQueueSend(xQueue_FeedCommands, &cmd, 0);
            Serial.printf("[Stream] CMD_THUCAN_STATE: %s\n", data.boolData() ? "ON" : "OFF");
        }
    }
}

void streamTimeoutCallback(bool timeout) {
    if (timeout) {
        Serial.println("[Firebase_Stream] ⏳ Timeout - Đang kết nối lại Stream...");
        s_FirebaseStreamStarted = false; // Đánh dấu để vòng lặp chính tự tạo lại stream
    }
}

//  3. KHỞI TẠO FIREBASE (Chuẩn Auth Email/Password)
void initFirebase() {
    Serial.println("[NetworkTask] Khởi tạo Firebase...");
    
    // Gắn Host và API Key từ secrets.h
    fbConfig.database_url = FIREBASE_HOST;
    fbConfig.api_key = FIREBASE_API_KEY;
    
    // Cấu hình Authentication (Email & Password)
    fbAuth.user.email = USER_EMAIL;
    fbAuth.user.password = USER_PASSWORD;
    
    // Tùy chỉnh Timeout SSL
    fbConfig.timeout.serverResponse = 6000;
    fbConfig.timeout.socketConnection = 5000;
    fbConfig.timeout.sslHandshake = 5000;
    
    // BẮT BUỘC: Truyền cả fbConfig và fbAuth vào hàm begin
    Firebase.begin(&fbConfig, &fbAuth);
    Firebase.reconnectNetwork(true);
    
    s_FirebaseReady = true;
    Serial.println("[NetworkTask] ✓ Firebase init xong!");
}
void startFirebaseStream() {
    if (!s_FirebaseReady || !Firebase.ready() || s_FirebaseStreamStarted) return;
    
    Serial.println("[NetworkTask] Đang mở ống Stream lắng nghe App...");
    
    // LƯU Ý: Dùng biến streamData riêng biệt, không dùng fbData để tránh CRASH
    if (!Firebase.beginStream(streamData, "/aquarium/control")) {
        Serial.printf("[Firebase] ❌ Lỗi mở Stream: %s\n", streamData.errorReason().c_str());
        return;
    }
    
    Firebase.setStreamCallback(streamData, firebaseCallback, streamTimeoutCallback);
    s_FirebaseStreamStarted = true;
    Serial.println("[Firebase Stream] ✓ Đã cắm ống Stream - Sẵn sàng nhận lệnh!");
}

//  4. HÀM ĐẨY DỮ LIỆU CẢM BIẾN (Chạy định kỳ)
void syncSensorDataToFirebase(const SensorData_t& data) {
    if (!s_FirebaseReady || !Firebase.ready()) return;
    
    // Cố gắng lấy khóa Mutex trong 1 giây, nếu bận thì bỏ qua lần này
    if (xSemaphoreTake(xMutex_Firebase, pdMS_TO_TICKS(1000)) == pdTRUE) {
        
        // ĐÓNG GÓI JSON: Gửi 1 cục thay vì 4 request lẻ tẻ 
        FirebaseJson json;
        json.set("temperature", data.temperature);
        json.set("water_quality", data.tds);
        json.set("ts300b", data.turbidity);
        json.set("weight", data.weight);
        
        if (Firebase.updateNode(fbData, "/aquarium", json)) {
            Serial.printf("[NetworkTask] Cập nhật Cảm biến OK (Temp:%.1f, TDS:%.1f)\n", data.temperature, data.tds);
        } else {
            Serial.printf("[NetworkTask] Cập nhật lỗi: %s\n", fbData.errorReason().c_str());
        }
        
        // Ghi Chart Log (Mỗi 60s)
        static unsigned long s_lastChartSync = 0;
        if (millis() - s_lastChartSync > 60000 || s_lastChartSync == 0) {
            time_t now = time(nullptr);
            if (now > 24 * 3600) { 
                long long tsMs = (long long)now * 1000LL;
                String path = "/tds_logs/" + String(tsMs);
                Firebase.setFloat(fbData, path, data.tds);
                s_lastChartSync = millis();
            }
        }

        // Nhả khóa Mutex cho các Task khác
        xSemaphoreGive(xMutex_Firebase);
    }
}

//  TASK LOOP CHÍNH
void networkTaskLoop(void* unused) {
    // 1. CHẠY WIFIMANAGER TRƯỚC TIÊN (Chặn luồng cho đến khi có mạng hoặc timeout)
    connectWiFiManager();
    
    // Nếu có mạng thì cấu hình giờ NTP và Firebase
    if (s_WiFiConnected) {
        configTime(7 * 3600, 0, "time.google.com", "pool.ntp.org");
        initFirebase();
    }
    
    unsigned long lastSensorSync = 0;
    static unsigned long wifiLostTime = 0;
    
    for (;;) {
        
        // KIỂM TRA MẠNG BẰNG THƯ VIỆN GỐC (ESP32 sẽ tự reconnect ở background)
        if (WiFi.status() != WL_CONNECTED) {
            if (s_WiFiConnected) {
                Serial.println("[NetworkTask] ⚠️ Mất WiFi - Đang chờ ESP32 tự kết nối lại...");
                s_WiFiConnected = false;
                s_FirebaseStreamStarted = false;
                wifiLostTime = millis();
            }
            
            // Nếu đứt mạng quá 15 phút không tự nối lại được -> Reset mạch cho sạch RAM
            if (millis() - wifiLostTime > 900000) { 
                Serial.println("[NetworkTask] 🚨 Mất mạng quá lâu -> Khởi động lại hệ thống!");
                ESP.restart();
            }
        } else {
            // Mới có mạng trở lại
            if (!s_WiFiConnected) {
                Serial.println("[NetworkTask] 🌐 Đã có WiFi trở lại!");
                s_WiFiConnected = true;
                if (!s_FirebaseReady) initFirebase();
            }
            
            // Đảm bảo Stream luôn sống
            if (s_FirebaseReady && !s_FirebaseStreamStarted) {
                startFirebaseStream();
            }
            
            // Đẩy dữ liệu lên mây mỗi 2 giây
            if (s_FirebaseReady && (millis() - lastSensorSync > 2000)) {
                SensorData_t sensorData = {0};
                if (xQueuePeek(xQueue_SensorData, &sensorData, 0) == pdPASS) {
                    syncSensorDataToFirebase(sensorData);
                }
                lastSensorSync = millis();
            }
        }
        
        // Reset Watchdog Timer để chống treo chip
        esp_task_wdt_reset();
        
        // Nhường CPU 1 giây
        vTaskDelay(pdMS_TO_TICKS(1000)); 
    }
}

} 

//  HÀM PUBLIC

void NetworkTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[NetworkTask_init] Tạo FreeRTOS task...");
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        networkTaskLoop, "NetworkTask", stackSize, nullptr, priority, &s_TaskHandle, 1);
        
    if (xReturned == pdPASS) Serial.println("[NetworkTask_init] ✓ Task tạo thành công");
    else Serial.println("[NetworkTask_init] ✗ Lỗi tạo task!");
}

bool NetworkTask_IsWiFiConnected() { return s_WiFiConnected; }
bool NetworkTask_IsFirebaseConnected() { return s_FirebaseReady && Firebase.ready(); }