#pragma once

#include "Config.h"
#include <FirebaseESP32.h>

//  NETWORKTASK - WiFi, Firebase, Command polling, Watchdog
//  Chi tiết: WiFi retry, Firebase sync, Offline cache, WDT

// Firebase objects (global, chia sẻ với FeedingTask)
extern FirebaseData fbData;
extern FirebaseConfig fbConfig;
extern FirebaseAuth fbAuth;

/**
 * @brief Khởi tạo NetworkTask
 * @param priority: FreeRTOS priority (3 = HIGH)
 * @param stackSize: Task stack size (bytes), mặc định 8192
 * 
 * Gọi xTaskCreatePinnedToCore internally
 * Phải subscribe vào Watchdog timeout
 */
void NetworkTask_init(UBaseType_t priority = 3, uint16_t stackSize = 8192);

/**
 * @brief Check trạng thái kết nối WiFi
 * @return true nếu WiFi connected
 */
bool NetworkTask_IsWiFiConnected();

/**
 * @brief Check trạng thái kết nối Firebase
 * @return true nếu Firebase initialized & connected
 */
bool NetworkTask_IsFirebaseConnected();
