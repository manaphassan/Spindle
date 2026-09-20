# Spindle — Official Latest Release Package
**Build Date:** September 20, 2026  
**Publisher:** HaNa Innovation  
**License:** Apache 2.0  

---

> **💬 A Personal Note from the Creator:**  
> I am not a professional programmer—just someone who is passionate about music and wanted to repurpose old devices to bring back the nostalgic magic of a cassette Walkman. This is a personal passion project shared freely with you. Please use this software **"as is"** and enjoy your music—don't expect a polished corporate app!

---

## 📦 Package Contents

This release package contains verified, production-ready APK builds for both Spindle editions:

| File | Edition | Version | Size | Target OS | Minimum Android |
| :--- | :--- | :---: | :---: | :--- | :--- |
| **`Spindle-Standard-v1.4.0-alpha.apk`** | Standard | `v1.4.0-alpha` (Code 5) | **4.88 MB** | Modern DAP & Smartphones | Android 8.0+ (API 26+) |
| **`Spindle-Lite-v1.0.0-LITE.apk`** | Lite | `v1.0.0-LITE` (Code 3) | **1.79 MB** | Vintage DAP & Small Screens | Android 4.4+ (API 19+) |
| **`Spindle-Standard-v1.4.0-alpha-debug.apk`** | Standard (Debug) | `v1.4.0-alpha.debug` | **11.2 MB** | Developer / Testing | Android 8.0+ (API 26+) |
| **`Spindle-Lite-v1.0.0-LITE-debug.apk`** | Lite (Debug) | `v1.0.0-LITE.debug` | **6.00 MB** | Developer / Testing | Android 4.4+ (API 19+) |

---

## 🔒 Integrity Verification (SHA-256)

Verify downloaded APKs using PowerShell or Linux terminal:

```bash
# Windows PowerShell
Get-FileHash -Algorithm SHA256 *.apk

# Linux / macOS
sha256sum -c SHA256SUMS.txt
```

### Official Checksums:
```
9e0f3d0d47554c6dd32c146ce3dadf09ce02ef2f1f54d2bae236cc90c053dd68  Spindle-Standard-v1.4.0-alpha.apk
eaa7d70a40598d84154a8a8351d69ddccda1af611ad0a35b0768cd2a91d17c34  Spindle-Lite-v1.0.0-LITE.apk
2bef5520584c4fa45af4aa6f389641f93755378004cb593a04db491262a11d9f  Spindle-Standard-v1.4.0-alpha-debug.apk
e4a5795e33a3777fa97f4ba3ac42f4b537dff47c9d0958c12b87d6483c31639d  Spindle-Lite-v1.0.0-LITE-debug.apk
```

---

## 📲 Quick Installation Instructions

### Standard Installation (Via Phone Browser / File Manager):
1. Copy or download the appropriate `.apk` file to your Android device.
2. Tap the `.apk` file in your Downloads or File Manager.
3. If prompted, allow installation from unknown sources.
4. Launch Spindle and grant storage permissions to scan your music.

### Direct ADB Installation (Over USB / Wi-Fi):
```bash
# Install Spindle Standard on modern device
adb install -r Spindle-Standard-v1.4.0-alpha.apk

# Install Spindle Lite on vintage device
adb install -r Spindle-Lite-v1.0.0-LITE.apk
```

---

## 🎯 Which Version Should You Install?

- **Choose Spindle Standard (`1.4.0-alpha`)** if your device was made in the last 6–8 years running Android 8 or newer. Includes animated kinetic reels, live synchronized lyrics, audio visualizer, FM and internet radio, and 10-band tone equalizer.
- **Choose Spindle Lite (`1.0.0-LITE`)** if you have an older music player or vintage phone (Android 4.4 down to 4.1, 512MB RAM, 3-inch screen like Sony Ericsson Xperia active). Uses less than 18MB RAM and is optimized for physical hardware buttons (camera shutter, volume, d-pad).

---

<sub>Spindle · Dedicated Audiophile Music Player Launcher · HaNa Innovation</sub>
