#pragma once

/**
 * Credentials Example File
 * 
 * HƯỚNG DẪN:
 * 1. Copy file này thành "secrets.h"
 * 2. Điền các giá trị thực của bạn
 * 3. File "secrets.h" sẽ được gitignore - KHÔNG push lên GitHub
 * 4. Share file này (example) để team biết cần fill gì
 */

// ─────────────────────────────────────────────────────────
//  WiFi Credentials
// ─────────────────────────────────────────────────────────
const char* WIFI_SSID_1 = "YOUR_MAIN_WIFI_SSID";
const char* WIFI_PASS_1 = "YOUR_MAIN_WIFI_PASSWORD";

const char* WIFI_SSID_2 = "YOUR_BACKUP_WIFI_SSID";
const char* WIFI_PASS_2 = "YOUR_BACKUP_WIFI_PASSWORD";

// ─────────────────────────────────────────────────────────
//  Firebase Credentials
// ─────────────────────────────────────────────────────────
const char* FIREBASE_HOST = "your-project-id-default-rtdb.asia-southeast1.firebasedatabase.app";
const char* FIREBASE_API_KEY = "YOUR_FIREBASE_API_KEY_HERE";

// ─────────────────────────────────────────────────────────
//  Firebase Auth (Email/Password)
// ─────────────────────────────────────────────────────────
const char* USER_EMAIL = "your-email@gmail.com";
const char* USER_PASSWORD = "your-firebase-password";
