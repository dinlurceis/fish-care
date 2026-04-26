#include "SystemHealth.h"
#include "TaskDelay.h"
#include "Config.h"
#include <esp_heap_caps.h>
#include <esp_task_wdt.h>       // BẮT BUỘC PHẢI CÓ để gọi các hàm Watchdog
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

// Kéo ống dữ liệu và cờ mạng từ main.cpp & NetworkTask.cpp sang
extern QueueHandle_t xQueue_SensorData;
extern volatile bool isWiFiConnected;

// ============================================================
//  SYSTEMHEALTH - Implementation by Công (Leader)
//  Đảm bảo FreeRTOS system không crash, memory đầy, watchdog cảnh báo
// ============================================================

static uint8_t g_wdt_timeout_sec = 20;

void SystemHealth_ConfigureWatchdog(uint8_t timeout_sec) {
    g_wdt_timeout_sec = timeout_sec;
    
    // Khởi tạo task WDT (Task Watchdog Timer)
    // true = enable panic if WDT timeout (reboot)
    esp_err_t err = esp_task_wdt_init(timeout_sec, true);
    
    Serial.print("[SystemHealth] Watchdog initialized: ");
    Serial.print(timeout_sec);
    Serial.println(err == ESP_OK ? "s timeout (OK)" : "s timeout (FAILED)");
}

void SystemHealth_SubscribeTaskToWatchdog(TaskHandle_t task_handle) {
    if (task_handle == nullptr) {
        Serial.println("[SystemHealth] ERROR: NULL task handle");
        return;
    }
    
    esp_err_t err = esp_task_wdt_add(task_handle);
    if (err == ESP_OK) {
        Serial.print("[SystemHealth] Task subscribed to WDT: ");
        Serial.println(pcTaskGetName(task_handle));
    } else {
        Serial.print("[SystemHealth] ERROR subscribing task to WDT: ");
        Serial.println(err);
    }
}

void SystemHealth_ResetWatchdog() {
    // FIX BUG: Nạp lại bộ đếm cho Watchdog để không bị reset chip
    esp_task_wdt_reset(); 
}

SystemHealth_t SystemHealth_GetStatus() {
    SystemHealth_t health = {0};
    
    // 1. Uptime & Memory
    health.uptime_ms = millis();
    health.free_heap_bytes = esp_get_free_heap_size();
    
    // 2. Lấy Sensor Data CHUẨN KIẾN TRÚC IPC (Không cần gọi hàm của member)
    // Dùng xQueuePeek để copy dữ liệu ra mà không làm mất data trong ống
    SensorData_t currentData;
    if (xQueue_SensorData != nullptr && xQueuePeek(xQueue_SensorData, &currentData, 0) == pdPASS) {
        health.temp_celsius = currentData.temperature;
        health.tds_ppm = currentData.tds;
        // Bổ sung lấy thêm weight và turbidity nếu trong Struct SystemHealth_t có
    }
    
    // 3. WiFi status
    health.wifi_connected = isWiFiConnected;
    
    return health;
}

void SystemHealth_PrintStatus() {
    SystemHealth_t health = SystemHealth_GetStatus();
    
    Serial.println("\n╔════════════════════════════════════╗");
    Serial.println("║       SYSTEM HEALTH MONITOR        ║");
    Serial.println("╠════════════════════════════════════╣");
    
    Serial.printf("║ Uptime (ms):     %lu\n", health.uptime_ms);
    Serial.printf("║ Free Heap (B):   %u\n", health.free_heap_bytes);
    Serial.printf("║ WiFi Connected:  %s\n", health.wifi_connected ? "YES ✓" : "NO ✗");
    
    // In ra cảnh báo nếu RAM tụt quá thấp (< 20KB)
    if (health.free_heap_bytes < 20480) {
        Serial.println("║ ⚠️ WARNING: LOW MEMORY DETECTED!   ║");
    }
    
    if (health.temp_celsius > 0) {
        Serial.printf("║ Temperature:     %.1f °C\n", health.temp_celsius);
    }
    
    if (health.tds_ppm > 0) {
        Serial.printf("║ TDS:             %.1f ppm\n", health.tds_ppm);
    }
    
    Serial.println("╚════════════════════════════════════╝\n");
}

void SystemHealth_SoftReset(const char* reason) {
    Serial.println("\n[SystemHealth] ⚠️  SOFT RESET INITIATED");
    Serial.print("[SystemHealth] Reason: ");
    Serial.println(reason);
    Serial.println("[SystemHealth] Shutting down tasks...");
    
    // An toàn: Tắt hết các Motor vật lý trước khi khởi động lại
    // Tránh tình trạng chip reset mà Motor vẫn được cấp điện quay liên tục
    digitalWrite(PIN_OXY_EN, LOW);
    digitalWrite(PIN_FEED_EN, LOW);
    
    Task_Delay(1000);
    Serial.println("[SystemHealth] Calling ESP.restart()...");
    ESP.restart();
}