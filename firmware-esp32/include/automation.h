#ifndef AUTOMATION_H
#define AUTOMATION_H

#include "common.h"
#include <FreeRTOS.h>

#define MOTOR_A_ENA 5
#define MOTOR_A_IN1 26
#define MOTOR_A_IN2 27

#define TEMP_THRESHOLD 32.0
#define TDS_CRITICAL 10.0

void AutomationTask(void *pvParameters);
void controlOxygen(bool state);

#endif
