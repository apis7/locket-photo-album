package com.locketphoto.gallery.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    internal var bounds = Size.Zero

    val isZoomed: Boolean get() = scale > 1.02f

    private fun clamp(o: Offset, s: Float): Offset {
        val maxX = (bounds.width * (s - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (bounds.height * (s - 1f) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Zoom by [zoom] keeping the point under [centroid] fixed, then pan by [pan]. */
    internal fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(0.85f, MAX_SCALE)
        val center = Offset(bounds.width / 2f, bounds.height / 2f)
        val p = (centroid - center - offset) / scale
        val newOffset = centroid - center - p * newScale + pan
        scale = newScale
        offset = if (newScale >= 1f) clamp(newOffset, newScale) else newOffset
    }

    /** Pan only; returns the part of [pan] that could NOT be applied (we hit an edge). */
    internal fun panBy(pan: Offset): Offset {
        val wanted = offset + pan
        val got = clamp(wanted, scale)
        offset = got
        return wanted - got
    }

    internal suspend fun animateTo(targetScale: Float, focus: Offset) {
        val center = Offset(bounds.width / 2f, bounds.height / 2f)
        val startScale = scale
        val startOffset = offset
        val p = (focus - center - startOffset) / startScale
        val endOffset = if (targetScale <= 1f) Offset.Zero else clamp(focus - center - p * targetScale, targetScale)
        val anim = Animatable(0f)
        anim.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) {
            val t = value
            scale = startScale + (targetScale - startScale) * t
            offset = startOffset + (endOffset - startOffset) * t
        }
    }

    suspend fun reset() {
        if (scale != 1f || offset != Offset.Zero) animateTo(1f, Offset(bounds.width / 2f, bounds.height / 2f))
    }

    internal suspend fun settle() {
        if (scale < 1f) reset()
    }
}

/**
 * Pinch / double-tap zoom and pan for one viewer page. Leaves horizontal swipes to the
 * pager unless zoomed in (and even then hands over once you pan into the photo's edge).
 * A mostly-vertical drag at normal size is reported as a dismiss gesture.
 */
fun Modifier.zoomable(
    state: ZoomState,
    scope: CoroutineScope,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissDrag: (dy: Float) -> Unit,
    onDismissEnd: () -> Unit,
): Modifier = this
    .pointerInput(state) {
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = { if (!state.isZoomed) onLongPress() },
            onDoubleTap = { pos ->
                scope.launch { state.animateTo(if (state.isZoomed) 1f else DOUBLE_TAP_SCALE, pos) }
            },
        )
    }
    .pointerInput(state) {
        awaitEachGesture {
            state.bounds = Size(size.width.toFloat(), size.height.toFloat())
            awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            var total = Offset.Zero
            var dismissing = false
            var multiTouched = false
            var handedToPager = false
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                when {
                    pressed >= 2 -> {
                        multiTouched = true
                        state.transform(event.calculateCentroid(useCurrent = true), pan, zoom)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                    multiTouched || handedToPager -> Unit
                    state.isZoomed -> {
                        val leftover = state.panBy(pan)
                        // Pushing sideways past the photo's edge: let the pager have it.
                        if (abs(leftover.x) > 0.5f && abs(pan.x) > abs(pan.y)) {
                            handedToPager = true
                        } else {
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                    else -> {
                        total += pan
                        if (!dismissing && abs(total.y) > slop && abs(total.y) > abs(total.x) * 1.4f) dismissing = true
                        if (dismissing) {
                            onDismissDrag(pan.y)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (dismissing) onDismissEnd()
            scope.launch { state.settle() }
        }
    }
