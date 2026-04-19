#pragma once

#include "Config.h"
#include <FirebaseESP32.h>

// ============================================================
//  NETWORKTASK - WiFi, Firebase, Command polling, Watchdog
//  Chịu trách nhiệm: Hoàng
//  Chi tiết: WiFi retry, Firebase sync, Offline cache, WDT
// ============================================================

// ── Firebase objects (global, chia sẻ với FeedingTask) ──
extern FirebaseData fbData;
extern FirebaseConfig fbConfig;
extern FirebaseAuth fbAuth;

/**
 * @brief Khởi tạo NetworkTask
 * @param priority: FreeRTOS priority (4 = Highest)
 * @param stackSize: Task stack size (bytes), mặc định 8192
 * 
 * Gọi xTaskCreatePinnedToCore internally
 * Phải subscribe vào Watchdog timeout
 */
void NetworkTask_init(UBaseType_t priority = 4, uint16_t stackSize = 8192);

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

/**
 * @brief Lấy số lượng sensor data còn lưu trong offline cache
 * @return uint16_t: số frame đang cached
 */
uint16_t NetworkTask_GetOfflineCacheSize();

/**
 * @brief Manually force sync offline cache (debug)
 * 
 * Thường không cần gọi, NetworkTask tự động sync
 */
void NetworkTask_SyncOfflineCache();

/**
 * @brief Ghi log lịch sử cho ăn vào Firebase
 * @param grams: Số gram đã cho ăn
 * @param mode: Chế độ cho ăn ("auto", "gram", "manual")
 * @param timeStr: Chuỗi thời gian (format HH:MM DD/MM/YYYY)
 * 
 * Gọi từ FeedingTask.cpp, thread-safe bằng mutex
 */
void NetworkTask_LogFeedHistory(float grams, const String& mode, const String& timeStr);

/**
 * @brief Push cảnh báo ngưỡng lên /notifications Firebase
 * @param type:    "water_quality" | "temperature" | "alert"
 * @param title:   Tiêu đề notification
 * @param message: Nội dung chi tiết
 *
 * Có debounce 10 phút để tránh spam cùng 1 loại alert.
 */
void pushAlertToFirebase(const char* type, const char* title, const char* message);
