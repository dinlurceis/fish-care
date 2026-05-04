#include "TaskDelay.h"
#include <esp_task_wdt.h>

void Task_Delay(uint32_t delayMs) {
    // Validate delay không quá lâu (để WDT không timeout)
    if (delayMs > 15000) {
        Serial.printf("[⚠️ Task_Delay] ⚠️ Delay %lums quá lâu! WDT timeout ở 20s\n", delayMs);
    }
    
    // vTaskDelay() cho phép scheduler chạy các task khác
    vTaskDelay(pdMS_TO_TICKS(delayMs));
}

bool Task_ValidateDelay(uint32_t delayMs, uint32_t wdtTimeoutMs) {
    // Check nếu delay vượt quá WDT timeout
    if (delayMs >= wdtTimeoutMs) {
        Serial.printf("[Task_Delay] DANGER: Delay %lums >= WDT timeout %lums!\n", 
                      delayMs, wdtTimeoutMs);
        return false;
    }
    
    // Check nếu delay quá gần timeout (80% rule)
    if (delayMs > (wdtTimeoutMs * 80 / 100)) {
        Serial.printf("[Task_Delay] WARNING: Delay %lums gần WDT timeout %lums (80%% rule)\n", 
                      delayMs, wdtTimeoutMs);
        return true;  // Still safe but warn
    }
    
    return true;  // Safe to delay
}

void Task_FeedWatchdog() {
    // Reset watchdog counter từ trong task
    esp_task_wdt_reset();
}

uint32_t Task_GetTickCount() {
    // Get current FreeRTOS tick count
    return (uint32_t)xTaskGetTickCount();
}

TickType_t Task_MsToTicks(uint32_t ms) {
    // Convert milliseconds → FreeRTOS ticks
    return pdMS_TO_TICKS(ms);
}

uint32_t Task_TicksToMs(TickType_t ticks) {
    // Convert FreeRTOS ticks → milliseconds
    return (uint32_t)(ticks * 1000 / configTICK_RATE_HZ);
}
