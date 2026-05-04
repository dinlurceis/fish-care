#pragma once

#include "Config.h"
#include "sensors/SensorTypes.h"

//  SENSORTASK - Đọc cảm biến & đẩy vào Queue
//  Chi tiết: Đọc DS18B20 (OneWire), TDS (ADC), Turbidity (ADC)

/**
 * @brief Khởi tạo SensorTask
 * @param priority: FreeRTOS priority (1 = LOWEST)
 * @param stackSize: Task stack size (bytes), mặc định 4096
 * 
 * Gọi xTaskCreatePinnedToCore internally để tạo task
 */
void SensorTask_init(UBaseType_t priority = 1, uint16_t stackSize = 4096);

/**
 * @brief Lấy dữ liệu cảm biến mới nhất đang lưu trong queue
 * @param out_data: Pointer đến SensorData_t để nhận dữ liệu
 * @return true nếu lấy được data, false nếu queue trống
 */
bool SensorTask_GetLatest(SensorData_t* out_data);

/**
 * @brief Lấy trạng thái kế nối cảm biến DS18B20
 * @return true nếu DS18B20 online
 */
bool SensorTask_IsDS18B20Connected();