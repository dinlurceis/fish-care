#include <Arduino.h>
#include "Config.h"
#include "AutomationTask.h"
#include "FeedingTask.h"
#include "NetworkTask.h"
#include "SensorTask.h"

QueueHandle_t gSensorQueue = nullptr;
QueueHandle_t gCommandQueue = nullptr;
SemaphoreHandle_t gFirebaseMutex = nullptr;

void setup() {
  Serial.begin(115200);
  delay(300);

  gSensorQueue = xQueueCreate(SENSOR_QUEUE_LENGTH, sizeof(SensorData));
  gCommandQueue = xQueueCreate(COMMAND_QUEUE_LENGTH, sizeof(CommandMessage));
  gFirebaseMutex = xSemaphoreCreateMutex();

  if (gSensorQueue == nullptr || gCommandQueue == nullptr || gFirebaseMutex == nullptr) {
    Serial.println("[BOOT] Queue/Mutex init failed. Restarting...");
    delay(1000);
    ESP.restart();
  }

  startNetworkTask(4);
  startFeedingTask(3);
  startSensorTask(2);
  startAutomationTask(1);

  Serial.println("[BOOT] Fish-Care system started with Fan/Oxy control logic.");
}

void loop() {
  vTaskDelay(pdMS_TO_TICKS(1000));
}
