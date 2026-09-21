package org.namchieh.rusmorph.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WerusButtonStyle { Primary, Secondary }

@Composable
fun WerusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: WerusButtonStyle = WerusButtonStyle.Primary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = WerusShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (style == WerusButtonStyle.Primary) WerusColors.Red else WerusColors.Beige,
            contentColor = if (style == WerusButtonStyle.Primary) WerusColors.OnDark else WerusColors.RedDark,
            disabledContainerColor = WerusColors.BeigeMuted,
            disabledContentColor = WerusColors.Disabled,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WerusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = WerusColors.Paper,
    border: BorderStroke = BorderStroke(1.dp, WerusColors.Border),
    accentColor: Color? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        shape = if (accentColor != null) WerusBookShape else WerusShapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accentColor != null) {
                Surface(
                    modifier = Modifier.width(5.dp).fillMaxHeight(),
                    color = accentColor,
                    content = {},
                )
            }
            Box(Modifier.weight(1f).padding(contentPadding)) { content() }
        }
    }
}

@Composable
fun WerusChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    containerColor: Color = if (selected) WerusColors.Red else WerusColors.Beige,
    contentColor: Color = if (selected) WerusColors.OnDark else WerusColors.RedDark,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = WerusPillShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, if (selected) WerusColors.RedDark else WerusColors.Border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = WerusTypography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WerusBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WerusColors.Paper,
        contentColor = WerusColors.Ink,
        shape = WerusShapes.extraLarge,
        dragHandle = { BottomSheetDefaults.DragHandle(color = WerusColors.Red) },
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { content() }
    }
}
