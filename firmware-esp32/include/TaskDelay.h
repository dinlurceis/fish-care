#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

/**
 *  TASKDELAY - FreeRTOS-Safe Delay Utility
 *  
 *  Mục đích: Thay thế delay() (blocking) trong FreeRTOS tasks
 *  Đảm bảo: Non-blocking, scheduler-aware, watchdog-safe
 */

/**
 * @brief Delay hiện tại task mà KHÔNG block các task khác (FreeRTOS-safe)
 * @param delayMs: Thời gian delay (milliseconds)
 */
void Task_Delay(uint32_t delayMs);

/**
 * @brief Validate delay time (kiểm tra có vượt watchdog timeout không)
 */
bool Task_ValidateDelay(uint32_t delayMs, uint32_t wdtTimeoutMs = 20000);

/**
 * @brief Reset watchdog counter từ trong task
 */
void Task_FeedWatchdog();

/**
 * @brief Get current FreeRTOS tick count
 */
uint32_t Task_GetTickCount();

/**
 * @brief Convert milliseconds → FreeRTOS ticks
 */
TickType_t Task_MsToTicks(uint32_t ms);

/**
 * @brief Convert FreeRTOS ticks → milliseconds
 */
uint32_t Task_TicksToMs(TickType_t ticks);
