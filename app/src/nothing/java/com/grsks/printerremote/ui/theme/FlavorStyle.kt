package com.grsks.printerremote.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.grsks.printerremote.R

/** Версия «Nothing»: монохром, точечный дисплей, Space Mono (SIL OFL 1.1). */
object FlavorStyle {
    val style = AppStyle.NOTHING
    val labelFont: FontFamily = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
    const val licenseAsset = "licenses/SpaceMono-OFL.txt"
}
