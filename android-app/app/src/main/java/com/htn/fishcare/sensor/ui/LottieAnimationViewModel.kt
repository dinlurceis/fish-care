package com.htn.fishcare.sensor.ui

import androidx.annotation.RawRes
import androidx.lifecycle.ViewModel
import com.htn.fishcare.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SensorAnimationState(
    @RawRes val temperatureAnimation: Int = R.raw.sun,
    @RawRes val humidityAnimation: Int = R.raw.sky,
    @RawRes val tdsAnimation: Int = R.raw.tds,
    @RawRes val turbidityAnimation: Int = R.raw.cannang,
    @RawRes val phAnimation: Int = R.raw.sun
)

@HiltViewModel
class LottieAnimationViewModel @Inject constructor() : ViewModel() {

    private val _animationState = MutableStateFlow(SensorAnimationState())
    val animationState: StateFlow<SensorAnimationState> = _animationState

    /**
     * Cập nhật animation dựa trên nhiệt độ (°C)
     *  < 15°C  → rain   (quá lạnh)
     * 15–24°C  → sky    (lạnh vừa)
     * 24–34°C  → sun    (bình thường ✅)
     * 34–40°C  → doduc  (nóng)
     *  > 40°C  → windy  (quá nóng)
     */
    fun updateTemperatureAnimation(temperature: Float) {
        val animRes = when {
            temperature < 15f  -> R.raw.rain
            temperature < 24f  -> R.raw.sky
            temperature <= 34f -> R.raw.sun
            temperature <= 40f -> R.raw.doduc
            else               -> R.raw.windy
        }
        _animationState.value = _animationState.value.copy(temperatureAnimation = animRes)
    }

    /**
     * Cập nhật animation dựa trên độ ẩm (%)
     *  < 40%   → sun   (khô)
     * 40–60%   → sky   (bình thường)
     *  > 60%   → rain  (ẩm cao)
     */
    fun updateHumidityAnimation(humidity: Int) {
        val animRes = when {
            humidity < 40  -> R.raw.sun
            humidity <= 60 -> R.raw.sky
            else           -> R.raw.rain
        }
        _animationState.value = _animationState.value.copy(humidityAnimation = animRes)
    }

    /**
     * Cập nhật animation dựa trên TDS (ppm)
     *  < 100   → ca     (nước tinh khiết)
     * 100–300  → sun    (bình thường)
     * 300–600  → doduc  (cao)
     *  > 600   → windy  (quá mặn)
     */
    fun updateTdsAnimation(tds: Int) {
        val animRes = when {
            tds < 100  -> R.raw.ca
            tds <= 300 -> R.raw.sun
            tds <= 600 -> R.raw.doduc
            else       -> R.raw.windy
        }
        _animationState.value = _animationState.value.copy(tdsAnimation = animRes)
    }

    /**
     * Cập nhật animation theo độ đục (NTU)
     *  < 5 NTU  → sun    (trong)
     *  5–15     → ca     (hơi đục)
     * 15–50     → rain   (đục)
     *  > 50     → windy  (rất đục)
     */
    fun updateTurbidityAnimation(turbidity: Float) {
        val animRes = when {
            turbidity < 5f  -> R.raw.sun
            turbidity < 15f -> R.raw.ca
            turbidity < 50f -> R.raw.rain
            else            -> R.raw.windy
        }
        _animationState.value = _animationState.value.copy(turbidityAnimation = animRes)
    }

    /**
     * Cập nhật animation theo pH
     *  < 6.0  → rain  (quá axit)
     * 6.0–8.0 → sun   (bình thường ✅)
     *  > 8.0  → sky   (quá kiềm)
     */
    fun updatePhAnimation(ph: Float) {
        val animRes = when {
            ph < 6.0f  -> R.raw.rain
            ph <= 8.0f -> R.raw.sun
            else       -> R.raw.sky
        }
        _animationState.value = _animationState.value.copy(phAnimation = animRes)
    }

    /** Cập nhật tất cả animations cùng lúc */
    fun updateAllAnimations(
        temperature: Float,
        humidity: Int = 60,
        tds: Int,
        turbidity: Float,
        ph: Float = 7.0f
    ) {
        updateTemperatureAnimation(temperature)
        updateHumidityAnimation(humidity)
        updateTdsAnimation(tds)
        updateTurbidityAnimation(turbidity)
        updatePhAnimation(ph)
    }
}
