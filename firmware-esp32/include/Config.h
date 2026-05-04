#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/semphr.h>

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

// WiFi/Firebase credentials được load từ include/secrets.h

// Queue sizing and task timing.
constexpr uint8_t SENSOR_QUEUE_LENGTH = 12;
constexpr uint8_t COMMAND_QUEUE_LENGTH = 12;
constexpr uint16_t SENSOR_SAMPLE_INTERVAL_MS = 2000;
constexpr uint16_t AUTOMATION_CHECK_INTERVAL_MS = 100;    // Check every 100ms (was 50ms)

// Retry and watchdog behavior.
constexpr uint32_t WIFI_RETRY_BASE_MS = 2000;
constexpr uint32_t WIFI_RETRY_MAX_MS = 30000;
constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 10000;
constexpr uint8_t NETWORK_WDT_TIMEOUT_SEC = 20;  
constexpr uint8_t OFFLINE_CACHE_CAPACITY = 32;


//  COMMAND DATA STRUCTURE - Firebase → ESP32 Control
typedef enum {
    CMD_GUONG_ON = 1,      // Bật oxy motor (Motor A)
    CMD_GUONG_OFF = 2,     // Tắt oxy motor
    CMD_THUCAN_GRAM = 3,   // Nhả cám theo gram
    CMD_THUCAN_MANUAL = 4, // Nhả cám manual (manual on/off)
    CMD_THUCAN_AUTO = 5,   // Nhả cám auto mode
    CMD_RESET = 6          // Reset mạch
} CommandType_e;

typedef struct {
    CommandType_e type;    // Loại lệnh
    float value;           // Giá trị kèm theo (ví dụ: gram cần nhả)
    uint32_t timestamp;    // Thời điểm lệnh được tạo
} CommandData_t;


//  CẤU TRÚC DỮ LIỆU CẢM BIẾN - Shared payload giữa các Task

typedef struct {
    float temperature;   // °C  - DS18B20 (GPIO 18), OneWire
    float tds;           // ppm - TDS analog (GPIO 34), direct ADC read
    int   turbidity;     // raw 0-4095 - TS-300B (GPIO 32), direct ADC read
    float weight;        // grams - LoadCell (cập nhật bởi FeedingTask)
    uint32_t timestamp;  // millis() lúc đọc xong
} SensorData_t;


extern QueueHandle_t xQueue_SensorData;
extern QueueHandle_t xQueue_FeedCommands;
extern QueueHandle_t xQueue_AutoCommands;
extern SemaphoreHandle_t xMutex_Firebase;
extern volatile bool isWiFiConnected;