#pragma once
#ifndef SYSTEMHEALTH_H
#define SYSTEMHEALTH_H

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <esp_task_wdt.h>


//  SYSTEM HEALTH MONITORING & WATCHDOG UTILITIES


typedef struct {
    uint32_t uptime_ms;           // Thời gian chạy (ms)
    uint32_t free_heap_bytes;     // Bộ nhớ heap còn trống
    uint8_t core0_load_percent;   // CPU load core 0
    uint8_t core1_load_percent;   // CPU load core 1
    bool wifi_connected;          // Trạng thái WiFi
    uint16_t temp_celsius;        // Nhiệt độ sensor
    uint16_t tds_ppm;             // TDS sensor
} SystemHealth_t;


//  FUNCTION DECLARATIONS


/**
 * @brief Cấu hình watchdog timer
 * @param timeout_sec: Thời gian timeout (giây)
 * 
 * Ý nghĩa: Nếu NetworkTask bị treo quá timeout_sec mà không
 * gọi esp_task_wdt_reset(), mạch sẽ tự động restart.
 * 
 * Điều này phòng chống: Deadlock Firebase, WiFi stuck, etc.
 */
void SystemHealth_ConfigureWatchdog(uint8_t timeout_sec);

/**
 * @brief Subscribe một task vào watchdog
 * @param task_handle: Handle của task cần monitored
 * 
 * Ví dụ: esp_task_wdt_add(xTaskGetHandle("NetworkTask"));
 */
void SystemHealth_SubscribeTaskToWatchdog(TaskHandle_t task_handle);

/**
 * @brief Reset watchdog timer (gọi từ task để báo "tôi còn sống")
 * 
 * Hoàng (NetworkTask) phải gọi cái này định kỳ,
 * nếu 20s không gọi → mạch restart tự động.
 */
void SystemHealth_ResetWatchdog();

/**
 * @brief Lấy thông tin system health hiện tại
 * @return SystemHealth_t struct chứa stats
 * 
 */
SystemHealth_t SystemHealth_GetStatus();

/**
 * @brief Log thông tin system health ra Serial
 * 
 * Tiện cho debugging, check CPU load, memory leaks, etc.
 */
void SystemHealth_PrintStatus();

/**
 * @brief Soft reset nếu phát hiện anomaly
 * @param reason: String mô tả nguyên nhân reset
 * 
 * Nếu heap < ngưỡng hoặc task deadlock → gọi hàm này
 * để graceful restart thay vì hard watchdog reset
 */
void SystemHealth_SoftReset(const char* reason);

#endif // SYSTEMHEALTH_H
