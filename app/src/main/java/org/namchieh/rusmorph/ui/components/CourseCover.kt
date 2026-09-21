package org.namchieh.rusmorph.ui.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.ui.design.WerusColors

/**
 * Resolves a drawable resource id by name safely.
 * Returns null if not found or identifier is 0.
 */
@DrawableRes
fun resolveCoverResourceId(context: Context, coverResourceName: String?): Int? {
    if (coverResourceName.isNullOrBlank()) return null
    val cleanName = coverResourceName.substringAfterLast('/')
    return runCatching {
        val resId = context.resources.getIdentifier(cleanName, "drawable", context.packageName)
        if (resId != 0) resId else null
    }.getOrNull()
}

/**
 * Classical Werus paper placeholder for courses/books without a dedicated cover image.
 * Features textured paper colors, book spine accent, serif typography, and academic styling.
 */
@Composable
fun WerusBookCoverPlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 6.dp, bottomEnd = 6.dp)

    Surface(
        modifier = modifier
            .shadow(2.dp, shape)
            .border(BorderStroke(1.dp, WerusColors.Border), shape),
        shape = shape,
        color = WerusColors.Paper,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Book spine / binding stripe
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(WerusColors.RedDark),
            )

            // Book inner cover page
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(WerusColors.Beige.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top academic header
                Text(
                    text = "РУССКИЙ ЯЗЫК",
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = WerusColors.GoldDark,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )

                // Middle title block
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = WerusColors.RedDark,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Serif,
                            color = WerusColors.InkMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Bottom edition stamp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WerusColors.Border),
                )
            }
        }
    }
}

/**
 * Course cover component.
 * If coverResourceName resolves to a valid drawable, displays the cover image.
 * Otherwise, falls back gracefully to the Werus paper book cover placeholder.
 */
@Composable
fun CourseCoverImage(
    coverResourceName: String?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resId = remember(coverResourceName) { resolveCoverResourceId(context, coverResourceName) }
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 6.dp, bottomEnd = 6.dp)

    if (resId != null) {
        Box(
            modifier = modifier
                .shadow(2.dp, shape)
                .clip(shape)
                .border(BorderStroke(1.dp, WerusColors.Border), shape),
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        WerusBookCoverPlaceholder(
            title = title,
            subtitle = subtitle,
            modifier = modifier,
        )
    }
}
