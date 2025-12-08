# Long SMS Sender | ارسال پیامک بلند

[🇮🇷 فارسی](README.fa.md) | [🇺🇸 English](README.md)

![Long SMS Sender](long-sms-sender.png)

A production-grade Android application that allows users to send very long text messages as real multi-part SMS, **absolutely preventing conversion to MMS**.

## 📱 What This App Does

Long SMS Sender enables you to:
- Send **unlimited length** text messages as **multi-part SMS**
- **Prevent automatic MMS conversion** that carriers often trigger
- Select recipients from contacts or enter phone numbers manually
- Send to single or multiple recipients with confirmation
- Real-time SMS segment counter
- Full Persian (فارسی) and English language support
- Dark mode support

## 🛡️ Why It Prevents MMS

This app uses **`SmsManager.sendMultipartTextMessage()`** which:
- ✅ Sends messages as **true multi-part SMS** (not MMS)
- ✅ Uses **no subject line** (null parameter)
- ✅ Includes **no attachments**
- ✅ Avoids emoji patterns that trigger MMS conversion
- ✅ Ensures messages are sent as **standard SMS** regardless of length

Unlike default messaging apps that convert long messages to MMS (which costs more and may not be supported by all carriers), this app guarantees your messages are sent as SMS.

## 🔒 Privacy Policy

**This app does NOT collect, store, or transmit any personal data.**

- ✅ **No internet permission** - App works completely offline
- ✅ No analytics or tracking
- ✅ No ads
- ✅ No data collection
- ✅ All SMS messages are sent directly from your device
- ✅ Contact data is only used locally for recipient selection
- ✅ No data is stored on external servers
- ✅ All information in About dialog is embedded (no network requests)

Your privacy is our priority. All operations happen locally on your device.

## 🔐 Permissions Explained

This app requires only **two permissions**:

### 1. `SEND_SMS` Permission
- **Why**: Required to send SMS messages
- **When used**: Only when you tap the "Send" button
- **What it does**: Sends SMS directly from your device
- **Privacy**: No data is collected or transmitted

### 2. `READ_CONTACTS` Permission
- **Why**: To allow you to select recipients from your contact list
- **When used**: Only when you tap "Load Contacts"
- **What it does**: Reads contact names and phone numbers locally
- **Privacy**: Contact data never leaves your device

Both permissions are requested at runtime with clear explanations. You can deny permissions and still use the app with manual phone number entry.

## 🏗️ How to Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 34
- Gradle 8.2+

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/mostafaafrouzi/long-sms-sender.git
   cd long-sms-sender
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```
   Or use Android Studio's Build menu → Build Bundle(s) / APK(s) → Build APK(s)

4. **Find the APK**:
   - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   - Release APK: `app/build/outputs/apk/release/app-release.apk`

### Building Release APK

For a release build, you'll need to configure signing:

1. Create a keystore (if you don't have one):
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```

2. Create `keystore.properties` in the project root:
   ```properties
   storeFile=my-release-key.jks
   storePassword=your-store-password
   keyAlias=my-key-alias
   keyPassword=your-key-password
   ```

3. Build signed release:
   ```bash
   ./gradlew assembleRelease
   ```

## 📥 How to Install

### From GitHub Releases (Recommended)

1. Go to [Releases](https://github.com/mostafaafrouzi/long-sms-sender/releases)
2. Download the latest APK file
3. On your Android device:
   - Enable "Install from Unknown Sources" in Settings
   - Open the downloaded APK file
   - Follow the installation prompts

### From Source

1. Build the APK (see [How to Build](#-how-to-build))
2. Transfer the APK to your Android device
3. Install using the same method as above

### Requirements

- Android 6.0 (API 23) or higher
- SMS sending capability (requires SIM card with SMS plan)

## 🚀 GitHub Release Usage

This project uses **GitHub Actions** for automated releases:

### Creating a Release

1. **Create and push a tag**:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

2. **GitHub Actions will automatically**:
   - Build the release APK
   - Sign it (if keystore is configured)
   - Create a GitHub Release
   - Attach the APK to the release

### Configuring Signing (Optional)

To enable APK signing in GitHub Actions:

1. Create a keystore file
2. Encode it in base64:
   ```bash
   base64 -i my-release-key.jks | pbcopy
   ```
3. Add GitHub Secrets:
   - `KEYSTORE_FILE`: Base64-encoded keystore file
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key password
   - `KEY_ALIAS`: Key alias

If signing is not configured, the workflow will create an unsigned APK.

## 📸 Screenshots

<!-- Add screenshots here -->
![Main Screen](screenshots/main.png)
![Contact Selection](screenshots/contacts.png)
![Dark Mode](screenshots/dark.png)

## 🛒 Download from CafeBazaar

Download the app from CafeBazaar (Iranian app store):

🔗 [Download from CafeBazaar](https://cafebazaar.ir/app/com.mostafaafrouzi.longsmssender)

*Note: Link will be available after app submission to CafeBazaar*

## 🛠️ Technical Details

- **Language**: Kotlin 100%
- **UI**: XML layouts (no Jetpack Compose)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle with Kotlin DSL

## 📋 Features

- ✅ Send unlimited length SMS as multi-part
- ✅ Prevent MMS conversion
- ✅ Contact selection (single/multiple/all) with search
- ✅ Manual phone number input (multi-line support)
- ✅ Fast paste button
- ✅ Real-time segment counter
- ✅ Send status tracking with detailed dialogs
- ✅ Bulk send confirmation
- ✅ Progress dialog for long sends
- ✅ Persian & English language support
- ✅ Quick language switch button (EN/فا)
- ✅ RTL (Right-to-Left) layout support
- ✅ Dark mode, Light mode, and System default theme
- ✅ Theme change notifications
- ✅ Alphabetical scroller for contacts
- ✅ Tablet & landscape support
- ✅ No ads, no analytics, no tracking
- ✅ No internet permission required

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

## 👤 Developer

**Mostafa Afrouzi**

- GitHub: [@mostafaafrouzi](https://github.com/mostafaafrouzi)

## ⚠️ Disclaimer

This app sends SMS messages using your device's SMS service. Standard operator SMS charges apply. The developer is not responsible for any charges incurred from using this app.

## 📞 Support

For issues, feature requests, or questions:
- Open an issue on [GitHub Issues](https://github.com/mostafaafrouzi/long-sms-sender/issues)
- Check existing issues before creating a new one

---

**Made with ❤️ for users who need to send long SMS messages without MMS conversion**

