package com.locketphoto.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Red used for the armed trash can. */
val TrashRed = Color(0xFFFF5A5F)

/**
 * Trash button that needs two taps, no dialog. First tap "arms" it: the can turns red,
 * wiggles and fills halfway, then the fill drains away over [armedMillis]. Tap again
 * before it empties to actually trash; otherwise it quietly resets.
 * Any change to [resetKey] (another photo, a different selection) disarms it.
 */
@Composable
fun TwoTapTrashButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    resetKey: Any? = null,
    armedMillis: Int = 3000,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var armed by remember(resetKey) { mutableStateOf(false) }
    val fill = remember(resetKey) { Animatable(0f) }
    val wiggle = remember(resetKey) { Animatable(0f) }
    val scale = remember(resetKey) { Animatable(1f) }
    var job by remember(resetKey) { mutableStateOf<Job?>(null) }
    val color by animateColorAsState(if (armed) TrashRed else tint, label = "trashTint")

    IconButton(
        modifier = modifier,
        onClick = {
            job?.cancel()
            if (!armed) {
                armed = true
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                job = scope.launch {
                    launch {
                        scale.animateTo(1.18f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                        scale.animateTo(1f, spring(dampingRatio = 0.5f))
                    }
                    launch {
                        for (a in listOf(-14f, 12f, -8f, 5f, 0f)) wiggle.animateTo(a, tween(55))
                    }
                    fill.animateTo(0.55f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
                    fill.animateTo(0f, tween(armedMillis, easing = LinearEasing))
                    armed = false
                }
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                job = scope.launch {
                    fill.animateTo(1f, tween(130))
                    onConfirm()
                    armed = false
                    fill.snapTo(0f)
                    wiggle.snapTo(0f)
                    scale.snapTo(1f)
                }
            }
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                rotationZ = wiggle.value
                scaleX = scale.value
                scaleY = scale.value
            },
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = if (armed) "Tap again to move to trash" else "Move to trash",
                tint = color,
            )
            // The "partly deleted" fill: the solid can, revealed from the bottom up.
            if (fill.value > 0f) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = TrashRed,
                    modifier = Modifier.drawWithContent {
                        val top = size.height * (1f - fill.value)
                        clipRect(top = top) { this@drawWithContent.drawContent() }
                    },
                )
            }
        }
    }
}
