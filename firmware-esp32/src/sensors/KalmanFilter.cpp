#include "KalmanFilter.h"

KalmanFilter1D::KalmanFilter1D(float processNoise,
                               float measurementNoise,
                               float initialEstimate,
                               float initialError)
    : _Q(processNoise)
    , _R(measurementNoise)
    , _x(initialEstimate)
    , _P(initialError)
{}

float KalmanFilter1D::update(float measurement) {
    // --- Predict ---
    // x_hat không đổi (mô hình không có input điều khiển)
    _P = _P + _Q;

    // --- Update ---
    float K = _P / (_P + _R);          // Kalman Gain
    _x = _x + K * (measurement - _x); // Cập nhật ước lượng
    _P = (1.0f - K) * _P;             // Cập nhật sai số ước lượng

    return _x;
}

void KalmanFilter1D::reset(float initialEstimate) {
    _x = initialEstimate;
    _P = 1.0f;
}
