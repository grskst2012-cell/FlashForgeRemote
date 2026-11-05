package com.grsks.printerremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grsks.printerremote.data.SavedPrinter
import com.grsks.printerremote.net.Discovery
import com.grsks.printerremote.net.DiscoveredPrinter
import com.grsks.printerremote.printer.Credentials
import com.grsks.printerremote.printer.PrinterModel
import com.grsks.printerremote.printer.PrinterModels
import com.grsks.printerremote.ui.theme.BigValue
import com.grsks.printerremote.ui.theme.DotMatrixText
import com.grsks.printerremote.ui.theme.NCard
import com.grsks.printerremote.ui.theme.NLabel
import com.grsks.printerremote.ui.theme.NothingLogo
import com.grsks.printerremote.ui.theme.StatusDot
import com.grsks.printerremote.ui.theme.accent
import com.grsks.printerremote.ui.theme.cased
import com.grsks.printerremote.ui.theme.isNothing
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion

@Composable
fun PrinterListScreen(
    printers: List<SavedPrinter>,
    onOpen: (SavedPrinter) -> Unit,
    /** Новый принтер приходит с пустым id. */
    onSave: (SavedPrinter) -> Unit,
    onDelete: (SavedPrinter) -> Unit,
    onSettings: () -> Unit,
) {
    // null — диалог закрыт; пустой id — добавление; иначе правка.
    var editing by remember { mutableStateOf<SavedPrinter?>(null) }
    var toDelete by remember { mutableStateOf<SavedPrinter?>(null) }
    val newPrinter = SavedPrinter(id = "", name = "", host = "", modelId = PrinterModels.all.first().id)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Header(count = printers.size, onAdd = { editing = newPrinter }, onSettings = onSettings)
            }

            if (printers.isEmpty()) {
                item { EmptyState(onAdd = { editing = newPrinter }) }
            }

            items(printers, key = { it.id }) { printer ->
                val model = PrinterModels.byId(printer.modelId)
                NCard(Modifier.fillMaxWidth(), onClick = { onOpen(printer) }) {
                    Row(
                        Modifier.padding(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(MaterialTheme.colorScheme.accent)
                                Spacer(Modifier.size(10.dp))
                                Text(printer.name, style = MaterialTheme.typography.titleMedium)
                            }
                            NLabel(model?.fullName ?: printer.modelId)
                            Text(
                                printer.host,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = printer }) {
                            Icon(Icons.Filled.Edit, "Изменить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toDelete = printer }) {
                            Icon(Icons.Filled.Close, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    editing?.let { printer ->
        PrinterDialog(
            initial = printer,
            onDismiss = { editing = null },
            onConfirm = { onSave(it); editing = null },
        )
    }

    toDelete?.let { printer ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("Удалить «${printer.name}»?") },
            text = { Text("Принтер пропадёт из списка. Файлы на самом принтере не затрагиваются.") },
            confirmButton = {
                TextButton(onClick = { onDelete(printer); toDelete = null }) {
                    Text("Удалить".cased(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена".cased()) } },
        )
    }
}

@Composable
private fun Header(count: Int, onAdd: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isNothing) {
            NothingLogo(dot = 5.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DotMatrixText("PRINTER", dot = 3.dp)
                NLabel(if (count == 0) "Нет принтеров" else "Принтеров: $count")
            }
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Мои принтеры", style = MaterialTheme.typography.headlineMedium)
                NLabel(if (count == 0) "Пока пусто" else "Принтеров: $count")
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
        }
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = if (isNothing) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
            contentColor = if (isNothing) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить принтер")
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    NCard(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onAdd) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigValue("NO DATA", dot = 4.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Нажмите «+», чтобы найти принтеры в сети или добавить по IP-адресу.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val hostRegex = Regex("""[A-Za-z0-9.\-]+(:\d{1,5})?""")

@Composable
private fun PrinterDialog(
    initial: SavedPrinter,
    onDismiss: () -> Unit,
    onConfirm: (SavedPrinter) -> Unit,
) {
    val isNew = initial.id.isEmpty()
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var host by rememberSaveable { mutableStateOf(initial.host) }
    var modelId by rememberSaveable { mutableStateOf(initial.modelId) }
    var serial by rememberSaveable { mutableStateOf(initial.serial) }
    var code by rememberSaveable { mutableStateOf(initial.accessCode) }
    var camera by rememberSaveable { mutableStateOf(initial.cameraUrl) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    val selected = PrinterModels.byId(modelId) ?: PrinterModels.all.first()
    val hostValid = host.trim().matches(hostRegex)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(if (isNew) "Новый принтер" else "Изменить принтер") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isNew) {
                    ScanSection { found ->
                        host = found.host
                        name = found.name
                        modelId = found.modelId
                        if (found.serial.isNotBlank()) serial = found.serial
                    }
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP-адрес") },
                    placeholder = { Text("192.168.1.50") },
                    supportingText = { Text("Нестандартный порт: 192.168.1.50:5000") },
                    singleLine = true,
                    isError = host.isNotEmpty() && !hostValid,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                NLabel("Модель", Modifier.padding(top = 8.dp))
                ModelRow(selected, selected = true, trailing = if (pickerOpen) "▲" else "▼") { pickerOpen = !pickerOpen }
                if (pickerOpen) {
                    PrinterModels.all.groupBy { it.vendor }.forEach { (vendor, models) ->
                        NLabel(vendor, Modifier.padding(top = 8.dp, bottom = 2.dp))
                        models.forEach { model ->
                            ModelRow(model, selected = model.id == modelId) {
                                modelId = model.id
                                pickerOpen = false
                            }
                        }
                    }
                }
                NLabel("Связь: ${selected.protocol.title}, порт ${selected.controlPort}")

                when (selected.credentials) {
                    Credentials.FLASHFORGE_HTTP -> {
                        OutlinedTextField(
                            value = serial,
                            onValueChange = { serial = it.trim() },
                            label = { Text("Серийный номер (SN…)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.trim() },
                            label = { Text("Код доступа (checkCode)") },
                            supportingText = { Text("На принтере: Настройки → Сеть. Включите LAN-режим.") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Credentials.API_KEY -> OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.trim() },
                        label = { Text("API-ключ OctoPrint") },
                        supportingText = { Text("Настройки OctoPrint → API → Application Keys") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Credentials.NONE -> Unit
                }

                OutlinedTextField(
                    value = camera,
                    onValueChange = { camera = it.trim() },
                    label = { Text("Адрес камеры (необязательно)") },
                    placeholder = { Text("http://192.168.1.50:8080/?action=stream") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = hostValid,
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.ifBlank { selected.displayName },
                            host = host.trim(),
                            modelId = selected.id,
                            serial = if (selected.credentials == Credentials.FLASHFORGE_HTTP) serial else "",
                            accessCode = if (selected.credentials == Credentials.NONE) "" else code,
                            cameraUrl = camera,
                        ),
                    )
                },
            ) { Text((if (isNew) "Добавить" else "Сохранить").cased()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена".cased()) } },
    )
}

/** Кнопка «Найти в сети» и список найденного. */
@Composable
private fun ScanSection(onPick: (DiscoveredPrinter) -> Unit) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<DiscoveredPrinter>() }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        found.clear()
        Discovery.scan(context)
            .catch { }
            .onCompletion { scanning = false; scanned = true }
            .collect { found += it }
    }

    OutlinedButton(
        onClick = { if (!scanning) scanning = true },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (scanning) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text("Ищу в сети…")
        } else {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Найти в сети")
        }
    }
    found.forEach { p ->
        val model = PrinterModels.byId(p.modelId)
        Surface(
            onClick = { onPick(p) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${p.host} · ${model?.fullName ?: p.modelId}" + if (p.note.isNotBlank()) " · ${p.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (scanned && !scanning && found.isEmpty()) {
        Text(
            "Ничего не найдено. Телефон и принтер должны быть в одной сети Wi-Fi; введите IP вручную.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelRow(model: PrinterModel, selected: Boolean, trailing: String? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) colors.surfaceContainerHighest else colors.surfaceContainer)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (selected) colors.accent else colors.outline)
        Text(
            if (trailing != null) model.fullName else model.displayName,
            Modifier.padding(start = 12.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trailing != null) Text(trailing, color = colors.onSurfaceVariant)
    }
}
