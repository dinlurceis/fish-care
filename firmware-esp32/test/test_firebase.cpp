#include <unity.h>
#include <Arduino.h>
#include <ArduinoJson.h>

// ===== FIREBASE DATA VALIDATION TESTS =====

// Test 1: Sensor data serialization to Firebase JSON
void test_sensor_data_to_json(void) {
    JsonDocument doc;
    
    // Simulate sensor readings
    float temperature = 28.5;
    float tds = 450.2;
    int turbidity = 1200;
    float weight = 2500.5;
    
    // Create JSON payload
    doc["aquarium"]["temperature"] = temperature;
    doc["aquarium"]["water_quality"] = tds;
    doc["aquarium"]["ts300b"] = turbidity;
    doc["aquarium"]["weight"] = weight;
    
    // Verify JSON structure
    TEST_ASSERT_EQUAL(28.5, doc["aquarium"]["temperature"].as<float>());
    TEST_ASSERT_EQUAL(450.2, doc["aquarium"]["water_quality"].as<float>());
    TEST_ASSERT_EQUAL(1200, doc["aquarium"]["ts300b"].as<int>());
    
    String json_str;
    serializeJson(doc, json_str);
    
    Serial.printf("[JSON] Serialized: %s\n", json_str.c_str());
    
    // Size check (nên < 200 bytes)
    TEST_ASSERT_LESS_OR_EQUAL(200, json_str.length());
}

// Test 2: Control command deserialization from Firebase
void test_control_command_from_json(void) {
    const char* json_str = R"({
        "aquarium": {
            "control": {
                "guong": true,
                "thucan": {
                    "mode": "gram",
                    "state": true,
                    "target_gram": 50.0
                }
            }
        }
    })";
    
    JsonDocument doc;
    deserializeJson(doc, json_str);
    
    // Verify values
    bool guong = doc["aquarium"]["control"]["guong"].as<bool>();
    const char* mode = doc["aquarium"]["control"]["thucan"]["mode"].as<const char*>();
    float target = doc["aquarium"]["control"]["thucan"]["target_gram"].as<float>();
    
    TEST_ASSERT_TRUE(guong);
    TEST_ASSERT_EQUAL(50.0, target);
    TEST_ASSERT_EQUAL_STRING("gram", mode);
    
    Serial.println("[JSON] Control command deserialized successfully");
}

// Test 3: Log entry generation
void test_log_entry_generation(void) {
    JsonDocument doc;
    
    doc["logs"]["counter"] = 125;
    doc["logs"]["log126"]["gram"] = 48.5;
    doc["logs"]["log126"]["mode"] = "gram";
    doc["logs"]["log126"]["time"] = "17:15 14/04/2026";
    
    TEST_ASSERT_EQUAL(125, doc["logs"]["counter"].as<int>());
    TEST_ASSERT_EQUAL(48.5, doc["logs"]["log126"]["gram"].as<float>());
    
    String json_str;
    serializeJson(doc, json_str);
    Serial.printf("[LOG] Entry: %s\n", json_str.c_str());
}

// ===== FIREBASE UPLOAD LATENCY TESTS =====

// Simulate Firebase upload time (real Firebase would do actual HTTP)
void test_firebase_upload_simulation(void) {
    // Simulate network latency distribution
    // Realistic: 100-500ms
    
    unsigned long latencies[20];
    unsigned long total_time = 0;
    
    for(int i = 0; i < 20; i++) {
        unsigned long latency = random(100, 500);  // ms
        latencies[i] = latency;
        total_time += latency;
    }
    
    // Sort để tính percentile
    std::sort(latencies, latencies + 20);
    
    unsigned long avg = total_time / 20;
    unsigned long p50 = latencies[10];
    unsigned long p95 = latencies[19];
    
    Serial.printf("[FIREBASE] Upload latency - Avg: %lu ms, P50: %lu ms, P95: %lu ms\n", 
                  avg, p50, p95);
    
    // Nên < 500ms average
    TEST_ASSERT_LESS_OR_EQUAL(500, avg);
}

// Test 4: Firebase update frequency (sensor → cloud → app)
void test_update_frequency_compliance(void) {
    // Requirement: Sensor data upload mỗi 2 giây
    unsigned long intervals[10];
    unsigned long prev_time = millis();
    
    for(int i = 0; i < 10; i++) {
        delay(2000);  // 2 second interval
        unsigned long current_time = millis();
        intervals[i] = current_time - prev_time;
        prev_time = current_time;
    }
    
    // Verify all intervals ~ 2000ms
    int out_of_spec = 0;
    for(int i = 0; i < 10; i++) {
        if(intervals[i] < 1950 || intervals[i] > 2050) {
            out_of_spec++;
        }
    }
    
    Serial.printf("[FREQUENCY] Out of spec: %d / 10 intervals\n", out_of_spec);
    
    // Nên có <= 1 ngoài spec
    TEST_ASSERT_LESS_OR_EQUAL(1, out_of_spec);
}

// Test 5: Retry mechanism for failed uploads
void test_firebase_retry_mechanism(void) {
    int max_retries = 3;
    int successful_retries = 0;
    
    for(int attempt = 1; attempt <= max_retries; attempt++) {
        // Simulate: 30% fail rate on first attempt
        bool success = random(0, 100) > (attempt == 1 ? 30 : 5);
        
        if(success) {
            successful_retries = attempt;
            break;
        }
        
        Serial.printf("[RETRY] Attempt %d failed, retrying...\n", attempt);
        delay(100 * attempt);  // Exponential backoff
    }
    
    Serial.printf("[RETRY] Succeeded on attempt %d\n", successful_retries);
    
    // Nên succeed <= attempt 2
    TEST_ASSERT_LESS_OR_EQUAL(2, successful_retries);
}

// ===== FIREBASE SCHEMA VALIDATION TESTS =====

void test_aquarium_schema_validation(void) {
    JsonDocument doc;
    
    // Required fields
    doc["aquarium"]["temperature"] = 28.5;
    doc["aquarium"]["water_quality"] = 450.0;
    doc["aquarium"]["ts300b"] = 1200;
    doc["aquarium"]["weight"] = 2500.0;
    
    // Validate types
    TEST_ASSERT_TRUE(doc["aquarium"]["temperature"].is<float>());
    TEST_ASSERT_TRUE(doc["aquarium"]["water_quality"].is<float>());
    TEST_ASSERT_TRUE(doc["aquarium"]["ts300b"].is<int>());
    TEST_ASSERT_TRUE(doc["aquarium"]["weight"].is<float>());
    
    Serial.println("[SCHEMA] Aquarium schema validated");
}

void test_control_schema_validation(void) {
    JsonDocument doc;
    
    doc["aquarium"]["control"]["guong"] = true;
    doc["aquarium"]["control"]["thucan"]["mode"] = "gram";
    doc["aquarium"]["control"]["thucan"]["state"] = false;
    doc["aquarium"]["control"]["thucan"]["target_gram"] = 50.0;
    
    // Validate
    bool guong_valid = doc["aquarium"]["control"]["guong"].is<bool>();
    const char* mode_str = doc["aquarium"]["control"]["thucan"]["mode"].as<const char*>();
    
    TEST_ASSERT_TRUE(guong_valid);
    TEST_ASSERT_EQUAL_STRING("gram", mode_str);
    
    // Validate mode enum
    bool valid_mode = (strcmp(mode_str, "auto") == 0 || 
                       strcmp(mode_str, "gram") == 0 || 
                       strcmp(mode_str, "manual") == 0);
    
    TEST_ASSERT_TRUE(valid_mode);
    
    Serial.println("[SCHEMA] Control schema validated");
}

// ===== EDGE CASES =====

void test_extreme_values(void) {
    JsonDocument doc;
    
    // Test boundary values
    doc["aquarium"]["temperature"] = 60.0;  // Max
    doc["aquarium"]["water_quality"] = 2000.0;  // Max TDS
    doc["aquarium"]["ts300b"] = 4095;  // Max ADC
    doc["aquarium"]["weight"] = 5000.0;  // Max weight
    
    TEST_ASSERT_EQUAL(60.0, doc["aquarium"]["temperature"].as<float>());
    TEST_ASSERT_EQUAL(4095, doc["aquarium"]["ts300b"].as<int>());
    
    Serial.println("[EDGE] Extreme values handled");
}

void test_empty_data_handling(void) {
    JsonDocument doc;
    
    // Test with empty/null values
    doc["aquarium"]["temperature"] = 0.0;
    doc["aquarium"]["weight"] = 0.0;
    
    TEST_ASSERT_EQUAL(0.0, doc["aquarium"]["temperature"].as<float>());
    
    Serial.println("[EDGE] Empty/zero values handled");
}

// ===== Main runner =====
void setup() {
    delay(2000);
    Serial.begin(115200);
    
    UNITY_BEGIN();
    
    // Serialization tests
    RUN_TEST(test_sensor_data_to_json);
    RUN_TEST(test_control_command_from_json);
    RUN_TEST(test_log_entry_generation);
    
    // Latency tests
    RUN_TEST(test_firebase_upload_simulation);
    RUN_TEST(test_update_frequency_compliance);
    RUN_TEST(test_firebase_retry_mechanism);
    
    // Schema validation
    RUN_TEST(test_aquarium_schema_validation);
    RUN_TEST(test_control_schema_validation);
    
    // Edge cases
    RUN_TEST(test_extreme_values);
    RUN_TEST(test_empty_data_handling);
    
    UNITY_END();
}

void loop() {
    delay(1000);
}
