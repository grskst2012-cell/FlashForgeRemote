package com.grsks.printerremote.printer

/** Протокол, которым приложение разговаривает с принтером. Каждому соответствует свой драйвер. */
enum class PrinterProtocol(val title: String) {
    /** TCP 8899, команды вида "~M119\r\n". Adventurer 3/4, Finder, Guider, Dreamer и др. */
    FLASHFORGE_LEGACY_TCP("FlashForge TCP"),

    /** HTTP API Moonraker (порт 7125). Klipper-принтеры: Creality K1, Elegoo Neptune 4, Voron и др. */
    MOONRAKER("Klipper / Moonraker"),

    /** HTTP 8898, JSON с serialNumber и checkCode. Adventurer 5M / 5M Pro, AD5X. */
    FLASHFORGE_HTTP("FlashForge HTTP"),

    /** REST API OctoPrint, ключ в заголовке X-Api-Key. */
    OCTOPRINT("OctoPrint"),
}

/** Какие учётные данные нужно ввести при добавлении принтера. */
enum class Credentials {
    NONE,

    /** Серийный номер (SN…) и код доступа с экрана принтера. */
    FLASHFORGE_HTTP,

    /** API-ключ OctoPrint (Настройки → API → Application Keys). */
    API_KEY,
}

data class BuildVolume(val x: Int, val y: Int, val z: Int)

data class PrinterCapabilities(
    val camera: Boolean,
    val light: Boolean,
    val heatedBed: Boolean,
    val extruders: Int,
    val maxNozzleTemp: Int,
    val maxBedTemp: Int,
    val buildVolume: BuildVolume,
    /** Расширения файлов, которые принтер умеет печатать. */
    val uploadExtensions: List<String>,
    /** Умеет ли протокол двигать оси и парковать. */
    val motion: Boolean = true,
)

data class PrinterModel(
    val id: String,
    val vendor: String,
    val displayName: String,
    val protocol: PrinterProtocol,
    val capabilities: PrinterCapabilities,
    /** Подстроки поля "Machine Type" из ответа M115 — для автоопределения модели. */
    val machineTypeAliases: List<String>,
    val controlPort: Int = 8899,
    /** Адрес видеопотока MJPEG, `{host}` заменяется на IP принтера. */
    val cameraUrlTemplate: String? = null,
    val credentials: Credentials = Credentials.NONE,
) {
    val fullName: String get() = "$vendor $displayName"
}

/**
 * Реестр поддерживаемых моделей. Чтобы добавить принтер с уже реализованным протоколом,
 * достаточно описать его здесь и включить в [all].
 *
 * Размеры и температуры взяты из спецификаций производителей и нужны только для
 * ограничения ввода; реальные значения принтер сообщает сам.
 */
object PrinterModels {

    private val FLASHFORGE_FILES = listOf("gx", "gcode", "g")
    private val GCODE_FILES = listOf("gcode", "g", "gco")

    private fun flashforge(
        id: String,
        name: String,
        volume: BuildVolume,
        nozzle: Int,
        bed: Int,
        camera: Boolean,
        light: Boolean,
        extruders: Int = 1,
        aliases: List<String> = listOf(name),
    ) = PrinterModel(
        id = id,
        vendor = "FlashForge",
        displayName = name,
        protocol = PrinterProtocol.FLASHFORGE_LEGACY_TCP,
        capabilities = PrinterCapabilities(
            camera = camera,
            light = light,
            heatedBed = bed > 0,
            extruders = extruders,
            maxNozzleTemp = nozzle,
            maxBedTemp = bed,
            buildVolume = volume,
            uploadExtensions = FLASHFORGE_FILES,
        ),
        machineTypeAliases = aliases,
        cameraUrlTemplate = if (camera) "http://{host}:8080/?action=stream" else null,
    )

    private fun klipper(
        id: String,
        vendor: String,
        name: String,
        volume: BuildVolume,
        nozzle: Int,
        bed: Int,
        camera: String?,
    ) = PrinterModel(
        id = id,
        vendor = vendor,
        displayName = name,
        protocol = PrinterProtocol.MOONRAKER,
        capabilities = PrinterCapabilities(
            camera = camera != null,
            light = false,
            heatedBed = true,
            extruders = 1,
            maxNozzleTemp = nozzle,
            maxBedTemp = bed,
            buildVolume = volume,
            uploadExtensions = GCODE_FILES,
        ),
        machineTypeAliases = emptyList(),
        controlPort = 7125,
        cameraUrlTemplate = camera,
    )

    private fun flashforgeHttp(
        id: String,
        name: String,
        volume: BuildVolume,
        camera: Boolean,
    ) = PrinterModel(
        id = id,
        vendor = "FlashForge",
        displayName = name,
        protocol = PrinterProtocol.FLASHFORGE_HTTP,
        capabilities = PrinterCapabilities(
            camera = camera,
            light = true,
            heatedBed = true,
            extruders = 1,
            maxNozzleTemp = 265,
            maxBedTemp = 100,
            buildVolume = volume,
            uploadExtensions = FLASHFORGE_FILES,
            motion = false,
        ),
        machineTypeAliases = emptyList(),
        controlPort = 8898,
        cameraUrlTemplate = if (camera) "http://{host}:8080/?action=stream" else null,
        credentials = Credentials.FLASHFORGE_HTTP,
    )

    val ADVENTURER_4 = flashforge(
        id = "flashforge_adventurer_4",
        name = "Adventurer 4",
        volume = BuildVolume(220, 200, 250),
        nozzle = 265, bed = 100, camera = true, light = true,
        aliases = listOf("Adventurer 4", "Adventurer4"),
    )

    val all: List<PrinterModel> = listOf(
        ADVENTURER_4,
        flashforge(
            "flashforge_adventurer_3", "Adventurer 3", BuildVolume(150, 150, 150),
            nozzle = 240, bed = 100, camera = true, light = true,
            aliases = listOf("Adventurer 3", "Adventurer3"),
        ),
        flashforge(
            "flashforge_adventurer_3_pro", "Adventurer 3 Pro", BuildVolume(150, 150, 150),
            nozzle = 240, bed = 100, camera = true, light = true,
            aliases = listOf("Adventurer 3 Pro", "Adventurer3 Pro"),
        ),
        flashforge(
            "flashforge_adventurer_3_lite", "Adventurer 3 Lite", BuildVolume(150, 150, 150),
            nozzle = 240, bed = 100, camera = false, light = true,
            aliases = listOf("Adventurer 3 Lite", "Adventurer3 Lite"),
        ),
        flashforge(
            "flashforge_adventurer_4_lite", "Adventurer 4 Lite", BuildVolume(220, 200, 250),
            nozzle = 265, bed = 100, camera = false, light = true,
            aliases = listOf("Adventurer 4 Lite", "Adventurer4 Lite"),
        ),
        flashforge(
            "flashforge_guider_2s", "Guider 2S", BuildVolume(280, 250, 300),
            nozzle = 300, bed = 120, camera = true, light = true,
            aliases = listOf("Guider IIs", "Guider 2S", "Guider II S"),
        ),
        flashforge(
            "flashforge_finder", "Finder", BuildVolume(140, 140, 140),
            nozzle = 240, bed = 0, camera = false, light = false,
            aliases = listOf("Finder"),
        ),
        flashforge(
            "flashforge_dreamer", "Dreamer", BuildVolume(230, 150, 140),
            nozzle = 270, bed = 120, camera = false, light = false, extruders = 2,
            aliases = listOf("Dreamer"),
        ),
        flashforge(
            "flashforge_generic", "Другая модель (общий режим)", BuildVolume(200, 200, 200),
            nozzle = 260, bed = 100, camera = false, light = false,
            aliases = emptyList(),
        ),

        flashforgeHttp("flashforge_adventurer_5m", "Adventurer 5M", BuildVolume(220, 220, 220), camera = false),
        flashforgeHttp("flashforge_adventurer_5m_pro", "Adventurer 5M Pro", BuildVolume(220, 220, 220), camera = true),
        flashforgeHttp("flashforge_ad5x", "AD5X", BuildVolume(220, 220, 220), camera = true),

        klipper(
            "creality_k1", "Creality", "K1 / K1C", BuildVolume(220, 220, 250),
            nozzle = 300, bed = 100, camera = "http://{host}:8080/?action=stream",
        ),
        klipper(
            "creality_k1_max", "Creality", "K1 Max", BuildVolume(300, 300, 300),
            nozzle = 300, bed = 100, camera = "http://{host}:8080/?action=stream",
        ),
        klipper(
            "elegoo_neptune_4", "Elegoo", "Neptune 4 / 4 Pro", BuildVolume(225, 225, 265),
            nozzle = 300, bed = 100, camera = "http://{host}/webcam/?action=stream",
        ),
        klipper(
            "klipper_generic", "Klipper", "Любой принтер с Moonraker", BuildVolume(250, 250, 250),
            nozzle = 300, bed = 120, camera = "http://{host}/webcam/?action=stream",
        ),
        PrinterModel(
            id = "octoprint_generic",
            vendor = "OctoPrint",
            displayName = "Любой принтер с OctoPrint",
            protocol = PrinterProtocol.OCTOPRINT,
            capabilities = PrinterCapabilities(
                camera = true,
                light = false,
                heatedBed = true,
                extruders = 1,
                maxNozzleTemp = 300,
                maxBedTemp = 120,
                buildVolume = BuildVolume(220, 220, 250),
                uploadExtensions = GCODE_FILES,
            ),
            machineTypeAliases = emptyList(),
            controlPort = 80,
            cameraUrlTemplate = "http://{host}/webcam/?action=stream",
            credentials = Credentials.API_KEY,
        ),
    )

    /** PID из UDP-ответа новых принтеров FlashForge. */
    fun byFlashForgePid(pid: Int): PrinterModel? = when (pid) {
        35 -> byId("flashforge_adventurer_5m")
        36 -> byId("flashforge_adventurer_5m_pro")
        38 -> byId("flashforge_ad5x")
        else -> null
    }

    fun byId(id: String): PrinterModel? = all.firstOrNull { it.id == id }

    fun detect(machineType: String): PrinterModel? = all
        .filter { it.protocol == PrinterProtocol.FLASHFORGE_LEGACY_TCP }
        // Длинные названия первыми: "Adventurer 3 Pro" раньше, чем "Adventurer 3".
        .sortedByDescending { model -> model.machineTypeAliases.maxOfOrNull { it.length } ?: 0 }
        .firstOrNull { model -> model.machineTypeAliases.any { machineType.contains(it, ignoreCase = true) } }
}
