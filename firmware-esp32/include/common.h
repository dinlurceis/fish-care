#ifndef COMMON_H
#define COMMON_H

#include <Arduino.h>

struct SensorData {
    float temperature;
    float water_quality;
    int turbidity;
};

struct ControlCommand {
    char type[10];
    bool state;
    float value;
};

#endif
