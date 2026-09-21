package org.namchieh.rusmorph.ui.theme

import org.namchieh.rusmorph.ui.design.WerusColors

/**
 * Compatibility names for screens that have not yet moved to the design package.
 * Deprecated: Migrate to [org.namchieh.rusmorph.ui.design.WerusColors].
 */
@Deprecated(
    message = "RusMorphColors is deprecated. Use WerusColors directly.",
    replaceWith = ReplaceWith("WerusColors", "org.namchieh.rusmorph.ui.design.WerusColors")
)
object RusMorphColors {
    @Deprecated("Use WerusColors.Ink", ReplaceWith("WerusColors.Ink", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val CarbonBlack = WerusColors.Ink

    @Deprecated("Use WerusColors.InkMuted", ReplaceWith("WerusColors.InkMuted", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Graphite = WerusColors.InkMuted

    @Deprecated("Use WerusColors.Beige", ReplaceWith("WerusColors.Beige", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val WarmCream = WerusColors.Beige

    @Deprecated("Use WerusColors.Red", ReplaceWith("WerusColors.Red", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val VividOrange = WerusColors.Red

    @Deprecated("Use WerusColors.Canvas", ReplaceWith("WerusColors.Canvas", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Canvas = WerusColors.Canvas

    @Deprecated("Use WerusColors.BeigeMuted", ReplaceWith("WerusColors.BeigeMuted", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val BackgroundSecondary = WerusColors.BeigeMuted

    @Deprecated("Use WerusColors.Paper", ReplaceWith("WerusColors.Paper", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Surface = WerusColors.Paper

    @Deprecated("Use WerusColors.Paper", ReplaceWith("WerusColors.Paper", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val SurfaceElevated = WerusColors.Paper

    @Deprecated("Use WerusColors.BeigeMuted", ReplaceWith("WerusColors.BeigeMuted", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val SurfaceMuted = WerusColors.BeigeMuted

    @Deprecated("Use WerusColors.RedDark", ReplaceWith("WerusColors.RedDark", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val SurfaceDark = WerusColors.RedDark

    @Deprecated("Use WerusColors.Beige", ReplaceWith("WerusColors.Beige", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val PillBackground = WerusColors.Beige

    @Deprecated("Use WerusColors.Ink", ReplaceWith("WerusColors.Ink", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val TextPrimary = WerusColors.Ink

    @Deprecated("Use WerusColors.InkMuted", ReplaceWith("WerusColors.InkMuted", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val TextSecondary = WerusColors.InkMuted

    @Deprecated("Use WerusColors.InkFaint", ReplaceWith("WerusColors.InkFaint", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val TextTertiary = WerusColors.InkFaint

    @Deprecated("Use WerusColors.Disabled", ReplaceWith("WerusColors.Disabled", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val TextDisabled = WerusColors.Disabled

    @Deprecated("Use WerusColors.OnDark", ReplaceWith("WerusColors.OnDark", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val TextOnDark = WerusColors.OnDark

    @Deprecated("Use WerusColors.OnDark", ReplaceWith("WerusColors.OnDark", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val TextOnAccent = WerusColors.OnDark

    @Deprecated("Use WerusColors.Red", ReplaceWith("WerusColors.Red", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Primary = WerusColors.Red

    @Deprecated("Use WerusColors.RedDark", ReplaceWith("WerusColors.RedDark", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val PrimaryDark = WerusColors.RedDark

    @Deprecated("Use WerusColors.RedSoft", ReplaceWith("WerusColors.RedSoft", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val PrimaryContainer = WerusColors.RedSoft

    @Deprecated("Use WerusColors.Gold", ReplaceWith("WerusColors.Gold", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Secondary = WerusColors.Gold

    @Deprecated("Use WerusColors.Beige", ReplaceWith("WerusColors.Beige", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val SecondaryContainer = WerusColors.Beige

    @Deprecated("Use WerusColors.InkMuted", ReplaceWith("WerusColors.InkMuted", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Tertiary = WerusColors.InkMuted

    @Deprecated("Use WerusColors.Red", ReplaceWith("WerusColors.Red", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val AccentOrange = WerusColors.Red

    @Deprecated("Use WerusColors.Error", ReplaceWith("WerusColors.Error", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Error = WerusColors.Error

    @Deprecated("Use WerusColors.Warning", ReplaceWith("WerusColors.Warning", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Warning = WerusColors.Warning

    @Deprecated("Use WerusColors.Success", ReplaceWith("WerusColors.Success", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Success = WerusColors.Success

    @Deprecated("Use WerusColors.Success", ReplaceWith("WerusColors.Success", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val AccentGreen = WerusColors.Success

    @Deprecated("Use WerusColors.GoldDark", ReplaceWith("WerusColors.GoldDark", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val AccentBlue = WerusColors.GoldDark

    @Deprecated("Use WerusColors.BorderStrong", ReplaceWith("WerusColors.BorderStrong", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val OutlineStrong = WerusColors.BorderStrong

    @Deprecated("Use WerusColors.Border", ReplaceWith("WerusColors.Border", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Outline = WerusColors.Border

    @Deprecated("Use WerusColors.BorderSoft", ReplaceWith("WerusColors.BorderSoft", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val OutlineSoft = WerusColors.BorderSoft

    @Deprecated("Use WerusColors.Border", ReplaceWith("WerusColors.Border", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val Divider = WerusColors.Border

    @Deprecated("Use WerusColors.Shadow", ReplaceWith("WerusColors.Shadow", "org.namchieh.rusmorph.ui.design.WerusColors"))
    val ShadowWarm = WerusColors.Shadow
}
