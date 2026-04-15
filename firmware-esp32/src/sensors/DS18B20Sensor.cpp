#include "DS18B20Sensor.h"

DS18B20Sensor::DS18B20Sensor()
    : _oneWire(DS18B20_PIN)
    , _sensors(&_oneWire)
    , _index(0)
    , _bufferFull(false)
    , _filtered(0.0f)
    , _valid(false)
{
    memset(_buffer, 0, sizeof(_buffer));
}

void DS18B20Sensor::begin() {
    _sensors.begin();
    // Đặt độ phân giải 12-bit (0.0625°C)
    _sensors.setResolution(12);
    // Không dùng blocking wait - ta chủ động requestTemperatures() + đọc
    _sensors.setWaitForConversion(true); // blocking trong readTemperature()
    Serial.printf("[DS18B20] Found %d sensor(s) on GPIO %d\n",
                  _sensors.getDeviceCount(), DS18B20_PIN);
}

float DS18B20Sensor::readTemperature() {
    _sensors.requestTemperatures(); // ~750ms blocking ở 12-bit

    float raw = _sensors.getTempCByIndex(0);

    if (raw == DS18B20_ERROR_VAL || raw == DEVICE_DISCONNECTED_C) {
        Serial.println("[DS18B20] ERROR: sensor disconnected or CRC fail");
        _valid = false;
        return DS18B20_ERROR_VAL;
    }

    _valid   = true;
    _filtered = _computeMovingAverage(raw);
    return _filtered;
}

// ─── Private ─────────────────────────────────────────────────
float DS18B20Sensor::_computeMovingAverage(float newVal) {
    _buffer[_index] = newVal;
    _index = (_index + 1) % DS18B20_MA_SAMPLES;

    if (_index == 0) _bufferFull = true;

    uint8_t count = _bufferFull ? DS18B20_MA_SAMPLES : _index;
    float   sum   = 0.0f;
    for (uint8_t i = 0; i < count; i++) {
        sum += _buffer[i];
    }
    return sum / count;
}
