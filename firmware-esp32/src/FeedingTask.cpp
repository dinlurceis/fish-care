#include "FeedingTask.h"

namespace {
TaskHandle_t sTaskHandle = nullptr;
hw_timer_t* sFeedTimeoutTimer = nullptr;
portMUX_TYPE sFeedMux = portMUX_INITIALIZER_UNLOCKED;
volatile bool sForceStopMotor = false;

bool sFeedRunning = false;
float sCurrentTargetGram = 0.0f;
float sDispensedGram = 0.0f;
uint32_t sLastFeedTick = 0;

void IRAM_ATTR feedTimeoutISR() {
  portENTER_CRITICAL_ISR(&sFeedMux);
  sForceStopMotor = true;
  portEXIT_CRITICAL_ISR(&sFeedMux);
}

void setFeedMotor(bool on) {
  if (on) {
    digitalWrite(PIN_FEED_EN, HIGH);
    digitalWrite(PIN_FEED_IN1, HIGH);
    digitalWrite(PIN_FEED_IN2, LOW);
  } else {
    digitalWrite(PIN_FEED_EN, LOW);
    digitalWrite(PIN_FEED_IN1, LOW);
    digitalWrite(PIN_FEED_IN2, LOW);
  }
}

void startFeedSession(float targetGram) {
  sCurrentTargetGram = max(0.0f, targetGram);
  sDispensedGram = 0.0f;
  sLastFeedTick = millis();
  sFeedRunning = true;

  portENTER_CRITICAL(&sFeedMux);
  sForceStopMotor = false;
  portEXIT_CRITICAL(&sFeedMux);

  timerWrite(sFeedTimeoutTimer, 0);
  timerAlarmEnable(sFeedTimeoutTimer);
  setFeedMotor(true);
}

void stopFeedSession() {
  sFeedRunning = false;
  timerAlarmDisable(sFeedTimeoutTimer);
  setFeedMotor(false);
}

void feedingTaskLoop(void* /*unused*/) {
  sFeedTimeoutTimer = timerBegin(1, 80, true);
  timerAttachInterrupt(sFeedTimeoutTimer, &feedTimeoutISR, true);
  timerAlarmWrite(sFeedTimeoutTimer, 30000000ULL, false);

  for (;;) {
    CommandMessage cmd{};
    if (xQueueReceive(gCommandQueue, &cmd, pdMS_TO_TICKS(200)) == pdPASS) {
      if (cmd.type == CommandType::START_FEED && cmd.boolValue) {
        startFeedSession(cmd.floatValue);
      }
    }

    bool timeoutStop = false;
    portENTER_CRITICAL(&sFeedMux);
    timeoutStop = sForceStopMotor;
    portEXIT_CRITICAL(&sFeedMux);

    if (timeoutStop && sFeedRunning) {
      Serial.println("[FeedingTask] Motor timeout protection triggered (30s).");
      stopFeedSession();
    }

    if (sFeedRunning) {
      const uint32_t now = millis();
      const uint32_t deltaMs = now - sLastFeedTick;
      sLastFeedTick = now;
      sDispensedGram += static_cast<float>(deltaMs) * 0.015f;

      if (sDispensedGram >= sCurrentTargetGram) {
        stopFeedSession();
      }
    }
  }
}
}  // namespace

void startFeedingTask(UBaseType_t priority, uint16_t stackSize) {
  xTaskCreatePinnedToCore(feedingTaskLoop, "FeedingTask", stackSize, nullptr, priority, &sTaskHandle, 1);
}