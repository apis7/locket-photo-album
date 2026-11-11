package com.locketphoto.gallery.edit

import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A crop in normalised (0..1) coordinates of the rotated/mirrored frame. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width get() = right - left
    val height get() = bottom - top
    val isFull get() = left <= 0.0005f && top <= 0.0005f && right >= 0.9995f && bottom >= 0.9995f

    /** The same region after the frame turns 90 degrees clockwise. */
    fun rotatedCw() = CropRect(1f - bottom, left, 1f - top, right)
    fun mirrored() = CropRect(1f - right, top, 1f - left, bottom)

    companion object {
        val Full = CropRect()

        /** Largest centred crop of [ratio] (w/h in pixels) in a frame of fw x fh. */
        fun centered(ratio: Float, fw: Float, fh: Float): CropRect {
            val frameRatio = fw / fh
            return if (ratio > frameRatio) {
                val h = frameRatio / ratio
                CropRect(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
            } else {
                val w = ratio / frameRatio
                CropRect((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
            }
        }
    }
}

enum class AspectChoice(val label: String) {
    Free("Free"), Original("Original"), Square("1:1"), R4x3("4:3"), R3x4("3:4"), R16x9("16:9"), R9x16("9:16");

    /** Pixel ratio (w/h), or null for free. */
    fun ratio(originalRatio: Float): Float? = when (this) {
        Free -> null
        Original -> originalRatio
        Square -> 1f
        R4x3 -> 4f / 3f
        R3x4 -> 3f / 4f
        R16x9 -> 16f / 9f
        R9x16 -> 9f / 16f
    }
}

/** Rotation, mirror, straighten and crop. Shared by photo and video edits. */
data class Geometry(
    /** Clockwise quarter turns, 0..3. */
    val quarterTurns: Int = 0,
    val mirror: Boolean = false,
    /** Fine rotation in degrees, -45..45. Photos only. */
    val straighten: Float = 0f,
    val crop: CropRect = CropRect.Full,
    val aspect: AspectChoice = AspectChoice.Free,
) {
    val isIdentity get() = quarterTurns == 0 && !mirror && straighten == 0f && crop.isFull

    /** Frame size (after quarter turns) for a source of sw x sh. */
    fun frameW(sw: Float, sh: Float) = if (quarterTurns % 2 == 1) sh else sw
    fun frameH(sw: Float, sh: Float) = if (quarterTurns % 2 == 1) sw else sh

    /** Output size in source pixels. */
    fun outW(sw: Float, sh: Float) = frameW(sw, sh) * crop.width
    fun outH(sw: Float, sh: Float) = frameH(sw, sh) * crop.height

    /**
     * Maps source pixels to frame pixels: quarter turns, mirror, then straighten (zoomed just
     * enough that the tilted picture still covers the whole frame — no empty corners).
     */
    fun frameMatrix(sw: Float, sh: Float): Matrix {
        val fw = frameW(sw, sh)
        val fh = frameH(sw, sh)
        return Matrix().apply {
            postTranslate(-sw / 2f, -sh / 2f)
            postRotate(90f * quarterTurns)
            if (mirror) postScale(-1f, 1f)
            if (straighten != 0f) {
                postRotate(straighten)
                val s = coverScale(straighten, fw, fh)
                postScale(s, s)
            }
            postTranslate(fw / 2f, fh / 2f)
        }
    }

    /** Maps source pixels to output pixels (the crop's top-left becomes 0,0). */
    fun outputMatrix(sw: Float, sh: Float): Matrix = frameMatrix(sw, sh).apply {
        postTranslate(-crop.left * frameW(sw, sh), -crop.top * frameH(sw, sh))
    }

    fun rotatedCw() = copy(
        quarterTurns = (quarterTurns + 1) % 4,
        crop = crop.rotatedCw(),
        aspect = when (aspect) {
            AspectChoice.R4x3 -> AspectChoice.R3x4
            AspectChoice.R3x4 -> AspectChoice.R4x3
            AspectChoice.R16x9 -> AspectChoice.R9x16
            AspectChoice.R9x16 -> AspectChoice.R16x9
            else -> aspect
        },
    )

    fun mirrored() = copy(mirror = !mirror, crop = crop.mirrored(), straighten = -straighten)

    companion object {
        fun coverScale(deg: Float, w: Float, h: Float): Float {
            val r = Math.toRadians(deg.toDouble())
            val c = abs(cos(r)).toFloat()
            val s = abs(sin(r)).toFloat()
            return max((w * c + h * s) / w, (w * s + h * c) / h)
        }
    }
}
