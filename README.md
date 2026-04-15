# Kamin Driver — Android

Professional turn-by-turn driver navigation app for Android, built with the
[Mapbox Navigation SDK v2](https://docs.mapbox.com/android/navigation/guides/).
Integrates with the existing FastAPI backend at **http://kamintransbus-transfer.de**.

---

## Features

- **Route selection** — Маршрут 1 / Маршрут 2 with automatic GPS location detection.
- **Professional in-app navigation** — route line, maneuver instructions, trip progress
  bar and voice guidance powered by Mapbox Navigation SDK (no external app required).
- **ПРИБУВ button** — tapping it posts an arrival event to the backend, receives the
  next stop and seamlessly continues navigation to the new destination.
- **Runtime Mapbox token** — fetched from `GET /api/config` so no token is hard-coded.
- **HTTP cleartext** scoped to `kamintransbus-transfer.de` only via Network Security Config.

---

## Architecture

```
com.pravos.kamindriver
├── KaminDriverApp.kt            # Application — sets Mapbox token at runtime
├── api/
│   ├── ApiClient.kt             # Retrofit + OkHttp singleton
│   ├── ApiService.kt            # Retrofit interface
│   └── model/                   # Moshi data classes (ConfigResponse, ClientStop, …)
├── ui/
│   ├── routepicker/
│   │   └── RoutePickerActivity  # Screen 1: route selection
│   └── navigation/
│       └── NavigationActivity   # Screen 2: Mapbox Navigation SDK UI + ПРИБУВ
└── util/
    └── LocationHelper.kt        # FusedLocationProviderClient helper (suspend fun)
```

**Backend endpoints used:**

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/config` | Fetch Mapbox public token |
| GET | `/api/route/{num}/next?from_lat=&from_lng=` | Next client stop |
| POST | `/api/route/{num}/arrived` | Report arrival, get next stop |

---

## Prerequisites

| Tool | Minimum version |
|------|-----------------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android SDK | Platform 34 |
| Mapbox account | [account.mapbox.com](https://account.mapbox.com) |

### Mapbox tokens

The Mapbox Navigation SDK requires **two** tokens:

| Token | Prefix | Used for |
|-------|--------|---------|
| Public access token | `pk.` | Runtime map rendering & navigation |
| Secret downloads token | `sk.` | Downloading the SDK via Gradle |

Create both at **https://account.mapbox.com/access-tokens/**.  
The secret token must have the **`DOWNLOADS:READ`** scope.

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/pravos-ui/kamin-driver-android.git
cd kamin-driver-android
```

### 2. Configure local tokens

Copy the example file and fill in your values:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
# Android SDK path — Android Studio fills this automatically
sdk.dir=/Users/<you>/Library/Android/sdk

# Mapbox public token (pk.*) — debug fallback when backend is unavailable
MAPBOX_ACCESS_TOKEN=pk.eyJ1...

# Mapbox secret downloads token (sk.*) — needed to download the SDK at build time
MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...
```

> **Never commit `local.properties`** — it is listed in `.gitignore`.

Alternatively, set the tokens in `~/.gradle/gradle.properties` (applies to all projects):

```properties
MAPBOX_ACCESS_TOKEN=pk.eyJ1...
MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...
```

### 3. Open in Android Studio

Open the project root folder in Android Studio.  
Gradle sync will download all dependencies automatically (requires internet access and the secret token).

### 4. Run on a device or emulator

Select a device (physical or AVD) with Android 8.0+ (API 26+) and press **Run ▶**.

> A physical device with GPS is recommended for testing navigation.

---

## Build from the command line

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Configuration

| BuildConfig field | Default | Description |
|---|---|---|
| `BASE_URL` | `http://kamintransbus-transfer.de/` | Backend base URL |
| `MAPBOX_TOKEN_DEBUG` | (from gradle property) | Debug Mapbox token fallback |

To point the app at a different backend, change `BASE_URL` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://your-backend.example.com/\"")
```

---

## Permissions

| Permission | Reason |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS fix for routing and arrival reporting |
| `INTERNET` | Backend API calls and Mapbox tile/directions downloads |
| `FOREGROUND_SERVICE` | Keep navigation alive while the app is in the foreground |

---

## Troubleshooting

**Gradle sync fails with 401 Unauthorized**  
→ `MAPBOX_DOWNLOADS_TOKEN` is missing or incorrect. Check `local.properties` or `~/.gradle/gradle.properties`.

**Map does not load / "Access token is invalid"**  
→ `MAPBOX_ACCESS_TOKEN` is missing. Ensure the backend `/api/config` endpoint returns a valid `pk.*` token, or set the debug fallback in `gradle.properties`.

**"Location unavailable"**  
→ GPS is disabled on the device, or the emulator has no location mock. Enable location or use a physical device.