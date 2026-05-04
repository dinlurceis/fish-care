#pragma once

#include "Config.h"
#include "sensors/SensorTypes.h"

//  FEEDINGTASK - Điều khiển động cơ cho ăn & LoadCell
//  Chi tiết: FreeRTOS xTask, Timeout 30s, LoadCell HX711

/**
 * @brief Khởi tạo FeedingTask
 * @param priority: FreeRTOS priority (4 = HIGHEST)
 * @param stackSize: Task stack size (bytes), mặc định 4096
 * 
 * Gọi xTaskCreatePinnedToCore internally
 * Cấu hình GPIO chân Motor B: EN(23), IN1(14), IN2(12)
 * Cấu hình GPIO LoadCell HX711: DOUT(21), SCK(22)
 */
void FeedingTask_init(UBaseType_t priority = 4, uint16_t stackSize = 4096);

/**
 * @brief Lấy trạng thái động cơ hiện tại
 * @return true nếu motor đang chạy
 */
bool FeedingTask_IsMotorRunning();

/**
 * @brief Lấy trọng lượng cám đã cho ăn (gram)
 * @return float: gram đã tính toán từ LoadCell
 */
float FeedingTask_GetDispensedGram();

/**
 * @brief Lấy trạng thái khối lượng thực trên bề mặt cân (Gram)
 * @return float: gram thực tế hiện tại
 */
float FeedingTask_GetCurrentWeight();