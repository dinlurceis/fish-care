#include "TdsSensor.h"
#include <algorithm>

void TdsSensor::begin() {
    // ── Cấu hình chân ADC ──
    // GPIO 34 là input-only trên ESP32, không cần pinMode
    // Đặt attenuation để đo full range 0-3.3V
    analogSetPinAttenuation(TDS_PIN, ADC_11db);
    Serial.printf("[TDS] Init on GPIO %d (12-bit ADC, 0-3.3V)\n", TDS_PIN);
}

float TdsSensor::readTds() {
    // ── Đọc trực tiếp ──
    int rawAdc = analogRead(TDS_PIN);

    // ── Hiệu chuẩn: Chuyển ADC → Điện áp ──
    float voltage = rawAdc * TDS_VREF / TDS_ADC_RES;

    // ── Tính TDS ppm ──
    float tds = _voltageToPpm(voltage);

    // Bảo vệ: TDS không thể âm
    if (tds < 0.0f) tds = 0.0f;

    return tds;
}

// ─── Private ─────────────────────────────────────────────────

float TdsSensor::_voltageToPpm(float voltage) {
    // TDS = (133.42*V³ - 255.86*V² + 857.39*V) * 0.75
    float v2 = voltage * voltage;
    float v3 = v2 * voltage;
    return (133.42f * v3 - 255.86f * v2 + 857.39f * voltage) * 0.75f;
}
