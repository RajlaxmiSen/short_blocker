# Shorts Blocker - Setup Instructions

## How to Build the APK

### Step 1: Install Android Studio
Download from: https://developer.android.com/studio
(It's free. Windows, Mac, and Linux are all supported.)

### Step 2: Open the Project
1. Open Android Studio
2. Click "Open" (not "New Project")
3. Select the `shorts-blocker-android` folder
4. Wait for Gradle sync to complete (may take a few minutes on first run)

### Step 3: Build the APK
1. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for the build to finish
3. Click "Locate" in the popup, or find it at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Step 4: Install on Your Phone
**Option A - USB:**
1. Enable Developer Options on your phone (Settings → About Phone → tap Build Number 7 times)
2. Enable USB Debugging
3. Connect phone to PC, run: `adb install app-debug.apk`

**Option B - Directly:**
1. Copy the APK to your phone
2. Enable "Install from Unknown Sources" in settings
3. Open the APK to install

---

## First-Time App Setup (on phone)

1. Open **Shorts Blocker**
2. Create your **Permanent Password** (keep this very safe — needed to uninstall)
3. Create your **Temporary Password** (used for 10-min sessions)
4. Grant all requested permissions:
   - **Accessibility Service**: Settings → Accessibility → Shorts Blocker → Enable
   - **Display over other apps**: Grant in popup
   - **Device Admin**: Grant for uninstall protection

---

## How It Works

| Feature | How |
|---------|-----|
| Detect Shorts | Android Accessibility Service monitors YouTube |
| Block Scrolling | Full-screen overlay appears on top of YouTube |
| Session (10 min) | Enter Temporary Password → timer starts |
| Daily Limit | Max 5 sessions (50 min). Resets at midnight |
| Uninstall protection | Device Admin prevents removal without Permanent Password |
| Auto-start | Boot receiver restarts service after reboot |
| Background protection | Foreground service keeps it alive |

---

## Package Details

- **Package name**: `com.shortsBlocker`
- **Min Android**: 8.0 (API 26)
- **Target Android**: 14 (API 34)
- **Permissions used**: Accessibility, Overlay, Device Admin, Boot Completed, Notifications

---

## Important Notes

- The Accessibility Service MUST be enabled for blocking to work
- The overlay permission MUST be granted for the block screen to appear
- Device Admin should be enabled to prevent uninstall without Permanent Password
- Do NOT forget your Permanent Password — if lost, you cannot uninstall the app
