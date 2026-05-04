#include "DS18B20Sensor.h"

DS18B20Sensor::DS18B20Sensor()
    : _oneWire(DS18B20_PIN)
    , _sensors(&_oneWire)
    , _valid(false)
{
}

void DS18B20Sensor::begin() {
    _sensors.begin();
    // Đặt độ phân giải 12-bit (0.0625°C)
    _sensors.setResolution(12);
    // Blocking wait - ta chủ động requestTemperatures() + đọc
    _sensors.setWaitForConversion(true); // blocking trong readTemperature()
    Serial.printf("[DS18B20] Found %d sensor(s) on GPIO %d\n",
                  _sensors.getDeviceCount(), DS18B20_PIN);
}

float DS18B20Sensor::readTemperature() {
    // ── Đọc 1 lần temperature (blocking ~750ms ở 12-bit) ──
    _sensors.requestTemperatures();
    float raw = _sensors.getTempCByIndex(0);

    // ── Xử lý lỗi ──
    if (raw == DS18B20_ERROR_VAL || raw == DEVICE_DISCONNECTED_C) {
        Serial.println("[DS18B20] ERROR: sensor disconnected or CRC fail");
        _valid = false;
        return DS18B20_ERROR_VAL;
    }

    _valid = true;
    return raw;
}
