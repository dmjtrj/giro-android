# Локальное окружение сборки: JDK, Android SDK и Gradle-кэш живут внутри проекта.
# Использование: source ./env.sh

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-${(%):-%x}}")" && pwd)"

export JAVA_HOME="$PROJECT_ROOT/.toolchain/jdk-21"
export ANDROID_HOME="$PROJECT_ROOT/.toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$PROJECT_ROOT/.toolchain/android-user"
export GRADLE_USER_HOME="$PROJECT_ROOT/.toolchain/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
