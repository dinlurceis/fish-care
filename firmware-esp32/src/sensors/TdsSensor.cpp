#include "TdsSensor.h"

TdsSensor::TdsSensor(float kalmanQ, float kalmanR)
    : _kalman(kalmanQ, kalmanR, 0.0f, 1.0f)
    , _index(0)
    , _bufferFull(false)
    , _filtered(0.0f)
{
    memset(_buffer, 0, sizeof(_buffer));
}

void TdsSensor::begin() {
    // GPIO 34 là input-only trên ESP32, không cần pinMode đặc biệt
    // nhưng đặt attenuation để đo full range 0-3.3V
    analogSetPinAttenuation(TDS_PIN, ADC_11db); // 0 - 3.3V range
    Serial.printf("[TDS] Init on GPIO %d (ADC 12-bit, 0-3.3V)\n", TDS_PIN);
}

float TdsSensor::readTds() {
    // Bước 1: Moving Average trên 10 mẫu ADC thô
    float avgAdc = _movingAverageAdc();

    // Bước 2: Chuyển ADC → Điện áp → TDS ppm
    float voltage = avgAdc * TDS_VREF / TDS_ADC_RES;
    float tdsRaw  = _voltageToPpm(voltage);

    // Bước 3: Kalman filter để làm mượt thêm lần hai
    _filtered = _kalman.update(tdsRaw);

    // TDS không thể âm
    if (_filtered < 0.0f) _filtered = 0.0f;

    return _filtered;
}

// ─── Private ─────────────────────────────────────────────────

float TdsSensor::_movingAverageAdc() {
    // Đọc 1 mẫu mới vào circular buffer
    _buffer[_index] = analogRead(TDS_PIN);
    _index = (_index + 1) % TDS_MA_SAMPLES;
    if (_index == 0) _bufferFull = true;

    uint8_t count = _bufferFull ? TDS_MA_SAMPLES : _index;
    long    sum   = 0;
    for (uint8_t i = 0; i < count; i++) {
        sum += _buffer[i];
    }
    return (float)sum / count;
}

float TdsSensor::_voltageToPpm(float voltage) {
    // Công thức từ PROJECT_CONTEXT.md:
    // TDS = (133.42*V³ - 255.86*V² + 857.39*V) * 0.75
    float v2 = voltage * voltage;
    float v3 = v2 * voltage;
    return (133.42f * v3 - 255.86f * v2 + 857.39f * voltage) * 0.75f;
}
