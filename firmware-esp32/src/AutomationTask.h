#pragma once

#include "Config.h"
#include "sensors/SensorTypes.h"

// ============================================================
//  AUTOMATIONTASK - Điều khiển Motor A (Oxy) + Edge Logic
//  Chịu trách nhiệm: Duy
//  Chi tiết: Bật/tắt guồng Oxy, phát hiện edge case (rớt mạng)
// ============================================================

/**
 * @brief Khởi tạo AutomationTask
 * @param priority: FreeRTOS priority (1 = Low)
 * @param stackSize: Task stack size (bytes), mặc định 3072
 * 
 * Gọi xTaskCreatePinnedToCore internally
 * Cấu hình GPIO chân Motor A: ENA(5), IN1(26), IN2(27)
 */
void AutomationTask_init(UBaseType_t priority = 1, uint16_t stackSize = 3072);

/**
 * @brief Bật/tắt guồng Oxy từ Firebase
 * @param on: true = bật, false = tắt
 * 
 * Được gọi từ NetworkTask khi nhận command từ Firebase
 */
void AutomationTask_SetOxy(bool on);

/**
 * @brief Lấy trạng thái Oxy hiện tại
 * @return true nếu oxy đang chạy
 */
bool AutomationTask_IsOxyRunning();

/**
 * @brief Check nếu hệ thống offline
 * @return true nếu mất kết nối WiFi
 */
bool AutomationTask_IsOffline();

/**
 * @brief Lấy thời gian Oxy đã chạy (milliseconds)
 * Dùng để track edge automation 5-minute auto-shutoff (test setting)
 * @return uint32_t: milliseconds
 */
uint32_t AutomationTask_GetOxyRuntime();