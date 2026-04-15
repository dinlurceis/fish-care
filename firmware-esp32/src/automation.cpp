#include "automation.h"
#include <Arduino.h>

void AutomationTask(void *pvParameters) {
    pinMode(MOTOR_A_ENA, OUTPUT);
    pinMode(MOTOR_A_IN1, OUTPUT);
    pinMode(MOTOR_A_IN2, OUTPUT);
    digitalWrite(MOTOR_A_ENA, LOW);
    digitalWrite(MOTOR_A_IN1, LOW);
    digitalWrite(MOTOR_A_IN2, LOW);

    extern QueueHandle_t xQueue_SensorData;
    extern QueueHandle_t xQueue_Commands;

    SensorData currentSensorData;
    ControlCommand currentCommand;

    const TickType_t xDelay10s = pdMS_TO_TICKS(10000);
    unsigned long oxyStartTime = 0;
    bool isOxyRunning = false;
    bool isOverrideMode = false;

    for (;;) {
        if (xQueueReceive(xQueue_Commands, &currentCommand, 0) == pdPASS) {
            if (strcmp(currentCommand.type, "quat") == 0) {
                controlOxygen(currentCommand.state);
                isOxyRunning = currentCommand.state;
                isOverrideMode = false;
            }
        }

        if (xQueueReceive(xQueue_SensorData, &currentSensorData, 0) == pdPASS) {
            bool highTemp = currentSensorData.temperature > TEMP_THRESHOLD;
            bool lowQuality = currentSensorData.water_quality < TDS_CRITICAL;

            if ((highTemp || lowQuality) && !isOxyRunning) {
                controlOxygen(true);
                isOxyRunning = true;
                isOverrideMode = true;
                oxyStartTime = millis();

                if (highTemp) {
                    Serial.println("Kích hoạt quạt nước tự động do nước quá nóng!");
                } else if (lowQuality) {
                    Serial.println("Kích hoạt quạt nước tự động do chất lượng nước kém!");
                }
            }
        }

        if (isOverrideMode && isOxyRunning) {
            if (millis() - oxyStartTime >= 15 * 60 * 1000) {
                controlOxygen(false);
                isOxyRunning = false;
                isOverrideMode = false;
                Serial.println("Đã chạy đủ 15 phút, tắt quạt nước.");
            }
        }

        vTaskDelay(xDelay10s);
    }
}

void controlOxygen(bool state) {
    if (state) {
        digitalWrite(MOTOR_A_ENA, HIGH);
        digitalWrite(MOTOR_A_IN1, HIGH);
        digitalWrite(MOTOR_A_IN2, LOW);
        Serial.println("Bật quạt nước");
    } else {
        digitalWrite(MOTOR_A_ENA, LOW);
        digitalWrite(MOTOR_A_IN1, LOW);
        digitalWrite(MOTOR_A_IN2, LOW);
        Serial.println("Tắt quạt nước");
    }
}
