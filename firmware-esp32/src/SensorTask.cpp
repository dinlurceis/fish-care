#include "SensorTask.h"
#include "sensors/DS18B20Sensor.h"
#include "sensors/TdsSensor.h"
#include "sensors/TurbiditySensor.h"

// ============================================================
//  SENSORTASK - Đọc cảm biến & đẩy vào Queue
//  Chịu trách nhiệm: Hằng
//  Chi tiết: Đọc DS18B20 (OneWire), TDS (ADC direct), Turbidity (ADC direct)
//            Dữ liệu mượt vì SensorTask gọi mỗi 2 giây
// ============================================================

namespace {

TaskHandle_t s_TaskHandle = nullptr;

// ─── Sensor wrapper instances ──────────────────────────────
static DS18B20Sensor  s_tempSensor;
static TdsSensor      s_tdsSensor;
static TurbiditySensor s_turbiditySensor;

// ─────────────────────────────────────────────────────────
//  TASK LOOP CHÍNH - Đọc từng cảm biến & đẩy vào Queue
// ─────────────────────────────────────────────────────────
void sensorTaskLoop(void* unused) {
    // ───── Khởi tạo Hardware ─────
    Serial.println("[SensorTask] Khởi tạo cảm biến...");
    
    // Khởi động các sensor wrapper
    s_tempSensor.begin();      // DS18B20 OneWire
    s_tdsSensor.begin();       // TDS ADC (GPIO 34)
    s_turbiditySensor.begin(); // Turbidity ADC (GPIO 32)
    
    Serial.println("[SensorTask] ✓ Cảm biến sẵn sàng. Bắt đầu vòng lặp đọc...");
    
    // ───── Vòng lặp đọc cảm biến ─────
    for (;;) {
        SensorData_t reading = {0};
        reading.timestamp = millis();
        
        // 1. Đọc Nhiệt độ (DS18B20 - OneWire)
        reading.temperature = s_tempSensor.readTemperature();
        
        // 2. Đọc TDS (direct ADC read - không MA)
        reading.tds = s_tdsSensor.readTds();
        
        // 3. Đọc Độ đục (direct ADC read - không MA)
        reading.turbidity = s_turbiditySensor.readTurbidity();
        
        // 4. Weight: FeedingTask sẽ cập nhật (khi mô-tơ chạy)
        reading.weight = 0.0f;
        
        // ───── Đẩy vào Queue ─────
        // xQueueOverwrite: luôn ghi đè data cũ, luôn giữ data mới nhất
        if (xQueue_SensorData != nullptr) {
            xQueueOverwrite(xQueue_SensorData, &reading);
        }
        
        // ───── Debug Serial ─────
        Serial.printf("[SensorTask] Temp=%.1f°C | TDS=%.1fppm | Turbidity=%d | ms=%lu\n",
                      reading.temperature, reading.tds, reading.turbidity, reading.timestamp);
        
        // ───── Chờ trước lần đọc kế tiếp ─────
        // Mặc định 2 giây (từ Config.h: SENSOR_SAMPLE_INTERVAL_MS)
        vTaskDelay(pdMS_TO_TICKS(SENSOR_SAMPLE_INTERVAL_MS));
    }
}

}  // namespace

// ============================================================
//  HÀM PUBLIC
// ============================================================

void SensorTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[SensorTask_init] Tạo FreeRTOS task...");
    
    // Tạo task chạy trên Core 1 (Core 0 dành cho WiFi/Bluetooth)
    // Priority 2 = Medium (thấp hơn NetworkTask và FeedingTask)
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        sensorTaskLoop,           // Hàm task
        "SensorTask",             // Tên task (debug)
        stackSize,                // Stack size (bytes)
        nullptr,                  // Parameter (không dùng)
        priority,                 // FreeRTOS priority (2)
        &s_TaskHandle,            // Handle output
        1                         // Pin to Core 1 (tự do)
    );
    
    if (xReturned == pdPASS) {
        Serial.println("[SensorTask_init] ✓ Task tạo thành công");
    } else {
        Serial.println("[SensorTask_init] ✗ Lỗi tạo task!");
    }
}

bool SensorTask_GetLatest(SensorData_t* out_data) {
    if (!out_data || !xQueue_SensorData) {
        return false;
    }
    
    // xQueuePeek: lấy data mới nhất mà không xóa khỏi queue
    return xQueuePeek(xQueue_SensorData, out_data, 0) == pdPASS;
}

bool SensorTask_IsDS18B20Connected() {
    // Check if DS18B20 wrapper is valid (has been initialized)
    return s_tempSensor.isValid();
}