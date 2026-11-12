package app.opentaiment.core.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/*
 * Fonts bundled with the app (all SIL Open Font License 1.1, see core/ui/FONTS.md).
 * They ship inside the APK because many Android head units have no Google Play services
 * and so cannot use downloadable fonts.
 */

/** One entry per weight; for a variable font each entry sets its own `wght` axis. */
private fun family(res: Int, vararg weights: Int) = FontFamily(weights.map { Font(res, FontWeight(it)) })

internal val SpaceGrotesk = family(R.font.space_grotesk, 300, 400, 500, 600, 700)
internal val JetBrainsMono = family(R.font.jetbrains_mono, 200, 300, 400, 500, 600)
internal val Outfit = family(R.font.outfit, 200, 300, 400, 500, 600)
internal val ArchivoBlack = FontFamily(
    Font(R.font.archivo_black, FontWeight.Normal),
    Font(R.font.archivo_black, FontWeight.Black),
)
internal val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)
internal val Vt323 = FontFamily(Font(R.font.vt323))
