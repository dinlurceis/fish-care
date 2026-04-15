#include <Arduino.h>
#include "common.h"
#include "automation.h"

QueueHandle_t xQueue_SensorData;
QueueHandle_t xQueue_Commands;

void setup() {
  Serial.begin(115200);

  xQueue_SensorData = xQueueCreate(10, sizeof(SensorData));
  xQueue_Commands = xQueueCreate(10, sizeof(ControlCommand));

  if (xQueue_SensorData == NULL || xQueue_Commands == NULL) {
    Serial.println("Lỗi khởi tạo Queue!");
    return;
  }

  xTaskCreatePinnedToCore(
    AutomationTask,
    "AutomationTask",
    4096,
    NULL,
    1,
    NULL,
    1
  );

  Serial.println("Hệ thống Fish-Care đã sẵn sàng!");
}

void loop() {
  vTaskDelay(pdMS_TO_TICKS(1000));
}
