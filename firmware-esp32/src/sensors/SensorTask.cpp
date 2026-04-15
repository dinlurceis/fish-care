#include "SensorTask.h"
#include "DS18B20Sensor.h"
#include "TdsSensor.h"
#include "TurbiditySensor.h"

// ─── Sensor instances (static - chỉ dùng trong file này) ────
static DS18B20Sensor  s_tempSensor;
static TdsSensor      s_tdsSensor;
static TurbiditySensor s_turbiditySensor;

// ─── Public API ──────────────────────────────────────────────

void SensorTask_init() {
    // Khởi tạo phần cứng từng cảm biến
    s_tempSensor.begin();
    s_tdsSensor.begin();
    s_turbiditySensor.begin();

    // Tạo FreeRTOS Task, ghim vào Core 1
    xTaskCreatePinnedToCore(
        SensorTask_run,           // Hàm task
        "SensorTask",             // Tên task (debug)
        SENSOR_TASK_STACK_SIZE,   // Stack size (bytes)
        nullptr,                  // Tham số truyền vào
        SENSOR_TASK_PRIORITY,     // Priority
        nullptr,                  // Task handle (không cần lưu)
        SENSOR_TASK_CORE          // Core 1
    );

    Serial.println("[SensorTask] Task created on Core 1, period 2000ms");
}

// ─── FreeRTOS Task Loop ───────────────────────────────────────

void SensorTask_run(void* pvParameters) {
    // Dùng vTaskDelayUntil để chu kỳ chính xác, tránh drift
    TickType_t xLastWakeTime = xTaskGetTickCount();
    const TickType_t xPeriod = pdMS_TO_TICKS(SENSOR_TASK_PERIOD_MS);

    for (;;) {
        // ── 1. Đọc Nhiệt độ ────────────────────────────────────
        float temp = s_tempSensor.readTemperature(); // ~750ms blocking ở 12-bit

        if (!s_tempSensor.isValid()) {
            Serial.println("[SensorTask] WARN: DS18B20 read failed, skipping cycle");
            vTaskDelayUntil(&xLastWakeTime, xPeriod);
            continue; // Bỏ qua chu kỳ này nếu nhiệt kế lỗi
        }

        // ── 2. Đọc TDS ─────────────────────────────────────────
        float tds = s_tdsSensor.readTds();

        // ── 3. Đọc Độ đục ──────────────────────────────────────
        int turbidity = s_turbiditySensor.readTurbidity();

        // ── 4. Đóng gói struct ─────────────────────────────────
        SensorData_t data = {
            .temperature = temp,
            .tds         = tds,
            .turbidity   = turbidity,
            .timestamp   = (uint32_t)millis()
        };

        // ── 5. Push vào Queue ───────────────────────────────────
        // Dùng xQueueOverwrite: không block, luôn ghi đè dữ liệu cũ
        // → Hoàng và Duy luôn nhận dữ liệu MỚI NHẤT, không bị backlog
        if (xQueue_SensorData != nullptr) {
            xQueueOverwrite(xQueue_SensorData, &data);
        }

        // ── 6. Log Serial (debug) ───────────────────────────────
        Serial.printf("[SensorTask] T=%.2f°C | TDS=%.1fppm | Turb=%d (raw) | t=%lums\n",
                      temp, tds, turbidity, (unsigned long)data.timestamp);

        if (s_turbiditySensor.isAlertLevel()) {
            Serial.println("[SensorTask] ⚠️  ALERT: Turbidity at danger level!");
        }

        // ── 7. Chờ đủ chu kỳ 2s ────────────────────────────────
        // vTaskDelayUntil bù thời gian đọc cảm biến (~750ms DS18B20)
        // → Chu kỳ thực tế luôn đúng 2000ms
        vTaskDelayUntil(&xLastWakeTime, xPeriod);
    }
}
