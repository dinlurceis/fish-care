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
    // ── Lọc trung vị (Median Filter) với 15 mẫu theo phần 2.5.1 ──
    int samples[15];
    for(int i = 0; i < 15; i++) {
        samples[i] = analogRead(TDS_PIN);
        delay(2); // delay cực nhỏ giữa các lần đọc
    }
    
    // Sắp xếp mảng để lấy giá trị giữa
    std::sort(samples, samples + 15);
    int medianAdc = samples[7]; // Mẫu vị trí giữa (index 7)

    // ── Hiệu chuẩn: Chuyển ADC → Điện áp (phần 2.5.2 a) ──
    float voltage = medianAdc * TDS_VREF / TDS_ADC_RES;

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
