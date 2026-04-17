# 📱 PhotoSync Android

> 🚀 A modern, high-performance Android client for your private PhotoSync Server.

[![Version](https://img.shields.io/badge/version-1.5.0-blue.svg)]()
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)]()
[![Kotlin](https://img.shields.io/badge/kotlin-Modern-blue.svg)]()
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)]()

---

## 🔗 Ecosystem

- 🖥 Backend Server → https://github.com/sagarmakhija1994/PhotoSync-Python
- 📱 Android Client → (this repo)

---

## ✨ What is PhotoSync Android?

PhotoSync Android is a **privacy-first media backup app** that connects to your self-hosted PhotoSync server.

- 🔄 Automatic background sync
- ⚡ Lightning-fast local transfers
- 🌐 Remote access via tunnel/domain
- 👨‍👩‍👧 Private family sharing

---

## ⚡ Quick Start

### 📥 Install APK

- Download latest APK from **Releases**
- Install on device
- Open app

---

### 🔧 First Setup

1. Enter **Server URL**
   ```
   https://your-domain.com
   ```

2. (Optional) Add **Local IP**
   ```
   http://192.168.x.x:8000
   ```

3. Enable **Prioritize Local Network** (recommended)

4. Login → Done ✅

---

## 🏗 Architecture

```mermaid
flowchart LR
    A[📱 Android App] -->|Local / Remote| B[🌐 PhotoSync Server]
    B --> C[(📁 Storage)]
    B --> D[(🗄 DB)]
```

---

### 🖼️ Screenshots
<div align="center"> <img src="https://github.com/user-attachments/assets/c347558c-3c0d-44fb-9f1c-b1e1874782bf" width="180"/> <img src="https://github.com/user-attachments/assets/c84ad64d-c1e5-490c-a322-b7bd913e9257" width="180"/> <img src="https://github.com/user-attachments/assets/2771f0f1-35ab-45a5-b4d6-a18d65033758" width="180"/> <img src="https://github.com/user-attachments/assets/3e5bca4f-f7bf-40ba-b1e7-01b838d8e792" width="180"/> <img src="https://github.com/user-attachments/assets/a70b3493-5f67-4bc5-840c-6be0e1496e22" width="180"/> <img src="https://github.com/user-attachments/assets/049e5e4c-22c0-4306-ab49-b431ee2c4840" width="180"/> <img src="https://github.com/user-attachments/assets/04269656-01a4-4c58-8c4c-68b6a3ff70b2" width="180"/> <img src="https://github.com/user-attachments/assets/bca1434b-dbd9-43ac-89e9-3f24321ea1ab" width="180"/> <img src="https://github.com/user-attachments/assets/78dd9abb-5957-4ed7-bc06-e77956909049" width="180"/> <img src="https://github.com/user-attachments/assets/d9885e0a-0f58-4db0-b4b3-056c134d2e01" width="180"/> <img src="https://github.com/user-attachments/assets/b24f58cf-683e-4f08-8d0a-4c237238df7f" width="180"/> <img src="https://github.com/user-attachments/assets/ff7255bc-04bc-43be-b5ad-48ce50cd134d" width="180"/> <img src="https://github.com/user-attachments/assets/211d0dea-9f7c-4f27-8680-d53d9eb207f9" width="180"/> <img src="https://github.com/user-attachments/assets/db961e3c-fd07-4696-a1f0-6a5bd299943b" width="180"/> <img src="https://github.com/user-attachments/assets/1edba6de-d48a-4489-94c3-38498527ce83" width="180"/> <img src="https://github.com/user-attachments/assets/6ab30443-fabd-4083-8f3e-96575998bbbf" width="180"/> <img src="https://github.com/user-attachments/assets/9d9c9c37-0bbe-4c4b-a667-ec51f8389df9" width="180"/> <img src="https://github.com/user-attachments/assets/7304e87e-fc74-43bd-87bf-2e7e56ae56f3" width="180"/> </div>



---

## 🌟 Key Features

### 🔄 Sync Engine
- WorkManager-based background sync
- Battery & network aware
- Folder-level control

---

### 📤 Manual Uploads
- Select 100+ files
- Runs in foreground service
- Works with screen off

---

### 🌐 Smart Networking
- Dual URL (Local + Remote)
- Local prioritization
- Auto fallback

---

### 📶 Connection Intelligence
- Live connection testing
- Server version detection
- Network indicator (WiFi / Cloud)

---

### 🎨 UI/UX
- Jetpack Compose UI
- Smooth animations
- Dynamic grids
- Fullscreen viewer

---

### 🎥 Media Experience
- Native video playback
- Swipe navigation
- Smart zoom (pinch / double tap)

---

### ⚡ Performance
- 100MB disk cache
- Zero redundant downloads
- Optimized memory usage

---

## 🛠 Tech Stack

| Component | Technology |
|----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Networking | Retrofit + OkHttp |
| Image | Coil |
| Background | WorkManager |
| Storage | EncryptedSharedPreferences |

---

## 📁 Project Structure

```
app/src/main/java/com/sagar/prosync/
├─ auth/
├─ data/
├─ device/
├─ navigation/
├─ sync/
├─ ui/
└─ MainActivity.kt
```

---

## 🔐 Security

- JWT authentication
- Secure storage
- Auto logout on invalid session

---

## 🎯 Why PhotoSync?

| Feature            | PhotoSync | Google Photos |
|------------------|----------|--------------|
| Self-hosted       | ✅       | ❌           |
| Privacy           | ✅       | ❌           |
| Local speed       | ✅       | ❌           |
| No subscription   | ✅       | ❌           |

---

## 👨‍💻 Author

Sagar Makhija

---

## 📜 License

Private / Internal Use
