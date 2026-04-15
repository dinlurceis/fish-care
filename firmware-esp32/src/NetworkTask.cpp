#include "NetworkTask.h"

#include <cstring>

#include <Firebase_ESP_Client.h>
#include <WiFi.h>
#include <esp_system.h>

namespace {
TaskHandle_t sTaskHandle = nullptr;

FirebaseData sFbdo;
FirebaseAuth sAuth;
FirebaseConfig sFirebaseConfig;

bool sWiFiConnected = false;
bool sFirebaseInitialized = false;
uint32_t sLastReconnectAttemptMs = 0;
uint32_t sCurrentRetryDelayMs = WIFI_RETRY_BASE_MS;

bool sLastOxyState = false;
bool sLastFeedState = false;
float sLastFeedTarget = 0.0f;
char sLastFeedMode[12] = "gram";

struct CachedSensorFrame {
  SensorData payload;
  uint8_t retryCount;
};

CachedSensorFrame sOfflineCache[OFFLINE_CACHE_CAPACITY];
size_t sCacheHead = 0;
size_t sCacheTail = 0;
size_t sCacheCount = 0;

portMUX_TYPE sWdtMux = portMUX_INITIALIZER_UNLOCKED;
volatile uint8_t sWdtSecondsWithoutLoop = 0;
volatile bool sWdtExpired = false;
hw_timer_t* sWdtTimer = nullptr;

void IRAM_ATTR networkWdtISR() {
  portENTER_CRITICAL_ISR(&sWdtMux);
  if (sWdtSecondsWithoutLoop < 255) {
    ++sWdtSecondsWithoutLoop;
  }
  if (sWdtSecondsWithoutLoop >= NETWORK_WDT_TIMEOUT_SEC) {
    sWdtExpired = true;
  }
  portEXIT_CRITICAL_ISR(&sWdtMux);
}

void resetWdtHeartbeat() {
  portENTER_CRITICAL(&sWdtMux);
  sWdtSecondsWithoutLoop = 0;
  portEXIT_CRITICAL(&sWdtMux);
}

bool isWdtExpired() {
  bool expired = false;
  portENTER_CRITICAL(&sWdtMux);
  expired = sWdtExpired;
  portEXIT_CRITICAL(&sWdtMux);
  return expired;
}

void initWatchdogInterrupt() {
  sWdtTimer = timerBegin(0, 80, true);
  timerAttachInterrupt(sWdtTimer, &networkWdtISR, true);
  timerAlarmWrite(sWdtTimer, 1000000ULL, true);
  timerAlarmEnable(sWdtTimer);
}

void cachePush(const SensorData& frame) {
  if (sCacheCount >= OFFLINE_CACHE_CAPACITY) {
    // Drop oldest frame first to keep newest readings available.
    sCacheHead = (sCacheHead + 1) % OFFLINE_CACHE_CAPACITY;
    --sCacheCount;
  }

  sOfflineCache[sCacheTail] = CachedSensorFrame{frame, 0};
  sCacheTail = (sCacheTail + 1) % OFFLINE_CACHE_CAPACITY;
  ++sCacheCount;
}

CachedSensorFrame* cacheFront() {
  if (sCacheCount == 0) {
    return nullptr;
  }
  return &sOfflineCache[sCacheHead];
}

void cachePop() {
  if (sCacheCount == 0) {
    return;
  }
  sCacheHead = (sCacheHead + 1) % OFFLINE_CACHE_CAPACITY;
  --sCacheCount;
}

bool sendSensorToFirebase(const SensorData& data) {
  if (!sFirebaseInitialized) {
    return false;
  }

  FirebaseJson payload;
  payload.set("temperature", data.temperatureC);
  payload.set("water_quality", data.tds);
  payload.set("ts300b", data.turbidityRaw);
  payload.set("weight", data.weightGram);

  if (xSemaphoreTake(gFirebaseMutex, pdMS_TO_TICKS(300)) != pdTRUE) {
    return false;
  }

  const bool ok = Firebase.RTDB.updateNode(&sFbdo, "/aquarium", &payload);
  xSemaphoreGive(gFirebaseMutex);

  return ok;
}

void publishCachedData() {
  CachedSensorFrame* front = cacheFront();
  if (front == nullptr) {
    return;
  }

  if (sendSensorToFirebase(front->payload)) {
    cachePop();
  } else {
    if (front->retryCount < 255) {
      ++front->retryCount;
    }
  }
}

void pushCommandIfChanged(bool oxyState, bool feedState, float feedTarget, const char* mode) {
  if (oxyState != sLastOxyState) {
    CommandMessage cmd{};
    cmd.type = CommandType::SET_OXY;
    cmd.boolValue = oxyState;
    xQueueSend(gCommandQueue, &cmd, 0);
    sLastOxyState = oxyState;
  }

  if (feedState != sLastFeedState || fabsf(feedTarget - sLastFeedTarget) > 0.01f || strncmp(mode, sLastFeedMode, sizeof(sLastFeedMode)) != 0) {
    CommandMessage cmd{};
    cmd.type = CommandType::START_FEED;
    cmd.boolValue = feedState;
    cmd.floatValue = feedTarget;
    strncpy(cmd.mode, mode, sizeof(cmd.mode) - 1);
    cmd.mode[sizeof(cmd.mode) - 1] = '\0';
    xQueueSend(gCommandQueue, &cmd, 0);

    sLastFeedState = feedState;
    sLastFeedTarget = feedTarget;
    strncpy(sLastFeedMode, mode, sizeof(sLastFeedMode) - 1);
    sLastFeedMode[sizeof(sLastFeedMode) - 1] = '\0';
  }
}

void pollControlNode() {
  if (!sFirebaseInitialized) {
    return;
  }

  if (xSemaphoreTake(gFirebaseMutex, pdMS_TO_TICKS(500)) != pdTRUE) {
    return;
  }

  bool ok = Firebase.RTDB.getJSON(&sFbdo, "/aquarium/control");
  if (!ok) {
    xSemaphoreGive(gFirebaseMutex);
    return;
  }

  FirebaseJson* root = sFbdo.to<FirebaseJson*>();
  FirebaseJsonData field;

  bool oxyState = false;
  bool feedState = false;
  float feedTarget = 0.0f;
  char mode[12] = "gram";

  root->get(field, "quat");
  if (field.success) {
    oxyState = field.to<bool>();
  }

  root->get(field, "thucan/state");
  if (field.success) {
    feedState = field.to<bool>();
  }

  root->get(field, "thucan/target_gram");
  if (field.success) {
    feedTarget = field.to<float>();
  }

  root->get(field, "thucan/mode");
  if (field.success && field.typeNum == FirebaseJson::JSON_STRING) {
    const String modeString = field.to<String>();
    modeString.toCharArray(mode, sizeof(mode));
  }

  xSemaphoreGive(gFirebaseMutex);
  pushCommandIfChanged(oxyState, feedState, feedTarget, mode);
}

void connectFirebaseIfNeeded() {
  if (sFirebaseInitialized) {
    return;
  }

  sFirebaseConfig.api_key = FIREBASE_API_KEY;
  sFirebaseConfig.database_url = FIREBASE_DB_URL;
  Firebase.reconnectNetwork(true);
  Firebase.begin(&sFirebaseConfig, &sAuth);
  sFirebaseInitialized = true;
}

void maintainWiFiConnection() {
  if (WiFi.status() == WL_CONNECTED) {
    sWiFiConnected = true;
    sCurrentRetryDelayMs = WIFI_RETRY_BASE_MS;
    return;
  }

  sWiFiConnected = false;

  const uint32_t now = millis();
  if (now - sLastReconnectAttemptMs < sCurrentRetryDelayMs) {
    return;
  }
  sLastReconnectAttemptMs = now;

  Serial.printf("[NetworkTask] Reconnecting WiFi... next retry backoff=%lu ms\n", static_cast<unsigned long>(sCurrentRetryDelayMs));
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  const uint32_t connectStart = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - connectStart < WIFI_CONNECT_TIMEOUT_MS) {
    vTaskDelay(pdMS_TO_TICKS(300));
  }

  if (WiFi.status() == WL_CONNECTED) {
    sWiFiConnected = true;
    sCurrentRetryDelayMs = WIFI_RETRY_BASE_MS;
    Serial.printf("[NetworkTask] WiFi connected. IP=%s\n", WiFi.localIP().toString().c_str());
  } else {
    sCurrentRetryDelayMs = min(sCurrentRetryDelayMs * 2UL, WIFI_RETRY_MAX_MS);
  }
}

void networkTaskLoop(void* /*unused*/) {
  WiFi.mode(WIFI_STA);
  connectFirebaseIfNeeded();
  initWatchdogInterrupt();

  for (;;) {
    resetWdtHeartbeat();

    if (isWdtExpired()) {
      Serial.println("[NetworkTask] WDT interrupt fired: task appears blocked. Restarting MCU...");
      delay(50);
      esp_restart();
    }

    maintainWiFiConnection();

    SensorData fresh{};
    if (xQueueReceive(gSensorQueue, &fresh, pdMS_TO_TICKS(100)) == pdPASS) {
      if (sWiFiConnected) {
        if (!sendSensorToFirebase(fresh)) {
          cachePush(fresh);
        }
      } else {
        cachePush(fresh);
      }
    }

    if (sWiFiConnected) {
      publishCachedData();
      pollControlNode();
    }

    vTaskDelay(pdMS_TO_TICKS(200));
  }
}
}  // namespace

void startNetworkTask(UBaseType_t priority, uint16_t stackSize) {
  xTaskCreatePinnedToCore(networkTaskLoop, "NetworkTask", stackSize, nullptr, priority, &sTaskHandle, 0);
}

bool NetworkTask_IsOnline() {
  return sWiFiConnected;
}