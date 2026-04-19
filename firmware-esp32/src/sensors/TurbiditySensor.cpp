#include "TurbiditySensor.h"
#include <algorithm>

void TurbiditySensor::begin() {
    // ── Cấu hình chân ADC ──
    // Đặt attenuation để đo full range 0-3.3V
    analogSetPinAttenuation(TURBIDITY_PIN, ADC_11db);
    Serial.printf("[Turbidity] TS-300B init on GPIO %d\n", TURBIDITY_PIN);
    Serial.printf("[Turbidity] Alert threshold: ADC < %d (nước đục)\n",
                  TURBIDITY_ALERT_THRESHOLD);
}

int TurbiditySensor::readTurbidity() {
    // ── Lọc trung vị (Median Filter) với 15 mẫu (phần 2.5.1) ──
    int samples[15];
    for(int i = 0; i < 15; i++) {
        samples[i] = analogRead(TURBIDITY_PIN);
        delay(2); // delay ngắn giữa các mẫu
    }
    
    // Sắp xếp mảng để lấy giá trị giữa
    std::sort(samples, samples + 15);
    int turbidityRaw = samples[7]; // Lấy giá trị trung vị (index 7)

    // Bảo vệ: giới hạn trong range 0-4095
    turbidityRaw = constrain(turbidityRaw, 0, 4095);

    return turbidityRaw;
}

bool TurbiditySensor::isAlertLevel() const {
    // ⚠️  TODO: Hiện tại không lưu state → hàm này chưa dùng được
    // Cách 1: Lưu _lastValue trong readTurbidity()
    // Cách 2: Hoặc gọi readTurbidity() rồi check ngay trong AutomationTask
    // Tạm thời trả về false (sẽ implement sau)
    return false;
}
