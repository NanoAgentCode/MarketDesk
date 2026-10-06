$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$env:JAVA_HOME = 'D:\Program Files\jdk\17'
$env:ANDROID_HOME = Join-Path $projectDir '.tools\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectDir '.tools\gradle-cache'
$gradleDir = Join-Path $projectDir '.tools\gradle-8.9'
if (-not (Test-Path (Join-Path $gradleDir 'bin\gradle.bat'))) {
    New-Item -ItemType Directory -Force (Join-Path $projectDir '.tools') | Out-Null
    $zipPath = Join-Path $projectDir '.tools\gradle-8.9-bin.zip'
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $zipPath
    Expand-Archive -LiteralPath $zipPath -DestinationPath (Join-Path $projectDir '.tools') -Force
}
& (Join-Path $gradleDir 'bin\gradle.bat') -p $projectDir assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Check JDK17, Android SDK35 and network.' }
Write-Host "APK: $projectDir\app\build\outputs\apk\debug\app-debug.apk"
