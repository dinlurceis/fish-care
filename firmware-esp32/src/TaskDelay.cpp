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
