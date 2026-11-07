# Printer Remote — контекст проекта

Android-приложение (Kotlin, Jetpack Compose, minSdk 26, targetSdk 35) для управления 3D-принтерами по локальной сети: FlashForge (TCP 8899 и HTTP 8898), Klipper/Moonraker, OctoPrint. Пакет `com.grsks.printerremote`. Пользователь говорит по-русски. Исследование и дорожная карта: `reports/Функции приложения для 3D-принтеров.md` (заметки в `research_notes/`).

## Архитектура
- Два flavor из одной кодовой базы: `nothing` (`.nothing`, точечный дисплей, Space Mono) и `material` (`.m3`, кастомизируемый M3). Общие токены темы — `ui/theme/`, различия — `src/nothing/` и `src/material/` (`FlavorStyle`).
- Драйверы реализуют `PrinterDriver`; выбор — `PrinterDrivers.create`. Новая модель — запись в `PrinterModels`, новый протокол — значение `PrinterProtocol` + драйвер + ветка в `create`.
- `FlashForgeTcpClient` сериализует команды мьютексом и повторяет один раз на новом сокете; принтер пускает одного клиента, поэтому `PrintMonitorService` не опрашивает, пока приложение открыто.
- `tools/mock-printer.js` — имитатор Adventurer 4 на порту 8899.

## Статус
- v0.3.0. Проверен на железе только FlashForge Adventurer 4; остальные протоколы написаны по документации.
- Эмулятор не запускается (нет аппаратной виртуализации), интерфейс визуально не проверялся.
- Не сделано из роадмапа: история печатей, виджет Glance, Bambu, `ACCESS_LOCAL_NETWORK` (нужен только при targetSdk 37).

## Сборка
JDK 17–21 (в Android Studio: `C:/Users/grsks/.jdks/jbr-21.0.11`), `./gradlew assembleNothingDebug assembleMaterialDebug testNothingDebugUnitTest`. Путь проекта на рабочем столе содержит кириллицу: сборка идёт благодаря `android.overridePathCheck=true`, но юнит-тесты падают с `ClassNotFoundException` — тестировать из ASCII-копии (`git archive HEAD | tar -x -C <папка>` + `local.properties`). Файлы Gradle Kotlin DSL писать инструментом Write, а не через heredoc (теряются обратные слэши). Релизы на GitHub — debug-сборки.
