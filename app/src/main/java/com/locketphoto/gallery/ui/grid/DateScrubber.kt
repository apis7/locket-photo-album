package com.locketphoto.gallery.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Right-edge fast scroller. Appears while scrolling, shows the month you are in,
 * and can be grabbed to fly through years of photos.
 */
@Composable
fun DateScrubber(
    state: LazyGridState,
    labelAt: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentLabelAt by rememberUpdatedState(labelAt)

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(1400)
            visible = false
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val span = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / span).coerceIn(0f, 1f)
        }
    }
    val label by remember { derivedStateOf { currentLabelAt(state.firstVisibleItemIndex) } }

    val thumbHeight = 52.dp
    val barWidth by animateDpAsState(
        if (dragging) 10.dp else 6.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "barWidth",
    )

    BoxWithConstraints(modifier.fillMaxHeight().width(180.dp)) {
        val trackPx = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
        val fraction = if (dragging) dragFraction else scrollFraction
        val origin = TransformOrigin(1f, 0.5f)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f, transformOrigin = origin),
            exit = fadeOut() + scaleOut(targetScale = 0.8f, transformOrigin = origin),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * trackPx).roundToInt()) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.height(thumbHeight),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                // Grab area is wider than the visible bar.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(width = 36.dp, height = thumbHeight)
                        .pointerInput(trackPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                dragFraction = scrollFraction
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                var lastLabel = label
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    // Deltas stay correct even though this node moves with the finger:
                                    // positionChange compares positions within the same event's frame.
                                    dragFraction = (dragFraction + change.positionChange().y / trackPx).coerceIn(0f, 1f)
                                    change.consume()
                                    val total = state.layoutInfo.totalItemsCount
                                    val target = (dragFraction * (total - 1)).roundToInt().coerceAtLeast(0)
                                    scope.launch { state.scrollToItem(target) }
                                    val now = currentLabelAt(target)
                                    if (now != lastLabel) {
                                        lastLabel = now
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                }
                                dragging = false
                            }
                        },
                ) {
                    Box(
                        Modifier
                            .size(width = barWidth, height = thumbHeight - 8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                }
            }
        }
    }
}
