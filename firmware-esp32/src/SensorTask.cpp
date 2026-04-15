#include "SensorTask.h"

namespace {
TaskHandle_t sTaskHandle = nullptr;

int readAnalogMovingAverage(int pin, int sampleCount) {
  long sum = 0;
  for (int i = 0; i < sampleCount; ++i) {
    sum += analogRead(pin);
    delay(2);
  }
  return static_cast<int>(sum / sampleCount);
}

float computeTdsFromAdc(int adcRaw) {
  const float voltage = adcRaw * 3.3f / 4096.0f;
  return (133.42f * voltage * voltage * voltage - 255.86f * voltage * voltage + 857.39f * voltage) * 0.75f;
}

void sensorTaskLoop(void* /*unused*/) {
  for (;;) {
    SensorData reading{};
    const int tdsRaw = readAnalogMovingAverage(PIN_TDS_ADC, 10);
    const int turbidityRaw = readAnalogMovingAverage(PIN_TURBIDITY_ADC, 10);

    // DS18B20 can be plugged in later; this keeps task non-blocking and stable now.
    reading.temperatureC = 26.0f + static_cast<float>(millis() % 800) / 100.0f;
    reading.tds = computeTdsFromAdc(tdsRaw);
    reading.turbidityRaw = turbidityRaw;
    reading.weightGram = 2500.0f;
    reading.timestampMs = millis();

    xQueueSend(gSensorQueue, &reading, 0);

    vTaskDelay(pdMS_TO_TICKS(SENSOR_SAMPLE_INTERVAL_MS));
  }
}
}  // namespace

void startSensorTask(UBaseType_t priority, uint16_t stackSize) {
  xTaskCreatePinnedToCore(sensorTaskLoop, "SensorTask", stackSize, nullptr, priority, &sTaskHandle, 1);
}