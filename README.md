# 📱 PhotoSync – Android Client

PhotoSync is the native Android companion app for the PhotoSync Self-Hosted Server. It provides a seamless, Google Photos-like experience for backing up, viewing, and sharing your personal media to your own private server.

Built using **Modern Android Development (MAD)** principles, it leverages Jetpack Compose, WorkManager, and optimized networking for efficient and reliable syncing.

> ⚠️ This repository contains **only the Android Client app**.  
> The FastAPI backend lives in a separate repository.

---

## 🌟 Features

### 🔄 Automated Background Sync
- ✅ **WorkManager Integration**  
  Automatically backs up media every 24 hours.

- ✅ **Battery & Data Aware**  
  Syncs only when battery is sufficient. Supports WiFi-only or cellular options.

- ✅ **Folder Selection**  
  Choose specific folders (Camera, WhatsApp, etc.) using persistent URI permissions.

---

### 🌐 Smart Networking
- ✅ **Dual-URL Architecture**
    - Local IP (fast home network)
    - Cloudflare Tunnel (remote access)

- ✅ **Stateless Interceptors**
  Injects JWT tokens and device IDs into every request via OkHttp.

- ✅ **Auto-Logout Security**
  Handles `401 Unauthorized` responses by clearing session and redirecting to login.

---

### 👨‍👩‍👧 Private Family Network
- ✅ **My Network**
  Manage follow requests between users.

- ✅ **Albums & Sharing**
  Share albums with approved connections.

- ✅ **One-Tap Import**
  Clone shared albums into personal storage.

---

### 🎨 Modern UI/UX
- ✅ **Jetpack Compose UI**
- ✅ **Dynamic Grid Layouts**
- ✅ **Full-Screen Photo Viewer**
- ✅ **Native Sharing & Open-With Support**

---

## 🛠 Tech Stack

| Component | Technology |
|----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Networking | Retrofit + OkHttp |
| Image Loading | Coil |
| Background Tasks | WorkManager |
| Storage | EncryptedSharedPreferences |
| Navigation | Compose Navigation |

---

## 📁 Project Structure

```
app/src/main/java/com/sagar/prosync/
├─ auth/
├─ data/
│  ├─ api/
│  ├─ ApiClient.kt
│  ├─ SessionStore.kt
│  └─ SettingsStore.kt
├─ device/
├─ navigation/
├─ sync/
│  ├─ SyncWorker.kt
│  └─ FolderPicker.kt
├─ ui/
│  ├─ HomeScreen.kt
│  ├─ AlbumScreens.kt
│  ├─ NetworkScreen.kt
│  └─ SettingsScreen.kt
└─ MainActivity.kt
```

---

## 🚀 Setup & Installation

### 1️⃣ Requirements
- Android Studio (Latest Stable)
- Minimum SDK: API 26
- Target SDK: API 34

---

### 2️⃣ Clone Project
```bash
git clone <android-repo-url>
```

Open in Android Studio and sync Gradle.

---

### 3️⃣ First-Time Setup (Server Config)

On first launch:

1. Enter Remote Server URL  
   Example:
   ```
   https://photos.yourdomain.com/
   ```

2. (Optional) Enable Local Server  
   Example:
   ```
   http://192.168.0.181:8000/
   ```

3. Continue to login

---

### 4️⃣ Permissions Required

- READ_MEDIA_IMAGES / READ_MEDIA_VIDEO (Android 13+)
- READ_EXTERNAL_STORAGE (Below Android 13)
- POST_NOTIFICATIONS

---

## 🗺 Roadmap

### ✅ Release 1.0
- Full backup system
- Dual-URL networking
- Private sharing system

### 🔜 Release 1.5
- Web application (React/Vue)
- Desktop-style gallery management

---

## 👨‍💻 Author

**Sagar Makhija**

---

## 📜 License

Private / Internal Use (Update as needed)
