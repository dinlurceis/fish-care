#pragma once

#include <Arduino.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// ============================================================
//  CẢM BIẾN NHIỆT ĐỘ DS18B20
//  Giao tiếp: OneWire
//  Chân:      GPIO 18 (theo PROJECT_CONTEXT.md)
//  
//  🔍 GHI CHÚ THIẾT KẾ:
//  - Đọc nhiệt độ 1 lần/2 giây → dữ liệu đã mượt
//  - Thời gian đọc: ~750ms ở độ phân giải 12-bit
//  - Không cần lọc nhiễu (thay đổi chậm, không spike)
// ============================================================

#define DS18B20_PIN         18
#define DS18B20_ERROR_VAL  -127.0f  // Giá trị lỗi của DallasTemperature

class DS18B20Sensor {
public:
    DS18B20Sensor();

    /** Khởi tạo bus OneWire và thư viện DallasTemperature */
    void begin();

    /**
     * @brief  Đọc nhiệt độ từ DS18B20 (blocking ~750ms ở 12-bit)
     * @return float  Nhiệt độ °C, hoặc DS18B20_ERROR_VAL nếu lỗi
     */
    float readTemperature();

    /** true nếu lần đọc gần nhất thành công */
    bool isValid() const { return _valid; }

private:
    OneWire         _oneWire;
    DallasTemperature _sensors;
    bool            _valid;
};
