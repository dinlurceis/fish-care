# HỆ THỐNG NUÔI CÁ THÔNG MINH

---

## 📌 Giới Thiệu

Hệ thống nuôi cá tự động IoT là một **giải pháp tích hợp phần cứng, phần mềm nhúng, và ứng dụng di động** nhằm tự động hóa toàn bộ quy trình nuôi cá. Hệ thống giám sát 4 thông số môi trường chính (nhiệt độ, chất lượng nước, độ đục, khối lượng thức ăn) theo thời gian thực và tự động điều khiển các thiết bị như guồng tạo oxy, cho ăn, đồng thời cung cấp giao diện di động thân thiện để người dùng theo dõi và điều khiển từ xa.

---

## 🚀 Tính Năng Chính

### 📊 Giám Sát Thời Gian Thực

- **Nhiệt Độ Nước:** DS18B20 (OneWire)
- **Chất Lượng Nước:** TDS
- **Độ Đục Nước:** TS-300B Sensor
- **Khối Lượng Thức Ăn:** LoadCell HX711

### 🍖 Cho Ăn

- **Chế Độ Auto:** Cho ăn lúc 6h00 & 17h00
- **Chế Độ Gram:** Nhả thức ăn theo khối lượng chính xác
- **Chế Độ Manual:** Điều khiển bằng tay qua app
- **Bảo Vệ Motor:** Timeout 30s chống kẹt

### 💨 Điều Khiển Oxy

- **Bật/Tắt:** Điều khiển từ app hoặc Firebase
- **Edge Logic:** Tự động bật oxy khi mất WiFi + nước nóng (> 32°C)

### 🤖 Chẩn Đoán Bệnh AI

- **Groq LLaMA 2 API:** AI phân tích triệu chứng
- **Cơ Sở Tri Thức:** 25+ bệnh thủy sản phổ biến

### 📈 Biểu Đồ & Thống Kê

- **Groq LLaMA 2 API:** AI phân tích biểu đồ
- **LineChart TDS:** Lịch sử 24h/7 ngày

---

## 💻 Công Nghệ Sử Dụng

### Android App (Kotlin)

- Jetpack Compose, MVVM, StateFlow
- Firebase Realtime DB, Auth, FCM
- Hilt (DI), WorkManager
- MPAndroidChart, Lottie
- OkHttp, Glide, uCrop

### ESP32 Firmware

- FreeRTOS 4 tasks (Sensor, Feeding, Automation, Network)
- Arduino ESP32, Firebase Arduino Client
- DallasTemperature, OneWire, HX711

### Backend

- Firebase Realtime Database
- Firebase Authentication
- Groq API (LLaMA 2 AI)

---

## 🚀 Cài Đặt Nhanh

### Yêu Cầu

- Android Studio Hedgehog 2023.1.1+
- VS Code + PlatformIO
- Python 3.8+, JDK 11+
- Firebase Project

### Bước 1: Clone & Cấu Hình ESP32

```bash
git clone https://github.com/dinlurceis/fish-care.git
cd firmware-esp32

# Edit include/secrets.h
# - WiFi SSID, Password
# - Firebase Host, Auth Key

pio run -e esp32dev -t upload
pio device monitor -b 115200
```

### Bước 2: Firebase Console

```
1. console.firebase.google.com
2. Create project "FishCareApp"
3. Enable Realtime DB (Southeast Asia)
4. Configure Security Rules
5. Download google-services.json
6. Enable Auth, FCM, Analytics
```

### Bước 3: Build Android

```bash
cd android-app
cp google-services.json app/

./gradlew build
./gradlew installDebug  # Run on device
```

---

## 📁 Cấu Trúc Dự Án

```
fish-care/
├── firmware-esp32/
│   ├── src/
│   │   ├── main.cpp
│   │   ├── SensorTask.cpp/h
│   │   ├── FeedingTask.cpp/h
│   │   ├── AutomationTask.cpp/h
│   │   ├── NetworkTask.cpp/h
│   │   └── sensors/
│   └── include/Config.h, secrets.h
│
├── android-app/
│   ├── app/src/main/
│   │   ├── java/com/fishcare/app/
│   │   │   ├── FishCareApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/screens/, components/
│   │   │   ├── viewmodel/
│   │   │   ├── repository/
│   │   │   └── util/
│   │   └── res/raw/benh_ca.json
│   └── build.gradle.kts
│
└── README.md
```

---

## 👥 Đội Phát Triển

| Thành Viên          | Công Việc                        |
| ------------------- | -------------------------------- |
| **Vũ Thị Thu Hằng** | SensorTask, Dashboard            |
| **Phùng Đình Dũng** | FeedingTask, điều khiển motor    |
| **Mạc Đức Duy**     | AutomationTask, Edge Logic, Auth |
| **Đào Huy Hoàng**   | NetworkTask, Charts + AI         |
| **Nguyễn Sỹ Công**  | FreeRTOS, khám bệnh cá           |
