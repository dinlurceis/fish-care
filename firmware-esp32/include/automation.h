#ifndef AUTOMATION_H
#define AUTOMATION_H

#include "Config.h"

// Ngưỡng cảnh báo từ PROJECT_CONTEXT.md
#define TEMP_THRESHOLD 32.0
#define TDS_CRITICAL 10.0

void AutomationTask(void *pvParameters);
void controlWaterFan(bool state);

#endif
