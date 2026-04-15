#pragma once

#include <Arduino.h>
#include <HX711.h>
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

namespace FeedingClusterV2 {

enum class FeedMode : uint8_t {
  Auto = 0,
  Gram = 1,
  Manual = 2
};

struct FeedCommand {
  FeedMode mode;
  bool state;
  float targetGram;
};

struct FeedStatus {
  bool running;
  FeedMode mode;
  float startWeightGram;
  float currentWeightGram;
  float dispensedGram;
  bool stoppedByTarget;
  bool stoppedByTimeout;
};

class FeedingCluster {
public:
  FeedingCluster();

  bool begin();
  bool startTask(UBaseType_t priority = 4, uint32_t stackWords = 4096, BaseType_t coreId = tskNO_AFFINITY);

  bool submitCommand(const FeedCommand &cmd, TickType_t timeoutTicks = 0);
  void stopNow();
  void tare(uint8_t times = 10);
  void setScale(float scale);

  FeedStatus getStatus() const;

private:
  static constexpr uint8_t HX711_DOUT_PIN = 21;
  static constexpr uint8_t HX711_SCK_PIN = 22;

  static constexpr uint8_t MOTOR_ENB_PIN = 23;
  static constexpr uint8_t MOTOR_IN3_PIN = 14;
  static constexpr uint8_t MOTOR_IN4_PIN = 12;

  static constexpr float HX711_SCALE_FACTOR = 505.4633f;

  static constexpr uint32_t MOTOR_TIMEOUT_MS = 30000;
  static constexpr uint32_t TIMER_TICK_US = 100000; // 100ms interrupt tick
  static constexpr float AUTO_MODE_TARGET_GRAM = 30.0f;

  static FeedingCluster *instance_;

  static void taskEntry(void *ctx);
  static void IRAM_ATTR timerISR();

  void taskLoop();
  void applyCommand(const FeedCommand &cmd);
  void startFeeding(FeedMode mode, float targetGram);
  void stopFeeding(bool byTarget, bool byTimeout);

  void motorStart();
  void motorStop();

  bool consumeTimerTick();
  float readWeightNonBlocking(float fallbackValue);

  HX711 scale_;
  QueueHandle_t cmdQueue_;
  TaskHandle_t taskHandle_;

  hw_timer_t *timer_;
  portMUX_TYPE timerMux_;
  volatile bool timerTick_;

  volatile bool running_;
  volatile FeedMode mode_;

  float targetGram_;
  float startWeightGram_;
  float currentWeightGram_;
  float dispensedGram_;

  bool stoppedByTarget_;
  bool stoppedByTimeout_;

  uint32_t startedAtMs_;
};

} // namespace FeedingClusterV2
