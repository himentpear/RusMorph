package org.namchieh.rusmorph.ui.design

import androidx.compose.ui.graphics.Color

/** Canonical, database-independent WeRus colour tokens. */
object WerusColors {
    val Red = Color(0xFFA33A32)
    val RedDark = Color(0xFF702822)
    val RedSoft = Color(0xFFF0DCD8)

    val Paper = Color(0xFFFFFCF7)
    val Canvas = Color(0xFFF7F4EE)
    val Beige = Color(0xFFF0E5D1)
    val BeigeMuted = Color(0xFFF0ECE4)

    val Gold = Color(0xFFC19A5B)
    val GoldDark = Color(0xFF8B642F)

    val Ink = Color(0xFF292421)
    val InkMuted = Color(0xFF716861)
    val InkFaint = Color(0xFF8C827A)
    val Disabled = Color(0xFFB2AAA2)
    val OnDark = Color(0xFFFFFCF7)

    val BorderStrong = Color(0xFF9D938A)
    val Border = Color(0xFFDDD5CA)
    val BorderSoft = Color(0xFFE9E3DA)
    val Shadow = Color(0x24292421)

    val Success = Color(0xFF47765A)
    val Warning = Color(0xFFC67A34)
    val Error = Color(0xFFB8483F)
    val Info = Color(0xFF4F6F8F)

    // Transitional semantic aliases for screens being migrated to the canonical tokens.
    @Deprecated("Use Ink") val CarbonBlack = Ink
    @Deprecated("Use InkMuted") val Graphite = InkMuted
    @Deprecated("Use Beige") val WarmCream = Beige
    @Deprecated("Use Red") val VividOrange = Red
    @Deprecated("Use BeigeMuted") val BackgroundSecondary = BeigeMuted
    @Deprecated("Use Paper") val Surface = Paper
    @Deprecated("Use Paper") val SurfaceElevated = Paper
    @Deprecated("Use BeigeMuted") val SurfaceMuted = BeigeMuted
    @Deprecated("Use Ink") val SurfaceDark = Ink
    @Deprecated("Use Beige") val PillBackground = Beige
    @Deprecated("Use Ink") val TextPrimary = Ink
    @Deprecated("Use InkMuted") val TextSecondary = InkMuted
    @Deprecated("Use InkFaint") val TextTertiary = InkFaint
    @Deprecated("Use Disabled") val TextDisabled = Disabled
    @Deprecated("Use OnDark") val TextOnDark = OnDark
    @Deprecated("Use OnDark") val TextOnAccent = OnDark
    @Deprecated("Use Red") val Primary = Red
    @Deprecated("Use RedDark") val PrimaryDark = RedDark
    @Deprecated("Use RedSoft") val PrimaryContainer = RedSoft
    @Deprecated("Use Gold") val Secondary = Gold
    @Deprecated("Use Beige") val SecondaryContainer = Beige
    @Deprecated("Use InkMuted") val Tertiary = InkMuted
    @Deprecated("Use Red") val AccentOrange = Red
    @Deprecated("Use Success") val AccentGreen = Success
    @Deprecated("Use Info") val AccentBlue = Info
    @Deprecated("Use BorderStrong") val OutlineStrong = BorderStrong
    @Deprecated("Use Border") val Outline = Border
    @Deprecated("Use BorderSoft") val OutlineSoft = BorderSoft
    @Deprecated("Use BorderSoft") val Divider = BorderSoft
    @Deprecated("Use Shadow") val ShadowWarm = Shadow
}
