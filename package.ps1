# Baut ein Windows-Paket mit eigener, minimaler Java-Laufzeit, damit die JAR ohne installiertes Java startet:
#   build/steam-blacklist-tool-<version>-windows.zip
#     SteamBlacklistTool/Start.bat                 <- Doppelklick startet das Tool
#     SteamBlacklistTool/steam-blacklist-tool.jar
#     SteamBlacklistTool/runtime/                  <- minimale Java-Laufzeit (jlink)
# Benötigt ein JDK 17+ (jlink) und tar (in Windows 10/11 enthalten).
param([string]$Version = '1.0.0')
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'

& (Join-Path $root 'build.ps1') -Version $Version

$dist = Join-Path $build 'dist'
$app = Join-Path $dist 'SteamBlacklistTool'
if (Test-Path $dist) { Remove-Item -Recurse -Force $dist }
New-Item -ItemType Directory -Force $app | Out-Null

# Only the modules the tool needs (jdeps misses java.net.http; jdk.crypto.ec is needed for modern TLS).
$modules = 'java.base,java.desktop,java.prefs,java.net.http,jdk.crypto.ec'
& jlink --add-modules $modules --output (Join-Path $app 'runtime') `
    --strip-debug --no-header-files --no-man-pages --compress=2
if ($LASTEXITCODE -ne 0) { throw 'jlink fehlgeschlagen' }

Copy-Item (Join-Path $build "steam-blacklist-tool-$Version.jar") (Join-Path $app 'steam-blacklist-tool.jar')
Copy-Item (Join-Path $root 'LICENSE') (Join-Path $app 'LICENSE.txt')
# javaw = no console window; "start" lets the batch window close right away.
[IO.File]::WriteAllText((Join-Path $app 'Start.bat'),
    "@echo off`r`nstart `"`" `"%~dp0runtime\bin\javaw.exe`" -jar `"%~dp0steam-blacklist-tool.jar`"`r`n")

$zip = Join-Path $build "steam-blacklist-tool-$Version-windows.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
# Windows' bsdtar writes forward slashes (Compress-Archive in PowerShell 5.1 writes backslashes and fails
# on read-only files). Called by full path because a GNU tar from Git may come first in PATH.
& "$env:SystemRoot\System32\tar.exe" -a -c -f $zip -C $dist SteamBlacklistTool
if ($LASTEXITCODE -ne 0) { throw 'tar fehlgeschlagen' }
Write-Host ("Fertig: $zip ({0:N1} MB)" -f ((Get-Item $zip).Length / 1MB))
