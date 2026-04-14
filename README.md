# IoT Fish Farm System 🐟

## 📌 Description
Hệ thống nuôi cá thông minh sử dụng:
- ESP32 (IoT)
- Android App (Kotlin)
- Firebase (Realtime Database + Cloud Functions)

## 🏗️ Architecture
IoT (ESP32) → Firebase → Android App

## 📁 Structure
- firmware-esp32/: Code ESP32 (PlatformIO + FreeRTOS)
- android-app/: App Android (Kotlin, MVVM)
- firebase-functions/: Cloud Functions

## 👥 Team
- Vũ Thị Thu Hằng: Sensor + Dashboard
- Phùng Đình Dũng: Feeding + History
- Mạc Đức Duy: Automation + Auth + Notification
- Đào Huy Hoàng: Network + Charts + AI
- Nguyễn Sỹ Công: RTOS + Integration + AI

## 🚀 Features
- Real-time monitoring (Temp, TDS, Turbidity)
- Smart feeding (Manual / Auto / Gram)
- Push notification cảnh báo
- AI phân tích dữ liệu