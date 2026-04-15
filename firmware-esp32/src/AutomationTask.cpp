#include "AutomationTask.h"

#include "NetworkTask.h"

namespace {
TaskHandle_t sTaskHandle = nullptr;

bool sOxyOverrideOn = false;
uint32_t sOxyOverrideStartMs = 0;

void setOxyMotor(bool on) {
  if (on) {
    digitalWrite(PIN_OXY_EN, HIGH);
    digitalWrite(PIN_OXY_IN1, HIGH);
    digitalWrite(PIN_OXY_IN2, LOW);
  } else {
    digitalWrite(PIN_OXY_EN, LOW);
    digitalWrite(PIN_OXY_IN1, LOW);
    digitalWrite(PIN_OXY_IN2, LOW);
  }
}

void automationTaskLoop(void* /*unused*/) {
  for (;;) {
    SensorData latest{};
    const bool hasData = (xQueuePeek(gSensorQueue, &latest, pdMS_TO_TICKS(50)) == pdPASS);

    if (hasData && !NetworkTask_IsOnline()) {
      const bool highTemp = latest.temperatureC > 32.0f;
      const bool highTurbidity = latest.turbidityRaw > 2600;

      if ((highTemp || highTurbidity) && !sOxyOverrideOn) {
        setOxyMotor(true);
        sOxyOverrideOn = true;
        sOxyOverrideStartMs = millis();

        if (highTemp) {
          Serial.println("Kích hoạt quạt nước tự động do nước quá nóng!");
        } else if (highTurbidity) {
          Serial.println("Kích hoạt quạt nước tự động do chất lượng nước kém!");
        }
      }
    }

    if (sOxyOverrideOn && (millis() - sOxyOverrideStartMs >= 15UL * 60UL * 1000UL)) {
      setOxyMotor(false);
      sOxyOverrideOn = false;
      Serial.println("Đã chạy đủ 15 phút, tắt quạt nước.");
    }

    vTaskDelay(pdMS_TO_TICKS(AUTOMATION_CHECK_INTERVAL_MS));
  }
}
}  // namespace

void startAutomationTask(UBaseType_t priority, uint16_t stackSize) {
  xTaskCreatePinnedToCore(automationTaskLoop, "AutomationTask", stackSize, nullptr, priority, &sTaskHandle, 1);
}