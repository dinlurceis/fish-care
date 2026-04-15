#include <Arduino.h>
#include <HX711.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>

#include <math.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

// ==========================
// Shared contracts (Project Context)
// ==========================
typedef struct {
  float temp;
  float tds;
  int turbidity;
} SensorData;

typedef enum {
  CMD_FEED_STATE = 0,
  CMD_FEED_MODE_AUTO,
  CMD_FEED_MODE_GRAM,
  CMD_FEED_MODE_MANUAL,
  CMD_FEED_TARGET_GRAM
} CommandType;

typedef struct {
  CommandType cmdType;
  float value;
} CommandMessage;

typedef struct {
  float gram;
  char mode[8];
  char time[20];
} FeedLogEntry;

QueueHandle_t xQueue_SensorData = nullptr;
QueueHandle_t xQueue_Commands = nullptr;
QueueHandle_t xQueue_FeedingLogs = nullptr;

// ==========================
// Dung - Hardware mapping (must stay unchanged)
// ==========================
static constexpr uint8_t HX711_DOUT_PIN = 21;
static constexpr uint8_t HX711_SCK_PIN = 22;
static constexpr float HX711_SCALE_FACTOR = 505.4633f;

static constexpr uint8_t MOTOR_B_ENB = 23;
static constexpr uint8_t MOTOR_B_IN3 = 14;
static constexpr uint8_t MOTOR_B_IN4 = 12;

static constexpr uint32_t MOTOR_B_TIMEOUT_MS = 30000;
static constexpr uint32_t WEIGHT_SAMPLE_PERIOD_US = 200000;  // 200ms

HX711 g_loadCell;

enum class FeedMode : uint8_t {
  AUTO = 0,
  GRAM,
  MANUAL
};

volatile bool g_weightSampleRequested = false;
portMUX_TYPE g_timerMux = portMUX_INITIALIZER_UNLOCKED;
hw_timer_t* g_weightTimer = nullptr;

float g_latestWeight = 0.0f;

bool g_feedState = false;
float g_targetGram = 0.0f;
FeedMode g_feedMode = FeedMode::MANUAL;

bool g_isFeederRunning = false;
bool g_logOnStop = false;
float g_startWeight = 0.0f;
uint32_t g_feedStartMillis = 0;
char g_activeModeLabel[8] = "manual";

void motorBStart() {
  digitalWrite(MOTOR_B_ENB, HIGH);
  digitalWrite(MOTOR_B_IN3, HIGH);
  digitalWrite(MOTOR_B_IN4, LOW);
}

void motorBStop() {
  digitalWrite(MOTOR_B_ENB, LOW);
  digitalWrite(MOTOR_B_IN3, LOW);
  digitalWrite(MOTOR_B_IN4, LOW);
}

float computeDispensedGram(float startWeight, float currentWeight) {
  // Project context uses: gram = temp_start_weight - current_weight
  const float dispensed = startWeight - currentWeight;
  return dispensed > 0.0f ? dispensed : 0.0f;
}

void buildTimeString(char* out, size_t outLen) {
  const time_t now = time(nullptr);
  struct tm t;
#if defined(_POSIX_THREAD_SAFE_FUNCTIONS)
  localtime_r(&now, &t);
#else
  struct tm* ptr = localtime(&now);
  if (ptr != nullptr) {
    t = *ptr;
  } else {
    memset(&t, 0, sizeof(t));
  }
#endif
  snprintf(out, outLen, "%02d:%02d %02d/%02d/%04d",
           t.tm_hour,
           t.tm_min,
           t.tm_mday,
           t.tm_mon + 1,
           t.tm_year + 1900);
}

void queueFeedLog(float gram, const char* mode) {
  FeedLogEntry entry{};
  entry.gram = gram;
  strncpy(entry.mode, mode, sizeof(entry.mode) - 1);
  entry.mode[sizeof(entry.mode) - 1] = '\0';
  buildTimeString(entry.time, sizeof(entry.time));

  if (xQueue_FeedingLogs != nullptr) {
    xQueueSend(xQueue_FeedingLogs, &entry, 0);
  }
}

void stopFeedingAndLogIfNeeded(bool forceLog) {
  if (!g_isFeederRunning) {
    return;
  }

  motorBStop();
  g_isFeederRunning = false;

  if (forceLog || g_logOnStop) {
    const float gram = computeDispensedGram(g_startWeight, g_latestWeight);
    queueFeedLog(gram, g_activeModeLabel);
  }

  g_logOnStop = false;
  g_feedState = false;
  g_targetGram = 0.0f;
}

void startFeeding(const char* modeLabel, bool logOnStop) {
  if (g_isFeederRunning) {
    return;
  }

  g_startWeight = g_latestWeight;
  g_feedStartMillis = millis();
  g_logOnStop = logOnStop;

  strncpy(g_activeModeLabel, modeLabel, sizeof(g_activeModeLabel) - 1);
  g_activeModeLabel[sizeof(g_activeModeLabel) - 1] = '\0';

  motorBStart();
  g_isFeederRunning = true;
}

void IRAM_ATTR onWeightTimerTick() {
  portENTER_CRITICAL_ISR(&g_timerMux);
  g_weightSampleRequested = true;
  portEXIT_CRITICAL_ISR(&g_timerMux);
}

void handleIncomingCommand(const CommandMessage& cmd) {
  switch (cmd.cmdType) {
    case CMD_FEED_STATE:
      g_feedState = (cmd.value > 0.5f);
      break;
    case CMD_FEED_TARGET_GRAM:
      g_targetGram = cmd.value;
      break;
    case CMD_FEED_MODE_AUTO:
      g_feedMode = FeedMode::AUTO;
      break;
    case CMD_FEED_MODE_GRAM:
      g_feedMode = FeedMode::GRAM;
      break;
    case CMD_FEED_MODE_MANUAL:
      g_feedMode = FeedMode::MANUAL;
      break;
    default:
      break;
  }
}

void processWeightSampleIfRequested() {
  bool shouldSample = false;

  portENTER_CRITICAL(&g_timerMux);
  if (g_weightSampleRequested) {
    shouldSample = true;
    g_weightSampleRequested = false;
  }
  portEXIT_CRITICAL(&g_timerMux);

  if (!shouldSample) {
    return;
  }

  if (g_loadCell.is_ready()) {
    g_latestWeight = g_loadCell.get_units(1);
  }
}

void applyFeedStateMachine() {
  if (g_isFeederRunning) {
    const uint32_t elapsed = millis() - g_feedStartMillis;
    if (elapsed >= MOTOR_B_TIMEOUT_MS) {
      stopFeedingAndLogIfNeeded(true);
      return;
    }

    if (!g_feedState) {
      stopFeedingAndLogIfNeeded(true);
      return;
    }
  }

  if (!g_isFeederRunning && g_feedState) {
    switch (g_feedMode) {
      case FeedMode::GRAM:
        if (g_targetGram > 0.0f) {
          startFeeding("gram", true);
        }
        break;
      case FeedMode::AUTO:
        startFeeding("auto", true);
        break;
      case FeedMode::MANUAL:
        startFeeding("manual", true);
        break;
    }
  }

  if (g_isFeederRunning && g_feedMode == FeedMode::GRAM) {
    const float dispensed = computeDispensedGram(g_startWeight, g_latestWeight);
    if (dispensed >= g_targetGram && g_targetGram > 0.0f) {
      stopFeedingAndLogIfNeeded(true);
    }
  }

  if (g_isFeederRunning && g_feedMode == FeedMode::MANUAL && !g_feedState) {
    stopFeedingAndLogIfNeeded(true);
  }
}

void feedingMotorTask(void* pvParameters) {
  (void)pvParameters;

  CommandMessage cmd{};
  for (;;) {
    while (xQueue_Commands != nullptr &&
           xQueueReceive(xQueue_Commands, &cmd, 0) == pdTRUE) {
      handleIncomingCommand(cmd);
    }

    processWeightSampleIfRequested();
    applyFeedStateMachine();

    vTaskDelay(pdMS_TO_TICKS(20));
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(MOTOR_B_ENB, OUTPUT);
  pinMode(MOTOR_B_IN3, OUTPUT);
  pinMode(MOTOR_B_IN4, OUTPUT);
  motorBStop();

  g_loadCell.begin(HX711_DOUT_PIN, HX711_SCK_PIN);
  g_loadCell.set_scale(HX711_SCALE_FACTOR);
  delay(1500);
  g_loadCell.tare();
  g_latestWeight = g_loadCell.get_units(1);

  xQueue_SensorData = xQueueCreate(8, sizeof(SensorData));
  xQueue_Commands = xQueueCreate(12, sizeof(CommandMessage));
  xQueue_FeedingLogs = xQueueCreate(20, sizeof(FeedLogEntry));

  g_weightTimer = timerBegin(0, 80, true);
  timerAttachInterrupt(g_weightTimer, &onWeightTimerTick, true);
  timerAlarmWrite(g_weightTimer, WEIGHT_SAMPLE_PERIOD_US, true);
  timerAlarmEnable(g_weightTimer);

  xTaskCreatePinnedToCore(
      feedingMotorTask,
      "FeedingMotorTask",
      4096,
      nullptr,
      4,
      nullptr,
      1);
}

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

  pinMode(PIN_OXY_EN, OUTPUT);
  pinMode(PIN_OXY_IN1, OUTPUT);
  pinMode(PIN_OXY_IN2, OUTPUT);
  pinMode(PIN_FEED_EN, OUTPUT);
  pinMode(PIN_FEED_IN1, OUTPUT);
  pinMode(PIN_FEED_IN2, OUTPUT);

  digitalWrite(PIN_OXY_EN, LOW);
  digitalWrite(PIN_OXY_IN1, LOW);
  digitalWrite(PIN_OXY_IN2, LOW);
  digitalWrite(PIN_FEED_EN, LOW);
  digitalWrite(PIN_FEED_IN1, LOW);
  digitalWrite(PIN_FEED_IN2, LOW);

  startNetworkTask(4);
  startFeedingTask(3);
  startSensorTask(2);
  startAutomationTask(1);

  Serial.println("[BOOT] FreeRTOS tasks started.");
}

void loop() {
  vTaskDelay(pdMS_TO_TICKS(1000));
}