#pragma once

#include <Arduino.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// ============================================================
//  CẢM BIẾN NHIỆT ĐỘ DS18B20
//  Giao tiếp: OneWire
//  Chân:      GPIO 18 (theo PROJECT_CONTEXT.md)
//  Lọc nhiễu: Moving Average 10 mẫu (nhiệt độ thay đổi chậm,
//             Moving Average đủ dùng, không cần Kalman)
// ============================================================

#define DS18B20_PIN         18
#define DS18B20_MA_SAMPLES  10      // Số mẫu Moving Average
#define DS18B20_ERROR_VAL  -127.0f  // Giá trị lỗi của DallasTemperature

class DS18B20Sensor {
public:
    DS18B20Sensor();

    /** Khởi tạo bus OneWire và thư viện DallasTemperature */
    void begin();

    /**
     * @brief  Đọc nhiệt độ (có blocking ~750ms ở độ phân giải 12-bit).
     *         Đã áp dụng Moving Average.
     * @return float  Nhiệt độ °C, hoặc DS18B20_ERROR_VAL nếu lỗi.
     */
    float readTemperature();

    /** Trả về giá trị Moving Average hiện tại (không kích hoạt đọc mới) */
    float getFiltered() const { return _filtered; }

    /** true nếu lần đọc gần nhất thành công */
    bool isValid() const { return _valid; }

private:
    OneWire         _oneWire;
    DallasTemperature _sensors;

    // Moving Average buffer
    float    _buffer[DS18B20_MA_SAMPLES];
    uint8_t  _index;
    bool     _bufferFull;
    float    _filtered;
    bool     _valid;

    float _computeMovingAverage(float newVal);
};
