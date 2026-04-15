#include "TurbiditySensor.h"

TurbiditySensor::TurbiditySensor(float kalmanQ, float kalmanR)
    : _kalman(kalmanQ, kalmanR, 2048.0f, 1.0f) // Khởi tạo ở giữa range
    , _index(0)
    , _bufferFull(false)
    , _filtered(2048) // Giá trị trung lập
{
    memset(_buffer, 0, sizeof(_buffer));
}

void TurbiditySensor::begin() {
    analogSetPinAttenuation(TURBIDITY_PIN, ADC_11db); // Full range 0-3.3V
    Serial.printf("[Turbidity] TS-300B init on GPIO %d\n", TURBIDITY_PIN);
    Serial.printf("[Turbidity] Alert threshold: ADC < %d (turbid water)\n",
                  TURBIDITY_ALERT_THRESHOLD);
}

int TurbiditySensor::readTurbidity() {
    // Bước 1: Moving Average 10 mẫu thô
    float avgAdc = _movingAverageAdc();

    // Bước 2: Kalman filter
    float kalmanOut = _kalman.update(avgAdc);

    // Ép về int (Firebase schema cần int cho ts300b)
    _filtered = (int)kalmanOut;

    // Giới hạn trong range hợp lệ
    _filtered = constrain(_filtered, 0, 4095);

    return _filtered;
}

// ─── Private ─────────────────────────────────────────────────

float TurbiditySensor::_movingAverageAdc() {
    _buffer[_index] = analogRead(TURBIDITY_PIN);
    _index = (_index + 1) % TURBIDITY_MA_SAMPLES;
    if (_index == 0) _bufferFull = true;

    uint8_t count = _bufferFull ? TURBIDITY_MA_SAMPLES : _index;
    long    sum   = 0;
    for (uint8_t i = 0; i < count; i++) {
        sum += _buffer[i];
    }
    return (float)sum / count;
}
