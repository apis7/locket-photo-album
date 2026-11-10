package com.locketphoto.gallery.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** One shared clock for every shimmering cell, so the whole grid shimmers in sync. */
@Composable
fun rememberShimmerClock(): State<Float> =
    rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )

/**
 * Diagonal sheen. Reads the clock only in the draw phase, so the grid
 * never recomposes because of it.
 */
fun Modifier.shimmer(clock: State<Float>, base: Color, highlight: Color): Modifier =
    drawBehind {
        val w = size.width
        val x = clock.value * w * 2f - w // sweep across, offset per cell by its own width
        drawRect(base)
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + w, size.height),
            ),
        )
    }
