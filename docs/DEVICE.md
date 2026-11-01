# Проверка на устройстве

Эталонное устройство — Pixel 8, Android 17, по USB или Wi-Fi через adb.

```bash
source ./env.sh
adb devices                                   # телефон должен быть в статусе device
./gradlew installDebug
adb shell am start -n com.motioncues.app/.MainActivity
```

## Правила

- Перед любым нажатием через `adb shell input` проверь, что наше приложение на переднем плане:
  `adb shell dumpsys activity activities | grep -m1 topResumedActivity`.
  Если там не `com.motioncues.app` (или телефон заблокирован) — ничего не нажимай.
- Не включай службу специальных возможностей и не выдавай разрешения за пользователя:
  «Поверх других приложений», уведомления и спецвозможности включает владелец сам.
- Не снимай скриншоты шторки и чужих приложений.

## Полезное

```bash
adb exec-out screencap -p > shot.png          # скриншот (только наш экран)
adb exec-out uiautomator dump /dev/tty        # дерево интерфейса
adb logcat -s Haptics:* CueTile:* AndroidRuntime:E   # логи и падения
adb shell dumpsys sensorservice | grep -A3 com.motioncues.app   # частота датчиков
adb shell dumpsys vibrator_manager | tail -40 # история вибраций
```
