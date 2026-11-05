package com.grsks.printerremote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grsks.printerremote.monitor.AppVisibility
import com.grsks.printerremote.monitor.Notifier
import com.grsks.printerremote.monitor.PrintMonitorService
import com.grsks.printerremote.ui.DashboardScreen
import com.grsks.printerremote.ui.PrinterListScreen
import com.grsks.printerremote.ui.SettingsScreen
import com.grsks.printerremote.ui.theme.PrinterRemoteTheme
import com.grsks.printerremote.ui.theme.resolveDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PrinterRemoteApp
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle()
            val dark = resolveDark(theme)
            // Иконки системных панелей должны следовать выбранной теме, а не системной.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            PrinterRemoteTheme(theme) {
                AppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        val app = application as PrinterRemoteApp
        if (app.settings.notifications.value && Notifier.hasPermission(this)) {
            PrintMonitorService.start(this)
        }
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }
}

@Composable
private fun AppRoot() {
    val app = LocalContext.current.applicationContext as PrinterRemoteApp
    val printers by app.store.printers.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val open = printers.firstOrNull { it.id == openId }

    when {
        settingsOpen -> {
            BackHandler { settingsOpen = false }
            SettingsScreen(onBack = { settingsOpen = false })
        }
        open == null -> PrinterListScreen(
            printers = printers,
            onOpen = { openId = it.id },
            onSave = { if (it.id.isEmpty()) app.store.add(it) else app.store.update(it) },
            onDelete = { app.store.remove(it.id) },
            onSettings = { settingsOpen = true },
        )
        else -> {
            BackHandler { openId = null }
            DashboardScreen(printer = open, onBack = { openId = null })
        }
    }
}
