#pragma once

#include "Config.h"
#include "sensors/SensorTypes.h"

// ============================================================
//  FEEDINGTASK - Điều khiển động cơ cho ăn & LoadCell
//  Chịu trách nhiệm: Dũng
//  Chi tiết: FreeRTOS xTask, Timeout 30s, LoadCell HX711
// ============================================================

/**
 * @brief Khởi tạo FeedingTask
 * @param priority: FreeRTOS priority (3 = High)
 * @param stackSize: Task stack size (bytes), mặc định 4096
 * 
 * Gọi xTaskCreatePinnedToCore internally
 * Cấu hình GPIO chân Motor B: EN(23), IN1(14), IN2(12)
 * Cấu hình GPIO LoadCell HX711: DOUT(21), SCK(22)
 */
void FeedingTask_init(UBaseType_t priority = 3, uint16_t stackSize = 4096);

/**
 * @brief Bắt đầu cho ăn theo gram
 * @param target_gram: Số gram cần tính toán (ví dụ: 50.0)
 * 
 * Ghi lệnh vào xQueue_Commands → FeedingTask nhận lệnh
 * Bộ timer sẽ tự động tắt sau 30 giây (timeout protection)
 */
void FeedingTask_StartFeed(float target_gram);

/**
 * @brief Dừng quá trình cho ăn ngay lập tức
 */
void FeedingTask_StopFeed();

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