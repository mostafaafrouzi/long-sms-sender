# Long SMS Sender | ارسال پیامک بلند

[🇮🇷 فارسی](README.fa.md) | [🇺🇸 English](README.md)

![Long SMS Sender](https://github.com/mostafaafrouzi/long-sms-sender/raw/main/long-sms-sender-en.png)

A production-grade Android application that allows users to send very long text messages as real multi-part SMS, **absolutely preventing conversion to MMS**.

---

## 📱 What this app does

Long SMS Sender helps you to:

- Send **unlimited-length** text as real **multi-part SMS** (not MMS).
- **Avoid automatic MMS** that many default SMS apps trigger for long text.
- Pick recipients from **contacts** (search, multi-select) or type **multiple numbers** (multi-line).
- Use **recipient groups**: save, load, and delete named lists of numbers.
- Choose **which SIM** to use on **dual-SIM** phones.
- **Schedule** messages: pick date and time, confirm, optional **battery** hint; manage from the **toolbar** (clock)—cancel, send now, or reschedule.
- Use **prepared message templates** next to the message field.
- **Share** text or numbers into the app from other apps (`ACTION_SEND`, `sms` / `smsto` links).
- Send to one or many recipients with **confirmation**; for long jobs: **pause**, **resume**, or **cancel** the queue.
- See a live **segment counter**, detailed **send status**, and **share a report**.

**Interface:** full **Persian** and **English** with a quick language switch; **dark / light / system** theme; **RTL**; layouts for **phones and tablets**.

---

## 📋 Features

### Core (all versions)

- ✅ Unlimited-length SMS as multi-part messages  
- ✅ Strong focus on avoiding unwanted MMS for long text  
- ✅ Contact selection (single / multiple / all) with search; correct behavior when one contact has **several numbers**  
- ✅ Manual phone input (**multi-line**)  
- ✅ **Paste** button  
- ✅ **Real-time SMS segment** counter  
- ✅ **Send status** with detailed dialogs and **shareable report**  
- ✅ **Bulk send confirmation**  
- ✅ **Progress** dialog for long sends; **pause**, **resume**, or **cancel** the send queue  
- ✅ **Persian & English**; quick language switch (EN / فا)  
- ✅ **RTL** layout  
- ✅ **Dark**, **light**, and **system** theme; theme change feedback  
- ✅ **Alphabetical** fast scroller for contacts  
- ✅ **Tablet** & **landscape** support  
- ✅ **No ads**, no analytics, no tracking  
- ✅ **No `INTERNET` permission** (works fully offline)  

### New in v1.1.0

- ✅ **SIM selection** (default SIM or a specific subscription on multi-SIM devices)  
- ✅ **Recipient groups** (save, load, delete)  
- ✅ **Scheduled SMS** with confirmation; optional **battery optimization** request; **toolbar** manager: cancel, send now, reschedule  
- ✅ **Prepared message templates** (create, insert, manage)  
- ✅ **Share into the app** (`ACTION_SEND` for text, `ACTION_SENDTO` for `sms` / `smsto` / `mms` / `mmsto` where applicable)  
- ✅ **Locale fixes**: SIM labels, date/time pickers, segment and recipient **strings** stay aligned after switching language (no need to kill the app)  
- ✅ **About** website links with **UTM** parameters  
- ✅ **WorkManager** as a fallback when **exact alarms** are restricted (scheduled send)  

---

## 🛡️ Why it prevents MMS

This app uses **`SmsManager.sendMultipartTextMessage()`** which:

- Sends as **true multi-part SMS** (not MMS)  
- Uses **no subject line**  
- Has **no attachments**  
- Avoids patterns that often trigger MMS on some devices  

Unlike many default messengers that turn long text into MMS (cost and carrier support issues), this app keeps content on the **SMS** path.

---

## 🔒 Privacy

**The app does not collect, store, or upload your personal data.**

- No internet permission; fully **offline**  
- No analytics, no ads, no third-party trackers  
- SMS is sent from **your device** only  
- Contacts are read **only** when you use the contact picker, **on device**  
- No backend servers; “About” content is **embedded** (no extra network calls for that text)  

---

## 🔐 Permissions (when they are used)

| Permission | Why | When |
|------------|-----|------|
| `SEND_SMS` | Send SMS | When you send, or when a schedule/queue runs |
| `READ_CONTACTS` | Pick numbers from contacts | When you open the contact dialog |
| `READ_PHONE_STATE` | List SIMs / labels (e.g. SIM 1 / 2) | When you open **SIM selection** (optional) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | More reliable **scheduled** delivery | Only in the **scheduling** flow, optional system dialog |

Denying contacts or phone state: you can still use **manual numbers** and usually **default SIM** sending.

---

## 📥 Get the app (end users)

- **GitHub Releases (APK):** [github.com/mostafaafrouzi/long-sms-sender/releases](https://github.com/mostafaafrouzi/long-sms-sender/releases)  
- **CafeBazaar (Iran):** [cafebazaar.ir/app/com.afrouzi.longsmssender](https://cafebazaar.ir/app/com.afrouzi.longsmssender)  

On the device, allow **install from unknown sources** (or your browser’s install permission) if you sideload the APK from GitHub.

---

## 📜 Changelog

### v1.1.0 (current)

- SIM selection (default or specific SIM on multi-SIM devices).  
- Recipient groups: save, load, delete.  
- Scheduled SMS: date/time, confirmation dialog, optional battery dialog; management from the **clock** icon in the toolbar (cancel, send now, edit/reschedule).  
- Prepared message templates.  
- Share / intent integration: share text in, open `sms` / `smsto` (and related) links.  
- Send queue: pause, resume, cancel; improved result dialog and share report.  
- Contact UI: **one row per number** when a contact has multiple numbers.  
- Locale: SIM button, pickers, counters refresh correctly after **EN ⟷ فا** switch.  
- About links: UTM tags.  
- Scheduling: **WorkManager** fallback if exact alarm is not available.  

### v1.0.0

- First public release: long multi-part SMS, contacts, bulk confirm, bilingual UI, dark mode, no internet permission.

---

# 🧰 For developers & maintainers

The sections below are for **building from source**, **local signing**, and **GitHub Actions** releases.

---

## 🛠️ Technical stack

- **Language:** Kotlin  
- **UI:** XML layouts (ViewBinding), Material components  
- **Architecture:** MVVM (ViewModel, LiveData)  
- **Min SDK:** 23 (Android 6.0)  
- **Target / compile SDK:** 34  
- **Build:** Gradle with Kotlin DSL (`app/build.gradle.kts`)  
- **Other:** WorkManager (scheduled SMS fallback), `AlarmManager` (exact schedule when allowed)  

---

## 🏗️ How to build (local)

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer (recommended)  
- **JDK** 17+  
- **Android SDK** 34 (install via SDK Manager)  
- **Gradle** wrapper included (uses compatible Gradle 8.x)  

### Clone the repository

```bash
git clone https://github.com/mostafaafrouzi/long-sms-sender.git
cd long-sms-sender
```

### Open in Android Studio

1. **File → Open**  
2. Select the `long-sms-sender` folder  
3. Wait for Gradle sync  

### Debug build (no signing config required)

```bash
./gradlew assembleDebug
```

Or: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Where the APKs are

After a successful build:

```text
Debug:   app/build/outputs/apk/debug/app-debug.apk
Release: app/build/outputs/apk/release/app-release-unsigned.apk
```

> **Note:** The default `release` build in this repo is **unsigned** in Gradle unless you add a `signingConfig`. **GitHub Actions** signs the release APK in CI when secrets are set (see below).

---

## 🔏 Local release signing (optional)

If you want a **signed** `assembleRelease` on your machine, add a keystore and wire `signingConfigs` in `app/build.gradle.kts` (not committed in this template—keep keys private).

**1. Create a keystore (once):**

```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
```

**2. Create `keystore.properties` in the project root (do not commit):**

```properties
storeFile=my-release-key.jks
storePassword=your-store-password
keyAlias=my-key-alias
keyPassword=your-key-password
```

**3. Configure `signingConfigs` in Gradle** to read this file, then:

```bash
./gradlew assembleRelease
```

Use the **same** keystore as Google Play / CafeBazaar if you want **in-place upgrades** for users.

---

## 📥 Install from a built APK (device)

- **Android 6.0+** (API 23+)  
- A device that can send **SMS** (SIM / plan)  
- Enable **install from unknown sources** (or per-app install permission) if you copy the APK manually  

---

## 🚀 GitHub Actions: automated release

The workflow **`.github/workflows/release.yml`** runs when you **push a version tag** matching `v*`.

### What it does (summary)

- Runs `./gradlew assembleRelease`  
- If GitHub **Secrets** are set, **decodes** the keystore, **signs** the APK with `apksigner`  
- Creates a **GitHub Release** and uploads the **`.apk`**  

### Create and push a tag (example: v1.1.0)

```bash
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin v1.1.0
```

### Required repository secrets (for signed CI builds)

| Secret | Role |
|--------|------|
| `KEYSTORE_BASE64` | Your `.jks` file, **entire file** as **one line** of base64 |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Private key password |
| `KEY_ALIAS` | Key alias in the keystore |

**Encode keystore to base64 (examples):**

```bash
# Linux
base64 -w 0 my-release-key.jks

# macOS
base64 -i my-release-key.jks
```

Paste the result into `KEYSTORE_BASE64` in: **Repository → Settings → Secrets and variables → Actions**.

> Use the **same** keystore as your **store** releases so OTA updates **replace** the old app instead of installing a second package.

If secrets are **missing**, the workflow may still produce an APK, but it will **not** be signed with your store key.

---

## 🤝 Contributing

Contributions are welcome—open a Pull Request on GitHub.

## 📄 License

This project is open source under the [MIT License](LICENSE).

## 👤 Developer

**Mostafa Afrouzi**

- [GitHub](https://github.com/mostafaafrouzi) — [github.com/mostafaafrouzi](https://github.com/mostafaafrouzi)  
- [LinkedIn](https://linkedin.com/in/mostafaafrouzi) — [linkedin.com/in/mostafaafrouzi](https://linkedin.com/in/mostafaafrouzi)  
- Website (Persian, UTM) — [afrouzi.ir](https://afrouzi.ir/?utm_source=com.afrouzi.longsmssender&utm_medium=application&utm_campaign=portfolio)  
- Website (English, UTM) — [afrouzi.ir/en](https://afrouzi.ir/en/?utm_source=com.afrouzi.longsmssender&utm_medium=application&utm_campaign=portfolio)  

## ⚠️ Disclaimer

Sending SMS may incur **operator charges**. The developer is not responsible for any fees or delivery issues caused by carriers or device settings.

## 📞 Support

- [GitHub Issues](https://github.com/mostafaafrouzi/long-sms-sender/issues)  

---

**Made with ❤️ for people who need long SMS without unwanted MMS conversion.**
