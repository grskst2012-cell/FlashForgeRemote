@file:OptIn(ExperimentalLayoutApi::class)

package com.grsks.printerremote.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grsks.printerremote.PrinterRemoteApp
import com.grsks.printerremote.data.AppFont
import com.grsks.printerremote.data.CardStyle
import com.grsks.printerremote.data.NothingAccent
import com.grsks.printerremote.data.ThemeMode
import com.grsks.printerremote.monitor.Notifier
import com.grsks.printerremote.monitor.PrintMonitorService
import com.grsks.printerremote.ui.theme.FlavorStyle
import com.grsks.printerremote.ui.theme.NCard
import com.grsks.printerremote.ui.theme.NLabel
import com.grsks.printerremote.ui.theme.accent
import com.grsks.printerremote.ui.theme.cased
import com.grsks.printerremote.ui.theme.isNothing

private val SWATCHES = listOf(
    0xFF4F5BD5, 0xFF6750A4, 0xFFB3261E, 0xFFD2691E, 0xFFE0A100, 0xFF4C8C2B,
    0xFF00897B, 0xFF0288D1, 0xFFC2185B, 0xFF795548, 0xFF546E7A, 0xFF8E24AA,
).map { it.toInt() }

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PrinterRemoteApp
    val theme by app.settings.theme.collectAsStateWithLifecycle()
    val notify by app.settings.notifications.collectAsStateWithLifecycle()

    fun enableNotifications() {
        app.settings.setNotifications(true)
        PrintMonitorService.start(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableNotifications()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                Text("Настройки", style = MaterialTheme.typography.titleLarge)
            }

            // ---------------------------------------------------- Уведомления
            Section("Уведомления") {
                SwitchRow(
                    title = "Сообщать о завершении печати",
                    subtitle = "Служба в фоне опрашивает принтеры раз в 15 секунд, пока приложение закрыто.",
                    checked = notify,
                    onChange = { on ->
                        if (!on) {
                            app.settings.setNotifications(false)
                            PrintMonitorService.stop(context)
                        } else if (!Notifier.hasPermission(context) && Build.VERSION.SDK_INT >= 33) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            enableNotifications()
                        }
                    },
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Настройки батареи".cased()) }
                Text(
                    "На Samsung, Xiaomi и некоторых других телефонах разрешите приложению работать в фоне без ограничений, иначе система остановит службу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------------------------------------------- Внешний вид
            Section("Внешний вид") {
                NLabel("Тема")
                ChoiceRow(
                    listOf(ThemeMode.SYSTEM to "Системная", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная"),
                    theme.mode,
                ) { m -> app.settings.updateTheme { it.copy(mode = m) } }

                if (isNothing) {
                    NLabel("Акцент")
                    ChoiceRow(
                        listOf(
                            NothingAccent.RED to "Красный",
                            NothingAccent.WHITE to "Монохром",
                            NothingAccent.AMBER to "Янтарный",
                        ),
                        theme.nothingAccent,
                    ) { a -> app.settings.updateTheme { it.copy(nothingAccent = a) } }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(
                            title = "Цвета из обоев",
                            subtitle = "Material You: палитра подстраивается под обои телефона.",
                            checked = theme.dynamicColor,
                            onChange = { v -> app.settings.updateTheme { it.copy(dynamicColor = v) } },
                        )
                    }
                    if (!theme.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        NLabel("Цвет акцента")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SWATCHES.forEach { argb ->
                                Swatch(argb, selected = theme.seedColor == argb) {
                                    app.settings.updateTheme { it.copy(seedColor = argb) }
                                }
                            }
                        }
                        HexField(theme.seedColor) { argb -> app.settings.updateTheme { it.copy(seedColor = argb) } }
                    }
                    SwitchRow(
                        title = "Чёрный фон (AMOLED)",
                        subtitle = "В тёмной теме фон становится чисто чёрным.",
                        checked = theme.amoled,
                        onChange = { v -> app.settings.updateTheme { it.copy(amoled = v) } },
                    )
                    NLabel("Скругление углов: ${theme.cornerDp} dp")
                    Slider(
                        value = theme.cornerDp.toFloat(),
                        onValueChange = { v -> app.settings.updateTheme { it.copy(cornerDp = v.toInt()) } },
                        valueRange = 0f..40f,
                    )
                    NLabel("Шрифт")
                    ChoiceRow(
                        listOf(
                            AppFont.DEFAULT to "Обычный",
                            AppFont.SERIF to "С засечками",
                            AppFont.MONO to "Моноширинный",
                            AppFont.CURSIVE to "Рукописный",
                        ),
                        theme.font,
                    ) { f -> app.settings.updateTheme { it.copy(font = f) } }
                    NLabel("Карточки")
                    ChoiceRow(
                        listOf(
                            CardStyle.FILLED to "Залитые",
                            CardStyle.OUTLINED to "С рамкой",
                            CardStyle.ELEVATED to "Приподнятые",
                        ),
                        theme.cardStyle,
                    ) { c -> app.settings.updateTheme { it.copy(cardStyle = c) } }
                    OutlinedButton(onClick = { app.settings.resetTheme() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Сбросить оформление")
                    }
                }
                PreviewCard()
            }

            // ---------------------------------------------------- О приложении
            Section("О приложении") {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                Text("Printer Remote $version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Управление 3D-принтерами по локальной сети. Данные никуда не отправляются: приложение общается только с вашими принтерами.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (FlavorStyle.licenseAsset.isNotEmpty()) {
                    var license by remember { mutableStateOf<String?>(null) }
                    OutlinedButton(
                        onClick = {
                            license = if (license == null) {
                                runCatching {
                                    context.assets.open(FlavorStyle.licenseAsset).bufferedReader().use { it.readText() }
                                }.getOrDefault("Файл лицензии не найден")
                            } else {
                                null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Лицензия шрифта Space Mono".cased()) }
                    license?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    NCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NLabel(title, color = MaterialTheme.colorScheme.accent)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val color = Color(argb)
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = Color.White)
    }
}

@Composable
private fun HexField(current: Int, onColor: (Int) -> Unit) {
    var text by remember(current) { mutableStateOf("#%06X".format(current and 0xFFFFFF)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.take(7)
            val hex = text.removePrefix("#")
            if (hex.length == 6) hex.toIntOrNull(16)?.let { onColor(0xFF000000.toInt() or it) }
        },
        label = { Text("Свой цвет (HEX)") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Мини-превью: показывает, как выглядят кнопки и карточка при текущих настройках. */
@Composable
private fun PreviewCard() {
    NLabel("Предпросмотр")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Печать 42%", style = MaterialTheme.typography.titleMedium)
            com.grsks.printerremote.ui.theme.AppProgress(0.42f, color = MaterialTheme.colorScheme.accent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Пауза".cased()) }
                OutlinedButton(onClick = {}, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text("Отмена".cased())
                }
            }
        }
    }
}
