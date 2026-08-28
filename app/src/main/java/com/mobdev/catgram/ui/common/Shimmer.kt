package com.mobdev.catgram.ui.common

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

fun Modifier.shimmerEffect(baseColor: Color = Color.Unspecified): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
        ),
        label = "shimmerOffset",
    )
    val cardBackground = if (baseColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else baseColor
    val highlight = lerp(
        start = cardBackground,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.08f,
    )
    val shimmerColors = listOf(
        cardBackground,
        highlight,
        cardBackground,
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(startOffsetX, 0f),
            end = Offset(
                x = startOffsetX + size.width.toFloat(),
                y = size.height.toFloat(),
            ),
        ),
    ).onGloballyPositioned {
        size = it.size
    }
}
