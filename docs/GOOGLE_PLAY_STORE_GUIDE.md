# Google Play Store Submission Guide

## UDP Trigger - Android Application

---

## 1. Store Listing Information

### App Details
- **App Name**: UDP Trigger
- **Package Name**: com.udptrigger
- **Version**: 1.0
- **Category**: Tools
- **Content Rating**: Everyone

### Short Description (80 characters max)
```
Low-latency UDP packet sender with automation, macros, and network discovery tools.
```

### Full Description (4000 characters max)

```
UDP Trigger is a professional-grade network utility for sending UDP packets with minimal latency. Designed for network administrators, automation engineers, and developers who need precise control over UDP communications.

## Key Features

### Core Functionality
• Ultra-low latency UDP packet transmission (sub-millisecond)
• Real-time connection status monitoring
• Configurable packet content with hex mode support
• Automatic reconnect with configurable intervals
• Burst mode for rapid multi-packet transmission

### Automation & Macros
• Visual Automation Manager with trigger-based workflows
• Macro recording and playback with precise timing
• Packet action rules (respond to received packets)
• Support for conditional logic and variable substitution

### Network Tools
• Built-in network scanner for device discovery
• UDP server mode for listening and responding
• Quick connect to favorite hosts
• Recent connection history

### Advanced Features
• Hardware keyboard trigger support
• Deep link integration for automation
• Configurable rate limiting
• Background service operation
• Export/import configuration and history

### User Experience
• Material 3 design with dark mode
• Home screen widgets for quick access
• QR code configuration sharing
• Comprehensive packet history with statistics
• Sound and haptic feedback options

## Use Cases

• **Home Automation**: Control smart home devices via UDP
• **Network Testing**: Test UDP servers and clients
• **IoT Development**: Debug and prototype IoT communications
• **Automation**: Integrate with Tasker, MacroDroid, etc.
• **Broadcast**: Send broadcast packets to multiple devices
• **Show Control**: Trigger lighting and audio cues

## Technical Specifications

• Target: Android 12+ (API 31+)
• Minimum: Android 7.0 (API 24)
• Architecture: MVVM with Jetpack Compose
• Permissions: Internet, Vibrate, Network State, Wake Lock, Notifications

## Privacy

• No data collection or analytics
• No ads or in-app purchases
• Open source and fully transparent
• All permissions are necessary for core functionality

## Support

• Open source: https://github.com/yourusername/android-udp-transmit
• Issues tracked on GitHub
• Responsive to user feedback
```

---

## 2. Graphics Assets Requirements

### Screenshots (Phone)
- **Minimum**: 2 screenshots
- **Maximum**: 8 screenshots
- **Size**: At least 320px width (no max, but 16:9 recommended)
- **Format**: PNG or JPG (no transparency)
- **Orientation**: Portrait or Landscape (be consistent)

**Recommended Screenshots**:
1. Main trigger interface showing host/port configuration
2. Settings panel with various options
3. Packet history/statistics view
4. Automation Manager interface
5. Network discovery/scanning in action
6. Widget home screen
7. Macro/automation execution
8. Deep link/automation integration

**Screenshot Tools**:
- Android Studio: Tools → Layout Inspector → Screenshot
- ADB: `adb shell screencap -p > screenshot.png`
- Emulator: Extended controls → Screenshot
- Physical device: Power + Volume Down simultaneously

### Feature Graphic (1024x1024px)
- **Size**: Exactly 1024x512 pixels
- **Format**: PNG or JPG (no transparency)
- **Content**: App branding, key features highlighted

**Design Guidelines**:
- Use your app icon and brand colors
- Highlight 2-3 key features visually
- Keep it simple and uncluttered
- Include app name if not prominent

**Recommended Layout**:
```
+----------------------------------+
|  [App Icon]  UDP Trigger         |
|                                  |
|  Send UDP packets with low       |
|  latency                          |
|                                  |
|  • Automation  • Macros          |
|  • Network Tools  • Widgets      |
+----------------------------------+
```

### Application Icon (512x512px)
- **Size**: Exactly 512x512 pixels
- **Format**: PNG (no transparency)
- **Content**: 512x512px high-res app icon
- **Safe Zone**: Keep key elements within 394px diameter circle

**Icon Generation**:
- Use your existing launcher icons from `mipmap-xxxhdpi/ic_launcher.png`
- Or create from `mipmap-xxxhdpi/ic_launcher_round.png`
- Scale up to 512x512px

---

## 3. Content Rating Questionnaire

When filling out the content rating questionnaire:

### Violence
- **Violence**: No
- **Realistic Violence**: No
- **Fantasy Violence**: No

### Sexual Content
- **Sexual Content**: No
- **Sexually Suggestive**: No
- **Nudity**: No

### Profanity/Offensive Language
- **Profanity or Crude Humor**: No

### Controlled Substances
- **Controlled Substances**: No

### Gambling
- **Gambling**: No

### Hate Speech
- **Hate Speech**: No

### Crimes
- **Crimes**: No

### Horror
- **Horror**: No

### Other
- **Other Mature Themes**: No

---

## 4. Privacy Policy URL

**Your privacy policy is located at**: `PRIVACY.md`

For Play Store, you need to host this publicly:
- GitHub Pages: Upload PRIVACY.md to your repo
- Or use a privacy policy service
- Or host on your own website

**Valid URL format**:
```
https://github.com/yourusername/android-udp-transmit/blob/main/PRIVACY.md
```

---

## 5. Store Listing Steps

### 1. Create Google Play Console Account
- Go to https://play.google.com/console
- Sign in with your Google account
- Pay the $25 USD one-time registration fee

### 2. Create New App
- Click "Create app"
- Enter app name: "UDP Trigger"
- Select: "Yes, this is an app"
- Select: "Tools" category

### 3. Fill in Store Listing
- Upload app icon (512x512)
- Upload feature graphic (1024x512)
- Add screenshots (min 2)
- Add short and full descriptions
- Add privacy policy URL

### 4. Content Rating
- Complete content rating questionnaire
- Generate content rating

### 5. Pricing & Distribution
- Select: Free
- Select distribution countries (or "All available countries")
- Check compatibility
- No ads or in-app purchases

### 6. Release Management
- Upload signed APK or App Bundle
- Set rollout: 100% (or staged rollout)
- Review and publish

---

## 6. Asset Creation Checklist

- [ ] **App Icon (512x512)**: Scale from mipmap-xxxhdpi
- [ ] **Feature Graphic (1024x512)**: Create in design tool
- [ ] **Screenshot 1**: Main trigger interface
- [ ] **Screenshot 2**: Settings panel
- [ ] **Screenshot 3**: Packet history
- [ ] **Screenshot 4**: Automation Manager
- [ ] **Banner (optional)**: 180x120px tablet banner
- [ ] **Privacy Policy URL**: Host PRIVACY.md publicly

---

## 7. Quick Screenshot Guide

### Taking Screenshots with Emulator:
```bash
# Launch emulator
emulator -avd YOUR_DEVICE &

# Install app
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Take screenshots
adb shell screencap -p /sdcard/screenshot1.png
adb pull /sdcard/screenshot1.png ./screenshots/
```

### Taking Screenshots on Device:
1. Navigate to desired screen
2. Press Power + Volume Down simultaneously
3. Find in Photos/Screenshots
4. Transfer to computer via USB or Google Photos

---

## 8. Common Issues & Solutions

### "Target SDK version too low"
- **Issue**: Play Store requires target SDK 33+
- **Solution**: Already set to 34 ✓

### "Notification icons not Android 13+ compliant"
- **Issue**: Missing PNG icons
- **Solution**: Run `./scripts/generate_notification_icons.sh`

### "App crashes on Android 12"
- **Issue**: Splash screen not compatible
- **Solution**: Fixed - using SplashScreenCompat API ✓

### "Exported receiver security vulnerability"
- **Issue**: UdpIntentReceiver accessible by any app
- **Solution**: Fixed - added signature permission ✓

---

## 9. Testing Checklist

Before submitting, test on:

- [ ] **Android 12 (API 31)**: Minimum supported version
- [ ] **Android 13 (API 33)**: Notification permissions
- [ ] **Android 14 (API 34)**: Target SDK version
- [ ] **Different screen sizes**: Phone, tablet, foldable
- [ ] **Different orientations**: Portrait (locked), landscape support
- [ ] **Network conditions**: WiFi, mobile, no network
- [ ] **Key features**:
  - [ ] Send UDP packets
  - [ ] Configure host/port
  - [ ] View packet history
  - [ ] Use automation/macros
  - [ ] Network discovery
  - [ ] Notifications

---

## 10. Post-Submission Checklist

- [ ] Monitor crash reports on Play Console
- [ ] Respond to user reviews
- [ ] Check ANR (Application Not Responding) reports
- [ ] Update app for new Android versions
- [ ] Address security vulnerabilities if reported

---

**Last Updated**: January 22, 2026
**Document Version**: 1.0
