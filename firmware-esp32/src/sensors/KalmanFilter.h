#pragma once

// ============================================================
//  BỘ LỌC KALMAN 1D (Scalar Kalman Filter)
//
//  Dùng cho tín hiệu analog bị nhiễu (TDS, Turbidity).
//  Phương trình cơ bản:
//    Predict:  x_hat = x_hat  (no control input)
//              P = P + Q
//    Update:   K = P / (P + R)
//              x_hat = x_hat + K * (measurement - x_hat)
//              P = (1 - K) * P
//
//  Tham số cần chỉnh:
//    Q (process noise)    → nhỏ: tin mô hình hơn, mượt hơn
//    R (measurement noise) → lớn: tin đo ít hơn, lọc mạnh hơn
// ============================================================
class KalmanFilter1D {
public:
    /**
     * @param processNoise     Q - Nhiễu mô hình (thường 0.01 - 0.1)
     * @param measurementNoise R - Nhiễu đo lường (thường 1 - 50, tuỳ cảm biến)
     * @param initialEstimate  Giá trị khởi tạo ước lượng x̂₀
     * @param initialError     Sai số ước lượng ban đầu P₀ (thường 1.0)
     */
    KalmanFilter1D(float processNoise    = 0.01f,
                   float measurementNoise = 10.0f,
                   float initialEstimate  = 0.0f,
                   float initialError     = 1.0f);

    /**
     * @brief  Đưa 1 giá trị đo vào, trả về ước lượng đã lọc.
     * @param  measurement  Giá trị đọc thô từ ADC/cảm biến
     * @return float        Giá trị sau lọc Kalman
     */
    float update(float measurement);

    /** Reset bộ lọc về trạng thái khởi tạo */
    void reset(float initialEstimate = 0.0f);

private:
    float _Q;        // Process noise covariance
    float _R;        // Measurement noise covariance
    float _x;        // State estimate (x̂)
    float _P;        // Estimate error covariance
};
