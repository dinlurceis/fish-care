#include "AutomationTask.h"
#include "NetworkTask.h"

namespace {
TaskHandle_t sTaskHandle = nullptr;
bool sOxyRunning = false;
bool sOverrideMode = false;
uint32_t sOverrideStartMs = 0;

void setOxyMotor(bool on) {
  if (on) {
    digitalWrite(PIN_OXY_EN, HIGH);
    digitalWrite(PIN_OXY_IN1, HIGH);
    digitalWrite(PIN_OXY_IN2, LOW);
    Serial.println("[OXYGEN] Bật Quạt nước");
  } else {
    digitalWrite(PIN_OXY_EN, LOW);
    digitalWrite(PIN_OXY_IN1, LOW);
    digitalWrite(PIN_OXY_IN2, LOW);
    Serial.println("[OXYGEN] Tắt Quạt nước");
  }
}

void automationTaskLoop(void* /*unused*/) {
  pinMode(PIN_OXY_EN, OUTPUT);
  pinMode(PIN_OXY_IN1, OUTPUT);
  pinMode(PIN_OXY_IN2, OUTPUT);
  setOxyMotor(false);

  for (;;) {
    CommandMessage cmd{};
    // 1. Xử lý lệnh từ Online (Firebase)
    if (xQueueReceive(gCommandQueue, &cmd, 0) == pdPASS) {
      if (cmd.type == CommandType::SET_OXY) {
        setOxyMotor(cmd.boolValue);
        sOxyRunning = cmd.boolValue;
        sOverrideMode = false;
      }
    }

    // 2. Xử lý tự động hóa Offline (Edge Logic)
    SensorData latest{};
    if (xQueuePeek(gSensorQueue, &latest, 0) == pdPASS) {
      // Nếu mất mạng hoặc điều kiện môi trường xấu
      if (latest.temperatureC > 32.0f || latest.tds < 10.0f) {
        if (!sOxyRunning) {
          setOxyMotor(true);
          sOxyRunning = true;
          sOverrideMode = true;
          sOverrideStartMs = millis();
          Serial.println("[EDGE] Kích hoạt Quạt nước tự động (Nhiệt độ/Chất lượng nước kém)!");
        }
      }
    }

    // 3. Tự động tắt sau 15 phút nếu ở chế độ Override
    if (sOverrideMode && sOxyRunning) {
      if (millis() - sOverrideStartMs >= 15UL * 60UL * 1000UL) {
        setOxyMotor(false);
        sOxyRunning = false;
        sOverrideMode = false;
        Serial.println("[EDGE] Đã chạy đủ 15 phút, tắt Quạt nước.");
      }
    }

    vTaskDelay(pdMS_TO_TICKS(AUTOMATION_CHECK_INTERVAL_MS));
  }
}
}  // namespace

void startAutomationTask(UBaseType_t priority, uint16_t stackSize) {
  xTaskCreatePinnedToCore(automationTaskLoop, "AutomationTask", stackSize, nullptr, priority, &sTaskHandle, 1);
}
