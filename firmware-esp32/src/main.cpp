#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

// ── Sensor module của Hằng ───────────────────────────────────
#include "sensors/SensorTypes.h"
#include "sensors/SensorTask.h"

// ── TODO: Các module khác sẽ include vào đây khi hoàn thành ─
// #include "network/NetworkTask.h"   // Hoàng
// #include "feeding/FeedingTask.h"   // Dũng
// #include "automation/AutoTask.h"   // Duy

// ============================================================
//  QUEUE DEFINITIONS
//  Khai báo thực thể (definition) ở đây - các file khác extern
// ============================================================

// Queue chứa 1 phần tử (latest only) - dùng xQueueOverwrite để ghi
// Hoàng đọc để gửi Firebase; Duy đọc để check edge-automation
QueueHandle_t xQueue_SensorData = nullptr;

// TODO: Queue lệnh điều khiển Motor (Hoàng write, Dũng/Duy read)
// QueueHandle_t xQueue_Commands   = nullptr;

// ============================================================
//  SETUP
// ============================================================
void setup() {
    Serial.begin(115200);
    delay(500); // Chờ Serial ổn định
    Serial.println("\n========================================");
    Serial.println("  Fish-Care IoT System - Boot");
    Serial.println("  FreeRTOS Multi-Task Architecture");
    Serial.println("========================================");

    // ── 1. Tạo các Queue trước khi tạo Task ─────────────────
    // Kích thước queue = 1: Luôn giữ dữ liệu cảm biến MỚI NHẤT
    xQueue_SensorData = xQueueCreate(1, sizeof(SensorData_t));
    if (xQueue_SensorData == nullptr) {
        Serial.println("[FATAL] Cannot create xQueue_SensorData! Halting.");
        while (true) { delay(1000); }
    }
    Serial.println("[main] xQueue_SensorData created (size=1)");

    // TODO: Tạo xQueue_Commands tương tự
    // xQueue_Commands = xQueueCreate(5, sizeof(CommandData_t));

    // ── 2. Khởi động các FreeRTOS Task ─────────────────────
    SensorTask_init();   // Hằng - Core 1, Priority Medium

    // TODO: Uncomment khi các module khác sẵn sàng
    // NetworkTask_init();  // Hoàng  - Core 0, Priority High
    // FeedingTask_init();  // Dũng   - Core 0, Priority High
    // AutoTask_init();     // Duy    - Core 1, Priority Low

    Serial.println("[main] All tasks started. FreeRTOS scheduler running...");
    // Sau setup() Arduino framework tự gọi vTaskStartScheduler()
}

// ============================================================
//  LOOP - Không dùng trong kiến trúc FreeRTOS
//  Để trống hoặc thêm Watchdog feed nếu cần
// ============================================================
void loop() {
    // Mọi logic đã chạy trong FreeRTOS Tasks
    // loop() chạy ở priority thấp nhất (idle-like)
    vTaskDelay(pdMS_TO_TICKS(10000));
}