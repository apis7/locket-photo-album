package com.locketphoto.gallery.edit

/** A point in normalised frame coordinates (0..1 of the rotated, uncropped frame). */
data class NPoint(val x: Float, val y: Float)

enum class MarkTool { Pen, Highlighter, Text, Eraser }

data class Stroke(
    val points: List<NPoint>,
    val color: Int,
    /** Width as a fraction of the frame's shorter side. */
    val width: Float,
    val highlighter: Boolean,
)

data class TextMark(
    val text: String,
    val at: NPoint,
    val color: Int,
    /** Text height as a fraction of the frame's shorter side. */
    val size: Float,
)

data class Markup(val strokes: List<Stroke> = emptyList(), val texts: List<TextMark> = emptyList()) {
    val isEmpty get() = strokes.isEmpty() && texts.isEmpty()

    /** Keep annotations attached to the picture when the frame turns or flips. */
    fun map(f: (NPoint) -> NPoint) = Markup(
        strokes.map { s -> s.copy(points = s.points.map(f)) },
        texts.map { t -> t.copy(at = f(t.at)) },
    )
}

/** Everything about a photo edit. Immutable, so undo/redo is just a list of these. */
data class PhotoEdit(
    val adjust: Adjust = Adjust(),
    val filterId: String = Filters.none.id,
    val filterIntensity: Float = 1f,
    val geometry: Geometry = Geometry(),
    val markup: Markup = Markup(),
    /** The adjustments before Auto was switched on, so switching it off restores them. */
    val autoFrom: Adjust? = null,
) {
    val autoOn get() = autoFrom != null

    fun rotatedCw() = copy(geometry = geometry.rotatedCw(), markup = markup.map { NPoint(1f - it.y, it.x) })
    fun mirrored() = copy(geometry = geometry.mirrored(), markup = markup.map { NPoint(1f - it.x, it.y) })
}
