# Motion Cues

Лёгкое Android-приложение (Java, без Kotlin) — режим против укачивания в стиле Vehicle Motion Cues из iOS.
Android 17 (API 37), minSdk 31, Material You / Monet, плитка в быстрых настройках, проработанный виброотклик для Pixel.

## Окружение

Весь тулчейн лежит внутри проекта в `.toolchain/` (не коммитится):

- `jdk-21` — Eclipse Temurin 21
- `android-sdk` — Android SDK (platform 37, build-tools, platform-tools)
- `gradle-home` — Gradle-кэш (`GRADLE_USER_HOME`)

```bash
source ./env.sh
```

## Ветки

`master` — стабильная ветка. Каждая функция — отдельная ветка `feature/NN-alias`.
