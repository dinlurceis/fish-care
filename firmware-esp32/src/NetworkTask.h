#pragma once

#include "Config.h"

void startNetworkTask(UBaseType_t priority, uint16_t stackSize = 8192);
bool NetworkTask_IsOnline();