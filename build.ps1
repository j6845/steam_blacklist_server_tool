# Baut build/steam-blacklist-tool.jar (benötigt JDK 17+)
# Optional: .\build.ps1 -Version 1.2.0  -> build/steam-blacklist-tool-1.2.0.jar
param([string]$Version = 'dev')
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'

# FlatLaf (dark theme + native file dialogs), Apache License 2.0
$flatlafVersion = '3.7.1'
$flatlafSha256 = '11f498f3c89596a782fceaa727c853a665067aeabc09bd682dc53b3ef237d0c7'
$lib = Join-Path $build 'lib'
$flatlaf = Join-Path $lib "flatlaf-$flatlafVersion.jar"
if (-not (Test-Path $flatlaf)) {
    New-Item -ItemType Directory -Force $lib | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -OutFile "$flatlaf.part" `
        "https://repo1.maven.org/maven2/com/formdev/flatlaf/$flatlafVersion/flatlaf-$flatlafVersion.jar"
    Move-Item -Force "$flatlaf.part" $flatlaf
}
$actual = (Get-FileHash -Algorithm SHA256 $flatlaf).Hash.ToLower()
if ($actual -ne $flatlafSha256) {
    Remove-Item -Force $flatlaf
    throw "SHA-256 von $flatlaf stimmt nicht: $actual"
}

if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force $classes | Out-Null

$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $root 'src/main/java') | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 17 -cp $flatlaf -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'javac fehlgeschlagen' }

# Bundle FlatLaf into the JAR so the release stays a single file.
Push-Location $classes
try {
    & jar xf $flatlaf
    if ($LASTEXITCODE -ne 0) { throw 'jar xf fehlgeschlagen' }
} finally {
    Pop-Location
}
Remove-Item (Join-Path $classes 'META-INF/MANIFEST.MF')
Move-Item (Join-Path $classes 'META-INF/LICENSE') (Join-Path $classes 'META-INF/LICENSE-FlatLaf')
Copy-Item (Join-Path $root 'LICENSE') (Join-Path $classes 'META-INF/LICENSE')

$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Implementation-Title: Steam Server Blacklist Tool`nImplementation-Version: $Version`nMulti-Release: true`n")

$name = if ($Version -eq 'dev') { 'steam-blacklist-tool' } else { "steam-blacklist-tool-$Version" }
$jar = Join-Path $build "$name.jar"
& jar --create --file $jar --manifest $manifest --main-class blacklist.Main -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar fehlgeschlagen' }
Write-Host "Fertig: $jar  (Start: java -jar build/$name.jar)"
