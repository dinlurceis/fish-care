#pragma once

#include <Arduino.h>

//  CẢM BIẾN TDS (Total Dissolved Solids)
//  Giao tiếp: Analog ADC
//  Chân:      GPIO 34 (ADC1_CH6 - input only, theo PROJECT_CONTEXT.md)

//  Công thức: V = adc * 3.3 / 4096.0
//             TDS = (133.42*V³ - 255.86*V² + 857.39*V) * 0.75

#define TDS_PIN         34      // GPIO 34 (ADC1_CH6)
#define TDS_VREF        3.3f    // Điện áp tham chiếu ESP32 (V)
#define TDS_ADC_RES     4096.0f // Độ phân giải ADC 12-bit

class TdsSensor {
public:
    TdsSensor() = default;

    /** Cấu hình pin ADC */
    void begin();

    /**
     * @brief Đọc TDS: 1 lần ADC raw → chuyển V → TDS ppm
     * @return float  Giá trị TDS (ppm), >= 0
     */
    float readTds();

private:
    /** Chuyển điện áp → TDS ppm theo công thức từ PROJECT_CONTEXT */
    static float _voltageToPpm(float voltage);
};
