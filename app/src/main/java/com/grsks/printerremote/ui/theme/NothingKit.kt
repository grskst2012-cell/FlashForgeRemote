package com.grsks.printerremote.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.grsks.printerremote.data.CardStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- Точечный шрифт 5×7

private val GLYPHS: Map<Char, List<String>> = buildMap {
    fun g(c: Char, vararg rows: String) = put(c, rows.toList())
    g('0', "01110", "10001", "10011", "10101", "11001", "10001", "01110")
    g('1', "00100", "01100", "00100", "00100", "00100", "00100", "01110")
    g('2', "01110", "10001", "00001", "00010", "00100", "01000", "11111")
    g('3', "11110", "00001", "00001", "01110", "00001", "00001", "11110")
    g('4', "00010", "00110", "01010", "10010", "11111", "00010", "00010")
    g('5', "11111", "10000", "11110", "00001", "00001", "10001", "01110")
    g('6', "00110", "01000", "10000", "11110", "10001", "10001", "01110")
    g('7', "11111", "00001", "00010", "00100", "01000", "01000", "01000")
    g('8', "01110", "10001", "10001", "01110", "10001", "10001", "01110")
    g('9', "01110", "10001", "10001", "01111", "00001", "00010", "01100")
    g('A', "01110", "10001", "10001", "11111", "10001", "10001", "10001")
    g('B', "11110", "10001", "10001", "11110", "10001", "10001", "11110")
    g('C', "01110", "10001", "10000", "10000", "10000", "10001", "01110")
    g('D', "11110", "10001", "10001", "10001", "10001", "10001", "11110")
    g('E', "11111", "10000", "10000", "11110", "10000", "10000", "11111")
    g('F', "11111", "10000", "10000", "11110", "10000", "10000", "10000")
    g('G', "01110", "10001", "10000", "10111", "10001", "10001", "01111")
    g('H', "10001", "10001", "10001", "11111", "10001", "10001", "10001")
    g('I', "01110", "00100", "00100", "00100", "00100", "00100", "01110")
    g('J', "00111", "00010", "00010", "00010", "00010", "10010", "01100")
    g('K', "10001", "10010", "10100", "11000", "10100", "10010", "10001")
    g('L', "10000", "10000", "10000", "10000", "10000", "10000", "11111")
    g('M', "10001", "11011", "10101", "10101", "10001", "10001", "10001")
    g('N', "10001", "10001", "11001", "10101", "10011", "10001", "10001")
    g('O', "01110", "10001", "10001", "10001", "10001", "10001", "01110")
    g('P', "11110", "10001", "10001", "11110", "10000", "10000", "10000")
    g('Q', "01110", "10001", "10001", "10001", "10101", "10010", "01101")
    g('R', "11110", "10001", "10001", "11110", "10100", "10010", "10001")
    g('S', "01111", "10000", "10000", "01110", "00001", "00001", "11110")
    g('T', "11111", "00100", "00100", "00100", "00100", "00100", "00100")
    g('U', "10001", "10001", "10001", "10001", "10001", "10001", "01110")
    g('V', "10001", "10001", "10001", "10001", "10001", "01010", "00100")
    g('W', "10001", "10001", "10001", "10101", "10101", "10101", "01010")
    g('X', "10001", "10001", "01010", "00100", "01010", "10001", "10001")
    g('Y', "10001", "10001", "01010", "00100", "00100", "00100", "00100")
    g('Z', "11111", "00001", "00010", "00100", "01000", "10000", "11111")
    g('.', "0", "0", "0", "0", "0", "0", "1")
    g(':', "0", "1", "0", "0", "0", "1", "0")
    g('-', "000", "000", "000", "111", "000", "000", "000")
    g('%', "11001", "11010", "00010", "00100", "01000", "01011", "10011")
    g('/', "00001", "00010", "00010", "00100", "01000", "01000", "10000")
    g('°', "01100", "10010", "10010", "01100", "00000", "00000", "00000")
    g(' ', "000", "000", "000", "000", "000", "000", "000")
}

private const val GLYPH_ROWS = 7

private fun glyphFor(c: Char): List<String> = GLYPHS[c.uppercaseChar()] ?: GLYPHS.getValue('-')

/** Число столбцов точек, которое займёт [text] (с интервалом в одну точку между знаками). */
private fun columnsOf(text: String): Int =
    text.sumOf { glyphFor(it)[0].length } + (text.length - 1).coerceAtLeast(0)

/**
 * Текст из точек в духе Nothing. Поддерживает цифры, латиницу и знаки `. : - % / °`.
 * [dot] — шаг сетки; сами точки чуть меньше шага.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    dimColor: Color? = null,
) {
    val columns = columnsOf(text)
    Canvas(modifier.then(Modifier.size(dot * columns, dot * GLYPH_ROWS))) {
        val pitch = dot.toPx()
        val radius = pitch * 0.36f
        var x = 0
        for (ch in text) {
            val glyph = glyphFor(ch)
            for (row in 0 until GLYPH_ROWS) {
                for (col in glyph[row].indices) {
                    val on = glyph[row][col] == '1'
                    if (on || dimColor != null) {
                        drawCircle(
                            color = if (on) color else dimColor!!,
                            radius = radius,
                            center = Offset((x + col + 0.5f) * pitch, (row + 0.5f) * pitch),
                        )
                    }
                }
            }
            x += glyph[0].length + 1
        }
    }
}

/** Прогресс из рядка точек: заполненные — [color], остальные — приглушённые. */
@Composable
fun DotProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    dots: Int = 32,
    color: Color = MaterialTheme.colorScheme.onBackground,
    height: Dp = 10.dp,
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        val pitch = size.width / dots
        val radius = (minOf(pitch, size.height) * 0.36f)
        val filled = (progress.coerceIn(0f, 1f) * dots + 0.0001f).toInt()
        for (i in 0 until dots) {
            drawCircle(
                color = if (i < filled) color else track,
                radius = radius,
                center = Offset((i + 0.5f) * pitch, size.height / 2),
            )
        }
    }
}

/** Красная точка-индикатор. [pulse] — мягкое мигание (идёт печать). */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    pulse: Boolean = false,
) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "alpha",
        )
        a
    } else {
        1f
    }
    Canvas(modifier.size(size)) { drawCircle(color.copy(alpha = alpha)) }
}

// ---------------------------------------------------------------- Контейнеры

/**
 * Карточка. Nothing: тёмная поверхность с тонкой рамкой. Material: вид выбирается в настройках
 * (залитая, с рамкой или приподнятая).
 */
@Composable
fun NCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style = if (isNothing) CardStyle.OUTLINED else LocalCardStyle.current
    val container = when {
        isNothing -> colors.surfaceContainer
        style == CardStyle.FILLED -> colors.surfaceContainerHigh
        else -> colors.surfaceContainerLow
    }
    val border = if (style == CardStyle.OUTLINED) BorderStroke(1.dp, colors.outlineVariant) else null
    val elevation = if (style == CardStyle.ELEVATED) 3.dp else 0.dp
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = container,
            border = border,
            shadowElevation = elevation,
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = container,
            border = border,
            shadowElevation = elevation,
        ) { Column(content = content) }
    }
}

/** Подпись раздела. Nothing: моноширинный капс. Material: обычный регистр. */
@Composable
fun NLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.cased(), modifier, color = color, style = MaterialTheme.typography.labelMedium)
}

/** Регистр подписей: Nothing — заглавные, Material — как написано. */
fun String.cased(): String = if (isNothing) uppercase() else this

/**
 * Крупное значение: Nothing рисует его точками, Material — обычным крупным текстом.
 * [dot] — размер точки Nothing; для Material из него выводится размер шрифта.
 */
@Composable
fun BigValue(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 5.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    dimColor: Color? = null,
) {
    if (isNothing) {
        DotMatrixText(text, modifier, dot, color, dimColor)
    } else {
        val size = (dot.value * 5.2f).coerceIn(28f, 64f)
        Text(
            text.lowercase().replaceFirstChar { it.uppercase() },
            modifier,
            color = color,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size.sp,
                lineHeight = (size * 1.1f).sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

/** Прогресс. Nothing — ряд точек, Material — скруглённая линия. */
@Composable
fun AppProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    dots: Int = 32,
    color: Color = MaterialTheme.colorScheme.onBackground,
    height: Dp = 10.dp,
) {
    if (isNothing) {
        DotProgress(progress, modifier, dots, color, height)
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth().height(height.coerceAtLeast(6.dp)),
            color = if (color == MaterialTheme.colorScheme.onBackground) MaterialTheme.colorScheme.primary else color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

// ---------------------------------------------------------------- Логотип

// ---------------------------------------------------------------- Логотип

/**
 * Логотип приложения: буква «P» из точек и красная «капля» пластика у основания.
 * Тот же рисунок использован в иконке приложения.
 */
@Composable
fun NothingLogo(
    modifier: Modifier = Modifier,
    dot: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Canvas(modifier.size(dot * 5, dot * 7)) { drawLogo(dot.toPx(), color) }
}

private val LOGO_P = listOf(
    "11110",
    "10001",
    "10001",
    "11110",
    "10000",
    "10000",
    "10000",
)

private fun DrawScope.drawLogo(pitch: Float, color: Color) {
    val radius = pitch * 0.36f
    LOGO_P.forEachIndexed { row, line ->
        line.forEachIndexed { col, c ->
            if (c == '1') drawCircle(color, radius, Offset((col + 0.5f) * pitch, (row + 0.5f) * pitch))
        }
    }
    // «Капля» пластика под изгибом буквы.
    drawCircle(NothingRed, radius * 1.25f, Offset(4.5f * pitch, 6.5f * pitch))
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    DotMatrixText("PRINTER", modifier, dot = 3.dp)
}

@Composable
fun HSpace(w: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.width(w))
