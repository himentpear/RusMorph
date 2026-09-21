package org.namchieh.rusmorph.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.ui.design.WerusTypography

val RusMorphTypography = WerusTypography

object RusMorphTechTypography {
    val HeroDigit = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1.2).sp)
    val StatDigit = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp)
    val SmallDigit = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp)
    val MicroPill = WerusTypography.labelMedium
    val StatusHeader = WerusTypography.labelLarge
}
