#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>


//  SensorData_t definition has been moved to Config.h
//  to avoid duplicate typedef conflicts



//  QUEUE HANDLE - Khai báo extern, Công khởi tạo trong main.cpp

extern QueueHandle_t xQueue_SensorData;
