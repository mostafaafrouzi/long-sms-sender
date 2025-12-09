# PowerShell script to encode keystore to Base64
# This script reads the existing keystore and creates a base64-encoded version

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Keystore Base64 Encoder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$keystoreFile = "long-sms-sender-release.jks"
$outputFile = "keystore-base64.txt"

# Check if keystore file exists
if (-not (Test-Path $keystoreFile)) {
    Write-Host "ERROR: Keystore file not found: $keystoreFile" -ForegroundColor Red
    Write-Host "Please make sure the keystore file is in the current directory." -ForegroundColor Yellow
    exit 1
}

Write-Host "Reading keystore file: $keystoreFile" -ForegroundColor Green
$keystoreBytes = [System.IO.File]::ReadAllBytes($keystoreFile)
$fileSize = $keystoreBytes.Length

Write-Host "Keystore size: $fileSize bytes" -ForegroundColor White
Write-Host ""

Write-Host "Encoding to Base64..." -ForegroundColor Green
$base64String = [System.Convert]::ToBase64String($keystoreBytes)

# Verify the base64 string is not empty
if ([string]::IsNullOrEmpty($base64String)) {
    Write-Host "ERROR: Base64 encoding failed" -ForegroundColor Red
    exit 1
}

# Save to file
$base64String | Out-File -FilePath $outputFile -Encoding UTF8 -NoNewline

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Base64 encoding completed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Output file: $outputFile" -ForegroundColor White
Write-Host "Base64 length: $($base64String.Length) characters" -ForegroundColor White
Write-Host ""
Write-Host "IMPORTANT:" -ForegroundColor Yellow
Write-Host "1. The file $outputFile contains the base64-encoded keystore" -ForegroundColor White
Write-Host "2. Copy the ENTIRE content (one long line) to GitHub Secret: KEYSTORE_BASE64" -ForegroundColor White
Write-Host "3. Make sure there are NO line breaks in the secret value" -ForegroundColor White
Write-Host "4. The file will be added to .gitignore automatically" -ForegroundColor White
Write-Host ""

