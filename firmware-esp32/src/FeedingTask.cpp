#include "FeedingTask.h"
#include "NetworkTask.h"  // For Firebase access
#include <HX711.h>
#include <time.h>

// ============================================================
//  FEEDINGTASK - Điều khiển Motor B (nhả cám) + LoadCell
//  Chịu trách nhiệm: Dũng
//  Chi tiết: 3 chế độ (Auto, Gram, Manual), Timeout 30s bảo vệ motor
// ============================================================

namespace {

TaskHandle_t s_TaskHandle = nullptr;

// ─────────────────────────────────────────────────────────
//  TRẠNG THÁI FEEDING - Theo dõi quá trình nhả cám
// ─────────────────────────────────────────────────────────

// Chế độ feeding hiện tại
enum FeedMode_e { FEED_IDLE = 0, FEED_AUTO = 1, FEED_GRAM = 2, FEED_MANUAL = 3 };
FeedMode_e s_CurrentMode = FEED_IDLE;

// Trạng thái motor
bool s_MotorRunning = false;
unsigned long s_MotorStartTime = 0;

// Gram mode: mục tiêu và điểm bắt đầu
float s_GramTarget = 0.0f;
float s_GramStartWeight = 0.0f;

// Auto mode: điểm bắt đầu
float s_AutoStartWeight = 0.0f;
unsigned long s_AutoStartTime = 0;

// ─────────────────────────────────────────────────────────
//  LOADCELL HX711 - Cân nặng cám
// ─────────────────────────────────────────────────────────
HX711 s_LoadCell;
const float LOADCELL_SCALE_FACTOR = 505.4633;  // Từ PROJECT_CONTEXT.md

float readWeightFromLoadCell() {
    if (!s_LoadCell.is_ready()) {
        return 0.0f;  // Fallback nếu sensor không sẵn sàng
    }
    
    // Đọc cân nặng (blocking ~50ms)
    float weight = s_LoadCell.get_units(10);  // 10 samples để trung bình
    return weight;
}

// ─────────────────────────────────────────────────────────
//  ĐIỀU KHIỂN MOTOR B (Nhả thức ăn)
// ─────────────────────────────────────────────────────────

void startMotor() {
    // Bật motor: ENB=HIGH, IN3=HIGH, IN4=LOW (quay tự do)
    digitalWrite(PIN_FEED_EN, HIGH);
    digitalWrite(PIN_FEED_IN1, HIGH);
    digitalWrite(PIN_FEED_IN2, LOW);
    s_MotorRunning = true;
    s_MotorStartTime = millis();
    Serial.println("[FeedingTask] ✓ Motor nhả cám BẬT");
}

void stopMotor() {
    // Tắt motor: tất cả = LOW
    digitalWrite(PIN_FEED_EN, LOW);
    digitalWrite(PIN_FEED_IN1, LOW);
    digitalWrite(PIN_FEED_IN2, LOW);
    s_MotorRunning = false;
    Serial.println("[FeedingTask] ✓ Motor nhả cám TẮT");
}

// ─────────────────────────────────────────────────────────
//  TIỆN ÍCH THỜI GIAN - Format HH:MM DD/MM/YYYY
// ─────────────────────────────────────────────────────────

String formatTime() {
    time_t now = time(nullptr);
    struct tm* timeinfo = localtime(&now);
    
    // Nếu chưa sync được thời gian (< 1 ngày sau epoch)
    if (now < 24 * 3600) {
        return "00:00 01/01/1970";
    }
    
    char buffer[20];
    strftime(buffer, sizeof(buffer), "%H:%M %d/%m/%Y", timeinfo);
    return String(buffer);
}

// ─────────────────────────────────────────────────────────
//  LOG LỊCH SỬ NHẢI CÁM - Ghi vào Firebase qua NetworkTask
// ─────────────────────────────────────────────────────────

void logFeedLog(float grams, const String& mode) {
    // ✓ Ghi lịch sử nhải cám vào Firebase /logs/ qua NetworkTask
    // Đơn giản: format thời gian và gọi NetworkTask helper
    
    String timestamp = formatTime();
    NetworkTask_LogFeedHistory(grams, mode, timestamp);
}

// ─────────────────────────────────────────────────────────
//  TASK LOOP CHÍNH
// ─────────────────────────────────────────────────────────
void feedingTaskLoop(void* unused) {
    // ───── Khởi tạo Hardware ─────
    Serial.println("[FeedingTask] Khởi tạo Motor B + LoadCell...");
    
    // Motor B control pins: ENB(23), IN3(14), IN4(12)
    pinMode(PIN_FEED_EN, OUTPUT);
    pinMode(PIN_FEED_IN1, OUTPUT);
    pinMode(PIN_FEED_IN2, OUTPUT);
    stopMotor();  // Tắt ban đầu
    
    // LoadCell HX711: DOUT(21), SCK(22)
    s_LoadCell.begin(PIN_FEED_DOUT, PIN_FEED_SCK);
    s_LoadCell.set_scale(LOADCELL_SCALE_FACTOR);
    delay(1500);
    s_LoadCell.tare();  // Zero calibration
    
    Serial.println("[FeedingTask] ✓ Motor B + LoadCell sẵn sàng");
    
    // ───── Vòng lặp chính ─────
    for (;;) {
        
        // ════════════════════════════════════════
        //  XỬ LÝ LỆNH TỪ FIREBASE (qua Queue)
        // ════════════════════════════════════════
        CommandData_t cmd;
        memset(&cmd, 0, sizeof(cmd));
        if (xQueueReceive(xQueue_Commands, &cmd, 0) == pdPASS) {
            switch (cmd.type) {
                case CMD_THUCAN_AUTO:
                    if (cmd.value > 0.5f) {
                        // Bật auto mode
                        s_CurrentMode = FEED_AUTO;
                        Serial.println("[FeedingTask] CMD_THUCAN_AUTO ON - Chế độ auto");
                    } else {
                        // Tắt auto mode
                        s_CurrentMode = FEED_IDLE;
                        stopMotor();
                        Serial.println("[FeedingTask] CMD_THUCAN_AUTO OFF - Tắt");
                    }
                    break;
                    
                case CMD_THUCAN_GRAM:
                    // Nhả cám theo gram
                    s_GramTarget = cmd.value;
                    s_CurrentMode = FEED_GRAM;
                    Serial.printf("[FeedingTask] CMD_THUCAN_GRAM - Nhả %.1f gram\n", s_GramTarget);
                    break;
                    
                case CMD_THUCAN_MANUAL:
                    if (cmd.value > 0.5f) {
                        // Bật manual
                        s_GramStartWeight = readWeightFromLoadCell();
                        startMotor();
                        s_CurrentMode = FEED_MANUAL;
                        Serial.println("[FeedingTask] CMD_THUCAN_MANUAL ON - Bật motor");
                    } else {
                        // Tắt manual
                        stopMotor();
                        float dispensed = s_GramStartWeight - readWeightFromLoadCell();
                        if (dispensed < 0) dispensed = 0;
                        logFeedLog(dispensed, "manual");
                        s_CurrentMode = FEED_IDLE;
                        Serial.printf("[FeedingTask] CMD_THUCAN_MANUAL OFF - Tắt motor, gram=%.1f\n", dispensed);
                    }
                    break;
                    
                default:
                    // Lệnh khác (oxygen) không liên quan đến FeedingTask
                    break;
            }
        }
        
        float currentWeight = readWeightFromLoadCell();
        
        // ════════════════════════════════════════
        //  MODE AUTO: Nhả cám lúc 6h00 & 17h00
        // ════════════════════════════════════════
        if (s_CurrentMode == FEED_AUTO) {
            if (!s_MotorRunning) {
                // Kiểm tra giờ hiện tại có phải 6h00 hoặc 17h00 không
                time_t now = time(nullptr);
                struct tm* t = localtime(&now);
                
                if ((t->tm_hour == 6 && t->tm_min == 0) || 
                    (t->tm_hour == 17 && t->tm_min == 0)) {
                    
                    // Bắt đầu nhả cám
                    s_AutoStartWeight = currentWeight;
                    s_AutoStartTime = millis();
                    startMotor();
                    Serial.printf("[FeedingTask] AUTO: Bắt đầu nhả cám lúc %02d:%02d\n", 
                                  t->tm_hour, t->tm_min);
                }
            } else {
                // Motor đang chạy - kiểm tra timeout 20 giây
                if (millis() - s_MotorStartTime > 20000) {
                    stopMotor();
                    float dispensed = s_AutoStartWeight - currentWeight;
                    if (dispensed < 0) dispensed = 0;
                    logFeedLog(dispensed, "auto");
                    s_CurrentMode = FEED_IDLE;
                    Serial.printf("[FeedingTask] AUTO: Nhả xong, gram=%.1f\n", dispensed);
                }
            }
        }
        
        // ════════════════════════════════════════
        //  MODE GRAM: Nhả cám cho đến đủ số gram
        // ════════════════════════════════════════
        else if (s_CurrentMode == FEED_GRAM) {
            if (!s_MotorRunning && s_GramTarget > 0) {
                // Bắt đầu nhả cám
                s_GramStartWeight = currentWeight;
                startMotor();
                Serial.printf("[FeedingTask] GRAM: Nhả %d gram\n", (int)s_GramTarget);
            }
            
            if (s_MotorRunning) {
                float diff = s_GramStartWeight - currentWeight;  // Số gram đã rơi
                
                // Kiểm điều kiện dừng:
                // 1. Đã rơi đủ gram
                // 2. Timeout 30 giây (bảo vệ motor)
                if (diff >= s_GramTarget || millis() - s_MotorStartTime > 30000) {
                    stopMotor();
                    float dispensed = s_GramStartWeight - currentWeight;
                    if (dispensed < 0) dispensed = 0;
                    logFeedLog(dispensed, "gram");
                    s_CurrentMode = FEED_IDLE;
                    s_GramTarget = 0;
                    Serial.printf("[FeedingTask] GRAM: Nhả xong, gram=%.1f\n", dispensed);
                }
            }
        }
        
        // ════════════════════════════════════════
        //  MODE MANUAL: Bật/tắt tay
        // ════════════════════════════════════════
        else if (s_CurrentMode == FEED_MANUAL) {
            // Motor sẽ được điều khiển qua lệnh Firebase
            // Chỉ kiểm timeout bảo vệ
            if (s_MotorRunning && millis() - s_MotorStartTime > 30000) {
                stopMotor();
                Serial.println("[FeedingTask] MANUAL: Timeout tắt motor bảo vệ");
            }
        }
        
        // ════════════════════════════════════════
        //  MODE IDLE: Không làm gì
        // ════════════════════════════════════════
        else {
            if (s_MotorRunning) {
                stopMotor();
                s_CurrentMode = FEED_IDLE;
            }
        }
        
        // ───── Debug Serial ─────
        Serial.printf("[FeedingTask] Weight=%.1fg | Mode=%d | Motor=%s\n",
                      currentWeight, s_CurrentMode, s_MotorRunning ? "ON" : "OFF");
        
        // ───── Delay ─────
        vTaskDelay(pdMS_TO_TICKS(100));  // Check mỗi 100ms (chính xác hơn)
    }
}

}  // namespace

// ============================================================
//  HÀM PUBLIC
// ============================================================

void FeedingTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[FeedingTask_init] Tạo FreeRTOS task...");
    
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        feedingTaskLoop,          // Hàm task
        "FeedingTask",            // Tên task
        stackSize,                // Stack size
        nullptr,                  // Parameter
        priority,                 // Priority (3 = High)
        &s_TaskHandle,            // Handle output
        0                         // Core 0 (share với NetworkTask)
    );
    
    if (xReturned == pdPASS) {
        Serial.println("[FeedingTask_init] ✓ Task tạo thành công");
    } else {
        Serial.println("[FeedingTask_init] ✗ Lỗi tạo task!");
    }
}

bool FeedingTask_IsMotorRunning() {
    return s_MotorRunning;
}

float FeedingTask_GetDispensedGram() {
    if (s_MotorRunning && s_GramStartWeight > 0) {
        return s_GramStartWeight - readWeightFromLoadCell();
    }
    return 0.0f;
}