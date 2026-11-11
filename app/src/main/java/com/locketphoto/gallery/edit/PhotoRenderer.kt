package com.locketphoto.gallery.edit

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PhotoRenderer {

    /** Keeps the export within a sane memory budget (about 100 MB per ARGB copy). */
    const val MAX_EXPORT_PIXELS = 25_000_000L

    /**
     * Decode with EXIF orientation applied (ImageDecoder does that itself).
     * [maxSide] downsizes for previews; [maxPixels] caps full-size decodes.
     */
    fun decode(resolver: ContentResolver, uri: Uri, maxSide: Int = 0, maxPixels: Long = MAX_EXPORT_PIXELS): Bitmap {
        val src = ImageDecoder.createSource(resolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val w = info.size.width.toLong()
            val h = info.size.height.toLong()
            var sample = 1
            if (maxSide > 0) {
                while (max(w, h) / (sample * 2) >= maxSide) sample *= 2
            }
            if (w * h / (sample.toLong() * sample) > maxPixels) {
                sample = max(sample, ceil(sqrt((w * h).toDouble() / maxPixels)).toInt())
            }
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        val out = if (bmp.config != Bitmap.Config.ARGB_8888) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
        if (maxSide > 0 && max(out.width, out.height) > maxSide) {
            val k = maxSide.toFloat() / max(out.width, out.height)
            return Bitmap.createScaledBitmap(out, (out.width * k).roundToInt().coerceAtLeast(1), (out.height * k).roundToInt().coerceAtLeast(1), true)
        }
        return out
    }

    /** The finished picture: geometry + colour + tone + vignette + markup. */
    fun render(src: Bitmap, edit: PhotoEdit, onProgress: (Float) -> Unit = {}): Bitmap {
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()
        val g = edit.geometry
        val ow = g.outW(sw, sh).roundToInt().coerceAtLeast(1)
        val oh = g.outH(sw, sh).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val m = ColorMath.matrix(edit.adjust, edit.filterId, edit.filterIntensity)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            if (!ColorMath.isIdentity(m)) colorFilter = ColorMatrixColorFilter(m)
        }
        canvas.drawBitmap(src, g.outputMatrix(sw, sh), paint)
        onProgress(0.4f)
        if (edit.adjust.hasTone) applyTone(out, edit.adjust.highlights, edit.adjust.shadows) { onProgress(0.4f + it * 0.5f) }
        drawVignette(canvas, ow.toFloat(), oh.toFloat(), edit.adjust.vignette)
        if (!edit.markup.isEmpty) {
            val fw = g.frameW(sw, sh)
            val fh = g.frameH(sw, sh)
            canvas.save()
            canvas.translate(-g.crop.left * fw, -g.crop.top * fh)
            MarkupPainter().draw(canvas, edit.markup, fw, fh)
            canvas.restore()
        }
        onProgress(1f)
        return out
    }

    fun applyTone(bmp: Bitmap, highlights: Float, shadows: Float, onProgress: (Float) -> Unit = {}) {
        val table = IntArray(256) { (ColorMath.toneDelta(it / 255f, highlights, shadows) * 255f).roundToInt() }
        val w = bmp.width
        val rows = 64
        val buf = IntArray(w * rows)
        var y = 0
        while (y < bmp.height) {
            val n = min(rows, bmp.height - y)
            bmp.getPixels(buf, 0, w, 0, y, w, n)
            for (i in 0 until w * n) {
                val c = buf[i]
                val r = (c shr 16) and 0xFF
                val gg = (c shr 8) and 0xFF
                val b = c and 0xFF
                val d = table[(r * 54 + gg * 183 + b * 19) shr 8]
                buf[i] = (c and -0x1000000) or
                    ((r + d).coerceIn(0, 255) shl 16) or
                    ((gg + d).coerceIn(0, 255) shl 8) or
                    (b + d).coerceIn(0, 255)
            }
            bmp.setPixels(buf, 0, w, 0, y, w, n)
            y += n
            onProgress(y.toFloat() / bmp.height)
        }
    }

    /** Darkens (amount > 0) or brightens (amount < 0) the edges. Same look in preview and export. */
    fun drawVignette(canvas: Canvas, w: Float, h: Float, amount: Float) {
        if (amount == 0f) return
        val radius = sqrt(w * w + h * h) / 2f
        val edge = if (amount > 0) Color.BLACK else Color.WHITE
        val a = (kotlin.math.abs(amount) * 0.8f * 255f).roundToInt().coerceIn(0, 255)
        val paint = Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, radius,
                intArrayOf(Color.argb(0, Color.red(edge), Color.green(edge), Color.blue(edge)), Color.argb(0, Color.red(edge), Color.green(edge), Color.blue(edge)), Color.argb(a, Color.red(edge), Color.green(edge), Color.blue(edge))),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    /** One-tap enhancement: levels, exposure, colour balance and a touch of saturation. */
    fun autoEnhance(bmp: Bitmap): Adjust {
        val small = if (max(bmp.width, bmp.height) > 256) {
            val k = 256f / max(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt().coerceAtLeast(1), (bmp.height * k).roundToInt().coerceAtLeast(1), true)
        } else bmp
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val hist = IntArray(256)
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var sat = 0.0
        var dark = 0; var bright = 0
        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (r * 54 + g * 183 + b * 19) shr 8
            hist[l]++
            sr += r; sg += g; sb += b
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            if (mx > 0) sat += (mx - mn).toDouble() / mx
            if (l < 40) dark++
            if (l > 230) bright++
        }
        val n = px.size.coerceAtLeast(1)
        fun pct(p: Float): Float {
            val target = (n * p).toInt()
            var acc = 0
            for (i in 0..255) { acc += hist[i]; if (acc > target) return i / 255f }
            return 1f
        }
        val lo = pct(0.005f)
        val hi = pct(0.995f)
        val range = (hi - lo).coerceAtLeast(0.2f)
        val k = (1f / range).coerceIn(1f, 1.5f)
        val contrast = ((k - 1f) / 0.7f).coerceIn(0f, 0.5f)
        val mean = ((sr * 0.2126 + sg * 0.7152 + sb * 0.0722) / n / 255.0).toFloat()
        val kEff = 1f + contrast * 0.7f
        val meanAfter = kEff * (mean - 0.5f) + 0.5f
        val brightness = ((0.48f - meanAfter) / 0.22f * 0.6f).coerceIn(-0.35f, 0.35f)
        val avgSat = (sat / n).toFloat()
        val saturation = if (avgSat < 0.35f) ((0.35f - avgSat) * 0.8f).coerceIn(0.05f, 0.3f) else 0.05f
        val mr = (sr / n / 255.0).toFloat(); val mg = (sg / n / 255.0).toFloat(); val mb = (sb / n / 255.0).toFloat()
        val warmth = ((mb - mr) * 2.5f).coerceIn(-0.3f, 0.3f)
        val tint = ((mg - (mr + mb) / 2f) * 3f).coerceIn(-0.2f, 0.2f)
        val shadows = if (dark.toFloat() / n > 0.2f) 0.3f else 0f
        val highlights = if (bright.toFloat() / n > 0.12f) -0.3f else 0f
        if (small !== bmp) small.recycle()
        return Adjust(
            brightness = brightness.round2(),
            contrast = contrast.round2(),
            saturation = saturation.round2(),
            warmth = warmth.round2(),
            tint = tint.round2(),
            highlights = highlights,
            shadows = shadows,
        )
    }

    private fun Float.round2() = (this * 100f).roundToInt() / 100f
}

/** Draws pen/highlighter strokes and text in frame pixel space. Used by preview and export. */
/** Not thread-safe: keep one per thread (preview on main, export on a worker). */
class MarkupPainter {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun draw(canvas: Canvas, markup: Markup, fw: Float, fh: Float) {
        val unit = min(fw, fh)
        for (s in markup.strokes) drawStroke(canvas, s, fw, fh, unit)
        for (t in markup.texts) drawText(canvas, t, fw, fh, unit)
    }

    fun drawStroke(canvas: Canvas, s: Stroke, fw: Float, fh: Float, unit: Float) {
        if (s.points.isEmpty()) return
        strokePaint.color = s.color
        strokePaint.alpha = if (s.highlighter) 100 else 255
        strokePaint.strokeWidth = s.width * unit
        strokePaint.strokeCap = if (s.highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
        val path = Path()
        val p0 = s.points[0]
        path.moveTo(p0.x * fw, p0.y * fh)
        if (s.points.size == 1) {
            path.lineTo(p0.x * fw + 0.01f, p0.y * fh)
        } else {
            for (i in 1 until s.points.size) {
                val a = s.points[i - 1]
                val b = s.points[i]
                path.quadTo(a.x * fw, a.y * fh, (a.x + b.x) / 2f * fw, (a.y + b.y) / 2f * fh)
            }
            val last = s.points.last()
            path.lineTo(last.x * fw, last.y * fh)
        }
        canvas.drawPath(path, strokePaint)
    }

    fun drawText(canvas: Canvas, t: TextMark, fw: Float, fh: Float, unit: Float) {
        textPaint.textSize = t.size * unit
        textPaint.color = t.color
        val shadow = if (luminance(t.color) > 0.5f) Color.argb(140, 0, 0, 0) else Color.argb(140, 255, 255, 255)
        textPaint.setShadowLayer(textPaint.textSize * 0.06f, 0f, textPaint.textSize * 0.03f, shadow)
        val fm = textPaint.fontMetrics
        val baseline = t.at.y * fh - (fm.ascent + fm.descent) / 2f
        canvas.drawText(t.text, t.at.x * fw, baseline, textPaint)
    }

    /** Half-size of a text mark in frame pixels, for hit testing. */
    fun textHalfSize(t: TextMark, unit: Float): Pair<Float, Float> {
        textPaint.textSize = t.size * unit
        return textPaint.measureText(t.text) / 2f to textPaint.textSize * 0.6f
    }

    private fun luminance(c: Int) = (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f
}
