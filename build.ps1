# Baut build/steam-blacklist-tool.jar (benötigt JDK 17+)
# Optional: .\build.ps1 -Version 1.2.0  -> build/steam-blacklist-tool-1.2.0.jar
param([string]$Version = 'dev')
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force $classes | Out-Null

$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $root 'src/main/java') | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 17 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'javac fehlgeschlagen' }

$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Implementation-Title: Steam Server Blacklist Tool`nImplementation-Version: $Version`n")

$name = if ($Version -eq 'dev') { 'steam-blacklist-tool' } else { "steam-blacklist-tool-$Version" }
$jar = Join-Path $build "$name.jar"
& jar --create --file $jar --manifest $manifest --main-class blacklist.Main -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar fehlgeschlagen' }
Write-Host "Fertig: $jar  (Start: java -jar build/$name.jar)"
