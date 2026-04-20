#include "SystemHealth.h"
#include <esp_heap_caps.h>

// ============================================================
//  SYSTEMHEALTH - Implementation by Công (Leader)
//  Đảm bảo FreeRTOS system không crash, memory dậy, watchdog cảnh báo
// ============================================================

// Store watchdog timeout cho reference
static uint8_t g_wdt_timeout_sec = 20;

void SystemHealth_ConfigureWatchdog(uint8_t timeout_sec) {
    g_wdt_timeout_sec = timeout_sec;
    
    // Khởi tạo task WDT (Task Watchdog Timer - FreeRTOS built-in)
    // true = enable panic if WDT timeout (reboot)
    esp_task_wdt_init(timeout_sec, true);
    
    Serial.print("[SystemHealth] Watchdog initialized: ");
    Serial.print(timeout_sec);
    Serial.println("s timeout");
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
    // Gọi từ NetworkTask định kỳ để báo "tôi còn active"
}

SystemHealth_t SystemHealth_GetStatus() {
    SystemHealth_t health = {0};
    
    // Uptime
    health.uptime_ms = millis();
    
    // Free heap memory
    health.free_heap_bytes = esp_get_free_heap_size();
    
    // Get sensor data từ global state
    // (Hằng, Dũng, Duy sẽ expose hàm để lấy latest values)
    // health.temp_celsius = ...
    // health.tds_ppm = ...
    
    // WiFi status
    // (Hoàng sẽ update isWiFiConnected global)
    // health.wifi_connected = isWiFiConnected;
    
    return health;
}

void SystemHealth_PrintStatus() {
    SystemHealth_t health = SystemHealth_GetStatus();
    
    Serial.println("\n╔════════════════════════════════════╗");
    Serial.println("║       SYSTEM HEALTH MONITOR        ║");
    Serial.println("╠════════════════════════════════════╣");
    
    Serial.print("║ Uptime (ms):     ");
    Serial.println(health.uptime_ms);
    
    Serial.print("║ Free Heap (B):   ");
    Serial.println(health.free_heap_bytes);
    
    Serial.print("║ WiFi Connected:  ");
    Serial.println(health.wifi_connected ? "YES ✓" : "NO ✗");
    
    if (health.temp_celsius > 0) {
        Serial.print("║ Temperature:     ");
        Serial.print(health.temp_celsius);
        Serial.println("°C");
    }
    
    if (health.tds_ppm > 0) {
        Serial.print("║ TDS:             ");
        Serial.print(health.tds_ppm);
        Serial.println(" ppm");
    }
    
    Serial.println("╚════════════════════════════════════╝\n");
}

void SystemHealth_SoftReset(const char* reason) {
    Serial.println("\n[SystemHealth] ⚠️  SOFT RESET INITIATED");
    Serial.print("[SystemHealth] Reason: ");
    Serial.println(reason);
    Serial.println("[SystemHealth] Shutting down tasks...");
    
    // TODO: Gracefully shutdown all tasks
    // 1. Signal tasks to finish
    // 2. Wait for completion
    // 3. Clean up resources
    
    delay(1000);
    Serial.println("[SystemHealth] Calling ESP.restart()...");
    ESP.restart();
}
