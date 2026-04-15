#include "feeding_cluster.h"

namespace FeedingClusterV2 {

FeedingCluster *FeedingCluster::instance_ = nullptr;

FeedingCluster::FeedingCluster()
    : cmdQueue_(nullptr),
      taskHandle_(nullptr),
      timer_(nullptr),
      timerMux_(portMUX_INITIALIZER_UNLOCKED),
      timerTick_(false),
      running_(false),
      mode_(FeedMode::Manual),
      targetGram_(0.0f),
      startWeightGram_(0.0f),
      currentWeightGram_(0.0f),
      dispensedGram_(0.0f),
      stoppedByTarget_(false),
      stoppedByTimeout_(false),
      startedAtMs_(0) {}

bool FeedingCluster::begin() {
  pinMode(MOTOR_ENB_PIN, OUTPUT);
  pinMode(MOTOR_IN3_PIN, OUTPUT);
  pinMode(MOTOR_IN4_PIN, OUTPUT);
  motorStop();

  scale_.begin(HX711_DOUT_PIN, HX711_SCK_PIN);
  scale_.set_scale(HX711_SCALE_FACTOR);
  scale_.tare(10);

  cmdQueue_ = xQueueCreate(8, sizeof(FeedCommand));
  if (cmdQueue_ == nullptr) {
    return false;
  }

  currentWeightGram_ = readWeightNonBlocking(0.0f);

  instance_ = this;
  timer_ = timerBegin(1000000); // 1MHz timer clock
  if (timer_ == nullptr) {
    return false;
  }

  timerAttachInterrupt(timer_, &FeedingCluster::timerISR);
  timerAlarm(timer_, TIMER_TICK_US, true, 0);

  return true;
}

bool FeedingCluster::startTask(UBaseType_t priority, uint32_t stackWords, BaseType_t coreId) {
  if (taskHandle_ != nullptr) {
    return true;
  }

  BaseType_t ok = xTaskCreatePinnedToCore(
      FeedingCluster::taskEntry,
      "FeedingMotorTask",
      stackWords,
      this,
      priority,
      &taskHandle_,
      coreId);

  return ok == pdPASS;
}

bool FeedingCluster::submitCommand(const FeedCommand &cmd, TickType_t timeoutTicks) {
  if (cmdQueue_ == nullptr) {
    return false;
  }
  return xQueueSend(cmdQueue_, &cmd, timeoutTicks) == pdPASS;
}

void FeedingCluster::stopNow() {
  stopFeeding(false, false);
}

void FeedingCluster::tare(uint8_t times) {
  scale_.tare(times);
  currentWeightGram_ = readWeightNonBlocking(currentWeightGram_);
}

void FeedingCluster::setScale(float scale) {
  scale_.set_scale(scale);
}

FeedStatus FeedingCluster::getStatus() const {
  FeedStatus st{};
  st.running = running_;
  st.mode = mode_;
  st.startWeightGram = startWeightGram_;
  st.currentWeightGram = currentWeightGram_;
  st.dispensedGram = dispensedGram_;
  st.stoppedByTarget = stoppedByTarget_;
  st.stoppedByTimeout = stoppedByTimeout_;
  return st;
}

void FeedingCluster::taskEntry(void *ctx) {
  static_cast<FeedingCluster *>(ctx)->taskLoop();
}

void FeedingCluster::IRAM_ATTR timerISR() {
  if (instance_ == nullptr) {
    return;
  }

  portENTER_CRITICAL_ISR(&instance_->timerMux_);
  instance_->timerTick_ = true;
  portEXIT_CRITICAL_ISR(&instance_->timerMux_);
}

bool FeedingCluster::consumeTimerTick() {
  bool tick = false;
  portENTER_CRITICAL(&timerMux_);
  tick = timerTick_;
  timerTick_ = false;
  portEXIT_CRITICAL(&timerMux_);
  return tick;
}

float FeedingCluster::readWeightNonBlocking(float fallbackValue) {
  if (!scale_.is_ready()) {
    return fallbackValue;
  }

  // Use 1 sample to reduce blocking time in the control task.
  float gram = scale_.get_units(1);
  if (isnan(gram) || isinf(gram)) {
    return fallbackValue;
  }
  return gram;
}

void FeedingCluster::applyCommand(const FeedCommand &cmd) {
  if (!cmd.state) {
    stopFeeding(false, false);
    return;
  }

  float target = cmd.targetGram;
  if (cmd.mode == FeedMode::Auto) {
    target = AUTO_MODE_TARGET_GRAM;
  }

  if (cmd.mode == FeedMode::Manual) {
    target = -1.0f; // no target in manual mode
  }

  startFeeding(cmd.mode, target);
}

void FeedingCluster::startFeeding(FeedMode mode, float targetGram) {
  mode_ = mode;
  targetGram_ = targetGram;

  startWeightGram_ = readWeightNonBlocking(currentWeightGram_);
  currentWeightGram_ = startWeightGram_;
  dispensedGram_ = 0.0f;

  stoppedByTarget_ = false;
  stoppedByTimeout_ = false;

  startedAtMs_ = millis();
  running_ = true;

  motorStart();
}

void FeedingCluster::stopFeeding(bool byTarget, bool byTimeout) {
  motorStop();

  currentWeightGram_ = readWeightNonBlocking(currentWeightGram_);
  float d = startWeightGram_ - currentWeightGram_;
  dispensedGram_ = d > 0.0f ? d : 0.0f;

  running_ = false;
  stoppedByTarget_ = byTarget;
  stoppedByTimeout_ = byTimeout;
}

void FeedingCluster::motorStart() {
  // Required drive state by hardware spec: ENB=HIGH, IN3=HIGH, IN4=LOW
  digitalWrite(MOTOR_ENB_PIN, HIGH);
  digitalWrite(MOTOR_IN3_PIN, HIGH);
  digitalWrite(MOTOR_IN4_PIN, LOW);
}

void FeedingCluster::motorStop() {
  // Required stop state by hardware spec: all LOW
  digitalWrite(MOTOR_ENB_PIN, LOW);
  digitalWrite(MOTOR_IN3_PIN, LOW);
  digitalWrite(MOTOR_IN4_PIN, LOW);
}

void FeedingCluster::taskLoop() {
  FeedCommand cmd{};

  for (;;) {
    if (xQueueReceive(cmdQueue_, &cmd, pdMS_TO_TICKS(20)) == pdPASS) {
      applyCommand(cmd);
    }

    if (consumeTimerTick()) {
      currentWeightGram_ = readWeightNonBlocking(currentWeightGram_);
      float d = startWeightGram_ - currentWeightGram_;
      dispensedGram_ = d > 0.0f ? d : 0.0f;

      if (running_) {
        if ((millis() - startedAtMs_) >= MOTOR_TIMEOUT_MS) {
          stopFeeding(false, true);
          continue;
        }

        bool hasTarget = targetGram_ >= 0.0f;
        if (hasTarget && dispensedGram_ >= targetGram_) {
          stopFeeding(true, false);
        }
      }
    }

    vTaskDelay(pdMS_TO_TICKS(5));
  }
}

} // namespace FeedingClusterV2
