#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TAMALITOS_TOOLS:-$HOME/.local/share/tamalitos-tools}"
CACHE="${TMPDIR:-$HOME/.hermes/cache/scratch}/tamalitos-downloads"
mkdir -p "$TOOLS" "$CACHE"
fetch() { if [ ! -s "$2" ]; then curl --fail --location --retry 3 --output "$2.part" "$1"; mv "$2.part" "$2"; fi; }
if [ ! -x "$TOOLS/jdk-17/bin/java" ]; then
    fetch 'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse' "$CACHE/jdk17.tar.gz"
    mkdir -p "$TOOLS/jdk-17"
    tar -xzf "$CACHE/jdk17.tar.gz" -C "$TOOLS/jdk-17" --strip-components=1
fi
export JAVA_HOME="$TOOLS/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -x "$TOOLS/gradle-8.11.1/bin/gradle" ]; then
    fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' "$CACHE/gradle.zip"
    unzip -q "$CACHE/gradle.zip" -d "$TOOLS"
fi
export ANDROID_HOME="$TOOLS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    fetch 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip' "$CACHE/android-tools.zip"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q "$CACHE/android-tools.zip" -d "$CACHE/android-cli"
    mv "$CACHE/android-cli/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
printf '\nSDK licenses for Android development\n'
set +o pipefail
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
set -o pipefail
sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
python3 - "$ROOT" "$TOOLS" <<'PY'
import pathlib,sys,shlex
root,tools=map(pathlib.Path,sys.argv[1:])
(root/'local.properties').write_text('sdk.dir='+str(tools/'android-sdk')+'\n')
(root/'tools/env.sh').write_text('export JAVA_HOME='+shlex.quote(str(tools/'jdk-17'))+'\nexport ANDROID_HOME='+shlex.quote(str(tools/'android-sdk'))+'\nexport ANDROID_SDK_ROOT="$ANDROID_HOME"\nexport PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"\n')
PY
# Generate wrapper in an isolated empty Gradle project, not in incomplete app sources.
mkdir -p "$CACHE/wrapper"
python3 - "$CACHE/wrapper/settings.gradle" <<'PY'
import pathlib,sys
pathlib.Path(sys.argv[1]).write_text("rootProject.name = 'wrapper-bootstrap'\n")
PY
"$TOOLS/gradle-8.11.1/bin/gradle" -p "$CACHE/wrapper" wrapper --gradle-version 8.11.1 --distribution-type bin --gradle-distribution-sha256-sum f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6 --no-daemon -Dorg.gradle.jvmargs=-Xmx256m
cp "$CACHE/wrapper/gradlew" "$CACHE/wrapper/gradlew.bat" "$ROOT/"
mkdir -p "$ROOT/gradle/wrapper"
cp "$CACHE/wrapper/gradle/wrapper/gradle-wrapper.jar" "$CACHE/wrapper/gradle/wrapper/gradle-wrapper.properties" "$ROOT/gradle/wrapper/"
chmod +x "$ROOT/gradlew"
printf '\nToolchain ready: source tools/env.sh && ./gradlew assembleDebug\n'
