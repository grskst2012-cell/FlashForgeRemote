# Printer Remote

Android-приложение для управления 3D-принтерами по локальной сети, без облака и аккаунтов.
Выпускается в **двух версиях** из одного кода (build flavors), они ставятся рядом:

| Версия | applicationId | Оформление |
|---|---|---|
| `nothing` | `com.grsks.printerremote.nothing` | Монохром, точечный дисплей, Space Mono, один акцент (красный / монохром / янтарный) |
| `material` | `com.grsks.printerremote.m3` | Material 3: цвета из обоев или свой цвет, AMOLED, скругления, шрифт, вид карточек |

## Поддерживаемые принтеры

| Протокол | Модели | Что нужно ввести |
|---|---|---|
| FlashForge TCP 8899 | Adventurer 3 / 3 Pro / 3 Lite / 4 / 4 Lite, Guider 2S, Finder, Dreamer, общий режим | только IP |
| FlashForge HTTP 8898 | Adventurer 5M / 5M Pro, AD5X | IP, серийный номер, код доступа (на принтере: Настройки → Сеть, включить LAN-режим) |
| Klipper / Moonraker | Creality K1 / K1 Max, Elegoo Neptune 4, любой с Moonraker | только IP (доверенные клиенты) |
| OctoPrint | любой | IP и API-ключ (Settings → API → Application Keys) |

Порт можно указать через двоеточие: `192.168.1.50:5000`.

## Возможности

- Статус, прогресс, слои, **оставшееся время** (где его даёт принтер), температуры сопла и стола, **график температур**.
- Пауза, продолжение, отмена; задание температур; подсветка (FlashForge); перемещение осей и парковка (где протокол позволяет).
- Список файлов на принтере, загрузка с телефона, запуск печати.
- Камера MJPEG; для любого принтера можно задать свой адрес камеры.
- **Поиск принтеров в сети**: UDP FlashForge + проверка портов подсети (Moonraker, FlashForge, OctoPrint).
- **Уведомления**: фоновая служба раз в 15 с опрашивает принтеры и сообщает о завершении печати, остановке и ошибке.
  Пока приложение открыто, служба не опрашивает (FlashForge по TCP пускает одного клиента).
- Несколько принтеров, правка и удаление.

## Сборка

```bash
JAVA_HOME=<путь к JBR из Android Studio> ./gradlew assembleDebug testNothingDebugUnitTest
```

APK: `app/build/outputs/apk/{nothing,material}/debug/`.
В Android Studio выберите вариант сборки *nothingDebug* или *materialDebug* (Build Variants).

## Проверка без принтера

```bash
node tools/mock-printer.js          # имитатор Adventurer 4 на порту 8899
```

## Как добавить модель или протокол

Модель — запись в `PrinterModels` (`printer/PrinterModel.kt`). Новый протокол — реализация `PrinterDriver`,
значение в `PrinterProtocol` и ветка в `PrinterDrivers.create`. Драйверы: `printer/flashforge/`, `printer/moonraker/`, `printer/octoprint/`.

## Что не проверено

Всё, кроме FlashForge Adventurer 4, написано по документации и описаниям протоколов и **не пробовалось на реальных принтерах**.
Размеры стола, температуры и наличие камеры у новых моделей указаны по справочным данным.
Интерфейс собирался и проходил тесты, но в эмуляторе не запускался (нет аппаратной виртуализации).

## Лицензии

Шрифт Space Mono (версия Nothing) — SIL Open Font License 1.1, текст лежит в `app/src/nothing/assets/licenses/`.
