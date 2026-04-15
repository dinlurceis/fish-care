#include "TurbiditySensor.h"

void TurbiditySensor::begin() {
    // ── Cấu hình chân ADC ──
    // Đặt attenuation để đo full range 0-3.3V
    analogSetPinAttenuation(TURBIDITY_PIN, ADC_11db);
    Serial.printf("[Turbidity] TS-300B init on GPIO %d\n", TURBIDITY_PIN);
    Serial.printf("[Turbidity] Alert threshold: ADC < %d (nước đục)\n",
                  TURBIDITY_ALERT_THRESHOLD);
}

int TurbiditySensor::readTurbidity() {
    // ── Đọc 1 lần ADC thô ──
    int turbidityRaw = analogRead(TURBIDITY_PIN);

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
