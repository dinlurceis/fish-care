#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include "SensorTypes.h"

// ============================================================
//  SENSORTASK - FreeRTOS Task của Hằng
//
//  Luồng hoạt động:
//    1. Đọc DS18B20 (blocking ~750ms)
//    2. Đọc TDS     (10 mẫu ADC + Kalman, ~vài ms)
//    3. Đọc Turbidity (10 mẫu ADC + Kalman, ~vài ms)
//    4. Đóng gói vào SensorData_t
//    5. xQueueOverwrite vào xQueue_SensorData
//    6. Delay 2000ms (theo yêu cầu Firebase không spam quá nhanh)
//
//  Priority: tskIDLE_PRIORITY + 2 (Medium - theo PROJECT_CONTEXT)
//  Stack:    4096 bytes
//  Core:     1 (để Network Task có thể chạy Core 0 riêng)
// ============================================================

#define SENSOR_TASK_STACK_SIZE  4096
#define SENSOR_TASK_PRIORITY    (tskIDLE_PRIORITY + 2)
#define SENSOR_TASK_CORE        1
#define SENSOR_TASK_PERIOD_MS   2000  // Đọc mỗi 2 giây

/**
 * @brief  Khởi tạo và bắt đầu SensorTask.
 *         Gọi hàm này MỘT LẦN trong setup() của main.cpp SAU KHI
 *         xQueue_SensorData đã được tạo.
 */
void SensorTask_init();

/**
 * @brief  Hàm FreeRTOS Task nội bộ (không gọi trực tiếp từ bên ngoài).
 *         Khai báo public để xTaskCreatePinnedToCore có thể dùng.
 */
void SensorTask_run(void* pvParameters);
