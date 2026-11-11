package com.locketphoto.gallery.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import com.locketphoto.gallery.ui.theme.Gold
import kotlin.math.abs
import kotlin.math.roundToInt

internal val EditorBg = Color.Black
internal val Panel = Color(0xFF16151A)
internal val Muted = Color(0xFF9B97A3)

/** Undo/redo over immutable edit states. [update] is live (e.g. while dragging); [commit] records a step. */
@Stable
class EditHistory<T>(initial: T) {
    private val stack = mutableListOf(initial)
    private var index by mutableIntStateOf(0)
    var current by mutableStateOf(initial)
        private set
    private val initialValue = initial

    val canUndo get() = index > 0 || current != stack[index]
    val canRedo get() = index < stack.size - 1 && current == stack[index]
    val dirty get() = current != initialValue

    fun update(value: T) { current = value }

    fun commit() {
        if (current == stack[index]) return
        while (stack.size > index + 1) stack.removeAt(stack.size - 1)
        stack += current
        index++
    }

    fun set(value: T) { update(value); commit() }

    fun undo() {
        commit()
        if (index > 0) { index--; current = stack[index] }
    }

    fun redo() {
        if (index < stack.size - 1) { index++; current = stack[index] }
    }
}

@Composable
internal fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    canSave: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onSaveCopy: () -> Unit,
    overwriteAllowed: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancel", tint = Color.White) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f))
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f))
        }
        Spacer(Modifier.width(6.dp))
        Box {
            val bg by animateColorAsState(if (canSave) Gold else Color.White.copy(alpha = 0.12f), label = "saveBg")
            val scale by animateFloatAsState(if (canSave) 1f else 0.94f, spring(dampingRatio = 0.5f), label = "saveScale")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable(enabled = canSave) { menu = true }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text("Save", color = if (canSave) Color(0xFF231A06) else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color(0xFF232029)) {
                DropdownMenuItem(
                    text = { Text(if (overwriteAllowed) "Save" else "Save (copy only for this format)", color = if (overwriteAllowed) Color.White else Muted) },
                    enabled = overwriteAllowed,
                    onClick = { menu = false; onSave() },
                )
                DropdownMenuItem(text = { Text("Save as copy", color = Color.White) }, onClick = { menu = false; onSaveCopy() })
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

data class ToolTab(val key: String, val label: String, val icon: ImageVector)

/** Bottom tool tabs with a gold pill that springs from tab to tab. */
@Composable
internal fun ToolTabs(tabs: List<ToolTab>, selected: String, onSelect: (String) -> Unit) {
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val sel = bounds[selected]
    val x by animateDpAsState(with(density) { (sel?.first ?: 0f).toDp() }, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "pillX")
    val w by animateDpAsState(with(density) { (sel?.second ?: 0f).toDp() }, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "pillW")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (sel != null) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(w)
                    .height(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Gold.copy(alpha = 0.16f))
                    .border(1.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            for (t in tabs) {
                val on = t.key == selected
                val tint by animateColorAsState(if (on) Gold else Color.White.copy(alpha = 0.75f), label = "tabTint")
                val s by animateFloatAsState(if (on) 1.08f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "tabScale")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { bounds[t.key] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!on) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(t.key)
                        }
                        .scale(s),
                ) {
                    Icon(t.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, color = tint, fontSize = 11.sp, maxLines = 1, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * A slim slider whose fill grows from [center] (0 for most adjustments). Tap the label to reset.
 * The value label springs while you drag.
 */
@Composable
internal fun LocketSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    center: Float = 0f,
    resetTo: Float = center,
    format: (Float) -> String = { v -> ((v * 100).roundToInt()).let { if (it > 0) "+$it" else "$it" } },
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    val labelScale by animateFloatAsState(if (dragging) 1.18f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "valScale")
    val onChangeNow by rememberUpdatedState(onChange)
    val onFinishedNow by rememberUpdatedState(onFinished)
    val valueNow by rememberUpdatedState(value)
    val alpha = if (enabled) 1f else 0.35f
    Column(modifier.graphicsLayer { this.alpha = alpha }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeNow(resetTo); onFinishedNow()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                format(value),
                color = if (abs(value - center) > 0.0001f) Gold else Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.scale(labelScale).padding(horizontal = 4.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    fun toValue(px: Float): Float {
                        val t = ((px - 12.dp.toPx()) / (size.width - 24.dp.toPx())).coerceIn(0f, 1f)
                        return range.start + t * (range.endInclusive - range.start)
                    }
                    detectTapGestures { onChangeNow(snap(toValue(it.x), center, range)); onFinishedNow() }
                }
                .pointerInput(enabled, range) {
                    if (!enabled) return@pointerInput
                    var v = 0f
                    fun toValue(px: Float): Float {
                        val t = ((px - 12.dp.toPx()) / (size.width - 24.dp.toPx())).coerceIn(0f, 1f)
                        return range.start + t * (range.endInclusive - range.start)
                    }
                    detectDragGestures(
                        onDragStart = { dragging = true; v = valueNow },
                        onDragEnd = { dragging = false; onFinishedNow() },
                        onDragCancel = { dragging = false; onFinishedNow() },
                    ) { change, _ ->
                        change.consume()
                        val nv = snap(toValue(change.position.x), center, range)
                        if ((v - center) * (nv - center) <= 0f && nv != v && abs(nv - center) < 0.0001f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        v = nv
                        onChangeNow(nv)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val pad = 12.dp.toPx()
                val y = size.height / 2
                val span = size.width - 2 * pad
                fun xOf(v: Float) = pad + (v - range.start) / (range.endInclusive - range.start) * span
                drawLine(Color.White.copy(alpha = 0.18f), Offset(pad, y), Offset(size.width - pad, y), 3.dp.toPx(), StrokeCap.Round)
                val cx = xOf(center)
                val vx = xOf(value.coerceIn(range.start, range.endInclusive))
                drawLine(Gold, Offset(cx, y), Offset(vx, y), 3.dp.toPx(), StrokeCap.Round)
                if (center != range.start) drawCircle(Color.White.copy(alpha = 0.5f), 2.dp.toPx(), Offset(cx, y))
                drawCircle(Color.White, (if (dragging) 11.dp else 9.dp).toPx(), Offset(vx, y))
            }
        }
    }
}

/** Gentle magnet to the centre so "zero" is easy to hit. */
private fun snap(v: Float, center: Float, range: ClosedFloatingPointRange<Float>): Float {
    val tol = (range.endInclusive - range.start) * 0.015f
    return if (abs(v - center) < tol) center else v
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val bg by animateColorAsState(if (selected) Gold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f), label = "chipBg")
    val fg by animateColorAsState(if (selected) Gold else Color.White.copy(alpha = 0.85f), label = "chipFg")
    val s by animateFloatAsState(if (selected) 1.05f else 1f, spring(dampingRatio = 0.5f), label = "chipScale")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(s)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun RoundToolButton(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false, size: Dp = 44.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (active) Gold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClick),
        ) { Icon(icon, label, tint = if (active) Gold else Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
internal fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = Color(0xFF232029),
        title = { Text("Discard changes?", color = Color.White) },
        text = { Text("Your edits will be lost.", color = Muted) },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard", color = Gold) } },
        dismissButton = { TextButton(onClick = onKeep) { Text("Keep editing", color = Color.White) } },
    )
}

/** Blocks the screen while saving. [progress] < 0 means indeterminate. */
@Composable
internal fun SavingOverlay(label: String, progress: Float, onCancel: (() -> Unit)?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C1A21))
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .width(220.dp),
        ) {
            if (progress < 0f) {
                CircularProgressIndicator(color = Gold, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            } else {
                Text("${(progress * 100).roundToInt()}%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = Gold,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(label, color = Muted, fontSize = 13.sp)
            if (onCancel != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Cancel", color = Gold) }
            }
        }
    }
}

internal fun Modifier.offsetPx(x: Float, y: Float) = this.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
