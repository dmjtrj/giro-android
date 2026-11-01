#!/usr/bin/env bash
# Ставит в .toolchain/ всё, что нужно для сборки: JDK 21, Android SDK 37, adb.
# Повторный запуск пропускает уже установленное. Использование: ./scripts/setup-toolchain.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="${TOOLCHAIN_DIR:-$ROOT/.toolchain}"
DL="$TC/downloads"
mkdir -p "$DL"

case "$(uname -s)" in
  Darwin) OS=mac;   SDK_OS=mac;   SDK_SHA1=b62a5d8cf63ded173b47be867be4ee058ceda6df ;;
  Linux)  OS=linux; SDK_OS=linux; SDK_SHA1=63523a02a975a81102238566f2a16c057d52301e ;;
  *) echo "Поддерживаются только macOS и Linux" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARCH=aarch64 ;;
  x86_64)        ARCH=x64 ;;
  *) echo "Неизвестная архитектура $(uname -m)" >&2; exit 1 ;;
esac

# JDK 21 (Eclipse Temurin), контрольная сумма берётся из ответа Adoptium
if [ ! -x "$TC/jdk-21/bin/java" ]; then
  curl -fsSL "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=$ARCH&image_type=jdk&os=$OS&vendor=eclipse" -o "$DL/jdk21.json"
  read -r JDK_URL JDK_SHA < <(python3 -I -c 'import json,sys;p=json.load(open(sys.argv[1]))[0]["binary"]["package"];print(p["link"],p["checksum"])' "$DL/jdk21.json")
  curl -fSL --retry 3 -o "$DL/jdk21.tar.gz" "$JDK_URL"
  echo "$JDK_SHA  $DL/jdk21.tar.gz" | shasum -a 256 -c -
  rm -rf "$TC/jdk-tmp" && mkdir -p "$TC/jdk-tmp"
  tar -xzf "$DL/jdk21.tar.gz" -C "$TC/jdk-tmp"
  if [ "$OS" = mac ]; then mv "$TC"/jdk-tmp/*/Contents/Home "$TC/jdk-21"; else mv "$TC"/jdk-tmp/* "$TC/jdk-21"; fi
  rm -rf "$TC/jdk-tmp" "$DL/jdk21.tar.gz"
fi
export JAVA_HOME="$TC/jdk-21"

# Android command-line tools 21.0 (sdkmanager)
SDK="$TC/android-sdk"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  ZIP="commandlinetools-$SDK_OS-15641748_latest.zip"
  curl -fSL --retry 3 -o "$DL/$ZIP" "https://dl.google.com/android/repository/$ZIP"
  echo "$SDK_SHA1  $DL/$ZIP" | shasum -a 1 -c -
  rm -rf "$SDK/cmdline-tools" && mkdir -p "$SDK/cmdline-tools"
  unzip -q "$DL/$ZIP" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -f "$DL/$ZIP"
fi

# Платформа 37, build-tools и adb.
# sdkmanager не читает HTTPS_PROXY: за прокси нужны --proxy=http --proxy_host=... --proxy_port=...
export ANDROID_USER_HOME="$TC/android-user"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SDK"
yes | $SDKM --licenses > /dev/null || true
$SDKM "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"

echo "Готово. Дальше: source ./env.sh && ./gradlew assembleDebug"
