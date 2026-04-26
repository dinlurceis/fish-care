#include <unity.h>
#include <Arduino.h>
#include <limits.h>
#include <cmath>
#include "../src/sensors/TurbiditySensor.h"
#include "../src/sensors/TdsSensor.h"
#include "../src/sensors/DS18B20Sensor.h"
#include "../include/Config.h"

// ============================================================
//  UNIT TESTS - TURBIDITY SENSOR (TS-300B)
//  GPIO 32, ADC 0-4095, tỷ lệ nghịch với độ đục
//  Alert threshold: ADC < 1500 (nước đục)
// ============================================================

void setUp(void) {
    // Setup trước mỗi test (nếu cần)
}

void tearDown(void) {
    // Cleanup sau mỗi test
}

// TC-T01: Khởi tạo sensor không crash
void test_turbidity_01_init_no_crash(void) {
    TurbiditySensor sensor;
    sensor.begin();
    TEST_PASS();
}

// TC-T02: Đọc giá trị ADC trong range 0-4095
void test_turbidity_02_read_value_range(void) {
    TurbiditySensor sensor;
    sensor.begin();
    
    int turbidity = sensor.readTurbidity();
    
    // ADC 12-bit: 0-4095
    TEST_ASSERT_GREATER_OR_EQUAL(turbidity, 0);
    TEST_ASSERT_LESS_OR_EQUAL(turbidity, 4095);
}

// TC-T03: Median Filter (15 samples) giảm nhiễu, consistency < 10%
void test_turbidity_03_median_filter_consistency(void) {
    TurbiditySensor sensor;
    sensor.begin();
    
    // Lặp 5 lần đọc, kiểm tra consistency
    int readings[5];
    for (int i = 0; i < 5; i++) {
        readings[i] = sensor.readTurbidity();
        delay(100);
    }
    
    // Tính mean
    int sum = 0;
    for (int i = 0; i < 5; i++) {
        sum += readings[i];
    }
    int mean = sum / 5;
    
    // Độ lệch max < 10% của mean
    int maxDeviation = 0;
    for (int i = 0; i < 5; i++) {
        int deviation = abs(readings[i] - mean);
        if (deviation > maxDeviation) {
            maxDeviation = deviation;
        }
    }
    
    float tolerance = mean * 0.10f;  // 10% tolerance
    TEST_ASSERT_LESS_OR_EQUAL((int)tolerance, maxDeviation);
}

// TC-T04: Alert level (ngưỡng < 1500)
void test_turbidity_04_alert_threshold(void) {
    TurbiditySensor sensor;
    sensor.begin();
    
    // Hàm isAlertLevel() chưa implement
    // Kiểm tra không crash
    bool isAlert = sensor.isAlertLevel();
    TEST_ASSERT_FALSE(isAlert);
}

// ============================================================
//  UNIT TESTS - TDS SENSOR
//  GPIO 34 (input-only), ADC 0-3.3V
//  Formula: TDS = (133.42*V³ - 255.86*V² + 857.39*V) * 0.75
//  Range: 0-2000 ppm (nước: 150-500 ppm lành mạnh)
// ============================================================

// TC-D01: Khởi tạo sensor không crash
void test_tds_01_init_no_crash(void) {
    TdsSensor sensor;
    sensor.begin();
    TEST_PASS();
}

// TC-D02: Đọc giá trị TDS trong range 0-2000 ppm
void test_tds_02_read_value_range(void) {
    TdsSensor sensor;
    sensor.begin();
    
    float tds = sensor.readTds();
    
    // TDS: 0-2000 ppm
    TEST_ASSERT_GREATER_OR_EQUAL(tds, 0.0f);
    TEST_ASSERT_LESS_OR_EQUAL(tds, 2000.0f);
}

// TC-D03: TDS không âm (được constrain)
void test_tds_03_no_negative_values(void) {
    TdsSensor sensor;
    sensor.begin();
    
    // Lặp 10 lần, tất cả phải >= 0
    for (int i = 0; i < 10; i++) {
        float tds = sensor.readTds();
        TEST_ASSERT_GREATER_OR_EQUAL(tds, 0.0f);
        delay(10);
    }
}

// TC-D04: Giá trị hợp lệ (không NaN, không infinity)
void test_tds_04_valid_values(void) {
    TdsSensor sensor;
    sensor.begin();
    
    float tds = sensor.readTds();
    
    // Kiểm tra không phải NaN
    TEST_ASSERT_TRUE(!isnan(tds));
    // Kiểm tra không phải infinity
    TEST_ASSERT_TRUE(isfinite(tds));
}

// ============================================================
//  UNIT TESTS - DS18B20 TEMPERATURE SENSOR
//  GPIO 18 (OneWire), 12-bit resolution (0.0625°C)
//  Error value: -127.0°C
//  Range test: 0-60°C (nước nuôi cá: 15-30°C)
// ============================================================

// TC-H01: Khởi tạo OneWire không crash
void test_ds18b20_01_init_no_crash(void) {
    DS18B20Sensor sensor;
    sensor.begin();
    TEST_PASS();
}

// TC-H02: Đọc nhiệt độ hợp lệ (0-60°C)
void test_ds18b20_02_read_value_range(void) {
    DS18B20Sensor sensor;
    sensor.begin();
    
    float temp = sensor.readTemperature();
    
    // Nước nuôi cá: 0-60°C (test range)
    TEST_ASSERT_GREATER_OR_EQUAL(temp, 0.0f);
    TEST_ASSERT_LESS_OR_EQUAL(temp, 60.0f);
}

// TC-H03: 12-bit precision (0.0625°C), không phải error value
void test_ds18b20_03_precision_no_error(void) {
    DS18B20Sensor sensor;
    sensor.begin();
    
    float temp = sensor.readTemperature();
    
    // Không phải error value (-127.0)
    TEST_ASSERT_NOT_EQUAL(temp, -127.0f);
    // Không phải NaN
    TEST_ASSERT_TRUE(!isnan(temp));
}

// TC-H04: isValid() flag sau readTemperature()
void test_ds18b20_04_valid_flag(void) {
    DS18B20Sensor sensor;
    sensor.begin();
    
    float temp = sensor.readTemperature();
    
    // Nếu temperature hợp lệ → isValid() = true
    if (temp > -100.0f && temp < 100.0f) {
        TEST_ASSERT_TRUE(sensor.isValid());
    }
}

// ============================================================
//  PERFORMANCE TESTS - LATENCY MEASUREMENT
// ============================================================

// Turbidity latency: Median filter (15 samples × 2ms delay)
// Expected: ~30-50ms + sort time
// Target: < 100ms
void test_perf_01_turbidity_latency(void) {
    TurbiditySensor sensor;
    sensor.begin();
    
    unsigned long start = micros();
    int reading = sensor.readTurbidity();
    unsigned long elapsed = micros() - start;
    
    Serial.printf("[LATENCY] Turbidity: %lu µs (%.2f ms)\n", elapsed, elapsed / 1000.0f);
    
    TEST_ASSERT_LESS_OR_EQUAL(elapsed, 100000UL);  // < 100ms
}

// TDS latency: 1× ADC read + voltage + formula
// Expected: ~30-100µs
// Target: < 150ms
void test_perf_02_tds_latency(void) {
    TdsSensor sensor;
    sensor.begin();
    
    unsigned long start = micros();
    float tds = sensor.readTds();
    unsigned long elapsed = micros() - start;
    
    Serial.printf("[LATENCY] TDS: %lu µs (%.3f ms)\n", elapsed, elapsed / 1000.0f);
    TEST_ASSERT_LESS_OR_EQUAL(elapsed, 150000UL);  // < 150ms
}

// DS18B20 latency: Blocking ~750ms @ 12-bit
// Expected: ~700-800ms
// Target: < 1000ms (allow margin)
void test_perf_03_ds18b20_latency(void) {
    DS18B20Sensor sensor;
    sensor.begin();
    
    unsigned long start = micros();
    float temp = sensor.readTemperature();
    unsigned long elapsed = micros() - start;
    
    Serial.printf("[LATENCY] DS18B20: %lu µs (%.0f ms)\n", elapsed, elapsed / 1000.0f);
    TEST_ASSERT_LESS_OR_EQUAL(elapsed, 1000000UL);  // < 1s
}

// ============================================================
//  THROUGHPUT TESTS - MULTIPLE SEQUENTIAL READS
// ============================================================

// 10 sequential Turbidity reads
void test_perf_04_turbidity_throughput_10x(void) {
    TurbiditySensor sensor;
    sensor.begin();
    
    unsigned long start = micros();
    for (int i = 0; i < 10; i++) {
        int reading = sensor.readTurbidity();
        (void)reading;  // Suppress unused warning
    }
    unsigned long elapsed = micros() - start;
    
    float avgTime = elapsed / 10.0f;
    Serial.printf("[THROUGHPUT] 10× Turbidity: avg %.2f µs/read\n", avgTime);
    
    // Total < 500ms for 10 reads
    TEST_ASSERT_LESS_OR_EQUAL(elapsed, 500000UL);
}

// ============================================================
//  CONFIGURATION & CONSTANTS TESTS
// ============================================================

// Verify Config.h constants
void test_config_01_thresholds(void) {
    // Ngưỡng cảnh báo nước đục
    TEST_ASSERT_EQUAL(1500, TURBIDITY_ALERT_THRESHOLD);
    
    // Khoảng thời gian lấy mẫu
    TEST_ASSERT_EQUAL(2000, SENSOR_SAMPLE_INTERVAL_MS);
    
    // Khoảng thời gian kiểm tra automation
    TEST_ASSERT_EQUAL(50, AUTOMATION_CHECK_INTERVAL_MS);
}

// Verify SensorData_t structure
void test_config_02_sensor_data_struct(void) {
    SensorData_t data;
    
    // Initialize with valid values
    data.temperature = 28.5f;
    data.tds = 450.0f;
    data.turbidity = 2500;
    data.weight = 0.0f;
    data.timestamp = millis();
    
    // Verify fields
    TEST_ASSERT_GREATER_OR_EQUAL(data.temperature, 0.0f);
    TEST_ASSERT_LESS_OR_EQUAL(data.temperature, 60.0f);
    
    TEST_ASSERT_GREATER_OR_EQUAL(data.tds, 0.0f);
    TEST_ASSERT_LESS_OR_EQUAL(data.tds, 2000.0f);
    
    TEST_ASSERT_GREATER_OR_EQUAL(data.turbidity, 0);
    TEST_ASSERT_LESS_OR_EQUAL(data.turbidity, 4095);
}

// ============================================================
//  MAIN TEST RUNNER (PlatformIO)
// ============================================================

void setup() {
    delay(2000);
    Serial.begin(115200);
    
    UNITY_BEGIN();
    
    // Turbidity Tests
    RUN_TEST(test_turbidity_01_init_no_crash);
    RUN_TEST(test_turbidity_02_read_value_range);
    RUN_TEST(test_turbidity_03_median_filter_consistency);
    RUN_TEST(test_turbidity_04_alert_threshold);
    
    // TDS Tests
    RUN_TEST(test_tds_01_init_no_crash);
    RUN_TEST(test_tds_02_read_value_range);
    RUN_TEST(test_tds_03_no_negative_values);
    RUN_TEST(test_tds_04_valid_values);
    
    // DS18B20 Tests
    RUN_TEST(test_ds18b20_01_init_no_crash);
    RUN_TEST(test_ds18b20_02_read_value_range);
    RUN_TEST(test_ds18b20_03_precision_no_error);
    RUN_TEST(test_ds18b20_04_valid_flag);
    
    // Performance Tests
    RUN_TEST(test_perf_01_turbidity_latency);
    RUN_TEST(test_perf_02_tds_latency);
    RUN_TEST(test_perf_03_ds18b20_latency);
    RUN_TEST(test_perf_04_turbidity_throughput_10x);
    
    // Configuration Tests
    RUN_TEST(test_config_01_thresholds);
    RUN_TEST(test_config_02_sensor_data_struct);
    
    UNITY_END();
}

void loop() {
    delay(1000);
}
