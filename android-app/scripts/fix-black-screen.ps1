param(
    [string]$PackageName = "com.pfe.gestionetudiantmobile",
    [string]$ActivityName = ".ui.auth.LoginActivity"
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    $candidates = @(@(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) })

    if ($candidates.Count -gt 0) {
        return [string]$candidates[0]
    }

    $pathCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($pathCommand) {
        return $pathCommand.Source
    }

    throw "adb.exe introuvable. Verifiez que le SDK Android est installe."
}

$adb = Find-Adb

Write-Host "ADB: $adb"
Write-Host "Verification de l'emulateur..."

$devices = & $adb devices
$connectedDevice = $devices | Select-String -Pattern "device$" | Select-Object -First 1
if (-not $connectedDevice) {
    throw "Aucun emulateur/appareil Android connecte. Lancez l'emulateur puis relancez ce script."
}

Write-Host "Nettoyage du mode debugger..."
& $adb shell am clear-debug-app | Out-Null
& $adb shell settings delete global debug_app 2>$null | Out-Null
& $adb shell settings put global wait_for_debugger 0 2>$null | Out-Null

Write-Host "Reveil/deverrouillage de l'emulateur..."
& $adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Start-Sleep -Milliseconds 500
& $adb shell input keyevent 82 | Out-Null

Write-Host "Arret de l'ancien process app..."
& $adb shell am force-stop $PackageName | Out-Null
Start-Sleep -Milliseconds 500

Write-Host "Relance de l'application..."
& $adb shell am start -n "$PackageName/$ActivityName" | Out-Host

Write-Host ""
Write-Host "Termine. Si Android Studio affichait un ecran noir, l'app devrait maintenant apparaitre."
