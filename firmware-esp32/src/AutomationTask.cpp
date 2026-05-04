#include "AutomationTask.h"
#include "NetworkTask.h"
#include "TaskDelay.h"

//  AUTOMATIONTASK - Điều khiển Motor A (Oxy) + Edge Logic
//  Chi tiết: Bật/tắt oxy từ Firebase, tự động bật khi rớt mạng & môi trường xấu

namespace {

TaskHandle_t s_TaskHandle = nullptr;

//  TRẠNG THÁI OXY
bool s_OxyRunning = false;
bool s_EdgeOverrideActive = false;  // Flag: đang chạy edge logic offline
unsigned long s_EdgeOverrideStartTime = 0;
unsigned long s_LastDebugPrintTime = 0;  // Timer để giảm spam log

//  CÁC NGƯỠNG EDGE LOGIC (OFFLINE THRESHOLDS)
const float TEMP_THRESHOLD_HIGH = 32.0f;       // °C - Nước quá nóng
const float TDS_THRESHOLD_HIGH = 1000.0f;      // ppm - Nước quá bẩn
const int TURBIDITY_THRESHOLD_LOW = 1500;      // Nước đục (Giá trị ADC càng thấp càng đục)

const uint32_t EDGE_AUTO_DURATION_MS = 5UL * 60UL * 1000UL;  // 5 phút duy trì (Hysteresis)

//  ĐIỀU KHIỂN MOTOR A (Oxy)

void startOxy() {
    // Bật motor: ENA=HIGH, IN1=HIGH, IN2=LOW
    digitalWrite(PIN_OXY_EN, HIGH);
    digitalWrite(PIN_OXY_IN1, HIGH);
    digitalWrite(PIN_OXY_IN2, LOW);
    s_OxyRunning = true;
    Serial.println("[AutomationTask] ✓ Guồng Oxy BẬT");
}

void stopOxy() {
    // Tắt motor: tất cả = LOW
    digitalWrite(PIN_OXY_EN, LOW);
    digitalWrite(PIN_OXY_IN1, LOW);
    digitalWrite(PIN_OXY_IN2, LOW);
    s_OxyRunning = false;
    Serial.println("[AutomationTask] ✓ Guồng Oxy TẮT");
}

//  TASK LOOP CHÍNH
void automationTaskLoop(void* unused) {
    // Khởi tạo Hardware
    Serial.println("[AutomationTask] Khởi tạo Motor A (Oxy)...");
    
    // Motor A control pins: ENA(5), IN1(26), IN2(27)
    pinMode(PIN_OXY_EN, OUTPUT);
    pinMode(PIN_OXY_IN1, OUTPUT);
    pinMode(PIN_OXY_IN2, OUTPUT);
    stopOxy();  // Tắt ban đầu
    
    Serial.println("[AutomationTask] ✓ Motor A sẵn sàng");
    
    // Vòng lặp chính
    for (;;) {
        
        //  1. LỰA CHỌN NGUỒN ĐIỀU KHIỂN
        //     - Firebase: Khi có mạng
        //     - Edge Logic: Khi rớt mạng
        
       if (NetworkTask_IsWiFiConnected()) {
            // === CHẾ ĐỘ FIREBASE ===
            // Nhận lệnh từ NetworkTask (qua xQueue_AutoCommands)
            
            CommandData_t cmd;
            memset(&cmd, 0, sizeof(cmd));
            if (xQueueReceive(xQueue_AutoCommands, &cmd, 0) == pdPASS) {
                switch (cmd.type) {
                    case CMD_GUONG_ON:
                        startOxy();
                        Serial.println("[AutomationTask] CMD_GUONG_ON - Bật oxy");
                        break;
                    case CMD_GUONG_OFF:
                        stopOxy();
                        Serial.println("[AutomationTask] CMD_GUONG_OFF - Tắt oxy");
                        break;
                    default:
                        // Lệnh khác (feeding) không liên quan đến AutomationTask
                        break;
                }
            }
            
            // Xóa edge override và khôi phục trạng thái Firebase khi có mạng lại
            if (s_EdgeOverrideActive) {
                s_EdgeOverrideActive = false;
                stopOxy(); // Tắt motor để tránh kẹt trạng thái (State Sync Bug)
                Serial.println("[AutomationTask] 🌐 WiFi khôi phục - Tắt Edge Override, chờ lệnh từ Firebase");
            }
        } else {
            // === CHẾ ĐỘ EDGE LOGIC (OFFLINE) ===
            // Khi WiFi mất mà nước xấu → tự động bật oxy bảo vệ cá
            
            SensorData_t sensorData = {0};
            
            // Lấy dữ liệu sensor mới nhất từ queue
            if (xQueuePeek(xQueue_SensorData, &sensorData, 0) == pdPASS) {
                
                // Đánh giá cả 3 điều kiện: Nóng quá, Bẩn quá, Đục quá
                bool tempHigh   = (sensorData.temperature > TEMP_THRESHOLD_HIGH);
                bool tdsHigh    = (sensorData.tds > TDS_THRESHOLD_HIGH);
                bool waterDirty = (sensorData.turbidity < TURBIDITY_THRESHOLD_LOW);
                
                if ((tempHigh || tdsHigh || waterDirty) && !s_EdgeOverrideActive) {
                    // Kích hoạt edge override
                    startOxy();
                    s_EdgeOverrideActive = true;
                    s_EdgeOverrideStartTime = millis();
                    
                    Serial.printf("[AutomationTask] ⚠️ EDGE LOGIC: Mất mạng + Môi trường xấu (Nhiệt:%.1f, TDS:%.1f, Đục:%d) → Bật oxy 5 phút\n",
                                  sensorData.temperature, sensorData.tds, sensorData.turbidity);
                }
                
                // Kiểm timeout: Tắt oxy sau 5 phút (Chống cháy motor)
                if (s_EdgeOverrideActive && 
                    (millis() - s_EdgeOverrideStartTime > EDGE_AUTO_DURATION_MS)) {
                    
                    stopOxy();
                    s_EdgeOverrideActive = false;
                    
                    Serial.println("[AutomationTask] ⏱️ Edge override timeout - Tắt oxy");
                }
            }
        }
        
        // Debug Serial (Mỗi 5 giây)
        if (millis() - s_LastDebugPrintTime > 5000) {
            Serial.printf("[AutomationTask] WiFi=%s | Oxy=%s | EdgeOverride=%s\n",
                          NetworkTask_IsWiFiConnected() ? "ON" : "OFF",
                          s_OxyRunning ? "ON" : "OFF",
                          s_EdgeOverrideActive ? "ACTIVE" : "INACTIVE");
            s_LastDebugPrintTime = millis();
        }
        
        // Delay nhường CPU
        // (Sử dụng config từ AutomationTask.h)
        vTaskDelay(pdMS_TO_TICKS(AUTOMATION_CHECK_INTERVAL_MS));  
    }
}

} 

//  HÀM PUBLIC

void AutomationTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[AutomationTask_init] Tạo FreeRTOS task...");
    
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        automationTaskLoop,       // Hàm task
        "AutomationTask",         // Tên task
        stackSize,                // Stack size
        nullptr,                  // Parameter
        priority,                 // Priority (từ main.cpp)
        &s_TaskHandle,            // Handle output
        1                         // Core 1 (Quy hoạch Single-Core)
    );
    
    if (xReturned == pdPASS) {
        Serial.println("[AutomationTask_init] ✓ Task tạo thành công");
    } else {
        Serial.println("[AutomationTask_init] ✗ Lỗi tạo task!");
    }
}

bool AutomationTask_IsOxyRunning() {
    return s_OxyRunning;
}

bool AutomationTask_IsOffline() {
    return !isWiFiConnected;
}

uint32_t AutomationTask_GetOxyRuntime() {
    if (s_EdgeOverrideActive) {
        return millis() - s_EdgeOverrideStartTime;
    }
    return 0;
}