package com.locketphoto.gallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.locketphoto.gallery.edit.CropRect
import com.locketphoto.gallery.ui.theme.Gold
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class Handle { TL, T, TR, R, BR, B, BL, L, Move }

/**
 * Draggable crop frame over [frame] (the whole picture, in view pixels). Corner and edge handles,
 * drag inside to move, rule-of-thirds grid while touching. [ratio] (w/h in pixels) locks the shape.
 */
@Composable
internal fun CropOverlay(
    frame: Rect,
    crop: CropRect,
    ratio: Float?,
    onChange: (CropRect) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameNow by rememberUpdatedState(frame)
    val cropNow by rememberUpdatedState(crop)
    val ratioNow by rememberUpdatedState(ratio)
    val onChangeNow by rememberUpdatedState(onChange)
    val onEndNow by rememberUpdatedState(onEnd)
    var touching by remember { mutableStateOf(false) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var handle = Handle.Move
                var start = CropRect()
                var acc = Offset.Zero
                detectDragGestures(
                    onDragStart = { p ->
                        touching = true
                        acc = Offset.Zero
                        start = cropNow
                        val f = frameNow
                        val r = Rect(f.left + start.left * f.width, f.top + start.top * f.height, f.left + start.right * f.width, f.top + start.bottom * f.height)
                        val tol = 28.dp.toPx()
                        val nearL = abs(p.x - r.left) < tol
                        val nearR = abs(p.x - r.right) < tol
                        val nearT = abs(p.y - r.top) < tol
                        val nearB = abs(p.y - r.bottom) < tol
                        val inX = p.x > r.left - tol && p.x < r.right + tol
                        val inY = p.y > r.top - tol && p.y < r.bottom + tol
                        handle = when {
                            nearL && nearT -> Handle.TL
                            nearR && nearT -> Handle.TR
                            nearL && nearB -> Handle.BL
                            nearR && nearB -> Handle.BR
                            nearT && inX -> Handle.T
                            nearB && inX -> Handle.B
                            nearL && inY -> Handle.L
                            nearR && inY -> Handle.R
                            else -> Handle.Move
                        }
                    },
                    onDragEnd = { touching = false; onEndNow() },
                    onDragCancel = { touching = false; onEndNow() },
                ) { change, drag ->
                    change.consume()
                    acc += drag
                    val f = frameNow
                    val dx = acc.x / f.width
                    val dy = acc.y / f.height
                    onChangeNow(resize(start, handle, dx, dy, ratioNow?.let { it / (f.width / f.height) }))
                }
            },
    ) {
        val f = frame
        val r = Rect(f.left + crop.left * f.width, f.top + crop.top * f.height, f.left + crop.right * f.width, f.top + crop.bottom * f.height)
        // Dim what will be cut away.
        clipRect(r.left, r.top, r.right, r.bottom, ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.62f), topLeft = f.topLeft, size = f.size)
        }
        drawRect(Color.White.copy(alpha = 0.9f), r.topLeft, r.size, style = Stroke(1.dp.toPx()))
        val gridAlpha = if (touching) 0.55f else 0.25f
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f
            val y = r.top + r.height * i / 3f
            drawLine(Color.White.copy(alpha = gridAlpha), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = gridAlpha), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
        }
        val len = min(22.dp.toPx(), min(r.width, r.height) / 3f)
        val w = 3.5.dp.toPx()
        val c = if (touching) Gold else Color.White
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(c, Offset(x, y), Offset(x + sx * len, y), w, StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + sy * len), w, StrokeCap.Round)
        }
        corner(r.left, r.top, 1f, 1f)
        corner(r.right, r.top, -1f, 1f)
        corner(r.left, r.bottom, 1f, -1f)
        corner(r.right, r.bottom, -1f, -1f)
        if (ratio == null) {
            val e = len * 0.7f
            drawLine(c, Offset(r.center.x - e, r.top), Offset(r.center.x + e, r.top), w, StrokeCap.Round)
            drawLine(c, Offset(r.center.x - e, r.bottom), Offset(r.center.x + e, r.bottom), w, StrokeCap.Round)
            drawLine(c, Offset(r.left, r.center.y - e), Offset(r.left, r.center.y + e), w, StrokeCap.Round)
            drawLine(c, Offset(r.right, r.center.y - e), Offset(r.right, r.center.y + e), w, StrokeCap.Round)
        }
    }
}

private const val MIN = 0.08f

/** [nRatio] is the locked ratio in normalised units (w/h of the 0..1 rect), or null. */
private fun resize(s: CropRect, h: Handle, dx: Float, dy: Float, nRatio: Float?): CropRect {
    if (h == Handle.Move) {
        val mx = dx.coerceIn(-s.left, 1f - s.right)
        val my = dy.coerceIn(-s.top, 1f - s.bottom)
        return CropRect(s.left + mx, s.top + my, s.right + mx, s.bottom + my)
    }
    if (nRatio != null) {
        // Corners only; edges act like the nearest corner pair.
        val (ax, ay, sx, sy) = when (h) {
            Handle.TL, Handle.T, Handle.L -> Quad(s.right, s.bottom, -1f, -1f)
            Handle.TR, Handle.R -> Quad(s.left, s.bottom, 1f, -1f)
            Handle.BL -> Quad(s.right, s.top, -1f, 1f)
            else -> Quad(s.left, s.top, 1f, 1f)
        }
        val wFromX = (s.width + sx * dx)
        val wFromY = (s.height + sy * dy) * nRatio
        var w = when (h) {
            Handle.T, Handle.B -> wFromY
            Handle.L, Handle.R -> wFromX
            else -> max(wFromX, wFromY)
        }.coerceAtLeast(MIN)
        val maxW = if (sx > 0) 1f - ax else ax
        val maxH = if (sy > 0) 1f - ay else ay
        w = min(w, min(maxW, maxH * nRatio))
        val hh = w / nRatio
        val l = if (sx > 0) ax else ax - w
        val t = if (sy > 0) ay else ay - hh
        return CropRect(l, t, l + w, t + hh)
    }
    var l = s.left; var t = s.top; var r = s.right; var b = s.bottom
    if (h == Handle.TL || h == Handle.L || h == Handle.BL) l = (s.left + dx).coerceIn(0f, r - MIN)
    if (h == Handle.TR || h == Handle.R || h == Handle.BR) r = (s.right + dx).coerceIn(l + MIN, 1f)
    if (h == Handle.TL || h == Handle.T || h == Handle.TR) t = (s.top + dy).coerceIn(0f, b - MIN)
    if (h == Handle.BL || h == Handle.B || h == Handle.BR) b = (s.bottom + dy).coerceIn(t + MIN, 1f)
    return CropRect(l, t, r, b)
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

/** Where the frame sits in view pixels. */
internal fun frameRect(left: Float, top: Float, w: Float, h: Float) = Rect(Offset(left, top), Size(w, h))
