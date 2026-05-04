# 📋 FISH-CARE ESP32 FIRMWARE - CODE ANALYSIS

## 📌 TÓM TẮT HỆ THỐNG

Đây là **firmware IoT cho hệ thống chăm sóc cá tự động** (Fish-Care) chạy trên ESP32 với kiến trúc **FreeRTOS Multi-Task**:
- ✅ **4 FreeRTOS Task** chạy song song trên Core 1
- ✅ **Firebase Realtime Database** để điều khiển từ app
- ✅ **Edge Computing**: Tự động bật oxy khi rớt mạng + môi trường xấu
- ✅ **3 cảm biến**: Nhiệt độ (DS18B20), TDS, Độ đục
- ✅ **2 động cơ**: Motor oxy (A) + Motor cho ăn (B) với LoadCell

---

## 🏗️ KIẾN TRÚC HỆ THỐNG

### **1. Layer 0: IPC (Inter-Process Communication)**

**Các Queue & Mutex toàn cục (main.cpp):**

```cpp
QueueHandle_t xQueue_SensorData     // Size=1, Sensor data mới nhất
QueueHandle_t xQueue_FeedCommands   // Size=5, Lệnh cho ăn
QueueHandle_t xQueue_AutoCommands   // Size=5, Lệnh oxy
SemaphoreHandle_t xMutex_Firebase   // Bảo vệ Firebase write (tránh race condition)
volatile bool isWiFiConnected       // Flag WiFi status cho edge logic
```

**Lý do thiết kế:**
- **Size=1 với SensorData**: Luôn giữ data cảm biến mới nhất (overwrite queue)
- **Size=5 với Command**: Cho phép buffer lệnh nếu task bận
- **Mutex Firebase**: Vì NetworkTask & FeedingTask đều có thể write lên Firebase

---

### **2. Layer 1: FreeRTOS Tasks**

| **Task** | **Core** | **Priority** | **Stack** | **Interval** | **Chức năng** |
|----------|----------|-------------|----------|------------|--------------|
| **SensorTask** | 1 | 1 (LOWEST) | 4KB | 2 giây | Đọc 3 cảm biến → Queue |
| **AutomationTask** | 1 | 2 (MEDIUM) | 4KB | 100ms | Điều khiển Motor A (Oxy) + Edge Logic |
| **NetworkTask** | 1 | 3 (HIGH) | 8KB | - | WiFi, Firebase Stream, Lệnh từ app |
| **FeedingTask** | 1 | 4 (HIGHEST) | 4KB | - | Điều khiển Motor B + LoadCell + Auto/Manual feeding |

**Priority Giải thích:**
1. **FeedingTask Priority 4** (Cao nhất): Cần phản ứng nhanh với LoadCell interrupt
2. **NetworkTask Priority 3**: Phải giữ kết nối Firebase liên tục
3. **AutomationTask Priority 2**: Check offline logic + oxy automation
4. **SensorTask Priority 1**: Đọc cảm biến định kỳ, không time-critical

---

### **3. Layer 2: Cảm Biến (Sensors)**

#### **A. DS18B20 (Cảm biến nhiệt độ)**
- **GPIO**: 18 (OneWire)
- **Đặc tính**: Blocking read ~750ms ở 12-bit
- **Lọc nhiễu**: Không cần (thay đổi chậm)

#### **B. TDS Sensor (Độ mặn nước)**
- **GPIO**: 34 (ADC1_CH6, input-only)
- **Công thức**: `TDS = (133.42*V³ - 255.86*V² + 857.39*V) * 0.75`
- **Lọc nhiễu**: Trung bình 10 mẫu (mỗi 10ms) → 100ms tổng

#### **C. Turbidity Sensor TS-300B (Độ đục)**
- **GPIO**: 32 (ADC)
- **Giá trị**: 0-4095 (cao=trong, thấp=đục - tỷ lệ nghịch)
- **Lọc nhiễu**: Median filter 15 mẫu

#### **D. LoadCell HX711 (Cân nặng cám)**
- **GPIO**: DOUT=21, SCK=22 (SPI)
- **Cập nhật bởi**: FeedingTask
- **Scale Factor**: 505.4633

---

## 📊 LUỒNG DỮ LIỆU (Data Flow)

```
┌─────────────────────────────────────────────────────────────┐
│                    SENSOR LAYER                             │
│  DS18B20(18) → TDS(34) → Turbidity(32) → LoadCell(21,22)  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
         ┌─────────────────────────┐
         │   SensorTask (Pr 1)     │  ← Đọc 10-15 mẫu, lọc nhiễu
         │                         │  ← Ghi vào Queue_SensorData
         │  Interval: 2 giây       │
         └────────┬────────────────┘
                  │
                  ▼
         ┌─────────────────────────────────────────┐
         │    Queue_SensorData (Size=1)            │
         │  {temp, tds, turbidity, weight, ts}     │
         └────┬──────┬──────┬──────────────────────┘
              │      │      │
    ┌─────────┴─┐    │    ┌─┴──────────┐
    │            │    │    │            │
    ▼            ▼    ▼    ▼            ▼
┌─────────┐ ┌────────────┐ ┌──────────────┐ ┌──────────┐
│Network  │ │Automation  │ │Feeding       │ │System    │
│Task(P3) │ │Task(P2)    │ │Task(P4)      │ │Health    │
│         │ │            │ │              │ │Monitor   │
│ Firebase│ │ Edge Logic │ │ LoadCell     │ │          │
│ Stream  │ │ Oxy Auto   │ │ Auto/Manual  │ │          │
└────┬────┘ └────────────┘ │ Feeding      │ └──────────┘
     │                      │              │
     │ (Queue_Auto/Feed)    │              │
     └──────────┬───────────┴──────────────┘
                │
        ┌───────┴──────────┐
        │                  │
        ▼                  ▼
    ┌─────────┐      ┌──────────┐
    │Motor A  │      │Motor B   │
    │(Oxy)    │      │(Feeding) │
    │GPIO:5,  │      │GPIO:23,  │
    │26,27    │      │14,12     │
    │         │      │          │
    └─────────┘      └──────────┘
```

### **Chi Tiết Luồng Dữ Liệu:**

#### **1. Dữ liệu cảm biến → Firebase (Online)**
```
SensorTask (every 2s)
  ├─ Đọc DS18B20, TDS, Turbidity
  ├─ Lọc nhiễu (smooth)
  └─ xQueueOverwrite(Queue_SensorData) ✓

NetworkTask (every ~30s)
  ├─ xQueuePeek(Queue_SensorData) - Lấy data mới nhất
  ├─ xSemaphoreTake(xMutex_Firebase) - Lock
  ├─ Firebase.updateNode("/aquarium", data) → Cloud
  └─ xSemaphoreGive(xMutex_Firebase) ✓
```

#### **2. Lệnh từ Firebase → Motor (Online)**
```
Firebase Realtime DB
  └─ StreamCallback firebaseCallback()
       ├─ Phát hiện "guong" (oxy)
       │  └─ xQueueSend(xQueue_AutoCommands, cmd) ✓
       │
       ├─ Phát hiện "mode" (feeding mode)
       │  └─ xQueueSend(xQueue_FeedCommands, cmd) ✓
       │
       ├─ Phát hiện "target_gram"
       │  └─ xQueueSend(xQueue_FeedCommands, cmd) ✓
       │
       └─ Phát hiện "state" (manual on/off)
          └─ xQueueSend(xQueue_FeedCommands, cmd) ✓

AutomationTask / FeedingTask
  ├─ xQueueReceive(Queue_*Commands, &cmd, 0)
  ├─ Xử lý lệnh → Điều khiển GPIO
  └─ Ghi log kết quả
```

#### **3. Edge Logic - Khi rớt mạng (Offline)**
```
AutomationTask (every 100ms)
  ├─ if (!NetworkTask_IsWiFiConnected()) {
  │    ├─ xQueuePeek(Queue_SensorData, &data)
  │    ├─ Check: Temp > 32°C? TDS > 1000 ppm? Turbidity < 1500?
  │    ├─ if (any condition true && !s_EdgeOverrideActive) {
  │    │    ├─ startOxy() → GPIO HIGH
  │    │    ├─ s_EdgeOverrideActive = true
  │    │    └─ Timer 5 phút auto-off (chống cháy motor)
  │    │
  │    └─ if (timeout 5 phút) {
  │         ├─ stopOxy() → GPIO LOW
  │         └─ s_EdgeOverrideActive = false
  │    }
  │
  └─ if (WiFi khôi phục) {
       ├─ stopOxy() → Sync trạng thái với Firebase
       └─ Chờ lệnh mới từ Firebase
     }
```

---

## 📂 CÁC FILE & CHỨC NĂNG

### **1. main.cpp** - Entry Point & System Init
```
✓ Khởi tạo Queue & Mutex
✓ Cấu hình Watchdog (20s timeout)
✓ Tạo 4 FreeRTOS Tasks với priority khác nhau
✓ loop() chỉ gọi vTaskDelay() - tất cả logic ở tasks

📌 Quan trọng:
  - xQueue_SensorData size=1 (overwrite mode)
  - Tất cả tasks chạy trên Core 1 (dễ quản lý)
  - loop() priority 0 (Idle)
```

### **2. SensorTask.cpp/h** - Đọc cảm biến
```
✓ Priority 1 (LOWEST)
✓ Interval: 2 giây
✓ Đọc 3 loại cảm biến + LoadCell

🔍 Chi tiết:
  - DS18B20: readTemperature() → blocking ~750ms
  - TDS: readSmoothTDS() → 10 mẫu x 10ms = 100ms
  - Turbidity: readSmoothTurbidity() → Median 15 mẫu
  - LoadCell: Peek từ Queue (FeedingTask update)
  - Ghi vào Queue_SensorData (overwrite)
  - Debug print mỗi vòng (heavy logging)
```

### **3. NetworkTask.cpp/h** - WiFi & Firebase
```
✓ Priority 3 (HIGH)
✓ Stack: 8KB (lớn nhất)

🔍 Chi tiết:
  1. WiFiManager: Tự động kết nối WiFi
     - AP portal: FishCare_AP / 12345678
     - Timeout: 3 phút → offline mode
  
  2. Firebase Stream (Event-driven):
     - Lắng nghe /aquarium/control
     - Callback: firebaseCallback() → phân loại lệnh
     - Gửi vào xQueue_AutoCommands hoặc xQueue_FeedCommands
  
  3. Sync cảm biến: mỗi ~30 giây
     - Lock xMutex_Firebase
     - updateNode("/aquarium", {temp, tds, weight})
     - Chart log mỗi 60s
  
  4. Push Alert: Nếu ngưỡng vượt
     - pushAlertToFirebase() → /notifications

📌 Firebase Schema:
  /aquarium/
    ├── temperature: 26.5
    ├── water_quality: 650 (TDS)
    ├── ts300b: 3200 (turbidity)
    ├── weight: 45.2 (gram từ LoadCell)
    ├── control/
    │   ├── guong: true (oxy on/off)
    │   └── thucan/
    │       ├── mode: "auto" | "gram" | "manual"
    │       ├── target_gram: 50
    │       └── state: true (manual on/off)
```

### **4. FeedingTask.cpp/h** - Điều khiển cho ăn
```
✓ Priority 4 (HIGHEST)
✓ 3 chế độ cho ăn:
  - AUTO: Lúc 6:00 & 17:00 (1 lần/khung giờ)
  - GRAM: Cho ăn X grams (dùng LoadCell để đếm)
  - MANUAL: Bật/tắt manual từ app

🔍 Chi tiết:
  
  Mode AUTO:
    - Check giờ lúc 6:00 & 17:00
    - Bật motor → ghi lịch sử lên Firebase
    - Timeout: 20 giây hoặc user cancel
    
  Mode GRAM:
    - Record s_GramStartWeight khi bắt đầu
    - Tính: dispensed = startWeight - currentWeight
    - Ghi log khi motor tắt
    
  Mode MANUAL:
    - User bật/tắt từ app (ON/OFF button)
    - Ghi log gram đã nhả
    
  LoadCell:
    - HX711 SPI: DOUT(21), SCK(22)
    - Scale: 505.4633
    - Averaging: Giữ giá trị cũ nếu not ready
    
  Motor Control:
    - GPIO: EN(23), IN1(14), IN2(12)
    - ON: digitalWrite(EN, HIGH), IN1=HIGH, IN2=LOW
    - OFF: Tất cả LOW

⚠️ Safety:
  - s_MotorStartTime reset khi tắt (track lần sau)
  - Timeout 30s bảo vệ motor (nếu Firebase disconnect)
  - Chỉ ghi log nếu thực sự nhả được cám (dispensed > 0)
```

### **5. AutomationTask.cpp/h** - Điều khiển oxy + Edge Logic
```
✓ Priority 2 (MEDIUM)
✓ 2 chế độ:
  - FIREBASE MODE: Nhận lệnh từ app
  - EDGE MODE: Tự động offline khi điều kiện xấu

🔍 Chi tiết:

  Firebase Mode (Online):
    - xQueueReceive(xQueue_AutoCommands, &cmd, 0)
    - CMD_GUONG_ON → startOxy()
    - CMD_GUONG_OFF → stopOxy()
    
  Edge Logic Mode (Offline):
    - Kiểm tra 3 ngưỡng:
      * temperature > 32°C (nước quá nóng)
      * tds > 1000 ppm (nước quá bẩn)
      * turbidity < 1500 (nước quá đục)
    
    - Nếu bất kỳ điều kiện nào đúng:
      * startOxy() → Hysteresis 5 phút
      * Log: "⚠️ EDGE LOGIC: Mất mạng + Môi trường xấu"
    
    - Timeout 5 phút → stopOxy()
      * Chống cháy motor
    
    - WiFi khôi phục → stopOxy() + sync trạng thái
      * Tránh state bug (motor kẹt trạng thái)

  Motor A Control:
    - GPIO: EN(5), IN1(26), IN2(27)
    - ON: EN=HIGH, IN1=HIGH, IN2=LOW
    - OFF: Tất cả LOW

  Debug:
    - In trạng thái mỗi 5 giây: WiFi, Oxy, EdgeOverride
```

### **6. SystemHealth.cpp/h** - Giám sát hệ thống
```
✓ Chưa được gọi từ main.cpp (⚠️ DEAD CODE - xem phần Unused)

✓ Chức năng:
  - Watchdog: esp_task_wdt_init()
  - Memory: esp_get_free_heap_size()
  - SystemHealth_GetStatus() → struct
  - Print status → Serial
  - Soft reset → ESP.restart()
```

### **7. TaskDelay.cpp/h** - Hỗ trợ delay an toàn
```
✓ Task_Delay() → vTaskDelay() wrapper
✓ Task_ValidateDelay() → Check WDT timeout
✓ Task_FeedWatchdog() → esp_task_wdt_reset()
✓ Task_GetTickCount() → Lấy hiện tại tick
✓ Task_MsToTicks() → Convert ms → ticks

⚠️ DEAD CODE: Các hàm này không được gọi ở tasks
    (Tasks gọi vTaskDelay() trực tiếp)
```

### **8. Sensor Wrapper Classes**

**DS18B20Sensor:**
- `begin()`: Khởi tạo OneWire
- `readTemperature()`: Blocking ~750ms
- `isValid()`: Check connection

**TdsSensor:**
- `begin()`: Cấu hình ADC (GPIO 34)
- `readTds()`: 1 lần ADC → Điện áp → TDS ppm
- `_voltageToPpm()`: Công thức chuyển đổi

**TurbiditySensor:**
- `begin()`: Cấu hình ADC (GPIO 32)
- `readTurbidity()`: Median filter 15 mẫu

---

## 🔴 DEAD CODE / UNUSED CODE

### **1. SystemHealth.cpp/h** ⚠️ NOT CALLED
```cpp
// ✗ Không được gọi từ main.cpp hoặc tasks nào
// ✓ Có các hàm hữu ích nhưng chưa tích hợp

SystemHealth_ConfigureWatchdog()     // ← Thay thế bằng esp_task_wdt_init() trong main.cpp
SystemHealth_SubscribeTaskToWatchdog() // ← Không được gọi
SystemHealth_ResetWatchdog()          // ← Không được gọi
SystemHealth_GetStatus()              // ← Không được gọi
SystemHealth_PrintStatus()            // ← Không được gọi
SystemHealth_SoftReset()              // ← Không được gọi

💡 KHUYẾN NGHỊ: Xóa file này hoặc tích hợp vào NetworkTask
   để in status mỗi 60 giây (monitor RAM, WiFi, sensors)
```

### **2. TaskDelay.h - Các hàm không dùng**
```cpp
// ✗ Wrapper functions không được gọi

Task_Delay()           // ← Tasks gọi vTaskDelay() trực tiếp
Task_ValidateDelay()   // ← Không validation
Task_FeedWatchdog()    // ← Tasks không gọi esp_task_wdt_reset()
Task_GetTickCount()    // ← Không monitor tick
Task_MsToTicks()       // ← Dùng pdMS_TO_TICKS macro trực tiếp
Task_TicksToMs()       // ← Không convert

💡 KHUYẾN NGHỊ: Xóa file này hoặc chỉ dùng Task_Delay()
   Xóa các hàm validate & watchdog feed (không dùng)
```

### **3. AutomationTask.h - Các hàm header không match impl**
```cpp
// ✗ Khai báo trong header nhưng không implement trong .cpp

void AutomationTask_SetOxy(bool on);  // ← NOT IMPLEMENTED
                                       // Thay vào đó: startOxy()/stopOxy() static

💡 KHUYẾN NGHỊ: Xóa khai báo này hoặc implement trong .cpp
```

### **4. FeedingTask.h - Các hàm header không match impl**
```cpp
// ✗ Khai báo trong header nhưng không implement

void FeedingTask_StartFeed(float target_gram);  // ← NOT IMPLEMENTED
void FeedingTask_StopFeed();                    // ← NOT IMPLEMENTED
float FeedingTask_GetDispensedGram();           // ← NOT IMPLEMENTED
float FeedingTask_GetCurrentWeight();           // ← NOT IMPLEMENTED

💡 KHUYẾN NGHỊ: Implement các hàm này hoặc xóa
```

### **5. NetworkTask.cpp - Hàm header incomplete**
```cpp
// ✗ Các hàm khai báo trong header nhưng implement incomplete

uint16_t NetworkTask_GetOfflineCacheSize();  // ← Khai báo nhưng init bị comment
void NetworkTask_SyncOfflineCache();         // ← Khai báo nhưng không gọi
void pushAlertToFirebase(...);               // ← Khai báo nhưng không implementation

💡 KHUYẾN NGHỊ: Hoàn thành implementation hoặc xóa
```

### **6. Logging/Debug Code có thể optimize**
```cpp
// ✗ Serial prints gây chậm hệ thống

SensorTask:
  - Serial.printf() mỗi 2 giây (heavy)
  - Có thể reduce xuống 10 giây

AutomationTask:
  - Serial.printf() mỗi 5 giây (acceptable)

FeedingTask:
  - Serial.printf() mỗi lệnh & mode change (ok)

💡 KHUYẾN NGHỊ: Wrap prints trong #ifdef DEBUG
   để có thể disable logging khi release
```

---

## 🔍 TÓMO­M PHÂN TÍCH DEAD CODE

| **File** | **Status** | **Lý do** | **Hành động** |
|----------|-----------|----------|------------|
| **SystemHealth.cpp/h** | ⛔ DEAD | Không được khởi động trong setup() | XÓA hoặc TÍCH HỢP |
| **TaskDelay.cpp** | ⚠️ PARTIAL | Chỉ Task_Delay() được dùng | XÓA các hàm khác |
| **AutomationTask_SetOxy()** | ⛔ DEAD | Khai báo nhưng không gọi | XÓA hoặc IMPLEMENT |
| **FeedingTask_Start/Stop/Get** | ⛔ DEAD | Khai báo nhưng không implement | XÓA hoặc IMPLEMENT |
| **pushAlertToFirebase()** | ⚠️ INCOMPLETE | Khai báo nhưng logic chưa hoàn | IMPLEMENT hoặc XÓA |
| **Serial debug logging** | ⚠️ VERBOSE | Có thể slow down hệ thống | OPTIMIZE |

---

## 📈 TỔNG KẾT KIẾN TRÚC

```
📱 APP LAYER (Android Firebase App)
   ├─ /aquarium/control/guong → Bool (on/off oxy)
   ├─ /aquarium/control/thucan/mode → String ("auto"|"gram"|"manual")
   ├─ /aquarium/control/thucan/target_gram → Float
   ├─ /aquarium/control/thucan/state → Bool (manual on/off)
   │
   ✓ Nó nhân lại dữ liệu cảm biến từ /aquarium/
   │   (temperature, water_quality, ts300b, weight)
   │
   ✓ Nhận cảnh báo từ /notifications/

🌐 FIREBASE CLOUD LAYER
   ├─ Realtime Database (command stream)
   ├─ Authentication (Email/Password)
   ├─ Storage (chart logs, history)

📡 NETWORK LAYER (ESP32)
   ├─ WiFiManager (auto connect + AP portal)
   ├─ Firebase Stream (event-driven commands)
   ├─ SSL/TLS handshake
   ├─ Offline cache (khi mất mạng)

🔧 FREERTOS LAYER (4 Tasks)
   ├─ SensorTask (P1): Đọc cảm biến
   ├─ AutomationTask (P2): Edge logic + oxy
   ├─ NetworkTask (P3): Firebase sync + stream
   ├─ FeedingTask (P4): Điều khiển motor + LoadCell

⚙️ HARDWARE LAYER
   ├─ GPIO: DS18B20, TDS ADC, Turbidity ADC
   ├─ SPI: LoadCell HX711
   ├─ PWM Motor: EN/IN pins cho Motor A & B
```

---

## 🎯 KIẾN NGHỊ CẢI THIỆN

1. **Xóa dead code**: SystemHealth, TaskDelay wrappers
2. **Implement missing functions**: FeedingTask public functions
3. **Optimize logging**: Wrap Serial.println trong DEBUG mode
4. **Add offline cache**: Lưu sensor data khi mất mạng, sync lại khi online
5. **Watchdog subscription**: Subscribe NetworkTask vào WDT để monitor
6. **Error handling**: Add retry logic cho Firebase connection failures
7. **Add tests**: Unit test cho edge logic, sensor reads

