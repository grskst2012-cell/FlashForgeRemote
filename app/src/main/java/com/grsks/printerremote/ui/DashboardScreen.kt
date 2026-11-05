package com.grsks.printerremote.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grsks.printerremote.camera.MjpegStream
import com.grsks.printerremote.data.SavedPrinter
import com.grsks.printerremote.printer.Axis
import com.grsks.printerremote.printer.PrintState
import com.grsks.printerremote.printer.PrinterInfo
import com.grsks.printerremote.printer.PrinterModel
import com.grsks.printerremote.printer.Temperature
import com.grsks.printerremote.ui.theme.AppProgress
import com.grsks.printerremote.ui.theme.BigValue
import com.grsks.printerremote.ui.theme.cased
import com.grsks.printerremote.ui.theme.isNothing
import com.grsks.printerremote.ui.theme.NCard
import com.grsks.printerremote.ui.theme.NLabel
import com.grsks.printerremote.ui.theme.StatusDot
import com.grsks.printerremote.ui.theme.accent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val TABS = listOf("Статус", "Управление", "Файлы", "Камера")

@Composable
fun DashboardScreen(printer: SavedPrinter, onBack: () -> Unit) {
    val vm: DashboardViewModel = viewModel(key = printer.id, factory = DashboardViewModel.factory(printer))
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Опрашиваем принтер, только пока экран виден.
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.runSession() }
    }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header(printer, vm.model, state.connection, onBack)
            TabBar(selected = tab, onSelect = { tab = it })
            ConnectionBanner(state.connection)
            when (tab) {
                0 -> StatusTab(state, vm.model, onPause = vm::pause, onResume = vm::resume, onCancel = vm::cancel)
                1 -> ControlTab(state, vm.model, vm)
                2 -> FilesTab(
                    state = state,
                    model = vm.model,
                    onRefresh = vm::refreshFiles,
                    onPrint = vm::startPrint,
                    onUpload = { uri, startAfter -> vm.upload(context, uri, startAfter) },
                )
                else -> CameraTab(vm.cameraUrl)
            }
        }
    }
}

@Composable
private fun Header(printer: SavedPrinter, model: PrinterModel, connection: ConnectionState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(printer.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                "${model.fullName} · ${printer.host}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        val online = connection == ConnectionState.Connected
        StatusDot(
            color = if (online) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.accent,
            pulse = connection == ConnectionState.Connecting,
        )
        Spacer(Modifier.width(8.dp))
        NLabel(if (online) "Online" else "Offline")
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TABS.forEachIndexed { index, title ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = if (isSelected) (if (isNothing) colors.onBackground else colors.secondaryContainer) else Color.Transparent,
                contentColor = if (isSelected) (if (isNothing) colors.background else colors.onSecondaryContainer) else colors.onSurfaceVariant,
                border = if (isSelected) null else BorderStroke(1.dp, colors.outline),
            ) {
                Text(
                    title.cased(),
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ConnectionBanner(connection: ConnectionState) {
    val text = when (connection) {
        ConnectionState.Connected -> return
        ConnectionState.Connecting -> "Подключение…"
        is ConnectionState.Error -> "Нет связи: ${connection.message}. Повтор через 5 с"
    }
    val isError = connection is ConnectionState.Error
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(MaterialTheme.colorScheme.accent, pulse = !isError)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- Статус

@Composable
private fun StatusTab(
    state: DashboardState,
    model: PrinterModel,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val snap = state.snapshot
    var confirmCancel by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = if (snap?.state == PrintState.PRINTING) colors.accent else colors.onSurfaceVariant,
                        pulse = snap?.state == PrintState.PRINTING,
                    )
                    Spacer(Modifier.width(10.dp))
                    NLabel(snap?.state?.label() ?: "—")
                }
                val progress = snap?.progress
                BigValue(
                    text = when {
                        snap == null -> "--"
                        progress != null && snap.state == PrintState.PRINTING -> "${(progress * 100).roundToInt()}%"
                        else -> if (isNothing) snap.state.dotLabel() else snap.state.label()
                    },
                    dot = 8.dp,
                    color = colors.onBackground,
                    dimColor = colors.outlineVariant,
                )
                if (snap != null && progress != null) {
                    AppProgress(progress, color = colors.accent, dots = 36, height = 12.dp)
                    val layer = snap.layer
                    val totalLayers = snap.totalLayers
                    if (layer != null && totalLayers != null && totalLayers > 0) {
                        NLabel("Слой $layer из $totalLayers")
                    }
                    snap.remainingSeconds?.let { NLabel("Осталось ${formatDuration(it)}") }
                }
                snap?.currentFile?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TemperatureCard("Сопло", snap?.nozzle, model.capabilities.maxNozzleTemp, Modifier.weight(1f))
            if (model.capabilities.heatedBed) {
                TemperatureCard("Стол", snap?.bed, model.capabilities.maxBedTemp, Modifier.weight(1f))
            }
        }

        if (state.history.size > 2) TempChart(state.history, model.capabilities.heatedBed)

        val printing = snap?.state == PrintState.PRINTING
        val paused = snap?.state == PrintState.PAUSED
        if (printing || paused) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (printing) {
                    Button(
                        onClick = onPause,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = filledColors(),
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Пауза".cased(), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onResume,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = filledColors(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Продолжить".cased(), style = MaterialTheme.typography.labelLarge)
                    }
                }
                OutlinedButton(
                    onClick = { confirmCancel = true },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    border = BorderStroke(1.dp, colors.accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Отмена".cased(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        state.info?.let { InfoCard(it) }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("Отменить печать?") },
            text = { Text("Текущую печать нельзя будет продолжить.") },
            confirmButton = {
                TextButton(onClick = { onCancel(); confirmCancel = false }) {
                    Text("Отменить печать".cased(), color = colors.accent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Назад".cased()) } },
        )
    }
}

@Composable
private fun filledColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.onBackground,
    contentColor = MaterialTheme.colorScheme.background,
)

@Composable
private fun TemperatureCard(title: String, temperature: Temperature?, max: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    NCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NLabel(title)
            BigValue(
                temperature?.let { "${it.current.roundToInt()}" } ?: "--",
                dot = 5.dp,
                dimColor = colors.outlineVariant,
            )
            AppProgress(
                progress = temperature?.let { it.current / max } ?: 0f,
                dots = 14,
                color = if ((temperature?.target ?: 0f) > 0f) colors.accent else colors.onSurfaceVariant,
                height = 8.dp,
            )
            Text(
                temperature?.let { if (it.target > 0f) "цель ${it.target.roundToInt()} °C" else "нагрев выкл." } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(info: PrinterInfo) {
    NCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NLabel("О принтере")
            listOf(
                "Модель" to info.machineType,
                "Имя" to info.name,
                "Прошивка" to info.firmware,
                "Серийный №" to info.serial,
                "Область печати" to (info.buildVolume?.let { "${it.x}×${it.y}×${it.z} мм" } ?: ""),
            ).filter { it.second.isNotBlank() }.forEachIndexed { i, (k, v) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun PrintState.label(): String = when (this) {
    PrintState.IDLE -> "Готов"
    PrintState.PRINTING -> "Печатает"
    PrintState.PAUSED -> "Пауза"
    PrintState.COMPLETED -> "Печать завершена"
    PrintState.BUSY -> "Занят"
    PrintState.ERROR -> "Ошибка"
    PrintState.UNKNOWN -> "Неизвестно"
}

private fun PrintState.dotLabel(): String = when (this) {
    PrintState.IDLE -> "READY"
    PrintState.PRINTING -> "PRINT"
    PrintState.PAUSED -> "PAUSE"
    PrintState.COMPLETED -> "DONE"
    PrintState.BUSY -> "BUSY"
    PrintState.ERROR -> "ERROR"
    PrintState.UNKNOWN -> "--"
}

// ---------------------------------------------------------------- Управление

@Composable
private fun ControlTab(state: DashboardState, model: PrinterModel, vm: DashboardViewModel) {
    val snap = state.snapshot
    val printing = snap?.state == PrintState.PRINTING || snap?.state == PrintState.PAUSED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (model.capabilities.light) {
            NCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null)
                    Text("Подсветка", Modifier.weight(1f).padding(start = 14.dp))
                    Switch(
                        checked = snap?.lightOn == true,
                        onCheckedChange = { vm.setLight(it) },
                        enabled = !state.busy,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.accent,
                            checkedThumbColor = Color.White,
                            checkedBorderColor = MaterialTheme.colorScheme.accent,
                        ),
                    )
                }
            }
        }
        TemperatureControl("Сопло", model.capabilities.maxNozzleTemp, snap?.nozzle) { vm.setNozzleTemp(it) }
        if (model.capabilities.heatedBed) {
            TemperatureControl("Стол", model.capabilities.maxBedTemp, snap?.bed) { vm.setBedTemp(it) }
        }
        if (model.capabilities.motion) {
            JogPanel(
                enabled = !printing && !state.busy,
                onJog = { axis, distance -> vm.jog(axis, distance) },
                onHome = { vm.home() },
            )
        }
    }
}

@Composable
private fun TemperatureControl(title: String, max: Int, current: Temperature?, onSet: (Int) -> Unit) {
    var text by rememberSaveable(title) { mutableStateOf("") }
    val value = text.toIntOrNull()
    NCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NLabel(title)
                Text(
                    current?.let { "${it.current.roundToInt()} / ${it.target.roundToInt()} °C" } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                    label = { Text("°C, до $max") },
                    singleLine = true,
                    isError = value != null && value > max,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { value?.let(onSet) },
                    enabled = value != null && value <= max,
                    colors = filledColors(),
                ) { Text("Задать".cased(), style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(onClick = { onSet(0) }) { Text("Выкл".cased(), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun JogPanel(enabled: Boolean, onJog: (Axis, Float) -> Unit, onHome: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(10f) }
    val colors = MaterialTheme.colorScheme
    NCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NLabel("Перемещение осей")
            if (!enabled) {
                Text("Недоступно во время печати", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.1f, 1f, 10f).forEach { s ->
                    val selected = step == s
                    Surface(
                        onClick = { step = s },
                        shape = CircleShape,
                        color = if (selected) (if (isNothing) colors.onBackground else colors.secondaryContainer) else Color.Transparent,
                        contentColor = if (selected) (if (isNothing) colors.background else colors.onSecondaryContainer) else colors.onSurfaceVariant,
                        border = if (selected) null else BorderStroke(1.dp, colors.outline),
                    ) {
                        Text(
                            (if (s < 1f) "$s мм" else "${s.toInt()} мм").cased(),
                            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    JogButton(Icons.Filled.KeyboardArrowUp, "Y+", enabled) { onJog(Axis.Y, step) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        JogButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "X−", enabled) { onJog(Axis.X, -step) }
                        IconButton(
                            onClick = onHome,
                            enabled = enabled,
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White,
                                disabledContainerColor = colors.surfaceContainerHighest,
                            ),
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = "Парковка")
                        }
                        JogButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "X+", enabled) { onJog(Axis.X, step) }
                    }
                    JogButton(Icons.Filled.KeyboardArrowDown, "Y−", enabled) { onJog(Axis.Y, -step) }
                    Spacer(Modifier.height(4.dp))
                    NLabel("X / Y")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    JogButton(Icons.Filled.KeyboardArrowUp, "Z+", enabled) { onJog(Axis.Z, step) }
                    Spacer(Modifier.size(56.dp))
                    JogButton(Icons.Filled.KeyboardArrowDown, "Z−", enabled) { onJog(Axis.Z, -step) }
                    Spacer(Modifier.height(4.dp))
                    NLabel("Z")
                }
            }
        }
    }
}

@Composable
private fun JogButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Icon(icon, contentDescription = label)
    }
}

// ---------------------------------------------------------------- Файлы

@Composable
private fun FilesTab(
    state: DashboardState,
    model: PrinterModel,
    onRefresh: () -> Unit,
    onPrint: (String) -> Unit,
    onUpload: (Uri, Boolean) -> Unit,
) {
    var startAfter by rememberSaveable { mutableStateOf(false) }
    var confirmPrint by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onUpload(uri, startAfter)
    }
    val canPrint = state.snapshot?.state.let { it == PrintState.IDLE || it == PrintState.COMPLETED }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.connection) {
        if (state.connection == ConnectionState.Connected && !state.filesLoaded) onRefresh()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = state.upload == null,
                colors = filledColors(),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Загрузить".cased(), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !state.filesLoading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = startAfter, onCheckedChange = { startAfter = it })
            Text("Сразу запустить печать после загрузки", style = MaterialTheme.typography.bodyMedium)
        }
        NLabel("Поддерживаются: " + model.capabilities.uploadExtensions.joinToString { ".$it" })
        state.upload?.let { upload ->
            NLabel("Загрузка ${upload.fileName}: ${(upload.progress * 100).roundToInt()}%", color = colors.onBackground)
            AppProgress(upload.progress, color = colors.accent)
        }
        if (state.filesLoading) AppProgress(0f)

        if (state.filesLoaded && state.files.isEmpty()) {
            Text("На принтере нет файлов", Modifier.padding(top = 16.dp), color = colors.onSurfaceVariant)
        }
        NCard(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            LazyColumn {
                items(state.files) { file ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(file, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        IconButton(onClick = { confirmPrint = file }, enabled = canPrint) {
                            Icon(Icons.Filled.Print, contentDescription = "Печать")
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                }
            }
        }
    }

    confirmPrint?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmPrint = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("Начать печать?") },
            text = { Text("$file\n\nУбедитесь, что стол чистый и пластик заправлен.") },
            confirmButton = {
                TextButton(onClick = { onPrint(file); confirmPrint = null }) { Text("Печатать".cased()) }
            },
            dismissButton = { TextButton(onClick = { confirmPrint = null }) { Text("Отмена".cased()) } },
        )
    }
}

// ---------------------------------------------------------------- Камера

@Composable
private fun CameraTab(url: String?) {
    if (url == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            BigValue(if (isNothing) "NO CAM" else "Нет камеры", dot = 5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        while (true) {
            try {
                MjpegStream.frames(url).collect {
                    frame = it.asImageBitmap()
                    error = null
                }
                error = "Видеопоток прервался"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            delay(3_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val current = frame
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = "Камера принтера",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.accent)
            }
        }
        error?.let {
            Text(
                "Камера: $it. Проверьте, что камера включена в настройках принтера.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.accent,
            )
        }
        Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- График температур

@Composable
private fun TempChart(history: List<TempSample>, showBed: Boolean) {
    val colors = MaterialTheme.colorScheme
    val nozzleColor = colors.accent
    val bedColor = if (isNothing) colors.onBackground else colors.secondary
    val grid = colors.outlineVariant

    NCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NLabel("Температуры")
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(nozzleColor, size = 6.dp)
                    Text("сопло", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (showBed) {
                        StatusDot(bedColor, size = 6.dp)
                        Text("стол", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val all = history.flatMap { listOfNotNull(it.nozzle, if (showBed) it.bed else null) }
                if (all.isEmpty()) return@Canvas
                val lo = (all.min() - 5f).coerceAtLeast(0f)
                val hi = maxOf(all.max() + 5f, lo + 20f)
                val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                fun line(select: (TempSample) -> Float?, color: Color) {
                    val path = Path()
                    var started = false
                    history.forEachIndexed { i, s ->
                        val v = select(s) ?: return@forEachIndexed
                        val x = i * stepX
                        val y = size.height - (v - lo) / (hi - lo) * size.height
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                line({ it.nozzle }, nozzleColor)
                if (showBed) line({ it.bed }, bedColor)
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h ч $m мин" else "${m.coerceAtLeast(1)} мин"
}
