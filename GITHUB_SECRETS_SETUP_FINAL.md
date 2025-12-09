# راهنمای نهایی تنظیم GitHub Secrets برای Sign کردن APK

## 📋 خلاصه وضعیت Keystore فعلی

### بررسی Keystore با keytool

**دستور بررسی:**
```powershell
keytool -list -v -keystore "long-sms-sender-release.jks" -storepass "dHJDkvayl26SKBLcIFZgE0zCGqtQANRs"
```

**نتایج بررسی:**
- ✅ **Keystore Type:** PKCS12
- ✅ **Alias:** `long-sms-sender-key`
- ✅ **Status:** Keystore سالم و قابل استفاده است
- ✅ **Certificate:** معتبر تا سال 2053

**نکته مهم:** در keystore نوع PKCS12، `KEYSTORE_PASSWORD` و `KEY_PASSWORD` باید **۱۰۰٪ یکسان** باشند. اگر متفاوت باشند، خطای "Wrong password" و "padding error" رخ می‌دهد.

---

## 🔧 مرحله 1: Encode کردن Keystore به Base64

### اجرای اسکریپت PowerShell

**دستور اجرا:**
```powershell
powershell -ExecutionPolicy Bypass -File .\encode-keystore.ps1
```

**نتیجه:**
- فایل `keystore-base64.txt` در روت پروژه ایجاد می‌شود
- این فایل شامل یک رشته Base64 تک‌خطی است
- طول این رشته حدود 2000+ کاراکتر خواهد بود

**⚠️ مهم:**
- محتوای `keystore-base64.txt` باید به صورت **یک خط کامل** (بدون خطوط جدید) کپی شود
- این فایل به `.gitignore` اضافه شده و commit نمی‌شود

---

## 🔐 مرحله 2: تنظیم GitHub Secrets

### آدرس صفحه Secrets:
```
https://github.com/mostafaafrouzi/long-sms-sender/settings/secrets/actions
```

### مراحل تنظیم:

#### 1️⃣ حذف Secrets قبلی (اگر وجود دارند)
- به صفحه Secrets بروید
- هر Secret قبلی را پیدا کنید و حذف کنید:
  - `KEYSTORE_BASE64` (اگر وجود دارد)
  - `KEYSTORE_PASSWORD` (اگر وجود دارد)
  - `KEY_PASSWORD` (اگر وجود دارد)
  - `KEY_ALIAS` (اگر وجود دارد)

#### 2️⃣ ایجاد Secret جدید: `KEYSTORE_BASE64`
- روی **"New repository secret"** کلیک کنید
- **Name:** `KEYSTORE_BASE64`
- **Value:** 
  - فایل `keystore-base64.txt` را باز کنید
  - **تمام محتوا** را کپی کنید (یک خط طولانی)
  - در فیلد Value پیست کنید
  - **مطمئن شوید که هیچ خط جدیدی اضافه نشده است**
- روی **"Add secret"** کلیک کنید

#### 3️⃣ ایجاد Secret جدید: `KEYSTORE_PASSWORD`
- روی **"New repository secret"** کلیک کنید
- **Name:** `KEYSTORE_PASSWORD`
- **Value:** `dHJDkvayl26SKBLcIFZgE0zCGqtQANRs`
- روی **"Add secret"** کلیک کنید

#### 4️⃣ ایجاد Secret جدید: `KEY_PASSWORD`
- روی **"New repository secret"** کلیک کنید
- **Name:** `KEY_PASSWORD`
- **Value:** `dHJDkvayl26SKBLcIFZgE0zCGqtQANRs`
  - ⚠️ **توجه:** در keystore نوع PKCS12، این مقدار باید **دقیقاً همان** `KEYSTORE_PASSWORD` باشد
- روی **"Add secret"** کلیک کنید

#### 5️⃣ ایجاد Secret جدید: `KEY_ALIAS`
- روی **"New repository secret"** کلیک کنید
- **Name:** `KEY_ALIAS`
- **Value:** `long-sms-sender-key`
- روی **"Add secret"** کلیک کنید

---

## ✅ جدول خلاصه Secrets

| Secret Name | مقدار | توضیحات |
|------------|-------|---------|
| `KEYSTORE_BASE64` | محتوای کامل فایل `keystore-base64.txt` | یک رشته Base64 تک‌خطی (بدون خطوط جدید) |
| `KEYSTORE_PASSWORD` | `dHJDkvayl26SKBLcIFZgE0zCGqtQANRs` | رمز عبور keystore |
| `KEY_PASSWORD` | `dHJDkvayl26SKBLcIFZgE0zCGqtQANRs` | **باید دقیقاً همان KEYSTORE_PASSWORD باشد** (PKCS12 requirement) |
| `KEY_ALIAS` | `long-sms-sender-key` | نام alias در keystore |

---

## 🚀 مرحله 3: تست با ایجاد Release جدید

### ایجاد Tag و Push

```bash
# حذف tag قبلی (اگر وجود دارد)
git tag -d v1.0.2
git push origin :refs/tags/v1.0.2

# ایجاد tag جدید
git tag -a v1.0.2 -m "Release version 1.0.2 - Test signed build"

# Push tag
git push origin v1.0.2
```

### بررسی Workflow

پس از push کردن tag:
1. به صفحه Actions بروید:
   ```
   https://github.com/mostafaafrouzi/long-sms-sender/actions
   ```
2. workflow جدید را پیدا کنید و روی آن کلیک کنید
3. در Logs باید این پیام‌ها را ببینید:
   - ✅ `Keystore decoded successfully`
   - ✅ `Using zipalign: /path/to/zipalign`
   - ✅ `Using apksigner: /path/to/apksigner`
   - ✅ `Signing APK...`
   - ✅ `APK signed successfully!`
   - ✅ `Verifying signature...`

### بررسی Release

پس از اتمام build:
1. به صفحه Releases بروید:
   ```
   https://github.com/mostafaafrouzi/long-sms-sender/releases
   ```
2. Release جدید را پیدا کنید
3. باید فقط یک فایل APK ببینید:
   - ✅ `long-sms-sender-v1.0.2.apk` (signed)
   - ❌ نباید `app-release-unsigned.apk` وجود داشته باشد
   - ❌ نباید `app-release-aligned.apk` وجود داشته باشد

---

## 🔍 عیب‌یابی

### مشکل: "Wrong password" error

**علل احتمالی:**
1. `KEYSTORE_PASSWORD` و `KEY_PASSWORD` متفاوت هستند
   - **راه حل:** در keystore نوع PKCS12، این دو باید یکسان باشند
2. `KEYSTORE_BASE64` به درستی encode نشده
   - **راه حل:** اسکریپت `encode-keystore.ps1` را دوباره اجرا کنید
3. خطوط جدید در `KEYSTORE_BASE64` وجود دارد
   - **راه حل:** مطمئن شوید که Secret یک خط کامل است (بدون Enter)

### مشکل: "Failed to decode keystore"

**علل احتمالی:**
1. `KEYSTORE_BASE64` ناقص است
   - **راه حل:** تمام محتوای فایل `keystore-base64.txt` را کپی کنید
2. Base64 string معتبر نیست
   - **راه حل:** اسکریپت را دوباره اجرا کنید

### مشکل: "Signature verification failed"

**علل احتمالی:**
1. Signing با خطا مواجه شده
   - **راه حل:** Logs را بررسی کنید و مشکل را پیدا کنید
2. APK corrupted
   - **راه حل:** Build را دوباره انجام دهید

---

## 📝 چک‌لیست نهایی

قبل از ایجاد Release، این موارد را بررسی کنید:

- [ ] فایل `keystore-base64.txt` ایجاد شده است
- [ ] محتوای `keystore-base64.txt` یک خط کامل است (بدون خطوط جدید)
- [ ] Secret `KEYSTORE_BASE64` با محتوای کامل فایل تنظیم شده است
- [ ] Secret `KEYSTORE_PASSWORD` تنظیم شده است: `dHJDkvayl26SKBLcIFZgE0zCGqtQANRs`
- [ ] Secret `KEY_PASSWORD` تنظیم شده است: `dHJDkvayl26SKBLcIFZgE0zCGqtQANRs` (همان KEYSTORE_PASSWORD)
- [ ] Secret `KEY_ALIAS` تنظیم شده است: `long-sms-sender-key`
- [ ] همه Secrets قبلی حذف شده‌اند
- [ ] Workflow در `.github/workflows/release.yml` از همین 4 Secret استفاده می‌کند

---

## 🎯 خلاصه دستورات

```powershell
# 1. Encode keystore
powershell -ExecutionPolicy Bypass -File .\encode-keystore.ps1

# 2. بررسی فایل ایجاد شده
Get-Content keystore-base64.txt | Measure-Object -Line

# 3. ایجاد و push tag (بعد از تنظیم Secrets)
git tag -a v1.0.2 -m "Release version 1.0.2 - Test signed build"
git push origin v1.0.2
```

---

**تاریخ ایجاد:** 9 دسامبر 2025  
**آخرین به‌روزرسانی:** 9 دسامبر 2025

