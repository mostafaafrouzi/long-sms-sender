# Long SMS Sender | ارسال پیامک بلند

[🇮🇷 فارسی](README.fa.md) | [🇺🇸 English](README.md)

![Long SMS Sender](long-sms-sender.png)

A production-grade Android application that allows users to send very long text messages as real multi-part SMS, **absolutely preventing conversion to MMS**.

## 📱 What This App Does

Long SMS Sender enables you to:
- Send **unlimited length** text messages as **multi-part SMS**
- **Prevent automatic MMS conversion** that carriers often trigger
- Select recipients from contacts or enter phone numbers manually (multi-line), with search and multi-select
- **Recipient groups**: save, load, and reuse named lists of numbers
- **SIM selection** on dual-SIM devices (default SIM or a chosen subscription)
- **Scheduled SMS**: pick date & time, confirm, optional battery optimization hint; manage from the toolbar—cancel, send now, or edit/reschedule
- **Prepared message templates** next to the message field (save, insert, manage)
- **Share into the app** from other apps (plain text and `sms`/`smsto` links)
- Send to single or multiple recipients with confirmation; **pause / resume / cancel** long queues
- Real-time SMS segment counter; detailed send status and shareable report
- Full Persian (فارسی) and English support with instant language switch (labels and pickers stay in sync)
- Dark / light / system theme, RTL, tablet layouts

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

The app requests **runtime permissions** only when needed:

### 1. `SEND_SMS`
- **Why**: To send SMS messages.
- **When**: When you send immediately, from a scheduled job, or from the queue.

### 2. `READ_CONTACTS`
- **Why**: To pick recipients from your contacts.
- **When**: When you open the contact picker.

### 3. `READ_PHONE_STATE`
- **Why**: To list active SIM cards and show SIM labels (e.g. SIM 1 / SIM 2) so you can choose which SIM to use.
- **When**: When you open SIM selection (optional—if denied, only “Default SIM” may be available).

### 4. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **Why**: So scheduled messages are more likely to fire on time on aggressive power-saving devices.
- **When**: Only when you use scheduling and the app shows the optional system dialog (you can deny and still schedule; reliability may vary by device).

No permission is used for analytics or advertising. Denying contacts or phone state still allows manual numbers and default SIM sending in most cases.

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

1. **Create and push a tag** (example for v1.1.0):
   ```bash
   git tag -a v1.1.0 -m "Release version 1.1.0"
   git push origin v1.1.0
   ```

2. **GitHub Actions will automatically**:
   - Build the release APK
   - Sign it (if keystore is configured)
   - Create a GitHub Release
   - Attach the APK to the release

### Configuring Signing (recommended for upgrades)

Repository secrets expected by `.github/workflows/release.yml`:

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Your `.jks` file, **base64-encoded** (one line) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |
| `KEY_ALIAS` | Key alias |

Encode example (Linux/macOS):

```bash
base64 -w 0 my-release-key.jks   # Linux
base64 -i my-release-key.jks     # macOS
```

Paste the output into `KEYSTORE_BASE64`. **Use the same keystore as Cafe Bazaar / previous installs** so updates install over the old app.

If these secrets are missing, the workflow still uploads an APK built from `assembleRelease`, but it will **not** match your Play/Cafe signing key unless you sign locally.

## 📸 Screenshots

<!-- Add screenshots here -->
![Main Screen](screenshots/main.png)
![Contact Selection](screenshots/contacts.png)
![Dark Mode](screenshots/dark.png)

## 🛒 Download from CafeBazaar

Download the app from CafeBazaar (Iranian app store):

🔗 [Download from CafeBazaar](https://cafebazaar.ir/app/com.afrouzi.longsmssender)

*Note: Link will be available after app submission to CafeBazaar*

## 🛠️ Technical Details

- **Language**: Kotlin 100%
- **UI**: XML layouts (no Jetpack Compose)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle with Kotlin DSL

## 📜 Changelog

### v1.1.0 (current)

**English**

- SIM selection (default / per-slot on multi-SIM).
- Recipient groups (save, load, delete).
- Scheduled SMS with confirmation, battery optimization prompt, toolbar manager (cancel / send now / reschedule).
- Prepared message templates.
- Share targets: `ACTION_SEND` (text) and `ACTION_SENDTO` (`sms`, `smsto`, `mms`/`mmsto` schemes).
- Bulk send queue: pause, resume, cancel; improved status dialog and share report.
- Contact selection fixes for multiple numbers per contact.
- Locale fixes: SIM labels, date/time pickers, segment & recipient strings update when switching EN/فا without killing the app.
- About links include UTM parameters.
- Uses WorkManager as fallback when exact alarms are restricted.

**فارسی**

- انتخاب سیم‌کارت (پیش‌فرض یا اسلات مشخص).
- گروه‌های گیرنده (ذخیره، بارگذاری، حذف).
- پیام زمان‌بندی‌شده با تأیید، درخواست بهینه‌سازی باتری، مدیریت از نوار (لغو / فوری / زمان جدید).
- پیام‌های آماده (قالب).
- ورودی از اشتراک‌گذاری و لینک‌های پیامک.
- صف ارسال: توقف، ادامه، لغو؛ بهبود گزارش ارسال.
- رفع انتخاب چند شماره برای یک مخاطب.
- هماهنگی زبان با برچسب سیم، تقویم و شمارنده بدون بستن برنامه.
- لینک‌های درباره با UTM؛ پشتیبان WorkManager برای زمان‌بندی.

### v1.0.0

Initial public release: multi-part long SMS, contacts, bulk confirmation, bilingual UI, dark mode, no internet permission.

---

## 📋 Features

### Core app capabilities

- ✅ Send unlimited length SMS as multi-part messages
- ✅ Prevent MMS conversion for long text
- ✅ Contact selection (single / multiple / all) with search; correct behavior when one contact has several numbers
- ✅ Manual phone number input (multi-line support)
- ✅ Fast paste button
- ✅ Real-time SMS segment counter
- ✅ Send status tracking with detailed dialogs and shareable report
- ✅ Bulk send confirmation
- ✅ Progress dialog for long sends; pause, resume, or cancel the send queue
- ✅ Persian & English language support
- ✅ Quick language switch (EN / فا)
- ✅ RTL (right-to-left) layout support
- ✅ Dark mode, light mode, and system default theme
- ✅ Theme change toast / feedback
- ✅ Alphabetical fast scroller for contacts
- ✅ Tablet & landscape support
- ✅ No ads, no analytics, no tracking
- ✅ No internet permission required

### Added in v1.1.0

- ✅ SIM selection (default SIM or a chosen slot on dual-SIM devices)
- ✅ Recipient groups (save, load, delete)
- ✅ Scheduled SMS with confirmation; optional battery optimization prompt; toolbar manager (cancel, send now, reschedule)
- ✅ Prepared message templates next to the message field
- ✅ Share into the app (`ACTION_SEND`, `sms` / `smsto` links)
- ✅ Locale fixes: SIM labels, date/time pickers, and counters stay aligned after switching language

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

