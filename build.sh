#!/bin/sh
# Baut build/steam-blacklist-tool.jar (benötigt JDK 17+)
# Optional: VERSION=1.2.0 ./build.sh  -> build/steam-blacklist-tool-1.2.0.jar
set -e
cd "$(dirname "$0")"
VERSION="${VERSION:-dev}"
NAME="steam-blacklist-tool"
[ "$VERSION" != "dev" ] && NAME="$NAME-$VERSION"

rm -rf build/classes
mkdir -p build/classes
javac -encoding UTF-8 --release 17 -d build/classes $(find src/main/java -name '*.java')
printf 'Implementation-Title: Steam Server Blacklist Tool\nImplementation-Version: %s\n' "$VERSION" > build/MANIFEST.MF
jar --create --file "build/$NAME.jar" --manifest build/MANIFEST.MF --main-class blacklist.Main -C build/classes .
echo "Fertig: build/$NAME.jar  (Start: java -jar build/$NAME.jar)"
