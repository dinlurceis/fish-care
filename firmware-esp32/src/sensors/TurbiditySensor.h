#pragma once

#include <Arduino.h>

//  CẢM BIẾN ĐỘ ĐỤC NƯỚC TS-300B
//  Giao tiếp: Analog ADC
//  Chân:      GPIO 32 (ADC1_CH4, theo PROJECT_CONTEXT.md)
//  
//  🔍 GHI CHÚ THIẾT KẾ:
//  - Tín hiệu TỶ LỆ NGHỊCH với độ đục
//    (ADC cao = nước trong, ADC thấp = nước đục)
//  - Output: Giá trị int 0-4095 (Firebase schema: "ts300b")

#define TURBIDITY_PIN               32

// Ngưỡng cảnh báo: nếu ADC < này = nước đục nguy hiểm
// (Dùng bởi AutomationTask để trigger edge automation)
#define TURBIDITY_ALERT_THRESHOLD   1500

class TurbiditySensor {
public:
    TurbiditySensor() = default;

    /** Cấu hình pin ADC */
    void begin();

    /**
     * @brief Đọc độ đục: 1 lần ADC → trả về int (0-4095)
     * @return int  Giá trị raw 0-4095
     *              Cao = nước trong, Thấp = nước đục (tỷ lệ nghịch)
     */
    int readTurbidity();
};
