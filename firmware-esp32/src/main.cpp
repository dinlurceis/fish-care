#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>
#include <freertos/semphr.h>
#include <esp_task_wdt.h>

// ── Include các module của team ───────────────────────────────
#include "Config.h"
#include "./sensors/SensorTypes.h"
#include "TaskDelay.h"
#include "SensorTask.h"
#include "AutomationTask.h"
#include "FeedingTask.h"
#include "NetworkTask.h"

//  GLOBAL QUEUE & SYNCHRONIZATION PRIMITIVES
//  Công (Leader) khởi tạo - các task khác extern để dùng

// Queue chứa 1 phần tử (SensorData mới nhất)
// Hằng (SensorTask) WRITE → Hoàng (NetworkTask) + Duy (AutomationTask) READ
QueueHandle_t xQueue_SensorData = nullptr;

// Queue lệnh điều khiển từ Firebase
// Hoàng (NetworkTask) WRITE → Dũng (FeedingTask) + Duy (AutomationTask) READ
QueueHandle_t xQueue_FeedCommands = nullptr;
QueueHandle_t xQueue_AutoCommands = nullptr;

// Mutex bảo vệ khi write Firebase để tránh race condition
SemaphoreHandle_t xMutex_Firebase = nullptr;

// Flag theo dõi trạng thái WiFi - Duy check để edge automation
volatile bool isWiFiConnected = false;

//  WATCHDOG & ERROR HANDLING
void configureWatchdog() {
    // Cấu hình watchdog 20 giây
    esp_task_wdt_init(NETWORK_WDT_TIMEOUT_SEC, true);
    // Subscribe NetworkTask vào watchdog
    Serial.println("[main] Watchdog configured: " + String(NETWORK_WDT_TIMEOUT_SEC) + "s timeout");
}

//  SETUP - Khởi tạo Hardware & FreeRTOS
void setup() {
    Serial.begin(115200);
    Task_Delay(500);
    
    Serial.println("\n========================================");
    Serial.println("  🐟 Fish-Care IoT System - Boot 🐟");
    Serial.println("  FreeRTOS Multi-Task Architecture");
    Serial.println("========================================\n");

    // ── 1. Cấu hình Watchdog ───────────────────────────────
    configureWatchdog();

    // ── 2. Tạo các Queue & Mutex ───────────────────────────
    Serial.println("[BOOT] Creating IPC primitives...");
    
    // SensorData Queue: size=1 để luôn giữ data mới nhất
    xQueue_SensorData = xQueueCreate(1, sizeof(SensorData_t));

    if (!xQueue_SensorData) {
    Serial.println("[FATAL] Failed to create xQueue_SensorData! Rebooting...");
    Task_Delay(2000);
    ESP.restart(); // Lệnh reset an toàn của ESP32
}
    Serial.println("  ✓ xQueue_SensorData created");

    // Commands Queues: Mỗi consumer một queue riêng để tránh tranh giành lệnh
    xQueue_FeedCommands = xQueueCreate(5, sizeof(CommandData_t));
    xQueue_AutoCommands = xQueueCreate(5, sizeof(CommandData_t));
    
    if (!xQueue_FeedCommands || !xQueue_AutoCommands) {
        Serial.println("[FATAL] Failed to create Command Queues!");
        while (true) { Task_Delay(1000); }
    }
    Serial.println("  ✓ Command Queues (Feed + Auto) created");

    // Firebase Mutex
    xMutex_Firebase = xSemaphoreCreateMutex();
    if (!xMutex_Firebase) {
        Serial.println("[FATAL] Failed to create xMutex_Firebase!");
        while (true) { Task_Delay(1000); }
    }
    Serial.println("  ✓ xMutex_Firebase created");

    // ── 3. Khởi động FreeRTOS Tasks với Priority Levels ─────
    Serial.println("\n[BOOT] Starting FreeRTOS tasks on Core 1...");
    
    // Tất cả tasks chạy trên Core 1 để quản lý dễ hơn
    // Tham số cuối cùng "1" = Core 1
    // Tham số thứ 5 = Priority (4 = Cao nhất, 1 = Thấp nhất)
    
    // Priority 4 - FeedingTask (Dũng): HIGHEST
    // Phải cao nhất vì LoadCell interrupt cần phản ứng ngay
    FeedingTask_init(4, 4096);  // Dũng - Core 1, Priority 4
    Serial.println("  ✓ FeedingTask (Dũng) - Core 1, Priority 4 (HIGHEST)");
    
    // Priority 3 - NetworkTask (Hoàng): HIGH
    // Phải cao vì phải ping Firebase liên tục không đứt
    NetworkTask_init(3, 8192);  // Hoàng - Core 1, Priority 3
    Serial.println("  ✓ NetworkTask (Hoàng) - Core 1, Priority 3 (HIGH)");
    
    // Priority 2 - AutomationTask (Duy): MEDIUM
    // Check offline logic và motor automation
    AutomationTask_init(2, 4096);  // Duy - Core 1, Priority 2
    Serial.println("  ✓ AutomationTask (Duy) - Core 1, Priority 2 (MEDIUM)");
    
    // Priority 1 - SensorTask (Hằng): LOW
    // Đọc cảm biến định kỳ, priority thấp nhất
    SensorTask_init(1, 4096);  // Hằng - Core 1, Priority 1 (LOWEST)
    Serial.println("  ✓ SensorTask (Hằng) - Core 1, Priority 1 (LOWEST)");

    // ── 4. Cấu hình GPIO cho các module ─────────────────
    Serial.println("\n[BOOT] Configuring GPIO pins...");
    // SensorTask sẽ setup GPIO 18, 34, 32 (OneWire, ADCs)
    // FeedingTask sẽ setup GPIO 21, 22, 23, 14, 12 (LoadCell, Motor B)
    // AutomationTask sẽ setup GPIO 5, 26, 27 (Motor A)
    // NetworkTask sẽ init WiFi trong Task
    
    Serial.println("  ✓ GPIO configuration deferred to task init functions");

    Serial.println("\n========================================");
    Serial.println("🚀 FreeRTOS Scheduler starting...");
    Serial.println("  All tasks running in background");
    Serial.println("  Công (Leader) monitoring system health");
    Serial.println("========================================\n");
    
    // Arduino framework tự gọi vTaskStartScheduler() sau setup()
}

//  LOOP - Chạy ở priority thấp nhất (Idle)
//  Công có thể thêm monitoring logic ở đây nếu cần
void loop() {
    // Mọi logic chính chạy trong FreeRTOS Tasks
    // loop() chạy ở priority 0 (Idle priority) - chỉ khi các task khác idle
    
    // Optional: Thêm health monitoring tại đây
    // Ví dụ: check memory, log trạng thái, print debug info
    
    vTaskDelay(pdMS_TO_TICKS(5000)); // Check mỗi 5 giây
}
