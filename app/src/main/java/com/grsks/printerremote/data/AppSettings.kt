package com.grsks.printerremote.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppFont { DEFAULT, SERIF, MONO, CURSIVE }

enum class CardStyle { FILLED, OUTLINED, ELEVATED }

/** Акцент стиля Nothing: красный или белый (монохром). */
enum class NothingAccent { RED, WHITE, AMBER }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Цвета из обоев (Android 12+). Только для стиля Material. */
    val dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    /** Цвет-зерно (ARGB), когда динамические цвета выключены. */
    val seedColor: Int = 0xFF4F5BD5.toInt(),
    /** Чисто чёрный фон в тёмной теме (экономит батарею на OLED). */
    val amoled: Boolean = false,
    /** Базовый радиус скругления, dp. */
    val cornerDp: Int = 24,
    val font: AppFont = AppFont.DEFAULT,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val nothingAccent: NothingAccent = NothingAccent.RED,
)

/** Настройки приложения в SharedPreferences. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    private val _notifications = MutableStateFlow(prefs.getBoolean(K_NOTIFY, false))
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    fun updateTheme(block: (ThemeSettings) -> ThemeSettings) {
        val next = block(_theme.value)
        _theme.value = next
        prefs.edit()
            .putString("mode", next.mode.name)
            .putBoolean("dynamic", next.dynamicColor)
            .putInt("seed", next.seedColor)
            .putBoolean("amoled", next.amoled)
            .putInt("corner", next.cornerDp)
            .putString("font", next.font.name)
            .putString("card", next.cardStyle.name)
            .putString("nothing_accent", next.nothingAccent.name)
            .apply()
    }

    fun resetTheme() = updateTheme { ThemeSettings() }

    fun setNotifications(enabled: Boolean) {
        _notifications.value = enabled
        prefs.edit().putBoolean(K_NOTIFY, enabled).apply()
    }

    private fun loadTheme(): ThemeSettings {
        val d = ThemeSettings()
        return ThemeSettings(
            mode = enumOf(prefs.getString("mode", null), d.mode),
            dynamicColor = prefs.getBoolean("dynamic", d.dynamicColor),
            seedColor = prefs.getInt("seed", d.seedColor),
            amoled = prefs.getBoolean("amoled", d.amoled),
            cornerDp = prefs.getInt("corner", d.cornerDp),
            font = enumOf(prefs.getString("font", null), d.font),
            cardStyle = enumOf(prefs.getString("card", null), d.cardStyle),
            nothingAccent = enumOf(prefs.getString("nothing_accent", null), d.nothingAccent),
        )
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val K_NOTIFY = "notifications"
    }
}
