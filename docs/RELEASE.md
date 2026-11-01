# Релиз

Как собрать подписанный APK и выложить его на GitHub. Ключ подписи хранится вне репозитория,
в git не попадает (`*.jks` в `.gitignore`). Без этого ключа обновление поверх установленной
версии не встанет — его нужно беречь и держать резервную копию.

## 1. Поднять версию

В `app/build.gradle` увеличить `versionCode` (на 1) и `versionName` (например `0.2.0`).
Закоммитить в ветке функции, влить в master.

## 2. Собрать

```bash
source ./env.sh
./gradlew testDebugUnitTest assembleRelease
```

Получится `app/build/outputs/apk/release/app-release.apk`. Пока `signingConfig` в release —
debug-ключ, поэтому его нужно переподписать своим ключом.

## 3. Подписать

Ключ создаётся один раз (если его ещё нет):

```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/giro-release.jks -alias giro \
  -keyalg RSA -keysize 4096 -validity 10000
```

Подпись и проверка:

```bash
export PATH="$ANDROID_HOME/build-tools/37.0.0:$PATH"
apksigner sign --ks ~/keys/giro-release.jks --ks-key-alias giro \
  --out Giro-0.2.0.apk app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs Giro-0.2.0.apk
```

Пароль от ключа вводит владелец сам; агенту его не передают.

## 4. Опубликовать

1. `git tag v0.2.0 && git push origin master --tags`.
2. GitHub → Releases → Draft a new release → тег `v0.2.0`.
3. Описание: что нового (можно взять из тела коммитов `feat(NN)` с прошлого тега:
   `git log v0.1.0..v0.2.0 --format='%s%n%b'`).
4. Приложить `Giro-0.2.0.apk`, Publish.

Пушит и публикует владелец (у агента в песочнице нет учётных данных GitHub).
