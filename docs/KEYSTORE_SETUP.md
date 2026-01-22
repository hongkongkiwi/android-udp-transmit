# Keystore Setup Guide

This guide covers creating and managing your Android signing keystore for UDP Trigger.

---

## Why You Need a Keystore

Android apps must be signed with a certificate to be distributed. The same keystore must be used for all future updates of your app.

**CRITICAL**: If you lose your keystore, you cannot update your app on Google Play Store!

---

## Option 1: Create New Keystore (Recommended for New Apps)

### Generate Keystore

```bash
keytool -genkey -v -keystore udp-trigger-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias udp-trigger-key \
  -dname "CN=UDP Trigger, OU=Development, O=YourCompany, L=City, ST=State, C=US" \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD
```

**Parameters**:
- `-keystore`: Output file name
- `-keyalg`: Encryption algorithm (RSA recommended)
- `-keysize`: Key size (2048 bits is standard)
- `-validity`: Validity period in days (10000 = ~27 years)
- `-alias`: Name for this key within the keystore
- `-dname`: Distinguished Name (certificate info)
- `-storepass`: Password for the entire keystore
- `-keypass`: Password for this specific key (usually same as store password)

### What to Enter

| Parameter | Example Value | Your Value |
|-----------|---------------|------------|
| `-dname CN` | `UDP Trigger` | Your app name |
| `-dname OU` | `Development` | Your department |
| `-dname O` | `YourCompany` | Your organization |
| `-dname L` | `San Francisco` | Your city |
| `-dname ST` | `California` | Your state/province |
| `-dname C` | `US` | Your country code |
| `storepass` | `StrongP@ssw0rd123!` | **Use a strong password** |
| `keypass` | `StrongP@ssw0rd123!` | **Same as storepass** |

**Password Tips**:
- Use at least 12 characters
- Include uppercase, lowercase, numbers, symbols
- Store securely (password manager)
- Never commit to git

---

## Option 2: Extract from Existing Keystore

If you already have a keystore from another app:

```bash
# List keys in existing keystore
keytool -list -v -keystore your-existing.jks

# Export specific key info (if needed)
keytool -exportcert -alias your-alias -keystore your-existing.jks -file output.crt
```

---

## Backup Your Keystore

### 1. Create Secure Backup

```bash
# Create backup directory
mkdir -p ~/backups/android-keys

# Copy keystore
cp udp-trigger-release.jks ~/backups/android-keys/

# Set restrictive permissions
chmod 600 ~/backups/android-keys/udp-trigger-release.jks
```

### 2. Store Backup Information

Create `keystore-info.txt` (keep this separate from the keystore):

```text
UDP Trigger Keystore Information
=================================

App: UDP Trigger
Package: com.udptrigger
Keystore File: udp-trigger-release.jks
Key Alias: udp-trigger-key
Created: 2026-01-22

IMPORTANT: Store this information securely!
Do NOT store with the keystore file.

Passwords: [Store in password manager]
- Keystore Password: [YOUR_STORE_PASSWORD]
- Key Password: [YOUR_KEY_PASSWORD]

Locations:
- Primary: [Encrypted USB drive]
- Backup 1: [Password manager]
- Backup 2: [Safe deposit box]
```

---

## GitHub Actions Setup

### Encode Keystore for GitHub Secrets

```bash
# macOS
base64 -i udp-trigger-release.jks | pbcopy

# Linux
base64 -i udp-trigger-release.jks | xclip

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("udp-trigger-release.jks")) | Set-Clipboard
```

### Add to GitHub Secrets

1. Go to repository **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Add these secrets:

| Secret Name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | [Paste base64-encoded keystore] |
| `KEYSTORE_PASSWORD` | `YOUR_STORE_PASSWORD` |
| `KEY_ALIAS` | `udp-trigger-key` |
| `KEY_PASSWORD` | `YOUR_KEY_PASSWORD` |

---

## Local Build with Keystore

### Option 1: Environment Variables (Recommended)

```bash
export KEYSTORE_PATH=udp-trigger-release.jks
export KEYSTORE_PASSWORD=YOUR_STORE_PASSWORD
export KEY_ALIAS=udp-trigger-key
export KEY_PASSWORD=YOUR_KEY_PASSWORD

./gradlew assembleRelease
./gradlew bundleRelease
```

### Option 2: gradle.properties (Not Recommended - Security Risk)

```bash
# Add to ~/.gradle/gradle.properties (NEVER commit this)
KEYSTORE_PATH=udp-trigger-release.jks
KEYSTORE_PASSWORD=YOUR_STORE_PASSWORD
KEY_ALIAS=udp-trigger-key
KEY_PASSWORD=YOUR_KEY_PASSWORD
```

**Warning**: This file may be accidentally committed. Use environment variables instead.

---

## Verify Signed Build

```bash
# Verify APK signature
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# Check signing info
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## Troubleshooting

### "Incorrect key password"

**Solution**: Verify `KEY_PASSWORD` matches `KEYSTORE_PASSWORD` (if they're the same)

### "Keystore tampered with, or password incorrect"

**Solution**: Verify `KEYSTORE_PASSWORD` is correct

### "Cannot recover key"

**Solution**:
1. The key password is different from keystore password
2. Use `-keypass` when creating to set them the same

### "Certificate expired"

**Solution**:
1. Check expiration: `keytool -list -v -keystore udp-trigger-release.jks`
2. You can continue using expired keystore for app updates
3. New apps should use a new keystore

---

## Security Checklist

- [ ] Keystore file is **NOT** in git repository
- [ ] Keystore file is in `.gitignore`
- [ ] Keystore has strong password (12+ characters)
- [ ] Keystore is backed up in 2+ secure locations
- [ ] Passwords stored in password manager
- [ ] Only `KEYSTORE_BASE64` in GitHub Secrets (not raw file)
- [ ] File permissions are restrictive (`chmod 600`)
- [ ] Backup includes password hints (not actual passwords)

---

## .gitignore Entry

Ensure your keystore is ignored:

```gitignore
# Keystore files
*.jks
*.keystore
keystore.jks

# Signing configuration
local.properties
signing.properties
```

---

## Recovery

### If Keystore is Lost

1. **You cannot update your existing app** on Google Play
2. Options:
   - Contact Google Play Support (limited help)
   - Publish as a new app with new package name
   - Users will need to uninstall old app

### If Password is Lost

1. Try common passwords from password manager
2. Check for backup documentation
3. Use `-storetype PKCS12` if you remember the format

### If You Want to Change Keystore

Google Play allows **key rotation** starting August 2021:

1. Generate new keystore
2. Upload to Play Console under **Setup** → **App signing**
3. Play Console will manage the transition
4. Old users get updated seamlessly

**Note**: This requires the old keystore to be available for one-time verification.

---

## Quick Reference

### Generate Keystore
```bash
keytool -genkey -v -keystore udp-trigger-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias udp-trigger-key
```

### List Keys in Keystore
```bash
keytool -list -v -keystore udp-trigger-release.jks
```

### Build with Keystore
```bash
export KEYSTORE_PATH=udp-trigger-release.jks
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=udp-trigger-key
export KEY_PASSWORD=your_password
./gradlew bundleRelease
```

### Verify Signature
```bash
jarsigner -verify -verbose app/build/outputs/apk/release/app-release.apk
```

---

**Last Updated**: January 22, 2026
