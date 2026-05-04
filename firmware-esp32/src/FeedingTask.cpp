#include "FeedingTask.h"
#include "TaskDelay.h"
#include "NetworkTask.h"  // For Firebase access
#include <HX711.h>
#include <time.h>

// ============================================================
//  FEEDINGTASK - Điều khiển Motor B (cho ăn) + LoadCell
//  Chịu trách nhiệm: Dũng
//  Chi tiết: 3 chế độ (Auto, Gram, Manual), Timeout 30s bảo vệ motor
// ============================================================

namespace {

TaskHandle_t s_TaskHandle = nullptr;

// ─────────────────────────────────────────────────────────
//  TRẠNG THÁI FEEDING - Theo dõi quá trình cho ăn
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
const float LOADCELL_SCALE_FACTOR = 505.4633;

float readWeightFromLoadCell() {
    static float lastValidWeight = 0.0f; // Biến static giữ giá trị qua các vòng lặp
    
    if (s_LoadCell.is_ready()) {
        lastValidWeight = s_LoadCell.get_units(1);
    }
    // Nếu chưa ready, trả về số cân cũ, tránh việc cân bị rớt về 0 đột ngột
    return lastValidWeight; 
}

// ─────────────────────────────────────────────────────────
//  ĐIỀU KHIỂN MOTOR B (Cho ăn)
// ─────────────────────────────────────────────────────────

void startMotor() {
    // Bật motor: ENB=HIGH, IN3=HIGH, IN4=LOW (quay tự do)
    digitalWrite(PIN_FEED_EN, HIGH);
    digitalWrite(PIN_FEED_IN1, HIGH);
    digitalWrite(PIN_FEED_IN2, LOW);
    s_MotorRunning = true;
    s_MotorStartTime = millis();
    Serial.println("[FeedingTask] ✓ Motor cho ăn BẬT");
}

void stopMotor() {
    // Tắt motor: tất cả = LOW
    digitalWrite(PIN_FEED_EN, LOW);
    digitalWrite(PIN_FEED_IN1, LOW);
    digitalWrite(PIN_FEED_IN2, LOW);
    s_MotorRunning = false;
    s_MotorStartTime = 0;  // ← Reset startTime để track chính xác lần tắt tiếp theo
    Serial.println("[FeedingTask] ✓ Motor cho ăn TẮT");
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
    Task_Delay(1500);
    s_LoadCell.tare();  // Zero calibration
    
    Serial.println("[FeedingTask] ✓ Motor B + LoadCell sẵn sàng");
    
    // ───── Vòng lặp chính ─────
    for (;;) {
        
        // ════════════════════════════════════════
        //  XỬ LÝ LỆNH TỪ FIREBASE (qua Queue)
        // ════════════════════════════════════════
        CommandData_t cmd;
        memset(&cmd, 0, sizeof(cmd));
        if (xQueueReceive(xQueue_FeedCommands, &cmd, 0) == pdPASS) {
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
                    s_GramTarget = cmd.value;
                    if (cmd.value > 0) {
                        s_CurrentMode = FEED_GRAM;
                        // Log lịch sử được Android App ghi trực tiếp lên Firebase
                        Serial.printf("[FeedingTask] CMD_THUCAN_GRAM - Cho ăn %.1f gram\n", s_GramTarget);
                    } else {
                        s_CurrentMode = FEED_IDLE;
                        stopMotor();
                        Serial.printf("[FeedingTask] CMD_THUCAN_GRAM - Bỏ qua (target=%.1f)\n", s_GramTarget);
                    }
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
                        
                        // Chỉ ghi log nếu thực sự đã cho ăn
                        // Điều kiện: dispensed > 0 AND s_MotorStartTime > 0 (motor đã chạy)
                        // motorRan > 50ms để tránh false positive từ noise LoadCell
                        bool motorRan = s_MotorStartTime > 0 && (millis() - s_MotorStartTime) > 50;
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
        //  MODE AUTO: Cho ăn lúc 6h00 & 17h00 (Chỉ kích hoạt 1 lần mỗi khung giờ)
        // ════════════════════════════════════════
        if (s_CurrentMode == FEED_AUTO) {
            static int lastFeedHour = -1;
            
            if (!s_MotorRunning) {
                time_t now = time(nullptr);
                struct tm* t = localtime(&now);
                
                if ((t->tm_hour == 6 && t->tm_min == 0 && lastFeedHour != 6) || 
                    (t->tm_hour == 17 && t->tm_min == 0 && lastFeedHour != 17)) {
                    
                    lastFeedHour = t->tm_hour; // Đánh dấu đã cho ăn giờ này
                    s_AutoStartWeight = currentWeight;
                    s_AutoStartTime = millis();
                    startMotor();
                    Serial.printf("[FeedingTask] AUTO: Bắt đầu cho ăn lúc %02d:%02d\n", t->tm_hour, t->tm_min);
                }
            } else {
                // Motor đang chạy - kiểm tra timeout 20 giây hoặc user cancel
                bool timeout = millis() - s_MotorStartTime > 20000;
                
                CommandData_t checkCmd;
                bool userCancel = false;
                if (xQueuePeek(xQueue_FeedCommands, &checkCmd, 0) == pdPASS) {
                    if (checkCmd.type == CMD_THUCAN_AUTO && checkCmd.value < 0.5f) {
                        userCancel = true;
                    }
                }
                
                if (timeout || userCancel) {
                    stopMotor();
                    float dispensed = s_AutoStartWeight - currentWeight;
                    if (dispensed < 0) dispensed = 0;
                    
                    // Chỉ ghi log nếu thực sự đã cho ăn (dispensed > 0)
                    if (dispensed > 0) {
                        // Log lịch sử được Android App ghi trực tiếp lên Firebase
                    }
                    s_CurrentMode = FEED_IDLE;
                    
                    if (userCancel) {
                        Serial.printf("[FeedingTask] AUTO: Cho ăn dừng (user cancel), gram=%.1f\n", dispensed);
                    } else {
                        Serial.printf("[FeedingTask] AUTO: Cho ăn xong (timeout), gram=%.1f\n", dispensed);
                    }
                }
            }
        }
        
        // ════════════════════════════════════════
        //  MODE GRAM: Cho ăn cho đến đủ số gram
        // ════════════════════════════════════════
        else if (s_CurrentMode == FEED_GRAM) {
            if (!s_MotorRunning && s_GramTarget > 0) {
                // Bắt đầu cho ăn
                s_GramStartWeight = currentWeight;
                startMotor();
                Serial.printf("[FeedingTask] GRAM: Cho ăn %.1f gram\n", s_GramTarget);
            }
            
            if (s_MotorRunning) {
                float diff = s_GramStartWeight - currentWeight;  // Số gram đã rơi
                
                // Kiểm nếu user cancel (Firebase thay đổi target hoặc state)
                CommandData_t checkCmd;
                bool userCancel = false;
                if (xQueuePeek(xQueue_FeedCommands, &checkCmd, 0) == pdPASS) {
                    if (checkCmd.type == CMD_THUCAN_GRAM) {
                        if (checkCmd.value <= 0) {
                            userCancel = true;  // User set target <= 0
                        }
                    }
                }
                
                // Kiểm điều kiện dừng:
                // 1. Đã rơi đủ gram
                // 2. Timeout 30 giây (bảo vệ motor)
                // 3. User cancel (set target <= 0)
                if (diff >= s_GramTarget || 
                    millis() - s_MotorStartTime > 30000 || 
                    userCancel) {
                    
                    stopMotor();
                    float dispensed = s_GramStartWeight - currentWeight;
                    if (dispensed < 0) dispensed = 0;
                    
                    // Đã dời logic ghi log lên ngay lúc nhận lệnh để hiển thị ngay lập tức (instant)
                    s_CurrentMode = FEED_IDLE;
                    s_GramTarget = 0;
                    
                    // Debug: báo loại tắt motor
                    if (userCancel) {
                        Serial.printf("[FeedingTask] GRAM: Cho ăn dừng (user cancel), gram=%.1f\n", dispensed);
                    } else if (millis() - s_MotorStartTime > 30000) {
                        Serial.printf("[FeedingTask] GRAM: Cho ăn dừng (timeout), gram=%.1f\n", dispensed);
                    } else {
                        Serial.printf("[FeedingTask] GRAM: Cho ăn xong (đủ gram), gram=%.1f\n", dispensed);
                    }
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
        
        // ───── Cập nhật Weight Vào Queue (để NetworkTask sync lên Firebase) ─────
        // Nếu weight âm → set thành 0 (tránh âm lên Firebase)
        SensorData_t sensorData = {0};
        if (xQueuePeek(xQueue_SensorData, &sensorData, 0) == pdPASS) {
            float weightToSync = (currentWeight >= 0) ? currentWeight : 0.0f;
            sensorData.weight = weightToSync;
            xQueueOverwrite(xQueue_SensorData, &sensorData);
        }
        
        // ───── Delay ─────
        vTaskDelay(pdMS_TO_TICKS(20));  // Check mỗi 20ms (nhanh hơn 100ms)
    }
}

}  // namespace

// ============================================================
//  HÀM PUBLIC
// ============================================================

void FeedingTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[FeedingTask_init] Tạo FreeRTOS task...");
    
    // Tất cả tasks chạy trên Core 1 để quản lý dễ hơn
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        feedingTaskLoop,          // Hàm task
        "FeedingTask",            // Tên task
        stackSize,                // Stack size
        nullptr,                  // Parameter
        priority,                 // Priority (từ main.cpp)
        &s_TaskHandle,            // Handle output
        1                         // Core 1
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

float FeedingTask_GetCurrentWeight() {
    return readWeightFromLoadCell();
}