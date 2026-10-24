#!/bin/sh
# Baut build/steam-blacklist-tool.jar (benötigt JDK 17+ und curl)
# Optional: VERSION=1.2.0 ./build.sh  -> build/steam-blacklist-tool-1.2.0.jar
set -e
cd "$(dirname "$0")"
VERSION="${VERSION:-dev}"
NAME="steam-blacklist-tool"
[ "$VERSION" != "dev" ] && NAME="$NAME-$VERSION"

# FlatLaf (dark theme + native file dialogs), Apache License 2.0
FLATLAF_VERSION=3.7.1
FLATLAF_SHA256=11f498f3c89596a782fceaa727c853a665067aeabc09bd682dc53b3ef237d0c7
FLATLAF="build/lib/flatlaf-$FLATLAF_VERSION.jar"
if [ ! -f "$FLATLAF" ]; then
    mkdir -p build/lib
    curl -fsSL -o "$FLATLAF.part" "https://repo1.maven.org/maven2/com/formdev/flatlaf/$FLATLAF_VERSION/flatlaf-$FLATLAF_VERSION.jar"
    mv "$FLATLAF.part" "$FLATLAF"
fi
ACTUAL=$( (sha256sum "$FLATLAF" 2>/dev/null || shasum -a 256 "$FLATLAF") | cut -d' ' -f1)
if [ "$ACTUAL" != "$FLATLAF_SHA256" ]; then
    rm -f "$FLATLAF"
    echo "SHA-256 von $FLATLAF stimmt nicht: $ACTUAL" >&2
    exit 1
fi

rm -rf build/classes
mkdir -p build/classes
javac -encoding UTF-8 --release 17 -cp "$FLATLAF" -d build/classes $(find src/main/java -name '*.java')

# Bundle FlatLaf into the JAR so the release stays a single file.
(cd build/classes && jar xf "../lib/flatlaf-$FLATLAF_VERSION.jar")
rm build/classes/META-INF/MANIFEST.MF
mv build/classes/META-INF/LICENSE build/classes/META-INF/LICENSE-FlatLaf
cp LICENSE build/classes/META-INF/LICENSE

printf 'Implementation-Title: Steam Server Blacklist Tool\nImplementation-Version: %s\nMulti-Release: true\n' "$VERSION" > build/MANIFEST.MF
jar --create --file "build/$NAME.jar" --manifest build/MANIFEST.MF --main-class blacklist.Main -C build/classes .
echo "Fertig: build/$NAME.jar  (Start: java -jar build/$NAME.jar)"
