# Google Play Store API Setup Guide

This guide walks through setting up automated Google Play Store publishing using GitHub Actions and the Google Play Developer API.

---

## Overview

The workflow will:
1. Build a signed App Bundle (AAB) using GitHub Actions
2. Upload the AAB to Google Play Console via API
3. Create a release in the specified track (internal, alpha, beta, production)
4. Optionally create a GitHub Release

---

## Prerequisites

- Google Play Developer account ($25 one-time fee)
- App created in Google Play Console
- GitHub repository with the Android project

---

## Step 1: Create Google Play Service Account

### 1.1 Access Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Sign in with your developer account
3. Select your app (UDP Trigger)

### 1.2 Create Service Account

1. Navigate to **Settings** → **API access**
2. Click **Link a new service account**
3. Click the link to open **Google Cloud Console**

### 1.3 In Google Cloud Console

1. Click **Create Service Account**
2. Enter service account details:
   - **Name**: `github-actions-play-publisher`
   - **Description**: `GitHub Actions Play Store Publisher`
3. Click **Create and Continue**

### 1.4 Grant Permissions

1. Skip granting users access (click **Done**)
2. Click on the newly created service account
3. Go to **Keys** tab
4. Click **Add Key** → **Create New Key**
5. Select **JSON** key type
6. Click **Create**

**IMPORTANT**: The JSON key file will download automatically. Save it securely - you won't be able to download it again!

### 1.5 Enable Play Developer API

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Navigate to **APIs & Services** → **Library**
4. Search for "Google Play Android Developer API"
5. Click on it and click **Enable**

---

## Step 2: Configure Play Console Permissions

### 2.1 Grant Access to Service Account

1. Go back to [Google Play Console](https://play.google.com/console)
2. Navigate to **Settings** → **API access**
3. Find your service account email (looks like `github-actions-play-publisher@project-id.iam.gserviceaccount.com`)
4. Click **Grant Access**
5. Select the following permissions:
   - **Admin** (recommended for full automation) OR
   - **Release Manager** (if you want more control)
6. Click **Invite User**

---

## Step 3: Configure GitHub Secrets

### 3.1 Add Keystore Secrets

First, generate a keystore if you don't have one:

```bash
keytool -genkey -v -keystore udp-trigger-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias udp-trigger-key \
  -dname "CN=UDP Trigger, OU=Development, O=YourCompany, L=City, ST=State, C=US" \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD
```

Then encode the keystore as base64:

```bash
base64 -i udp-trigger-release.jks | pbcopy  # macOS
base64 -i udp-trigger-release.jks | xclip   # Linux
```

### 3.2 Add Secrets to GitHub

1. Go to your GitHub repository
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add the following secrets:

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore file | The entire base64 string from the command above |
| `KEYSTORE_PASSWORD` | Your keystore password | The password you set when creating the keystore |
| `KEY_ALIAS` | `udp-trigger-key` | The alias you used when creating the keystore |
| `KEY_PASSWORD` | Your key password | Usually the same as KEYSTORE_PASSWORD |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Entire JSON key file | The entire content of the downloaded JSON file |

**IMPORTANT**: Do NOT add quotes or escape characters when adding the JSON - paste the raw content.

---

## Step 4: Update Play Store Listing (One-Time)

Before you can publish to production tracks, you need to complete the Play Store listing:

### 4.1 Main Store Listing

1. Go to **Setup** → **Store listing**
2. Upload app icon (512x512 PNG)
3. Upload feature graphic (1024x512 PNG)
4. Add screenshots (min 2)
5. Add short description (80 chars max):
   ```
   Low-latency UDP packet sender with automation, macros, and network discovery tools.
   ```
6. Add full description (see `GOOGLE_PLAY_STORE_GUIDE.md`)

### 4.2 Content Rating

1. Go to **Setup** → **Content rating**
2. Complete the questionnaire:
   - Violence: No
   - Sexual Content: No
   - Profanity: No
   - Controlled Substances: No
   - Gambling: No
   - Hate Speech: No
   - Crimes: No
   - Horror: No
   - Other Mature Themes: No
3. Apply rating

### 4.3 Pricing & Distribution

1. Go to **Setup** → **Pricing & distribution**
2. Select: **Free**
3. Select distribution countries: **All available countries**
4. Check all content guidelines
5. Select device types: **Phones and Tablets**
6. Minimum Android version: **12+**

---

## Step 5: Test Internal Release

Before publishing to production, test with an internal release:

### 5.1 Create Internal Test List

1. Go to **Setup** → **Internal testing**
2. Add tester email addresses (including your own)
3. Create internal test list

### 5.2 Run Workflow

1. Go to **Actions** tab in your GitHub repository
2. Select **Publish to Google Play Store** workflow
3. Click **Run workflow**
4. Select track: `internal`
5. Select status: `completed`
6. Click **Run workflow**

### 5.3 Verify Release

1. Wait for workflow to complete (~5-10 minutes)
2. Go to Google Play Console
3. Navigate to **Testing & feedback** → **Internal testing**
4. Verify your release appears

---

## Step 6: Publish to Production

Once internal testing is successful:

### 6.1 Using GitHub Actions UI

1. Go to **Actions** tab
2. Select **Publish to Google Play Store** workflow
3. Click **Run workflow**
4. Select track: `production`
5. Select status: `completed`
6. Set rollout percentage: `100` (or lower for staged rollout)
7. Click **Run workflow**

### 6.2 Using Git Tags (Automatic)

```bash
# Tag and push a release
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

This will automatically:
- Build the signed AAB
- Upload to Play Store
- Create a GitHub Release
- Deploy to internal track (default)

---

## Workflow Tracks Explained

| Track | Purpose | Requires Testing? | Review Time |
|-------|---------|-------------------|-------------|
| **Internal** | Your private testing | No | Instant |
| **Alpha** | Closed beta testing | No | Instant |
| **Beta** | Open beta testing | No | Instant |
| **Production** | Public release | Yes | Up to 7 days |

### Recommended Rollout Strategy

1. **Internal Testing** - Test yourself
2. **Alpha Testing** - Share with trusted testers
3. **Beta Testing** - Public beta (optional)
4. **Production** - Staged rollout:
   - Start with 1% rollout
   - Monitor crash reports
   - Gradually increase to 100%

---

## Release Status Options

| Status | Description |
|--------|-------------|
| `completed` | Fully released to users in the track |
| `draft` | Saved but not released (for production) |
| `halted` | Halted but still available to existing users |

---

## Monitoring Releases

After publishing, monitor:

### Play Console Dashboard
- **Install metrics** - Track downloads
- **Crash reports** - Fix critical issues
- **ANR reports** - Application Not Responding issues
- **User ratings and reviews** - User feedback

### GitHub Actions
- **Workflow runs** - View publish history
- **Logs** - Debug any issues

---

## Troubleshooting

### Issue: "Authentication failed"

**Solution**:
- Verify `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is correct
- Check service account has proper permissions in Play Console
- Ensure Play Developer API is enabled in Google Cloud

### Issue: "App not found"

**Solution**:
- Verify package name `com.udptrigger` matches your Play Console app
- Ensure app is created in Play Console before first publish

### Issue: "Release failed - missing store listing"

**Solution**:
- Complete all store listing information in Play Console
- Upload at least 2 screenshots
- Add privacy policy URL

### Issue: "Keystore decode failed"

**Solution**:
- Verify `KEYSTORE_BASE64` is properly encoded
- Check passwords match exactly (case-sensitive)
- Ensure keystore was created with correct algorithms

---

## Security Best Practices

1. **Never commit secrets** to the repository
2. **Use environment-specific secrets** for development vs production
3. **Rotate credentials** periodically (especially if compromised)
4. **Limit service account permissions** to only what's needed
5. **Monitor audit logs** in Google Cloud Console
6. **Use branch protection** to control who can trigger releases

---

## Alternative: Using Gradle Play Publisher

For more advanced automation, consider using [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher):

```kotlin
plugins {
    id("com.github.triplet.play") version "3.9.1"
}

play {
    serviceAccountCredentials.set(file("play-store-service-account.json"))
    track.set("internal")
    defaultToAppBundles.set(true)
}
```

This allows publishing directly from Gradle without GitHub Actions.

---

## Quick Reference

### Secrets Checklist

- [ ] `KEYSTORE_BASE64` - Base64-encoded keystore
- [ ] `KEYSTORE_PASSWORD` - Keystore password
- [ ] `KEY_ALIAS` - Key alias (e.g., `udp-trigger-key`)
- [ ] `KEY_PASSWORD` - Key password
- [ ] `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` - Service account JSON

### Play Console Setup Checklist

- [ ] Create app in Play Console
- [ ] Create service account in Google Cloud
- [ ] Enable Play Developer API
- [ ] Grant service account permissions
- [ ] Complete store listing
- [ ] Complete content rating
- [ ] Set pricing and distribution
- [ ] Add internal testers

### First Release Checklist

- [ ] Run internal test release
- [ ] Install and test on internal test devices
- [ ] Fix any critical issues
- [ ] Release to alpha/beta (optional)
- [ ] Release to production with staged rollout
- [ ] Monitor crash reports and user feedback

---

**Last Updated**: January 22, 2026
