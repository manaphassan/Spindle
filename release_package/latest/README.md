# Spindle — Official Latest Release Package
**Build Date:** September 20, 2026  
**Publisher:** HaNa Innovation  
**License:** PolyForm Noncommercial 1.0.0 (Open & free for personal use; commercial sale strictly prohibited)  
**Signing:** Verified authentic private release keystore (`HaNa Innovation`, SHA-256 fingerprint ending in `...7D:C2`)  

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
8d8aea592ddf0121f99d14023d3cd077201b228ef282f835d01f47fafb48d76b  Spindle-Standard-v1.4.0-alpha.apk
677cd6e2b355a4e4502aea40cd62368d50a32afd31178ffe3f4ce45457250ad6  Spindle-Lite-v1.0.0-LITE.apk
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
