# Builds the end-user downloads into dist\:
#   Tel2Wheel-Server.exe  - self-contained single file, no .NET install needed
#   Tel2Wheel.apk         - Android app
# Usage: powershell -ExecutionPolicy Bypass -File .\build-release.ps1

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$dist = Join-Path $root 'dist'
$publish = Join-Path $root 'pc\Tel2Wheel.Server\bin\publish-win-x64'
New-Item -ItemType Directory -Force $dist | Out-Null

dotnet publish "$root\pc\Tel2Wheel.Server" -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true -o $publish
if ($LASTEXITCODE) { throw 'dotnet publish failed' }
Copy-Item "$publish\Tel2Wheel.Server.exe" "$dist\Tel2Wheel-Server.exe" -Force

Push-Location "$root\android"
try {
    .\gradlew.bat assembleRelease
    if ($LASTEXITCODE) { throw 'gradle build failed' }
} finally {
    Pop-Location
}
Copy-Item "$root\android\app\build\outputs\apk\release\app-release.apk" "$dist\Tel2Wheel.apk" -Force

Get-ChildItem $dist | Format-Table Name, @{ n = 'MB'; e = { [math]::Round($_.Length / 1MB, 1) } }
