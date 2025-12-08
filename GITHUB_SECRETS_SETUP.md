# راهنمای تنظیم GitHub Secrets برای Sign کردن خودکار APK

## 📋 مراحل تنظیم

### 1️⃣ ایجاد Keystore (انجام شده ✅)

Keystore با موفقیت ایجاد شد:
- **فایل:** `long-sms-sender-release.jks`
- **Credentials:** `keystore-credentials.txt`

### 2️⃣ افزودن Secrets به GitHub

1. به صفحه Secrets بروید:
   ```
   https://github.com/mostafaafrouzi/long-sms-sender/settings/secrets/actions
   ```

2. روی **"New repository secret"** کلیک کنید

3. چهار Secret زیر را اضافه کنید:

   #### Secret 1: `KEYSTORE_BASE64`
   - **Name:** `KEYSTORE_BASE64`
   - **Value:** محتوای base64 از فایل `keystore-credentials.txt` (بخش KEYSTORE BASE64)
   - **نکته:** تمام محتوای base64 را کپی کنید (یک خط طولانی)

   #### Secret 2: `KEYSTORE_PASSWORD`
   - **Name:** `KEYSTORE_PASSWORD`
   - **Value:** رمز عبور Store از فایل `keystore-credentials.txt`

   #### Secret 3: `KEY_PASSWORD`
   - **Name:** `KEY_PASSWORD`
   - **Value:** رمز عبور Key از فایل `keystore-credentials.txt`

   #### Secret 4: `KEY_ALIAS`
   - **Name:** `KEY_ALIAS`
   - **Value:** `long-sms-sender-key`

### 3️⃣ بررسی تنظیمات

پس از افزودن همه Secrets، می‌توانید با ایجاد یک Tag جدید، Release را تست کنید:

```bash
git tag -a v1.0.1 -m "Test release with signing"
git push origin v1.0.1
```

GitHub Actions به صورت خودکار:
- ✅ APK را build می‌کند
- ✅ با keystore شما sign می‌کند
- ✅ Release ایجاد می‌کند
- ✅ APK signed را به Release ضمیمه می‌کند

### 4️⃣ امنیت

⚠️ **مهم:** پس از افزودن Secrets به GitHub:
1. فایل `keystore-credentials.txt` را حذف کنید
2. فایل `long-sms-sender-release.jks` را در جای امن نگه دارید
3. این فایل‌ها در `.gitignore` هستند و به git اضافه نمی‌شوند

### 5️⃣ بررسی Logs

برای بررسی اینکه signing موفق بوده است، به Actions بروید:
```
https://github.com/mostafaafrouzi/long-sms-sender/actions
```

در Logs، باید این پیام را ببینید:
```
APK signed successfully!
Verifying signature...
```

---

## 🔧 عیب‌یابی

### مشکل: APK unsigned است
- بررسی کنید که همه 4 Secret اضافه شده باشند
- نام‌های Secret باید دقیقاً مطابق باشند (حساس به حروف بزرگ/کوچک)

### مشکل: خطای 403 در GitHub Actions
- بررسی کنید که `permissions: contents: write` در workflow وجود دارد ✅

### مشکل: خطای signing
- بررسی کنید که رمزهای عبور درست کپی شده باشند
- بررسی کنید که KEY_ALIAS درست باشد

---

**توجه:** این راهنما یک بار استفاده می‌شود. پس از تنظیم، می‌توانید آن را حذف کنید.

