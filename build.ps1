param([switch]$Verify)

$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
    }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 or newer installation.' }
$javaExecutable = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
}
$javaVersion = & $javaExecutable -version 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?:version\s+"|openjdk\s+)(\d+)' -or [int]$Matches[1] -lt 17) {
    throw 'JDK 17 or newer is required. Set JAVA_HOME before building.'
}
$env:ANDROID_HOME = Join-Path $projectDir '.tools\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectDir '.tools\gradle-cache'
$gradleDir = Join-Path $projectDir '.tools\gradle-8.9'
if (-not (Test-Path (Join-Path $gradleDir 'bin\gradle.bat'))) {
    New-Item -ItemType Directory -Force (Join-Path $projectDir '.tools') | Out-Null
    $zipPath = Join-Path $projectDir '.tools\gradle-8.9-bin.zip'
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $zipPath
    Expand-Archive -LiteralPath $zipPath -DestinationPath (Join-Path $projectDir '.tools') -Force
}
$tasks = @(':app:assembleDebug')
if ($Verify) { $tasks += @(':app:testDebugUnitTest', ':app:lintDebug') }
& (Join-Path $gradleDir 'bin\gradle.bat') -p $projectDir @tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Check JDK17, Android SDK35 and network.' }
Write-Host "APK: $projectDir\app\build\outputs\apk\debug\app-debug.apk"
