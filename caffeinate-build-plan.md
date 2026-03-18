# Caffeinate — Android App Build Plan

## Overview

Caffeinate is a minimal native Android app (Kotlin) that keeps the phone screen awake, controlled entirely via a Quick Settings tile (like the flashlight or Wi-Fi toggles). This is a personal-use app that will be sideloaded onto two Android phones — it will **not** be distributed via Google Play.

## Architecture

The app uses a **dual-layer approach**:

1. **Primary mechanism:** Toggle `Settings.System.SCREEN_OFF_TIMEOUT` to `Int.MAX_VALUE` (~24.8 days). This persists at the system level, survives process death, and requires no background service for core functionality.
2. **Safety-net foreground service (lightweight):** A slim foreground service that does **not** hold a wake lock. Its only jobs are:
   - Listen for `ACTION_SCREEN_OFF` (power button press) and auto-restore the original timeout + deactivate.
   - Monitor battery level and auto-deactivate when battery drops below 15%.
   - Show a persistent low-priority notification reminding the user that Caffeinate is active.

**Do NOT use a `PowerManager` wake lock.** The `SCREEN_OFF_TIMEOUT` approach makes it unnecessary.

**Do NOT use a transparent Activity with `FLAG_KEEP_SCREEN_ON`.** This approach fails the moment the Activity leaves the foreground.

## Target SDK & Compatibility

- `minSdk = 33` (Android 13)
- `targetSdk = 35` (Android 15)
- `compileSdk = 35`
- Language: Kotlin
- Build system: Gradle Kotlin DSL (`build.gradle.kts`)

## Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/skyler/caffeinate/
│   │   ├── CaffeinateTileService.kt
│   │   ├── CaffeinateService.kt
│   │   ├── CaffeinateState.kt
│   │   └── MainActivity.kt
│   └── res/
│       ├── drawable/
│       │   ├── ic_caffeinate_on.xml
│       │   └── ic_caffeinate_off.xml
│       ├── layout/
│       │   └── activity_main.xml
│       └── values/
│           ├── strings.xml
│           └── themes.xml
├── build.gradle.kts
```

**Package name:** `com.skyler.caffeinate`

---

## Component Specifications

### 1. `CaffeinateState.kt` — Shared State Manager

A singleton `object` using `SharedPreferences` to persist state across tile service and foreground service lifecycles.

**Stored values:**
- `is_active: Boolean` — whether Caffeinate is currently engaged
- `original_timeout: Int` — the user's screen timeout value before activation (default fallback: `60000` ms)

**Methods:**
- `isActive(context: Context): Boolean`
- `setActive(context: Context, active: Boolean)`
- `saveOriginalTimeout(context: Context, timeout: Int)`
- `getOriginalTimeout(context: Context): Int`

---

### 2. `CaffeinateTileService.kt` — Quick Settings Tile

Extends `android.service.quicksettings.TileService`.

**`onStartListening()`:**
- Call `refreshTile()` to sync tile appearance with current state.

**`onClick()`:**
1. Check if `Settings.System.canWrite(this)` is granted.
   - If **not granted**: launch `Settings.ACTION_MANAGE_WRITE_SETTINGS` for this package. On API 34+, use the `PendingIntent` overload of `startActivityAndCollapse()` (the `Intent` overload throws on API 34+).
   - If **granted**: proceed with toggle.
2. If currently active → call `deactivate()`.
3. If currently inactive → call `activate()`.
4. Call `refreshTile()`.

**`activate()`:**
1. Read current `Settings.System.SCREEN_OFF_TIMEOUT` and save it via `CaffeinateState.saveOriginalTimeout()`.
2. Write `Int.MAX_VALUE` to `Settings.System.SCREEN_OFF_TIMEOUT`.
3. Start `CaffeinateService` via `context.startForegroundService()`.
4. Set `CaffeinateState.setActive(true)`.

**`deactivate()`:**
1. Read saved original timeout from `CaffeinateState.getOriginalTimeout()`.
2. Write it back to `Settings.System.SCREEN_OFF_TIMEOUT`.
3. Stop `CaffeinateService`.
4. Set `CaffeinateState.setActive(false)`.

**`refreshTile()`:**
- Null-check `qsTile` (it can be null outside the listening window).
- Set `tile.state` to `Tile.STATE_ACTIVE` or `Tile.STATE_INACTIVE`.
- Set `tile.icon` to `ic_caffeinate_on` or `ic_caffeinate_off`.
- Set `tile.label` to "Caffeinate".
- On API 29+, set `tile.subtitle` to "On" or "Off".
- Set appropriate `tile.contentDescription` for accessibility.
- **Call `tile.updateTile()`** — nothing renders without this.

**`onTileRemoved()`:**
- If currently active, call `deactivate()` to clean up.

---

### 3. `CaffeinateService.kt` — Lightweight Foreground Service

Extends `android.app.Service`. This service holds **no wake lock**. Its purpose is purely safety-net behavior and the persistent notification.

**`onCreate()`:**
- Create notification channel `"caffeinate_channel"` with `IMPORTANCE_LOW` (no sound, visible in shade).

**`onStartCommand()`:**
1. Call `startForeground()` with the notification. On API 34+, pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`. Use `ServiceCompat.startForeground()` from AndroidX.
2. Register a `BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF` (use `RECEIVER_NOT_EXPORTED` flag).
3. Register a `BroadcastReceiver` for `Intent.ACTION_BATTERY_LOW` (fires at ~15%).
4. Return `START_STICKY`.

**Screen-off receiver behavior:**
- When `ACTION_SCREEN_OFF` is received (user pressed power button):
  1. Restore original `SCREEN_OFF_TIMEOUT` from `CaffeinateState`.
  2. Set `CaffeinateState.setActive(false)`.
  3. Request tile UI refresh via `TileService.requestListeningState()`.
  4. Call `stopSelf()`.

**Battery-low receiver behavior:**
- Same as screen-off: restore timeout, update state, refresh tile, stop self.

**Notification:**
- Title: "Caffeinate is active"
- Text: "Screen will stay on. Tap tile to disable."
- Small icon: `ic_caffeinate_on`
- Ongoing: `true`
- Priority: `LOW`
- Category: `CATEGORY_SERVICE`

**`onDestroy()`:**
- Unregister both broadcast receivers (wrap in try/catch).
- Call `ServiceCompat.stopForeground(this, STOP_FOREGROUND_REMOVE)`.

**Companion object with `start(context)` and `stop(context)` helper methods** for clean invocation from the tile service.

---

### 4. `MainActivity.kt` — Permission Grant Screen

This Activity exists **only** to provide a launcher icon and handle the one-time `WRITE_SETTINGS` permission flow. The user should never need to open this after initial setup.

**`onCreate()`:**
1. Check `Settings.System.canWrite(this)`.
2. If **not granted**: show a simple screen explaining that the app needs the "Modify system settings" permission, with a button that launches `Settings.ACTION_MANAGE_WRITE_SETTINGS`.
3. If **already granted**: show a simple screen that says "Caffeinate is ready! Add the tile to your Quick Settings panel." with brief instructions.

**`onResume()`:**
- Re-check permission state and update the UI accordingly. The user returns here after granting the permission in system settings.

**Layout** (`activity_main.xml`):
- Keep it dead simple. A centered layout with:
  - App icon / name at top
  - Status text ("Permission needed" or "Ready to use")
  - A button (visible only when permission not granted) to launch the settings screen
  - Brief instructions on how to add the tile to the Quick Settings panel

Use Material 3 theming. No need for anything fancy.

---

## AndroidManifest.xml — Full Specification

### Permissions Required

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```

**Do NOT include `android.permission.WAKE_LOCK`** — we are not using wake locks.

### Service Declarations

**TileService:**
```xml
<service
    android:name=".CaffeinateTileService"
    android:exported="true"
    android:icon="@drawable/ic_caffeinate_off"
    android:label="@string/tile_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
    <meta-data
        android:name="android.service.quicksettings.TOGGLEABLE_TILE"
        android:value="true" />
</service>
```

- `exported="true"` and `BIND_QUICK_SETTINGS_TILE` permission are **mandatory**.
- `TOGGLEABLE_TILE` meta-data improves accessibility.

**Foreground Service:**
```xml
<service
    android:name=".CaffeinateService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Keeps the device screen awake at the user's explicit request via a Quick Settings toggle." />
</service>
```

- `foregroundServiceType="specialUse"` is required on API 34+.
- Include the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` for completeness.

---

## Tile Icons

Create two vector drawable XML files at **24×24dp**. They must be **solid white (`#FFFFFF`) on a transparent background**. The system applies tinting automatically based on active/inactive state.

- `ic_caffeinate_on.xml` — a filled coffee cup icon
- `ic_caffeinate_off.xml` — an outlined/empty coffee cup icon

Use simple Material-style vector paths. These are Quick Settings icons, not launcher icons — keep them minimal.

---

## Dependencies (`build.gradle.kts`)

Keep dependencies minimal:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
```

No Compose, no Hilt, no Room, no Jetpack Navigation. This is a ~4-file app.

---

## Key Implementation Gotchas

1. **`qsTile` can be null.** Always null-check it. It's only valid between `onStartListening()` and `onStopListening()`.

2. **`startActivityAndCollapse(Intent)` crashes on API 34+.** Use the `PendingIntent` overload with `FLAG_IMMUTABLE` on API 34+.

3. **`updateTile()` must be called.** Setting properties on the tile object does nothing until you call `tile.updateTile()`.

4. **`RECEIVER_NOT_EXPORTED` flag is required on API 33+** when registering broadcast receivers in code.

5. **`ServiceCompat.startForeground()`** handles the `foregroundServiceType` parameter gracefully across API levels.

6. **`TileService.requestListeningState()`** is how the foreground service triggers a tile UI refresh from outside the tile service.

7. **The `WRITE_SETTINGS` permission** is not a runtime permission — it requires launching a system settings screen where the user manually toggles it. Check with `Settings.System.canWrite(context)`.

---

## Development Environment Setup

The developer has **no prior Android development experience**. The coding agent should generate a complete, ready-to-open Android Studio project. Additionally, the following setup steps will be needed:

### 1. Install Android Studio

- Download from https://developer.android.com/studio
- Run the installer — accept all defaults
- On first launch, Android Studio will download the Android SDK, build tools, and an emulator image. This takes a while. Let it finish.
- When prompted for a project, choose "Open" and point it at the generated project folder.

### 2. Project Setup in Android Studio

- After opening the project, Android Studio will run a **Gradle sync**. Wait for it to complete (progress bar at the bottom). This downloads all dependencies.
- If prompted to update Gradle or any plugins, accept the updates.
- If you see red errors about missing SDK versions, go to **Tools → SDK Manager** and install API 35 (Android 15).

### 3. Build the APK

There are two ways to get a sideloadable APK:

**Option A — Debug APK (easiest, recommended):**
1. In the menu bar: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for the build to complete. A notification will pop up with a link to the APK location.
3. The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`
4. This APK is unsigned (debug-signed) but works perfectly for sideloading.

**Option B — Signed release APK (optional, slightly smaller):**
1. **Build → Generate Signed Bundle / APK → APK**
2. Create a new keystore when prompted (remember the passwords — you'll need them for updates).
3. Select "release" build variant.
4. APK will be at: `app/build/outputs/apk/release/app-release.apk`

For personal use, Option A is perfectly fine. No need to sign anything.

---

## Sideloading the APK onto Your Phone

### Step 1 — Enable Developer Options on the phone

1. Open **Settings → About Phone**.
2. Tap **Build Number** 7 times. You'll see a toast message saying "You are now a developer!"
3. Go back to **Settings → System → Developer Options** (location varies by phone manufacturer).
4. Toggle **USB Debugging** to ON.

### Step 2 — Transfer and install the APK

**Method A — USB cable + ADB (most reliable):**
1. Install ADB on your computer:
   - **Windows**: Download "SDK Platform-Tools" from https://developer.android.com/tools/releases/platform-tools — extract the zip and add the folder to your PATH, or just run commands from inside that folder.
   - **Mac**: `brew install android-platform-tools`
   - **Linux**: `sudo apt install adb`
2. Connect your phone via USB cable. Accept the "Allow USB debugging?" prompt on the phone.
3. In a terminal, navigate to where the APK is and run:
   ```bash
   adb install app-debug.apk
   ```
4. You should see "Success" in the terminal. The app is now installed.

**Method B — Transfer the file directly (no ADB needed):**
1. Transfer the APK to your phone via Google Drive, email attachment, or USB file transfer.
2. On the phone, open the file. Android will prompt you to allow installing from that source (e.g., "Allow Chrome to install apps" or "Allow Files to install apps").
3. Toggle it on, then tap **Install**.

### Step 3 — First launch setup

1. Open the **Caffeinate** app from your app drawer.
2. Tap the button to grant the "Modify system settings" permission. This opens a system settings page — toggle the switch ON for Caffeinate, then press back.
3. Now add the tile: swipe down from the top of your screen to open Quick Settings, swipe down again to expand the full panel, tap the **pencil/edit icon** (usually bottom-left), find "Caffeinate" in the available tiles, and drag it into your active tiles.
4. Done! Tap the coffee cup tile anytime to keep your screen on.

### Installing on your wife's phone

Repeat Steps 1-3 on her phone. You can reuse the same APK file — just transfer it via Google Drive, AirDrop equivalent, Nearby Share, or whatever is convenient.

### Updating the app later

If you make changes and rebuild:
```bash
adb install -r app-debug.apk
```
The `-r` flag means "reinstall" — it overwrites the existing app without losing the permission grant or tile placement.

---

## What Success Looks Like

1. User installs APK via sideload.
2. User opens the app once → taps the button to grant `WRITE_SETTINGS` permission → done.
3. User edits their Quick Settings panel to add the "Caffeinate" tile.
4. From now on: swipe down, tap the coffee cup tile → screen stays on indefinitely. A low-priority notification appears.
5. Tap the tile again → original timeout restored, notification disappears.
6. If the user forgets and presses the power button → Caffeinate auto-deactivates.
7. If the battery gets low → Caffeinate auto-deactivates.
