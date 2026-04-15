#pragma once

#include <Arduino.h>
#include "KalmanFilter.h"

// ============================================================
//  CẢM BIẾN TDS (Total Dissolved Solids)
//  Giao tiếp: Analog ADC
//  Chân:      GPIO 34 (ADC1_CH6 - input only, theo PROJECT_CONTEXT.md)
//  Lọc nhiễu: Moving Average 10 mẫu + Kalman 1D (dual filter)
//  Công thức: V = adc * 3.3 / 4096.0
//             TDS = (133.42*V³ - 255.86*V² + 857.39*V) * 0.75
// ============================================================

#define TDS_PIN         34
#define TDS_MA_SAMPLES  10      // Số mẫu Moving Average thô
#define TDS_VREF        3.3f    // Điện áp tham chiếu ESP32 (V)
#define TDS_ADC_RES     4096.0f // Độ phân giải ADC 12-bit

class TdsSensor {
public:
    /**
     * @param kalmanQ  Process noise  (mặc định 0.01 - mượt, phản hồi chậm)
     * @param kalmanR  Sensor noise   (mặc định 15.0 - lọc mạnh nhiễu ADC)
     */
    TdsSensor(float kalmanQ = 0.01f, float kalmanR = 15.0f);

    /** Cấu hình pin ADC (gọi trong setup) */
    void begin();

    /**
     * @brief  Đọc TDS: lấy trung bình 10 mẫu ADC thô → chuyển V → TDS ppm
     *         → đưa qua Kalman để lọc lần hai.
     * @return float  Giá trị TDS (ppm) đã lọc, >= 0
     */
    float readTds();

    /** Giá trị TDS đã lọc hiện tại */
    float getFiltered() const { return _filtered; }

private:
    KalmanFilter1D _kalman;

    // Moving Average buffer (trên ADC raw)
    int     _buffer[TDS_MA_SAMPLES];
    uint8_t _index;
    bool    _bufferFull;
    float   _filtered;

    /** Đọc 10 mẫu ADC raw, trả về trung bình */
    float _movingAverageAdc();

    /** Chuyển điện áp → TDS ppm theo công thức từ PROJECT_CONTEXT */
    static float _voltageToPpm(float voltage);
};
