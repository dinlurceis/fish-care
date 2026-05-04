#ifndef SYSTEMHEALTH_H
#define SYSTEMHEALTH_H

#include "../src/SensorTask.h"
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

typedef struct {
    unsigned long uptime_ms;
    uint32_t free_heap_bytes;
    float temp_celsius;
    float tds_ppm;
    bool wifi_connected;
} SystemHealth_t;

void SystemHealth_ConfigureWatchdog(uint8_t timeout_sec);
void SystemHealth_SubscribeTaskToWatchdog(TaskHandle_t task_handle);
void SystemHealth_ResetWatchdog();
SystemHealth_t SystemHealth_GetStatus();
void SystemHealth_PrintStatus();

#endif // SYSTEMHEALTH_H
