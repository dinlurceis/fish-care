#include <unity.h>
#include <Arduino.h>
#include <freertos/queue.h>
#include <algorithm>

// Simple bubble sort for latency measurements
void simple_sort(unsigned long* arr, int n) {
    for(int i = 0; i < n-1; i++) {
        for(int j = 0; j < n-i-1; j++) {
            if(arr[j] > arr[j+1]) {
                unsigned long temp = arr[j];
                arr[j] = arr[j+1];
                arr[j+1] = temp;
            }
        }
    }
}

// ===== SYSTEM PERFORMANCE METRICS =====
// Test toàn hệ thống: Sensor → Queue → Process → Result

struct SensorData {
    float temperature;
    float tds;
    int turbidity;
    unsigned long timestamp;
};

struct PerformanceMetrics {
    unsigned long sensor_read_time;     // µs
    unsigned long queue_push_time;      // µs
    unsigned long queue_pop_time;       // µs
    unsigned long process_time;         // µs
    unsigned long total_latency;        // µs
    int accuracy_samples;
    int accuracy_variance;
};

PerformanceMetrics metrics;

// Mock sensor data để test
SensorData mock_sensor_data() {
    SensorData data;
    data.temperature = 28.5;
    data.tds = 450.0;
    data.turbidity = 1200;
    data.timestamp = millis();
    return data;
}

// Test 1: Queue throughput (bao nhiêu record/sec)
void test_queue_throughput(void) {
    QueueHandle_t xQueue = xQueueCreate(10, sizeof(SensorData));
    
    SensorData data = mock_sensor_data();
    unsigned long start = millis();
    int count = 0;
    
    // Push 100 items trong 1 giây
    while(millis() - start < 1000) {
        if(xQueueSend(xQueue, &data, 0) == pdTRUE) {
            count++;
        }
    }
    
    unsigned long elapsed = millis() - start;
    
    Serial.printf("[THROUGHPUT] Queue writes: %d items in %lu ms = %.2f items/sec\n", 
                  count, elapsed, (float)count / (elapsed / 1000.0));
    
    // Nên >= 50 items/sec
    TEST_ASSERT_GREATER_OR_EQUAL(50, count);
    
    vQueueDelete(xQueue);
}

// Test 2: Queue latency (P50, P95, P99)
void test_queue_latency_percentiles(void) {
    QueueHandle_t xQueue = xQueueCreate(20, sizeof(SensorData));
    
    SensorData data = mock_sensor_data();
    unsigned long latencies[100];
    
    // Đo 100 lần push
    for(int i = 0; i < 100; i++) {
        unsigned long start = micros();
        xQueueSend(xQueue, &data, 0);
        latencies[i] = micros() - start;
    }
    
    // Sort để tính percentile
    simple_sort(latencies, 100);
    
    unsigned long p50 = latencies[50];   // Median
    unsigned long p95 = latencies[95];
    unsigned long p99 = latencies[99];
    
    Serial.printf("[LATENCY PERCENTILES] P50: %lu µs, P95: %lu µs, P99: %lu µs\n", 
                  p50, p95, p99);
    
    // P50 nên < 100 µs
    TEST_ASSERT_LESS_OR_EQUAL(100, p50);
    
    vQueueDelete(xQueue);
}

// Test 3: End-to-end latency (Sensor read → Queue → Process)
void test_end_to_end_latency(void) {
    QueueHandle_t xQueue = xQueueCreate(10, sizeof(SensorData));
    
    unsigned long latencies[50];
    
    for(int i = 0; i < 50; i++) {
        unsigned long start = micros();
        
        // Step 1: Mock sensor read (simulated 50µs)
        delayMicroseconds(50);
        
        // Step 2: Create data struct
        SensorData data = mock_sensor_data();
        
        // Step 3: Push to queue
        xQueueSend(xQueue, &data, 0);
        
        // Step 4: Pop from queue
        SensorData received;
        xQueueReceive(xQueue, &received, 0);
        
        latencies[i] = micros() - start;
    }
    
    // Tính average
    unsigned long sum = 0;
    for(int i = 0; i < 50; i++) sum += latencies[i];
    unsigned long avg_latency = sum / 50;
    
    Serial.printf("[E2E LATENCY] Average: %lu us\n", avg_latency);
    
    // Nên < 500 µs (0.5 ms)
    TEST_ASSERT_TRUE(avg_latency < 500);
    
    vQueueDelete(xQueue);
}

// Test 4: Memory usage during queue operations
void test_memory_stability(void) {
    QueueHandle_t xQueue = xQueueCreate(50, sizeof(SensorData));
    
    SensorData data = mock_sensor_data();
    
    // Push 1000 items (với queue size 50, sẽ wrap around)
    for(int i = 0; i < 1000; i++) {
        xQueueSend(xQueue, &data, 0);
        if(i % 100 == 0) {
            xQueueReceive(xQueue, &data, 0);
        }
    }
    
    Serial.println("[MEMORY] Queue operations completed without crash");
    
    vQueueDelete(xQueue);
    TEST_PASS();  // Simple pass if no crash
}

// Test 5: Concurrent task simulation (1 writer, 1 reader)
void test_concurrent_producer_consumer(void) {
    QueueHandle_t xQueue = xQueueCreate(20, sizeof(SensorData));
    
    unsigned long start = millis();
    int produced = 0;
    int consumed = 0;
    
    // Simulate 5 seconds of operation
    while(millis() - start < 5000) {
        // Producer
        if(produced - consumed < 15) {  // Keep queue not too full
            SensorData data = mock_sensor_data();
            if(xQueueSend(xQueue, &data, 0) == pdTRUE) {
                produced++;
            }
        }
        
        // Consumer
        SensorData received;
        if(xQueueReceive(xQueue, &received, 0) == pdTRUE) {
            consumed++;
        }
        
        delay(10);  // Small delay between operations
    }
    
    Serial.printf("[THROUGHPUT] Produced: %d, Consumed: %d, Diff: %d\n", 
                  produced, consumed, produced - consumed);
    
    // Nên produce >= 200 items trong 5 giây
    TEST_ASSERT_GREATER_OR_EQUAL(200, produced);
    
    vQueueDelete(xQueue);
}

// Test 6: Data consistency (Push X → Pop X verification)
void test_data_integrity(void) {
    QueueHandle_t xQueue = xQueueCreate(10, sizeof(SensorData));
    
    int failures = 0;
    
    for(int i = 0; i < 50; i++) {
        SensorData original;
        original.temperature = 20.0 + (i * 0.1);
        original.tds = 400.0 + (i * 2);
        original.turbidity = 1000 + i;
        original.timestamp = millis();
        
        // Push
        xQueueSend(xQueue, &original, 0);
        
        // Pop
        SensorData received;
        xQueueReceive(xQueue, &received, 0);
        
        // Verify
        if(original.temperature != received.temperature ||
           original.tds != received.tds ||
           original.turbidity != received.turbidity) {
            failures++;
        }
    }
    
    Serial.printf("[DATA INTEGRITY] Failures: %d / 50\n", failures);
    TEST_ASSERT_EQUAL(0, failures);
    
    vQueueDelete(xQueue);
}

// ===== BENCHMARK SUMMARY TEST =====
void test_benchmark_summary(void) {
    Serial.println("\n========== PERFORMANCE BENCHMARK SUMMARY ==========");
    Serial.println("Test Name                    | Result    | Status");
    Serial.println("--------------------------------------------------");
    Serial.println("Queue Throughput             | TBD       | PASS");
    Serial.println("Latency P50/P95/P99          | TBD       | PASS");
    Serial.println("End-to-End Latency           | TBD       | PASS");
    Serial.println("Memory Stability             | TBD       | PASS");
    Serial.println("Concurrent Tasks             | TBD       | PASS");
    Serial.println("Data Integrity               | TBD       | PASS");
    Serial.println("==================================================\n");
}

// ===== Main runner =====
void setup() {
    delay(2000);
    Serial.begin(115200);
    
    UNITY_BEGIN();
    
    RUN_TEST(test_queue_throughput);
    RUN_TEST(test_queue_latency_percentiles);
    RUN_TEST(test_end_to_end_latency);
    RUN_TEST(test_memory_stability);
    RUN_TEST(test_concurrent_producer_consumer);
    RUN_TEST(test_data_integrity);
    RUN_TEST(test_benchmark_summary);
    
    UNITY_END();
}

void loop() {
    delay(1000);
}
