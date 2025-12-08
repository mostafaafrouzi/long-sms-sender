# PowerShell script to create keystore for Long SMS Sender
# This script creates a keystore file and provides instructions for GitHub Secrets

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Long SMS Sender - Keystore Generator" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if JAVA_HOME is set
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    Write-Host "JAVA_HOME is not set. Trying to find JDK..." -ForegroundColor Yellow
    
    # Common JDK locations
    $possiblePaths = @(
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-11",
        "C:\Program Files\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Android\Sdk\jbr"
    )
    
    foreach ($path in $possiblePaths) {
        if (Test-Path "$path\bin\keytool.exe") {
            $javaHome = $path
            Write-Host "Found JDK at: $javaHome" -ForegroundColor Green
            break
        }
    }
    
    if (-not $javaHome) {
        Write-Host "ERROR: Could not find JDK. Please set JAVA_HOME or install JDK 17+" -ForegroundColor Red
        Write-Host ""
        Write-Host "You can also create the keystore manually using Android Studio:" -ForegroundColor Yellow
        Write-Host "1. Open Android Studio" -ForegroundColor Yellow
        Write-Host "2. Build > Generate Signed Bundle / APK" -ForegroundColor Yellow
        Write-Host "3. Create new keystore" -ForegroundColor Yellow
        Write-Host ""
        exit 1
    }
}

$keytoolPath = "$javaHome\bin\keytool.exe"
if (-not (Test-Path $keytoolPath)) {
    Write-Host "ERROR: keytool.exe not found at: $keytoolPath" -ForegroundColor Red
    exit 1
}

Write-Host "Using keytool from: $keytoolPath" -ForegroundColor Green
Write-Host ""

# Keystore configuration
$keystoreFile = "long-sms-sender-release.jks"
$alias = "long-sms-sender-key"
$validity = 10000  # ~27 years

# Generate random passwords
$storePassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})
$keyPassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object {[char]$_})

Write-Host "Creating keystore..." -ForegroundColor Cyan
Write-Host "Keystore file: $keystoreFile" -ForegroundColor White
Write-Host "Alias: $alias" -ForegroundColor White
Write-Host "Validity: $validity days" -ForegroundColor White
Write-Host ""

# Create keystore
$dname = "CN=Mostafa Afrouzi, OU=Development, O=Afrouzi, L=Tehran, ST=Tehran, C=IR"

$arguments = @(
    "-genkey",
    "-v",
    "-keystore", $keystoreFile,
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", $validity.ToString(),
    "-alias", $alias,
    "-storepass", $storePassword,
    "-keypass", $keyPassword,
    "-dname", "`"$dname`""
)

$process = Start-Process -FilePath $keytoolPath -ArgumentList $arguments -Wait -NoNewWindow -PassThru

if ($process.ExitCode -ne 0) {
    Write-Host "ERROR: Failed to create keystore" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $keystoreFile)) {
    Write-Host "ERROR: Keystore file was not created" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Keystore created successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Convert keystore to base64
Write-Host "Converting keystore to base64..." -ForegroundColor Cyan
$keystoreBytes = [System.IO.File]::ReadAllBytes($keystoreFile)
$keystoreBase64 = [System.Convert]::ToBase64String($keystoreBytes)

# Save credentials to a secure file (not in git)
$credentialsFile = "keystore-credentials.txt"
$credentials = @"
KEYSTORE INFORMATION
====================

Keystore File: $keystoreFile
Alias: $alias
Validity: $validity days

STORE PASSWORD:
$storePassword

KEY PASSWORD:
$keyPassword

KEYSTORE BASE64 (for GitHub Secret KEYSTORE_BASE64):
$keystoreBase64

IMPORTANT: Keep this file secure and do NOT commit it to git!
"@

$credentials | Out-File -FilePath $credentialsFile -Encoding UTF8

Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "CREDENTIALS SAVED TO: $credentialsFile" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Cyan
Write-Host "1. Add the following secrets to GitHub:" -ForegroundColor White
Write-Host "   - KEYSTORE_BASE64: (see $credentialsFile)" -ForegroundColor White
Write-Host "   - KEYSTORE_PASSWORD: $storePassword" -ForegroundColor White
Write-Host "   - KEY_PASSWORD: $keyPassword" -ForegroundColor White
Write-Host "   - KEY_ALIAS: $alias" -ForegroundColor White
Write-Host ""
Write-Host "2. Go to: https://github.com/mostafaafrouzi/long-sms-sender/settings/secrets/actions" -ForegroundColor White
Write-Host ""
Write-Host "3. Add each secret (click 'New repository secret')" -ForegroundColor White
Write-Host ""
Write-Host "4. Keep $credentialsFile and $keystoreFile secure!" -ForegroundColor Red
Write-Host "   DO NOT commit these files to git!" -ForegroundColor Red
Write-Host ""
Write-Host "5. After adding secrets, delete $credentialsFile for security" -ForegroundColor Yellow
Write-Host ""

