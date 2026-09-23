package org.namchieh.rusmorph.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val WerusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val WerusBookShape = RoundedCornerShape(topStart = 5.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 5.dp)
val WerusPillShape = RoundedCornerShape(50)
