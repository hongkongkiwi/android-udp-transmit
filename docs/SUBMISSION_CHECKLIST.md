# Google Play Store Submission Checklist

## UDP Trigger - Android Application

**Prepared**: January 22, 2026
**Version**: 1.0 (versionCode: 1)
**Target API**: 34 (Android 14)
**Package**: com.udptrigger

---

## ✅ COMPLETED PRE-SUBMISSION TASKS

### Code & Security
- [x] Target SDK 34+ (Play Store requirement)
- [x] 64-bit architecture support (Kotlin/JVM)
- [x] Exported receiver security fix (signature permission)
- [x] Splash screen Android 12+ compliance
- [x] All permissions properly declared
- [x] No hardcoded credentials
- [x] No third-party SDKs with data collection

### App Assets
- [x] Launcher icons (mdpi through xxxhdpi)
- [x] Adaptive icon (any density)
- [x] App name and label
- [x] Notification icons (monochrome XML vectors)
- [x] Play Store graphics templates (SVG)

### Documentation
- [x] Privacy Policy (PRIVACY.md)
- [x] LICENSE file
- [x] CHANGELOG.md
- [x] README.md with app description
- [x] Google Play Store Guide
- [x] Icon generation scripts

---

## ⏳ PENDING TASKS (Before Submission)

### 1. Notification PNG Icons (Can Use System Defaults)
**Status**: XML vectors created, PNG conversion optional

**Current State**:
- Monochrome XML vector icons created
- Android 13+ prefers PNG but will use XML fallback
- Build succeeds without PNG icons

**Optional - Generate PNGs**:
```bash
# Install ImageMagick
brew install imagemagick

# Run generation script
cd scripts
./generate_notification_icons.sh
```

**Workaround**: System default notification icons work fine.

---

### 2. Play Store Graphics
**Status**: SVG templates created, need conversion to PNG

**Generated Files** in `play_store_assets/`:
- `app_icon.svg` → Convert to `app_icon.png` (512x512)
- `feature_graphic.svg` → Convert to `feature_graphic.png` (1024x512)
- `screenshot_template.svg` → Reference for screenshot composition

**Convert to PNG**:
```bash
# Option 1: Using Inkscape (GUI)
brew install --cask inkscape
open play_store_assets/

# Option 2: Using librsvg
brew install librsvg
rsvg-convert -w 512 -h 512 play_store_assets/app_icon.svg play_store_assets/app_icon.png
rsvg-convert -w 1024 -h 512 play_store_assets/feature_graphic.svg play_store_assets/feature_graphic.png

# Option 3: Online tools
# Visit: https://www.aconvert.com/image/svg-to-png/
# Upload SVG files and download PNGs
```

---

### 3. Screenshots (Manual Task)
**Required**: Minimum 2 screenshots (recommended 4-8)

**Recommended Screenshots**:

1. **Main Trigger Interface**
   - Shows host/port configuration
   - Connect button and status
   - Send UDP button

2. **Settings Panel**
   - Toggle switches for various options
   - Rate limiting, haptic feedback
   - Auto-reconnect settings

3. **Packet History**
   - List of sent packets
   - Timestamps and latency info
   - Statistics display

4. **Automation Manager**
   - List of automations
   - Enable/disable toggles
   - Run now buttons

5. **Network Discovery**
   - Scan button
   - Discovered devices list
   - Connect options

6. **Macro Playback**
   - Macro selection
   - Play/Pause controls
   - Step progress

7. **Home Screen Widget**
   - Widget on home screen
   - Quick trigger buttons

8. **Settings - Advanced**
   - Burst mode settings
   - Foreground service
   - Multicast options

**How to Capture**:

**On Emulator**:
```bash
# Start emulator
emulator -avd YOUR_AVD_NAME &

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.udptrigger/.MainActivity

# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./screenshots/screen1.png
```

**On Physical Device**:
1. Navigate to desired screen
2. Press Power + Volume Down simultaneously
3. Find in Photos app
4. Transfer to computer (Google Drive, email, USB)

**Screenshot Requirements**:
- Width: At least 320px
- Format: PNG or JPG
- No transparency
- Consistent orientation

---

## 📋 STORE LISTING INFORMATION

### Application Details
- **App Name**: UDP Trigger
- **Short Description** (80 chars max):
  ```
  Low-latency UDP packet sender with automation, macros, and network discovery tools.
  ```

- **Full Description**: See `GOOGLE_PLAY_STORE_GUIDE.md`

### Categorization
- **Category**: Tools
- **Content Rating**: Everyone

### Privacy Policy URL
Host `PRIVACY.md` publicly:
- GitHub Pages: Upload to repo
- Or use GitHub raw URL: `https://raw.githubusercontent.com/username/android-udp-transmit/main/PRIVACY.md`

---

## 🔧 BUILD & SIGNING

### Generate Release APK/AAB

**Option 1: Using Gradle (Recommended)**
```bash
# Set keystore environment variables
export KEYSTORE_PATH=path/to/keystore.jks
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password

# Build release APK
./gradlew assembleRelease

# OR build Android App Bundle (recommended)
./gradlew bundleRelease
```

**Output locations**:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

**Option 2: Using Android Studio**
1. Build → Generate Signed Bundle / APK
2. Select Android App Bundle or APK
3. Follow keystore signing steps

---

## 📤 SUBMISSION STEPS

### Step 1: Google Play Console Setup

1. Go to [Google Play Console](https://play.google.com/console)
2. Sign in with your Google account
3. Accept terms and conditions
4. Pay $25 USD registration fee (one-time)

### Step 2: Create New App

1. Click **Create app**
2. Enter details:
   - App name: `UDP Trigger`
   - Default language: English
   - Free or Paid: **Free**
3. Create app

### Step 3: Fill Main Store Listing

1. **Store Listing** → **Main information**
   - Upload app icon (512x512 PNG)
   - Add feature graphic (1024x512 PNG)
   - Add screenshots (min 2)
   - Short description (80 chars max)
   - Full description (see guide)

2. **Store Listing** → **Categorization**
   - Category: Tools
   - Tags: network, tools, automation, udp

3. **Store Listing** → **Contact details**
   - Developer website (or GitHub repo)
   - Email address
   - Privacy policy URL

### Step 4: Content Rating

1. **Content rating** → Start questionnaire
2. Complete questionnaire:
   - Violence: No
   - Sexual Content: No
   - Profanity: No
   - Controlled Substances: No
   - Gambling: No
   - Hate Speech: No
   - Crimes: No
   - Horror: No
   - Other Mature Themes: No
3. Calculate content rating
4. Apply rating

### Step 5: Pricing & Distribution

1. **Pricing & Distribution** → **Pricing**
   - Free: Yes
   - In-app purchases: No

2. **Pricing & Distribution** → **Distribution**
   - Main countries: All available countries (or select specific ones)
   - Country-specific availability: Leave blank for all countries

3. **Pricing & Distribution** → **Content guidelines**
   - All items checked

4. **Pricing & Distribution** → **App access**
   - Devices: Phones and Tablets
   - Android version: 12+ (API 31+)
   - Screen sizes: All

### Step 6: Upload Release

1. **Release management** → **Production**
2. Click **Create new release**
3. Upload signed APK or AAB
4. Wait for processing
5. Add release notes:
   ```
   Initial Release v1.0

   Features:
   • Low-latency UDP packet transmission
   • Automation Manager with trigger workflows
   • Macro recording and playback
   • Network discovery and device scanning
   • Hardware keyboard support
   • Home screen widgets
   • Configuration export/import
   • Comprehensive packet history
   ```
6. Set rollout: **100%** (or staged rollout)
7. Review release
8. **Save** and **Start rollout**

---

## ⚠️ COMMON SUBMISSION ISSUES

### Issue 1: "Target SDK version too low"
**Solution**: Already set to 34 ✓

### Issue 2: "Security vulnerability in exported receiver"
**Solution**: Fixed with signature permission ✓

### Issue 3: "Crash on Android 12+ devices"
**Solution**: Fixed with SplashScreenCompat API ✓

### Issue 4: "Missing notification icons"
**Solution**: XML vectors present, system defaults work

### Issue 5: "Insufficient screenshots"
**Solution**: Provide at least 2 screenshots

### Issue 6: "Missing privacy policy"
**Solution**: PRIVACY.md exists, host publicly

---

## 🧪 TESTING CHECKLIST

### Pre-Submission Testing

- [ ] **Test on Android 12 (API 31)** - Minimum supported
- [ ] **Test on Android 13 (API 33)** - Notification permission changes
- [ ] **Test on Android 14 (API 34)** - Target SDK
- [ ] **Test on different screen sizes**
- [ ] **Test on tablets**
- [ ] **Test with WiFi**
- [ ] **Test with mobile data**
- [ ] **Test airplane mode behavior**

### Core Functionality Testing

- [ ] Send UDP packet
- [ ] Configure host and port
- [ ] Connect to UDP server
- [ ] View packet history
- [ ] Create automation
- [ ] Create macro
- [ ] Network scan
- [ ] Use widget
- [ ] Export configuration
- [ ] Import configuration
- [ ] Toggle settings
- [ ] Receive UDP packet (listen mode)

### Edge Cases

- [ ] Invalid host/port input
- [ ] Network unavailable
- [ ] Server not responding
- [ ] Rapid button presses
- [ ] Long packet content
- [ ] Special characters in content
- [ ] Background service behavior
- [ ] Configuration change handling

---

## 📊 POST-SUBMISSION MONITORING

After submission, monitor:

- **Play Console Dashboard**:
  - Install metrics
  - Crash reports
  - ANR (Application Not Responding) reports
  - User ratings and reviews
  - Pre-launch report results

- **Address Issues Promptly**:
  - Fix crashes in next version
  - Respond to user reviews
  - Update store listing as needed

---

## 🔄 VERSION UPDATE CHECKLIST

When releasing updates:

1. Increment `versionCode` in `app/build.gradle.kts`
2. Update `versionName`
3. Add changelog entry
4. Test thoroughly
5. Generate signed APK/AAB
6. Upload to Play Console
7. Add release notes
8. Roll out to users

---

## 📞 SUPPORT CONTACTS

For Play Store console issues:
- Play Console Help: https://play.google.com/console/help
- Developer Policy Center: https://play.google.com/developer/policy

---

## ✅ FINAL VERIFICATION

Before submitting, confirm:

- [ ] App builds successfully
- [ ] Release APK/AAB generated
- [ ] App icon (512x512) ready
- [ ] Feature graphic (1024x512) ready
- [ ] At least 2 screenshots ready
- [ ] Privacy policy URL accessible
- [ ] Short description ready
- [ ] Full description ready
- [ ] Content rating completed
- [ ] All permissions justified

**Estimated Time to Submission**: 1-2 hours (mostly graphics creation and screenshot capture)

---

## 📝 FILES REFERENCE

Key files created/updated for submission:

**Code Changes**:
- `AndroidManifest.xml` - Added signature permission
- `SplashActivity.kt` - Android 12+ splash screen API
- `values-v31/themes.xml` - Android 12+ theme attributes
- `build.gradle.kts` - Added splashscreen library

**Assets**:
- `play_store_assets/app_icon.svg` - App icon template
- `play_store_assets/feature_graphic.svg` - Feature graphic template
- `play_store_assets/screenshot_template.svg` - Screenshot reference
- `drawable/ic_notification_send_monochrome.xml` - Notification icon
- `drawable/ic_notification_stop_monochrome.xml` - Notification icon

**Documentation**:
- `docs/GOOGLE_PLAY_STORE_GUIDE.md` - Complete guide
- `PRIVACY.md` - Privacy policy
- `LICENSE` - License file
- `README.md` - App description
- `CHANGELOG.md` - Version history

**Scripts**:
- `scripts/generate_notification_icons.sh` - Notification icon generator
- `scripts/generate_play_store_graphics.sh` - Play Store graphics generator

---

**Status**: Ready for submission after generating Play Store graphics and screenshots.

**Good luck with your submission! 🚀**
