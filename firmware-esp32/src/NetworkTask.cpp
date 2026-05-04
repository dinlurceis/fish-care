#include "NetworkTask.h"
#include "TaskDelay.h"
#include <WiFi.h>
#include <WiFiManager.h>      // ThÆ° viá»‡n quáº£n lĂ½ WiFi thĂ´ng minh
#include <FirebaseESP32.h>
#include <esp_task_wdt.h>
#include <time.h>
#include "secrets.h"          // Chá»‰ chá»©a FIREBASE_HOST vĂ  FIREBASE_API_KEY / SECRET

// ============================================================
//  NETWORKTASK - WiFiManager, Firebase Stream, Task Communication
//  Chá»‹u trĂ¡ch nhiá»‡m: HoĂ ng
//  Kiáº¿n trĂºc: Event-Driven (Firebase Stream) + JSON Batch Push
// ============================================================

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  FIREBASE SDK - GLOBAL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
FirebaseData fbData;        // DĂ¹ng riĂªng Ä‘á»ƒ Ä‘áº©y dá»¯ liá»‡u lĂªn (Upload)
FirebaseData streamData;    // DĂ¹ng RIĂNG Ä‘á»ƒ há»©ng dá»¯ liá»‡u Ä‘áº©y xuá»‘ng (Stream) - CHá»NG CRASH
FirebaseConfig fbConfig;
FirebaseAuth fbAuth;

extern SemaphoreHandle_t xMutex_Firebase;
extern QueueHandle_t xQueue_AutoCommands;
extern QueueHandle_t xQueue_FeedCommands;
extern QueueHandle_t xQueue_SensorData;

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  TRáº NG THĂI Káº¾T Ná»I
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
bool s_WiFiConnected = false;
bool s_FirebaseReady = false;
bool s_FirebaseStreamStarted = false;
TaskHandle_t s_TaskHandle = nullptr;
String s_CurrentFeedingMode = "idle";  // Theo dõi chế độ cho ăn để handle state changes
bool s_ThucanState = false;             // Theo dõi state hiện tại (true=bật, false=tắt)
float s_TargetGram = 0.0f;              // Theo dõi target gram để check khi state thay đổi

namespace {

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  1. HĂ€M Káº¾T Ná»I WIFI Tá»° Äá»˜NG (WIFIMANAGER)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
void connectWiFiManager() {
    Serial.println("[NetworkTask] Báº¯t Ä‘áº§u khá»Ÿi Ä‘á»™ng WiFiManager...");
    
    WiFiManager wm;
    
    // Äáº·t Timeout lĂ  3 phĂºt (180 giĂ¢y). 
    // Náº¿u quĂ¡ 3 phĂºt khĂ´ng ai káº¿t ná»‘i vĂ o WiFi áº£o Ä‘á»ƒ cĂ i Ä‘áº·t, ESP32 sáº½ bá» qua Ä‘á»ƒ cháº¡y code Offline
    wm.setConfigPortalTimeout(180); 
    
    // Äáº·t giao diá»‡n tá»‘i cho ngáº§u (TĂ¹y chá»n)
    wm.setClass("invert");

    // Khá»Ÿi táº¡o tráº¡m phĂ¡t WiFi áº£o
    // TĂªn WiFi: FishCare_AP  |  Máº­t kháº©u: 12345678
    Serial.println("[NetworkTask] Äang tĂ¬m WiFi cÅ©... Náº¿u khĂ´ng tháº¥y sáº½ phĂ¡t WiFi 'FishCare_AP'");
    bool res = wm.autoConnect("FishCare_AP", "12345678"); 
    //wm.resetSettings();
    if (!res) {
        Serial.println("[NetworkTask] â ï¸ Háº¿t thá»i gian cĂ i Ä‘áº·t WiFi hoáº·c lá»—i. Chuyá»ƒn sang cháº¿ Ä‘á»™ OFFLINE!");
        s_WiFiConnected = false;
    } else {
        s_WiFiConnected = true;
        // Ă‰p DNS Google Ä‘á»ƒ Firebase káº¿t ná»‘i á»•n Ä‘á»‹nh hÆ¡n, khĂ´ng bá»‹ lá»—i phĂ¢n giáº£i tĂªn miá»n
        IPAddress dns1(8, 8, 8, 8);
        IPAddress dns2(8, 8, 4, 4);
        WiFi.config(WiFi.localIP(), WiFi.gatewayIP(), WiFi.subnetMask(), dns1, dns2);
        
        Serial.printf("\n[NetworkTask] âœ“ Káº¿t ná»‘i thĂ nh cĂ´ng tá»›i WiFi: %s\n", WiFi.SSID().c_str());
        Serial.printf("[NetworkTask] IP: %s\n", WiFi.localIP().toString().c_str());
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  2. CALLBACK: Há»¨NG Sá»° KIá»†N Tá»ª FIREBASE STREAM
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
void firebaseCallback(StreamData data) {
   if (data.eventType() == "put" || data.eventType() == "patch") {
        String path = data.dataPath();
        
        // Bá» qua cĂ¡c tĂ­n hiá»‡u rĂ¡c hoáº·c node gá»‘c
        if (path == "/") return; 
        
        Serial.printf("[Firebase_Stream] Lá»‡nh má»›i tá»›i! Path: %s\n", path.c_str());

        // â”€â”€ Lá»‡nh Guá»“ng Oxy â”€â”€
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
        
        // â”€â”€ Lá»‡nh Thá»©c Äƒn â”€â”€
        else if (path.indexOf("mode") != -1 && path.indexOf("thucan") != -1) {
            String mode = data.stringData();
            // Lưu lại mode
            s_CurrentFeedingMode = mode;
            Serial.printf("[Stream] THUCAN_MODE changed: %s\n", mode.c_str());
            
            // Nếu state đã = true, tự động activate ngay theo mode mới
            if (s_ThucanState) {
                CommandData_t cmd = {.timestamp = millis()};
                
                if (mode == "gram") {
                    if (s_TargetGram > 0) {
                        cmd.type = CMD_THUCAN_GRAM;
                        cmd.value = -1.0f;
                        Serial.printf("[Stream] AUTO-ACTIVATE GRAM (state=true, mode changed, target=%.1fg)\n", s_TargetGram);
                        xQueueSend(xQueue_FeedCommands, &cmd, 0);
                    } else {
                        Serial.printf("[Stream] Mode=gram but no target gram, waiting...\n");
                    }
                } else if (mode == "auto") {
                    cmd.type = CMD_THUCAN_AUTO;
                    cmd.value = 1.0f;
                    Serial.printf("[Stream] AUTO-ACTIVATE AUTO (state=true, mode changed)\n");
                    xQueueSend(xQueue_FeedCommands, &cmd, 0);
                } else if (mode == "manual") {
                    cmd.type = CMD_THUCAN_MANUAL;
                    cmd.value = 1.0f;
                    Serial.printf("[Stream] AUTO-ACTIVATE MANUAL (state=true, mode changed)\n");
                    xQueueSend(xQueue_FeedCommands, &cmd, 0);
                }
            }
        }
        else if (path.indexOf("target_gram") != -1) {
            float targetGram = data.floatData();
            s_TargetGram = targetGram;  // Lưu target gram để check khi state thay đổi
            
            CommandData_t cmd = {.timestamp = millis()};
            cmd.type = CMD_THUCAN_GRAM;
            cmd.value = targetGram;
            xQueueSend(xQueue_FeedCommands, &cmd, 0);
            Serial.printf("[Stream] CMD_THUCAN_GRAM SET TARGET: %.1fg\n", targetGram);
            
            // Nếu state đã = true, tự động activate ngay
            if (s_ThucanState && targetGram > 0) {
                CommandData_t activateCmd = {
                    .type = CMD_THUCAN_GRAM,
                    .value = -1.0f,  // Activate
                    .timestamp = millis()
                };
                xQueueSend(xQueue_FeedCommands, &activateCmd, 0);
                Serial.printf("[Stream] AUTO-ACTIVATE GRAM (state=true + target=%.1fg)\n", targetGram);
            }
        }
        else if (path.indexOf("state") != -1 && path.indexOf("thucan") != -1) {
            // Xử lý kích hoạt/tắt cho ăn
            bool stateValue = data.boolData();
            s_ThucanState = stateValue;  // Lưu state hiện tại
            
            CommandData_t cmd = {.timestamp = millis()};
            
            if (!stateValue) {
                // state=false: Tắt/Deactivate tất cả modes
                cmd.type = CMD_THUCAN_GRAM;
                cmd.value = 0.0f;
                Serial.printf("[Stream] CMD_THUCAN_STATE: OFF (DEACTIVATE)\n");
                xQueueSend(xQueue_FeedCommands, &cmd, 0);
            } else {
                // state=true: Kích hoạt theo mode hiện tại
                if (s_CurrentFeedingMode == "gram") {
                    // Gram mode: cần có target_gram > 0
                    if (s_TargetGram <= 0) {
                        Serial.printf("[Stream] CMD_THUCAN_STATE: ON - GRAM mode nhưng chưa có target gram, đợi...\n");
                        return;  // Chưa có target, không activate
                    }
                    cmd.type = CMD_THUCAN_GRAM;
                    cmd.value = -1.0f;
                    Serial.printf("[Stream] CMD_THUCAN_STATE: ON - Activate GRAM (target=%.1fg)\n", s_TargetGram);
                } 
                else if (s_CurrentFeedingMode == "auto") {
                    // Auto mode: không cần target, gửi activate ngay
                    cmd.type = CMD_THUCAN_AUTO;
                    cmd.value = 1.0f;
                    Serial.printf("[Stream] CMD_THUCAN_STATE: ON - Activate AUTO mode\n");
                } 
                else if (s_CurrentFeedingMode == "manual") {
                    // Manual mode: không cần target, bật motor ngay
                    cmd.type = CMD_THUCAN_MANUAL;
                    cmd.value = 1.0f;
                    Serial.printf("[Stream] CMD_THUCAN_STATE: ON - Activate MANUAL\n");
                } else {
                    // Mode chưa set, không activate (chỉ lưu state, chờ mode được set)
                    Serial.printf("[Stream] CMD_THUCAN_STATE: ON - Mode chưa set, vui lòng set mode trước\n");
                    return;
                }
                xQueueSend(xQueue_FeedCommands, &cmd, 0);
            }
        }
    }
}

void streamTimeoutCallback(bool timeout) {
    if (timeout) {
        Serial.println("[Firebase_Stream] â³ Timeout - Äang káº¿t ná»‘i láº¡i Stream...");
        s_FirebaseStreamStarted = false; // ÄĂ¡nh dáº¥u Ä‘á»ƒ vĂ²ng láº·p chĂ­nh tá»± táº¡o láº¡i stream
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  3. KHá»I Táº O FIREBASE (Chuáº©n Auth Email/Password)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
void initFirebase() {
    Serial.println("[NetworkTask] Khá»Ÿi táº¡o Firebase...");
    
    // Gáº¯n Host vĂ  API Key tá»« secrets.h
    fbConfig.database_url = FIREBASE_HOST;
    fbConfig.api_key = FIREBASE_API_KEY;
    
    // Cáº¥u hĂ¬nh Authentication (Email & Password)
    fbAuth.user.email = USER_EMAIL;
    fbAuth.user.password = USER_PASSWORD;
    
    // TĂ¹y chá»‰nh Timeout SSL
    fbConfig.timeout.serverResponse = 6000;
    fbConfig.timeout.socketConnection = 5000;
    fbConfig.timeout.sslHandshake = 5000;
    
    // Báº®T BUá»˜C: Truyá»n cáº£ fbConfig vĂ  fbAuth vĂ o hĂ m begin
    Firebase.begin(&fbConfig, &fbAuth);
    Firebase.reconnectNetwork(true);
    
    s_FirebaseReady = true;
    Serial.println("[NetworkTask] âœ“ Firebase init xong!");
}
void startFirebaseStream() {
    if (!s_FirebaseReady || !Firebase.ready() || s_FirebaseStreamStarted) return;
    
    Serial.println("[NetworkTask] Äang má»Ÿ á»‘ng Stream láº¯ng nghe App...");
    
    // LÆ¯U Ă: DĂ¹ng biáº¿n streamData riĂªng biá»‡t, khĂ´ng dĂ¹ng fbData Ä‘á»ƒ trĂ¡nh CRASH
    if (!Firebase.beginStream(streamData, "/aquarium/control")) {
        Serial.printf("[Firebase] âŒ Lá»—i má»Ÿ Stream: %s\n", streamData.errorReason().c_str());
        return;
    }
    
    Firebase.setStreamCallback(streamData, firebaseCallback, streamTimeoutCallback);
    s_FirebaseStreamStarted = true;
    Serial.println("[Firebase Stream] âœ“ ÄĂ£ cáº¯m á»‘ng Stream - Sáºµn sĂ ng nháº­n lá»‡nh!");
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  4. HĂ€M Äáº¨Y Dá»® LIá»†U Cáº¢M BIáº¾N (Cháº¡y Ä‘á»‹nh ká»³)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
void syncSensorDataToFirebase(const SensorData_t& data) {
    if (!s_FirebaseReady || !Firebase.ready()) return;
    
    // Cá»‘ gáº¯ng láº¥y khĂ³a Mutex trong 1 giĂ¢y, náº¿u báº­n thĂ¬ bá» qua láº§n nĂ y
    if (xSemaphoreTake(xMutex_Firebase, pdMS_TO_TICKS(1000)) == pdTRUE) {
        
        // ÄĂ“NG GĂ“I JSON: Gá»­i 1 cá»¥c thay vĂ¬ 4 request láº» táº» -> Tiáº¿t kiá»‡m Quota máº¡ng
        FirebaseJson json;
        json.set("temperature", data.temperature);
        json.set("water_quality", data.tds);
        json.set("ts300b", data.turbidity);
        json.set("weight", data.weight);
        
        if (Firebase.updateNode(fbData, "/aquarium", json)) {
            Serial.printf("[NetworkTask] âœ“ Cáº­p nháº­t Cáº£m biáº¿n OK (Temp:%.1f, TDS:%.1f)\n", data.temperature, data.tds);
        } else {
            Serial.printf("[NetworkTask] â ï¸ Cáº­p nháº­t lá»—i: %s\n", fbData.errorReason().c_str());
        }
        
        // â”€â”€ Ghi Chart Log (Má»—i 60s) â”€â”€
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

        // Nháº£ khĂ³a Mutex cho cĂ¡c Task khĂ¡c
        xSemaphoreGive(xMutex_Firebase);
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  TASK LOOP CHĂNH
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
void networkTaskLoop(void* unused) {
    // 1. CHáº Y WIFIMANAGER TRÆ¯á»C TIĂN (Cháº·n luá»“ng cho Ä‘áº¿n khi cĂ³ máº¡ng hoáº·c timeout)
    connectWiFiManager();
    
    // Náº¿u cĂ³ máº¡ng thĂ¬ cáº¥u hĂ¬nh giá» NTP vĂ  Firebase
    if (s_WiFiConnected) {
        configTime(7 * 3600, 0, "time.google.com", "pool.ntp.org");
        initFirebase();
    }
    
    unsigned long lastSensorSync = 0;
    static unsigned long wifiLostTime = 0;
    
    for (;;) {
        
        // KIá»‚M TRA Máº NG Báº°NG THÆ¯ VIá»†N Gá»C (ESP32 sáº½ tá»± reconnect á»Ÿ background)
        if (WiFi.status() != WL_CONNECTED) {
            if (s_WiFiConnected) {
                Serial.println("[NetworkTask] â ï¸ Máº¥t WiFi - Äang chá» ESP32 tá»± káº¿t ná»‘i láº¡i...");
                s_WiFiConnected = false;
                s_FirebaseStreamStarted = false;
                wifiLostTime = millis();
            }
            
            // Náº¿u Ä‘á»©t máº¡ng quĂ¡ 15 phĂºt khĂ´ng tá»± ná»‘i láº¡i Ä‘Æ°á»£c -> Reset máº¡ch cho sáº¡ch RAM
            if (millis() - wifiLostTime > 900000) { 
                Serial.println("[NetworkTask] đŸ¨ Máº¥t máº¡ng quĂ¡ lĂ¢u -> Khá»Ÿi Ä‘á»™ng láº¡i há»‡ thá»‘ng!");
                ESP.restart();
            }
        } else {
            // Má»›i cĂ³ máº¡ng trá»Ÿ láº¡i
            if (!s_WiFiConnected) {
                Serial.println("[NetworkTask] đŸŒ ÄĂ£ cĂ³ WiFi trá»Ÿ láº¡i!");
                s_WiFiConnected = true;
                if (!s_FirebaseReady) initFirebase();
            }
            
            // Äáº£m báº£o Stream luĂ´n sá»‘ng
            if (s_FirebaseReady && !s_FirebaseStreamStarted) {
                startFirebaseStream();
            }
            
            // Äáº©y dá»¯ liá»‡u lĂªn mĂ¢y má»—i 2 giĂ¢y
            if (s_FirebaseReady && (millis() - lastSensorSync > 2000)) {
                SensorData_t sensorData = {0};
                if (xQueuePeek(xQueue_SensorData, &sensorData, 0) == pdPASS) {
                    syncSensorDataToFirebase(sensorData);
                }
                lastSensorSync = millis();
            }
        }
        
        // Reset Watchdog Timer Ä‘á»ƒ chá»‘ng treo chip
        esp_task_wdt_reset();
        
        // NhÆ°á»ng CPU 1 giĂ¢y
        vTaskDelay(pdMS_TO_TICKS(1000)); 
    }
}

}  // namespace

// ============================================================
//  HĂ€M PUBLIC
// ============================================================

void NetworkTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[NetworkTask_init] Táº¡o FreeRTOS task...");
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        networkTaskLoop, "NetworkTask", stackSize, nullptr, priority, &s_TaskHandle, 1);
        
    if (xReturned == pdPASS) Serial.println("[NetworkTask_init] âœ“ Task táº¡o thĂ nh cĂ´ng");
    else Serial.println("[NetworkTask_init] âœ— Lá»—i táº¡o task!");
}

bool NetworkTask_IsWiFiConnected() { return s_WiFiConnected; }
bool NetworkTask_IsFirebaseConnected() { return s_FirebaseReady && Firebase.ready(); }