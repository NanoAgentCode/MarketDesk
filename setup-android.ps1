$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Program Files\jdk\17'
$sdkDir = Join-Path $PSScriptRoot '.tools\android-sdk'
$toolsDir = Join-Path $sdkDir 'cmdline-tools'
New-Item -ItemType Directory -Force $toolsDir | Out-Null
if (-not (Test-Path (Join-Path $toolsDir 'latest\bin\sdkmanager.bat'))) {
    Expand-Archive -LiteralPath (Join-Path $PSScriptRoot '.tools\android-tools.zip') -DestinationPath (Join-Path $PSScriptRoot '.tools\android-extract') -Force
    Move-Item -LiteralPath (Join-Path $PSScriptRoot '.tools\android-extract\cmdline-tools') -Destination (Join-Path $toolsDir 'latest')
}
if (-not (Test-Path (Join-Path $PSScriptRoot '.tools\gradle-8.9\bin\gradle.bat'))) {
    Expand-Archive -LiteralPath (Join-Path $PSScriptRoot '.tools\gradle-8.9-bin.zip') -DestinationPath (Join-Path $PSScriptRoot '.tools') -Force
}
$manager = Join-Path $toolsDir 'latest\bin\android.exe'
foreach ($package in @('platform-tools', 'platforms;android-35', 'build-tools;35.0.0')) {
    & $manager --no-metrics "--sdk=$sdkDir" sdk install $package
    if ($LASTEXITCODE -ne 0) { throw "SDK install failed: $package" }
}
Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local.properties') -Value ('sdk.dir=' + $sdkDir.Replace('\','/')) -Encoding ascii
Write-Host 'Android SDK ready'
