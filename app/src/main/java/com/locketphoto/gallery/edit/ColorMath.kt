package com.locketphoto.gallery.edit

import android.graphics.ColorMatrix
import kotlin.math.pow

/**
 * The colour side of editing, shared by photos (Android ColorMatrix on a Canvas) and videos
 * (the same matrix baked into a Media3 3D LUT). Every value here is a plain 4x5 colour matrix,
 * so a preview and an export always match.
 */
data class Adjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val vignette: Float = 0f,
) {
    val hasTone: Boolean get() = highlights != 0f || shadows != 0f
    val isIdentity: Boolean get() = this == Adjust()

    fun get(kind: AdjustKind): Float = when (kind) {
        AdjustKind.Brightness -> brightness
        AdjustKind.Contrast -> contrast
        AdjustKind.Saturation -> saturation
        AdjustKind.Warmth -> warmth
        AdjustKind.Tint -> tint
        AdjustKind.Highlights -> highlights
        AdjustKind.Shadows -> shadows
        AdjustKind.Vignette -> vignette
    }

    fun with(kind: AdjustKind, v: Float): Adjust = when (kind) {
        AdjustKind.Brightness -> copy(brightness = v)
        AdjustKind.Contrast -> copy(contrast = v)
        AdjustKind.Saturation -> copy(saturation = v)
        AdjustKind.Warmth -> copy(warmth = v)
        AdjustKind.Tint -> copy(tint = v)
        AdjustKind.Highlights -> copy(highlights = v)
        AdjustKind.Shadows -> copy(shadows = v)
        AdjustKind.Vignette -> copy(vignette = v)
    }
}

enum class AdjustKind(val label: String, val needsShader: Boolean = false) {
    Brightness("Brightness"),
    Contrast("Contrast"),
    Saturation("Saturation"),
    Warmth("Warmth"),
    Tint("Tint"),
    Highlights("Highlights", needsShader = true),
    Shadows("Shadows", needsShader = true),
    Vignette("Vignette"),
}

/** A tasteful preset. [build] returns the full-strength matrix; intensity blends it with identity. */
class Filter(val id: String, val name: String, val build: () -> ColorMatrix)

object Filters {
    val none = Filter("none", "Original") { ColorMatrix() }

    val all: List<Filter> = listOf(
        none,
        Filter("vivid", "Vivid") { chain(saturation(1.35f), contrast(1.12f)) },
        Filter("warm", "Warm") { chain(scale(1.08f, 1.02f, 0.88f), saturation(1.06f)) },
        Filter("cool", "Cool") { chain(scale(0.92f, 1.0f, 1.1f), saturation(0.98f)) },
        Filter("mono", "Mono") { saturation(0f) },
        Filter("noir", "Noir") { chain(contrast(1.45f), offset(-0.03f), saturation(0f)) },
        Filter("fade", "Fade") { chain(offset(0.07f), contrast(0.82f), saturation(0.82f)) },
        Filter("vintage", "Vintage") { chain(offset(0.04f), contrast(0.9f), blend(sepia(), 0.55f)) },
        Filter("film", "Film") { chain(offset(0.025f), contrast(1.06f), scale(1.04f, 1.0f, 0.94f), saturation(0.9f)) },
        Filter("matte", "Matte") { chain(offset(0.06f), contrast(0.86f), saturation(0.94f)) },
        Filter("drama", "Drama") { chain(offset(-0.03f), contrast(1.3f), saturation(0.8f)) },
    )

    fun byId(id: String): Filter = all.firstOrNull { it.id == id } ?: none
}

object ColorMath {
    /** Filter (at [intensity]) first, then the user's adjustments on top. */
    fun matrix(adjust: Adjust, filterId: String, intensity: Float): ColorMatrix {
        val f = blend(Filters.byId(filterId).build(), intensity)
        return chain(adjustMatrix(adjust), f)
    }

    fun adjustMatrix(a: Adjust): ColorMatrix = chain(
        offset(a.brightness * 0.22f),
        contrast(if (a.contrast >= 0f) 1f + a.contrast * 0.7f else 1f + a.contrast * 0.55f),
        saturation((1f + a.saturation).coerceAtLeast(0f)),
        scale(1f + a.warmth * 0.12f, 1f - a.tint * 0.1f, 1f - a.warmth * 0.12f),
    )

    /**
     * Highlights/shadows: luminance-weighted lift or fall. Same formula as the AGSL preview
     * shader in the editor (keep them in sync).
     */
    fun toneDelta(luma: Float, highlights: Float, shadows: Float): Float {
        val ws = (1f - luma).pow(2) * 0.3f
        val wh = luma.pow(2) * 0.3f
        return shadows * ws + highlights * wh
    }

    /** Apply a 4x5 matrix to one colour (0..1 floats); returns clamped 0..1. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float, out: FloatArray) {
        out[0] = (m[0] * r + m[1] * g + m[2] * b + m[4] / 255f).coerceIn(0f, 1f)
        out[1] = (m[5] * r + m[6] * g + m[7] * b + m[9] / 255f).coerceIn(0f, 1f)
        out[2] = (m[10] * r + m[11] * g + m[12] * b + m[14] / 255f).coerceIn(0f, 1f)
    }

    /** A Media3-ready LUT cube: cube[r][g][b] = ARGB of the mapped colour. */
    fun lutCube(m: ColorMatrix, size: Int = 33): Array<Array<IntArray>> {
        val a = m.array
        val out = FloatArray(3)
        val step = 1f / (size - 1)
        return Array(size) { ri ->
            Array(size) { gi ->
                IntArray(size) { bi ->
                    apply(a, ri * step, gi * step, bi * step, out)
                    (0xFF shl 24) or
                        ((out[0] * 255f + 0.5f).toInt() shl 16) or
                        ((out[1] * 255f + 0.5f).toInt() shl 8) or
                        (out[2] * 255f + 0.5f).toInt()
                }
            }
        }
    }

    fun isIdentity(m: ColorMatrix): Boolean = m.array.contentEquals(ColorMatrix().array)
}

// ---- matrix building blocks (offsets are in 0..1 units) ----

internal fun chain(vararg outerToInner: ColorMatrix): ColorMatrix {
    // chain(a, b, c) applies c first, then b, then a.
    val acc = ColorMatrix()
    for (m in outerToInner.reversed()) acc.postConcat(m)
    return acc
}

internal fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

internal fun scale(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }

internal fun offset(o: Float) = ColorMatrix(
    floatArrayOf(
        1f, 0f, 0f, 0f, o * 255f,
        0f, 1f, 0f, 0f, o * 255f,
        0f, 0f, 1f, 0f, o * 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

internal fun contrast(k: Float): ColorMatrix {
    val t = (1f - k) * 0.5f * 255f
    return ColorMatrix(
        floatArrayOf(
            k, 0f, 0f, 0f, t,
            0f, k, 0f, 0f, t,
            0f, 0f, k, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

internal fun sepia() = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Linear blend between identity (t = 0) and [m] (t = 1). */
internal fun blend(m: ColorMatrix, t: Float): ColorMatrix {
    val id = ColorMatrix().array
    val a = m.array
    return ColorMatrix(FloatArray(20) { id[it] + (a[it] - id[it]) * t })
}
