#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>


/**
 * @brief Delay hiện tại task mà KHÔNG block các task khác (FreeRTOS-safe)
 * @param delayMs: Thời gian delay (milliseconds)
 */
void Task_Delay(uint32_t delayMs);