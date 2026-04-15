# 📊 So sánh Code Mới (FreeRTOS) vs Code Cũ

## ✅ **Features ĐÃ IMPLEMENT (từ code cũ)**

### 1. **WiFi & Firebase Connection**

- ✅ WiFi dual SSID fallback (Hằng + Hang)
- ✅ WiFi exponential retry (2s → 4s → 8s → ... → 30s max)
- ✅ Firebase credentials (API Key, DB URL, email/password)
- ✅ Firebase.begin() + reconnect

### 2. **Sensor Reading (SensorTask - Hằng)**

- ✅ DS18B20 (OneWire, GPIO 18)
- ✅ TDS (ADC GPIO 34) - formula: (133.42*V³ - 255.86*V² + 857.39*V)*0.75
- ✅ Turbidity (ADC GPIO 32) - raw 0-4095
- ✅ LoadCell HX711 (GPIO 21/22, scale 505.4633)

### 3. **Feeding Control (FeedingTask - Dũng)**

- ✅ **AUTO mode**: 6h00 & 17h00 (20s timeout)
- ✅ **GRAM mode**: Target gram with 30s timeout
- ✅ **MANUAL mode**: Toggle on/off with 30s timeout
- ✅ Motor B control (GPIO 23/14/12)
- ✅ LoadCell reading for gram calculation
- ✅ All 3 modes have fallback to IDLE

### 4. **Oxygen Control (AutomationTask - Duy)**

- ✅ Motor A control (GPIO 5/26/27)
- ✅ Edge logic: WiFi down + Temp > 32°C → auto-oxygen 5 minutes

### 5. **Watchdog (NetworkTask - Hoàng)**

- ✅ 20s WDT timeout
- ✅ esp_task_wdt_reset() every 2 seconds

---

## ❌ **MISSING Features (từ code cũ)**

### 1. **NTP Time Sync** ⏰

**OLD CODE:**

```cpp
configTime(7 * 3600, 0, "pool.ntp.org");
while (!time(nullptr)) delay(500);
```

**NEW CODE:** ❌ NO NTP SYNC

- Cơn thời gian sẽ sai hoặc trở thành 0 (epoch fail)
- Cần thêm vào **main.cpp** hoặc **NetworkTask.cpp**

**ACTION:** Add configTime() call after WiFi connected

---

### 2. **Firebase Logging (logFeed function)** 📝

**OLD CODE:**

```cpp
void logFeed(float grams, const String& mode) {
  if (!Firebase.ready()) return;

  if (Firebase.getInt(fbData, "/logs/counter")) {
    int c = fbData.intData();
    String path = "/logs/log" + String(c + 1);
    Firebase.setFloat (fbData, path + "/gram", grams);
    Firebase.setString(fbData, path + "/mode", mode);
    Firebase.setString(fbData, path + "/time", formatTime());
    Firebase.setInt   (fbData, "/logs/counter", c + 1);
  }
}
```

**NEW CODE:** ❌ ONLY logFeedLog() in FeedingTask.cpp

```cpp
void logFeedLog(float grams, const String& mode) {
    // TODO: Ghi vào Firebase /logs/log{counter}
    // Dũng sẽ implement phần này connect với NetworkTask
    Serial.printf("[FeedingTask] LOG: mode=%s, gram=%.1f\n", mode.c_str(), grams);
}
```

**ACTION:** Implement logFeedLog() - ghi vào Firebase /logs/

---

### 3. **Time Formatting (formatTime function)** 🕐

**OLD CODE:**

```cpp
String formatTime() {
  time_t now = time(nullptr);
  struct tm* t = localtime(&now);
  char buf[20];
  sprintf(buf, "%02d:%02d %02d/%02d/%04d", ...);
  return String(buf);
}
```

**NEW CODE:** ❌ NOT PRESENT

**ACTION:** Add formatTime() utility function

---

### 4. **Firebase Command Polling (pollCommandsFromFirebase)** 📡

**OLD CODE:**

```cpp
// updateGuongControl() - bật/tắt guồng
bool guong = Firebase.getBool(fbData, "/aquarium/control/guong");

// updateFeedingMode() - AUTO/GRAM/MANUAL với state
Firebase.getString(fbData, "/aquarium/control/thucan/mode");
Firebase.getBool(fbData, "/aquarium/control/thucan/state");
Firebase.getFloat(fbData, "/aquarium/control/thucan/target_gram");
```

**NEW CODE:** ❌ ONLY TODO comment

```cpp
void pollCommandsFromFirebase() {
    if (!s_FirebaseReady || !Firebase.ready()) {
        return;
    }

    // TODO: Hoàng sẽ implement phần này để đọc lệnh từ Firebase
    // và đẩy vào xQueue_Commands cho các task khác
}
```

**ACTION:** Implement pollCommandsFromFirebase():

1. Read `/aquarium/control/guong` → push cmd to xQueue_Commands
2. Read `/aquarium/control/thucan/mode` + `state` + `target_gram`
3. Push CMD_THUCAN_AUTO/GRAM/MANUAL to xQueue_Commands

---

### 5. **Oxygen Firebase Control** (AutomationTask) 🌬️

**OLD CODE:**

```cpp
void updateGuongControl() {
  bool guong = false;
  if (Firebase.getBool(fbData, "/aquarium/control/guong") && fbData.dataAvailable()) {
    guong = fbData.boolData();
  }
  if (guong) startGuong();
  else stopGuong();
}
```

**NEW CODE:** ❌ ONLY Edge Logic

```cpp
// AutomationTask chỉ có:
if (isWiFiConnected) {
    // TODO: Nhận lệnh từ Firebase (Hoàng implement)
    // Hiện tại: chỉ giữ trạng thái
}
```

**ACTION:** In AutomationTask, add Firebase polling from xQueue_Commands

---

## 🔄 **Differences in Implementation**

| Feature                | OLD CODE                                    | NEW CODE                                                             |
| ---------------------- | ------------------------------------------- | -------------------------------------------------------------------- |
| **Loop Timing**        | 500ms fixed loop                            | Task-specific: Sensor 2s, Feeding 100ms, Automation 10s, Network 2s  |
| **WiFi Multi**         | WiFiMulti.addAP()                           | Direct connectWiFi() with fallback                                   |
| **Feeding Logic**      | updateFeedingMode() + updateManualFeeding() | Integrated in FeedingTask loop                                       |
| **Oxygen Logic**       | updateGuongControl()                        | AutomationTask (Firebase + Edge)                                     |
| **Core Affinity**      | N/A (single core)                           | Core 0: NetworkTask, FeedingTask; Core 1: SensorTask, AutomationTask |
| **Data Exchange**      | Global variables                            | Queues (SensorData, Commands) + Mutex                                |
| **Timeout Protection** | AUTO 20s, GRAM 30s                          | ✅ Same                                                              |
| **Logging**            | Firebase incremental counter                | ❌ TODO                                                              |

---

## 🚀 **Cần Bổ Sung**

### **HIGH PRIORITY (Chức năng core):**

1. ✅ **NTP Time Sync** - Add configTime() in NetworkTask
2. ✅ **pollCommandsFromFirebase()** - Read Firebase commands
3. ✅ **logFeedLog() Firebase** - Actually write logs
4. ✅ **AutomationTask Oxygen Firebase Control** - Read from xQueue_Commands

### **MEDIUM PRIORITY (Helper functions):**

5. ✅ **formatTime()** - Format time string for logging
6. ✅ **FirebaseCommand handling** - Parse and execute commands

### **LOW PRIORITY (Optimization):**

7. Weight update to Firebase (done but can optimize)
8. Error handling improvements

---

## 📋 **TODO List for Dũng, Hoàng, Duy:**

**Hoàng (NetworkTask):**

- [ ] Add configTime() after WiFi connected
- [ ] Implement pollCommandsFromFirebase()
- [ ] Push CMD_GUONG_ON/OFF, CMD_THUCAN_AUTO/GRAM/MANUAL to xQueue_Commands

**Dũng (FeedingTask):**

- [ ] Implement logFeedLog() to actually write to Firebase /logs/
- [ ] Add formatTime() utility function
- [ ] Handle commands from xQueue_Commands for MANUAL mode

**Duy (AutomationTask):**

- [ ] Read from xQueue_Commands for oxygen control
- [ ] Handle CMD_GUONG_ON/OFF from Firebase via Hoàng
- [ ] Keep edge logic as fallback when WiFi down

**Công (Leader):**

- [ ] Verify all queue and mutex usage
- [ ] Test multi-task synchronization
- [ ] Document the new architecture
