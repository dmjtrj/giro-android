# Релиз

Как собрать подписанный APK и выложить его на GitHub. Ключ подписи хранится вне репозитория,
в git не попадает (`*.jks` в `.gitignore`). Без этого ключа обновление поверх установленной
версии не встанет — его нужно беречь и держать резервную копию.

## 1. Поднять версию

В `app/build.gradle` увеличить `versionCode` (на 1) и `versionName` (например `0.2.0`).
Закоммитить в ветке функции, влить в master.

## 2. Ключ подписи

Ключ создаётся один раз (если его ещё нет):

```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/giro-release.jks -alias giro \
  -keyalg RSA -keysize 4096 -validity 10000
```

Gradle ищет ключ в `~/keys/giro-release.jks` с алиасом `giro`. Другой путь или алиас —
`-Pgiro.keystore=...` и `-Pgiro.keyAlias=...`. Пароль берётся только из переменной окружения
`GIRO_STORE_PASSWORD` (пароль ключа — `GIRO_KEY_PASSWORD`, если он отличается).
Если ключа или пароля нет, release подписывается debug-ключом.

Пароль сразу сохраните в менеджер паролей, а файл ключа скопируйте вне компьютера.
Пароль ключа v0.1.0 был утерян, поэтому с 0.2.0 используется новый ключ: 0.2.0 не ставится
поверх 0.1.0, старую версию нужно удалить. Старый ключ лежит в `~/keys/giro-release-0.1.0.jks`.

## 3. Собрать и проверить

```bash
source ./env.sh
read -s GIRO_STORE_PASSWORD && export GIRO_STORE_PASSWORD
./gradlew testDebugUnitTest assembleRelease
unset GIRO_STORE_PASSWORD

export PATH="$ANDROID_HOME/build-tools/37.0.0:$PATH"
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
cp app/build/outputs/apk/release/app-release.apk Giro-0.2.0.apk
```

В выводе `apksigner` должен быть ваш сертификат, а не `CN=Android Debug`.
Пароль от ключа вводит владелец сам; агенту его не передают.

## 4. Опубликовать

1. `git tag v0.2.0 && git push origin master --tags`.
2. GitHub → Releases → Draft a new release → тег `v0.2.0`.
3. Описание: что нового (можно взять из тела коммитов `feat(NN)` с прошлого тега:
   `git log v0.1.0..v0.2.0 --format='%s%n%b'`).
4. Приложить `Giro-0.2.0.apk`, Publish.

Пушит и публикует владелец (у агента в песочнице нет учётных данных GitHub).
