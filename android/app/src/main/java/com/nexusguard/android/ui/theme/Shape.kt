package com.nexusguard.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Cantos de 12–16 dp nos cartões e de 10 dp nos botões
val NexusCardShape = RoundedCornerShape(14.dp)
val NexusCardLargeShape = RoundedCornerShape(16.dp)
val NexusButtonShape = RoundedCornerShape(10.dp)
val NexusBadgeShape = RoundedCornerShape(8.dp)

val NexusShapes = Shapes(
    extraSmall = NexusBadgeShape,
    small = NexusButtonShape,
    medium = NexusCardShape,
    large = NexusCardLargeShape
)
