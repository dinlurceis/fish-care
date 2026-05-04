#include "SensorTask.h"
#include "sensors/DS18B20Sensor.h"
#include "sensors/TdsSensor.h"
#include "sensors/TurbiditySensor.h"
#include "FeedingTask.h"
#include "TaskDelay.h"

// 
//  SENSORTASK - Đọc cảm biến & đẩy vào Queue

//  Chi tiết: Đọc DS18B20 (OneWire), TDS (Smooth), Turbidity (Smooth)
//            Dữ liệu mượt vì SensorTask gọi mỗi 2 giây
// 

namespace {

TaskHandle_t s_TaskHandle = nullptr;


static DS18B20Sensor  s_tempSensor;
static TdsSensor      s_tdsSensor;
static TurbiditySensor s_turbiditySensor;

//
//  HÀM HỖ TRỢ: LỌC NHIỄU ADC (Trung bình cộng 10 mẫu)
//
float readSmoothTDS() {
    float sum = 0;
    for(int i = 0; i < 10; i++) {
        sum += s_tdsSensor.readTds();
        vTaskDelay(pdMS_TO_TICKS(10)); // Trễ 10ms giữa mỗi lần đọc mẫu
    }
    return sum / 10.0f;
}

int readSmoothTurbidity() {
    long sum = 0;
    for(int i = 0; i < 10; i++) {
        sum += s_turbiditySensor.readTurbidity();
        vTaskDelay(pdMS_TO_TICKS(10));
    }
    return sum / 10;
}

//
//  TASK LOOP CHÍNH - Đọc từng cảm biến & đẩy vào Queue
//
void sensorTaskLoop(void* unused) {
    // ───── Khởi tạo Hardware ─────
    Serial.println("[SensorTask] Khởi tạo cảm biến...");
    
    // Khởi động các sensor wrapper
    s_tempSensor.begin();      // DS18B20 OneWire
    s_tdsSensor.begin();       // TDS ADC (GPIO 34)
    s_turbiditySensor.begin(); // Turbidity ADC (GPIO 32)
    
    Serial.println(\"[SensorTask] Cảm biến sẵn sàng. Bắt đầu vòng lặp đọc...\");
    
    // ───── Vòng lặp đọc cảm biến ─────
    for (;;) {
        SensorData_t reading = {0};
        
        // 1. PEEK ĐỂ LẤY DATA HIỆN TẠI TỪ QUEUE (Cực kỳ quan trọng)
        // Việc này giúp bảo toàn thông số 'weight' do Dũng (FeedingTask) vừa cập nhật.
        if (xQueue_SensorData != nullptr) {
            xQueuePeek(xQueue_SensorData, &reading, 0);
        }
        
        // 2. CẬP NHẬT CÁC THÔNG SỐ MÔI TRƯỜNG DO HẰNG QUẢN LÝ
        reading.timestamp = millis();
        
        // LƯU Ý CHO HẰNG: Đảm bảo trong hàm readTemperature() đã tắt delay() 
        // của thư viện DallasTemperature (sensors.setWaitForConversion(false))
        reading.temperature = s_tempSensor.readTemperature();
        
        // Đọc TDS & Độ đục qua hàm lọc nhiễu để số không bị nhảy lung tung
        reading.tds = readSmoothTDS();
        reading.turbidity = readSmoothTurbidity();
        
        // KHÔNG gọi FeedingTask_GetCurrentWeight() ở đây nữa để tránh Race Condition!
        
        // 3. ĐẨY LẠI VÀO QUEUE (Ghi đè)
        if (xQueue_SensorData != nullptr) {
            xQueueOverwrite(xQueue_SensorData, &reading);
        }
        
        // ───── Debug Serial ─────
        Serial.printf("[SensorTask] Temp=%.1f°C | TDS=%.1fppm | Turbidity=%d | Weight=%.1fg | ms=%lu\n",
                      reading.temperature, reading.tds, reading.turbidity, reading.weight, reading.timestamp);
        
        // ───── Chờ trước lần đọc kế tiếp ─────
        // Mặc định 2 giây (từ Config.h: SENSOR_SAMPLE_INTERVAL_MS)
        vTaskDelay(pdMS_TO_TICKS(SENSOR_SAMPLE_INTERVAL_MS));
    }
}

}  // namespace

// 
//  HÀM PUBLIC
// 

void SensorTask_init(UBaseType_t priority, uint16_t stackSize) {
    Serial.println("[SensorTask_init] Tạo FreeRTOS task...");
    
    // Tạo task chạy trên Core 1 (Core 0 dành cho WiFi/Firebase)
    // Priority 1 = Low (thấp nhất trong hệ thống)
    BaseType_t xReturned = xTaskCreatePinnedToCore(
        sensorTaskLoop,           // Hàm task
        "SensorTask",             // Tên task (debug)
        stackSize,                // Stack size (bytes)
        nullptr,                  // Parameter (không dùng)
        priority,                 // FreeRTOS priority
        &s_TaskHandle,            // Handle output
        1                         // Pin to Core 1
    );
    
    if (xReturned == pdPASS) {
        Serial.println("[SensorTask_init] Task tạo thành công");
    } else {
        Serial.println("[SensorTask_init] Lỗi tạo task!");
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