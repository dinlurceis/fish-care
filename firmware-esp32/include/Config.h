#pragma once

#include <Arduino.h>

#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"

// Pins from PROJECT_CONTEXT.md - keep these fixed to match wired hardware.
constexpr int PIN_DS18B20 = 18;
constexpr int PIN_TDS_ADC = 34;
constexpr int PIN_TURBIDITY_ADC = 32;

constexpr int PIN_FEED_DOUT = 21;
constexpr int PIN_FEED_SCK = 22;
constexpr int PIN_FEED_EN = 23;
constexpr int PIN_FEED_IN1 = 14;
constexpr int PIN_FEED_IN2 = 12;

constexpr int PIN_OXY_EN = 5;
constexpr int PIN_OXY_IN1 = 26;
constexpr int PIN_OXY_IN2 = 27;

// WiFi/Firebase credentials.
constexpr char WIFI_SSID[] = "YOUR_WIFI_SSID";
constexpr char WIFI_PASSWORD[] = "YOUR_WIFI_PASSWORD";
constexpr char FIREBASE_API_KEY[] = "YOUR_FIREBASE_API_KEY";
constexpr char FIREBASE_DB_URL[] = "https://your-project-id-default-rtdb.firebaseio.com";

// Queue sizing and task timing.
constexpr uint8_t SENSOR_QUEUE_LENGTH = 12;
constexpr uint8_t COMMAND_QUEUE_LENGTH = 12;
constexpr uint16_t SENSOR_SAMPLE_INTERVAL_MS = 2000;
constexpr uint16_t AUTOMATION_CHECK_INTERVAL_MS = 10000;

// Retry and watchdog behavior.
constexpr uint32_t WIFI_RETRY_BASE_MS = 2000;
constexpr uint32_t WIFI_RETRY_MAX_MS = 30000;
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 10000;
constexpr uint8_t NETWORK_WDT_TIMEOUT_SEC = 20;
constexpr uint8_t OFFLINE_CACHE_CAPACITY = 32;

struct SensorData {
  float temperatureC;
  float tds;
  int turbidityRaw;
  float weightGram;
  uint32_t timestampMs;
};

enum class CommandType : uint8_t {
  SET_OXY = 0,
  START_FEED = 1,
};

struct CommandMessage {
  CommandType type;
  bool boolValue;
  float floatValue;
  char mode[12];
};

extern QueueHandle_t gSensorQueue;
extern QueueHandle_t gCommandQueue;
extern SemaphoreHandle_t gFirebaseMutex;