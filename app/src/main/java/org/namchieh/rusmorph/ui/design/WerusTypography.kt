package org.namchieh.rusmorph.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Canonical Werus Typography Tokens
 *
 * Book/Learning UI:
 * - Serif titles
 * - Readable body text
 *
 * Laboratory UI:
 * - Monospace accents where appropriate
 */
val WerusDisplay = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp)
val WerusTitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp)
val WerusSubtitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
val WerusBody = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
val WerusSerifBody = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp)
val WerusCaption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
val WerusMetadata = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
val WerusCode = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

// Direct semantic accessors on Typography
val Typography.Display: TextStyle get() = WerusDisplay
val Typography.Title: TextStyle get() = WerusTitle
val Typography.Subtitle: TextStyle get() = WerusSubtitle
val Typography.Body: TextStyle get() = WerusBody
val Typography.SerifBody: TextStyle get() = WerusSerifBody
val Typography.Caption: TextStyle get() = WerusCaption
val Typography.Metadata: TextStyle get() = WerusMetadata
val Typography.Code: TextStyle get() = WerusCode

val WerusTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 50.sp),
    displayMedium = WerusDisplay,
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = WerusTitle,
    titleMedium = WerusSubtitle,
    bodyLarge = WerusSerifBody,
    bodyMedium = WerusBody,
    bodySmall = WerusCaption,
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = WerusMetadata,
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
)
