# Running ClassWave locally

## Requirements

- Node.js 22 or newer. The included `.nvmrc` selects Node.js 24 LTS.
- MongoDB running locally or at a reachable MongoDB URI.
- Android Studio, JDK 17 or 21, Android SDK 36, and an Android device or emulator running API 24 or newer.
- Build tools required by mediasoup when its native worker cannot be obtained as a prebuilt binary. On Windows, this may include Python and Visual Studio C++ Build Tools.

On Windows, clone into an ASCII-only directory such as `C:\Projects\ClassWave-Portfolio`. The Android Gradle plugin rejects project paths containing some non-ASCII characters.

## Backend

From the repository root, in PowerShell

```powershell
cd class-wave-backend
Copy-Item .env.example .env
npm ci
```

Edit `.env` before starting the server. Set `MONGO_URI` to your development database and generate a long random `JWT_SECRET`. Keep this configuration local.

```powershell
npm start
```

The backend listens on port 3000 by default. The media server uses ports 10000 through 10100. Firewall and network configuration must permit the required connections between the clients and backend.

`MEDIASOUP_ANNOUNCED_IP` specifies the address the media server advertises to clients. It defaults to an automatically selected network interface. Set it explicitly if the host has multiple interfaces or clients cannot reach that address.

## Android application

1. Open `class-wave-android-app` in Android Studio.
2. Let Android Studio configure the SDK path in `local.properties`.
3. Add `SERVER_IP` to the same file. Use the backend host's reachable LAN address for physical devices. Android emulator `10.0.2.2` reaches the host machine for HTTP requests, while media routing still requires a reachable announced address.
4. Sync Gradle and run the app.

```properties
SERVER_IP=10.0.2.2
```

The app builds its development URL as `http://<SERVER_IP>:3000/`. The checked-in `local.properties.example` illustrates the setting. Android Studio maintains the machine-specific SDK location in the local file.

## Optional simulated participants

Start the backend, create a demonstration course, then follow the instructions in [dummy-participants](../dummy-participants/README.md). This tool supplies demonstration participants for the classroom UI.

## Tests

```powershell
cd class-wave-backend
npm test
```

```powershell
cd class-wave-android-app
.\gradlew.bat testDebugUnitTest assembleDebug
```

Use an isolated database and demonstration accounts when exploring the prototype. The development-status section of the main README explains the remaining deployment work.
