#pragma once

#include <Arduino.h>
#include "KalmanFilter.h"

// ============================================================
//  CẢM BIẾN ĐỘ ĐỤC NƯỚC TS-300B
//  Giao tiếp: Analog ADC
//  Chân:      GPIO 32 (ADC1_CH4, theo PROJECT_CONTEXT.md)
//  Đặc tính:  Tín hiệu TỶ LỆ NGHỊCH với độ đục
//             (ADC cao = nước trong, ADC thấp = nước đục)
//  Lọc nhiễu: Moving Average 10 mẫu + Kalman 1D
//  Output:    Giá trị int thô 0-4095 (theo Firebase schema: "ts300b")
// ============================================================

#define TURBIDITY_PIN         32
#define TURBIDITY_MA_SAMPLES  10
#define TURBIDITY_VREF        3.3f
#define TURBIDITY_ADC_RES     4096.0f

// Ngưỡng cảnh báo (dùng bởi AutomationTask của Duy)
// Giá trị ADC < ngưỡng này = nước đục nguy hiểm
#define TURBIDITY_ALERT_THRESHOLD  1500

class TurbiditySensor {
public:
    /**
     * @param kalmanQ  Process noise  (mặc định 0.01)
     * @param kalmanR  Sensor noise   (mặc định 20.0 - lọc mạnh vì ADC turbidity nhiễu hơn TDS)
     */
    TurbiditySensor(float kalmanQ = 0.01f, float kalmanR = 20.0f);

    /** Cấu hình pin ADC (gọi trong setup) */
    void begin();

    /**
     * @brief  Đọc độ đục: Moving Average 10 mẫu → Kalman → trả về int.
     * @return int  Giá trị raw 0-4095 đã lọc.
     *              Cao = trong, Thấp = đục (tỷ lệ nghịch).
     */
    int readTurbidity();

    /** Giá trị đã lọc hiện tại (int) */
    int getFiltered() const { return _filtered; }

    /**
     * @brief  Kiểm tra có đang ở mức cảnh báo không.
     * @return true nếu ADC < TURBIDITY_ALERT_THRESHOLD (nước đục)
     */
    bool isAlertLevel() const { return _filtered < TURBIDITY_ALERT_THRESHOLD; }

private:
    KalmanFilter1D _kalman;

    int     _buffer[TURBIDITY_MA_SAMPLES];
    uint8_t _index;
    bool    _bufferFull;
    int     _filtered;

    /** Đọc 10 mẫu ADC raw, trả về trung bình float */
    float _movingAverageAdc();
};
