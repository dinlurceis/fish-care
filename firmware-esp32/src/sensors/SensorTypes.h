#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

// ============================================================
//  STRUCT DỮ LIỆU CẢM BIẾN - Shared payload giữa các Task
//  Hằng (SensorTask) WRITE  →  Hoàng (NetworkTask) READ
//                           →  Duy   (AutomationTask) READ
// ============================================================
typedef struct {
    float temperature;   // °C  - DS18B20 (GPIO 18)
    float tds;           // ppm - TDS analog (GPIO 34), đã lọc
    int   turbidity;     // raw 0-4095 - TS-300B (GPIO 32), đã lọc (tỷ lệ nghịch độ đục)
    uint32_t timestamp;  // millis() lúc đọc xong → tiện cho log
} SensorData_t;

// ============================================================
//  QUEUE HANDLE - Khai báo extern, Công khởi tạo trong main.cpp
// ============================================================
extern QueueHandle_t xQueue_SensorData;
