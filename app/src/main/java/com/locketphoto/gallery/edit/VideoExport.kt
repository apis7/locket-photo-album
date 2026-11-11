package com.locketphoto.gallery.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

data class VideoText(
    val text: String = "",
    val color: Int = Color.WHITE,
    /** Text height as a fraction of the output frame height. */
    val size: Float = 0.08f,
    val at: NPoint = NPoint(0.5f, 0.85f),
)

data class VideoEdit(
    val trimStartMs: Long = 0L,
    /** 0 = until the end. */
    val trimEndMs: Long = 0L,
    val geometry: Geometry = Geometry(),
    val filterId: String = Filters.none.id,
    val filterIntensity: Float = 1f,
    val text: VideoText = VideoText(),
    val muted: Boolean = false,
) {
    fun rotatedCw() = copy(geometry = geometry.rotatedCw(), text = text.copy(at = NPoint(1f - text.at.y, text.at.x)))
    fun mirrored() = copy(geometry = geometry.mirrored(), text = text.copy(at = NPoint(1f - text.at.x, text.at.y)))
}

/** Upright size, length and slow-motion flavour of a video file. */
data class VideoInfo(val width: Int, val height: Int, val durationMs: Long, val isSefSlowMo: Boolean)

@OptIn(UnstableApi::class)
object VideoExport {

    fun probe(context: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val upright = rot % 180 == 0
            return VideoInfo(if (upright) w else h, if (upright) h else w, dur, isSef(context, uri))
        } finally {
            r.release()
        }
    }

    /**
     * Samsung slow-motion clips carry a "SEF" trailer ending in the ASCII tag "SEFT".
     * Transformer can flatten those so the exported file actually plays slowly.
     */
    fun isSef(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { s ->
                val ch = s.channel
                val size = ch.size()
                if (size < 8) return@use false
                val buf = java.nio.ByteBuffer.allocate(4)
                ch.position(size - 4)
                ch.read(buf)
                String(buf.array(), Charsets.US_ASCII) == "SEFT"
            }
        } ?: false
    }.getOrDefault(false)

    /** A few evenly spaced frames for the trim filmstrip. */
    fun filmstrip(context: Context, uri: Uri, durationMs: Long, count: Int, heightPx: Int): List<Bitmap> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            (0 until count).mapNotNull { i ->
                val t = durationMs * (i + 0.5) / count
                val f = r.getFrameAtTime((t * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@mapNotNull null
                val k = heightPx.toFloat() / f.height
                Bitmap.createScaledBitmap(f, (f.width * k).roundToInt().coerceAtLeast(1), heightPx, true).also { if (it !== f) f.recycle() }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            r.release()
        }
    }

    private fun effects(edit: VideoEdit, info: VideoInfo): List<Effect> {
        val g = edit.geometry
        val out = mutableListOf<Effect>()
        // Media3 rotates counter-clockwise; ours are clockwise turns. Mirror after turning (view space).
        if (g.quarterTurns != 0) out += ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f * g.quarterTurns).build()
        if (g.mirror) out += ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build()
        val fw = g.frameW(info.width.toFloat(), info.height.toFloat())
        val fh = g.frameH(info.width.toFloat(), info.height.toFloat())
        var ow = fw
        var oh = fh
        if (!g.crop.isFull) {
            val c = g.crop
            out += Crop(2f * c.left - 1f, 2f * c.right - 1f, 1f - 2f * c.bottom, 1f - 2f * c.top)
            ow = fw * c.width
            oh = fh * c.height
            // Encoders want even sizes.
            out += Presentation.createForWidthAndHeight(evenOf(ow), evenOf(oh), Presentation.LAYOUT_STRETCH_TO_FIT)
        }
        if (edit.filterId != Filters.none.id && edit.filterIntensity > 0f) {
            out += SingleColorLut.createFromCube(ColorMath.lutCube(ColorMath.matrix(Adjust(), edit.filterId, edit.filterIntensity)))
        }
        if (edit.text.text.isNotBlank()) {
            val bmp = textBitmap(edit.text, oh)
            val settings = StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(2f * edit.text.at.x - 1f, 1f - 2f * edit.text.at.y)
                .build()
            out += OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bmp, settings)))
        }
        return out
    }

    private fun evenOf(v: Float) = (v.roundToInt() / 2 * 2).coerceAtLeast(2)

    fun textBitmap(t: VideoText, frameH: Float): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = max(8f, t.size * frameH)
            color = t.color
            val lum = (0.2126f * Color.red(t.color) + 0.7152f * Color.green(t.color) + 0.0722f * Color.blue(t.color)) / 255f
            setShadowLayer(textSize * 0.06f, 0f, textSize * 0.03f, if (lum > 0.5f) Color.argb(140, 0, 0, 0) else Color.argb(140, 255, 255, 255))
        }
        val fm = paint.fontMetrics
        val pad = paint.textSize * 0.2f
        val w = (paint.measureText(t.text) + pad * 2).roundToInt().coerceAtLeast(1)
        val h = (fm.descent - fm.ascent + pad * 2).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(t.text, pad, pad - fm.ascent, paint)
        return bmp
    }

    /**
     * Runs the export into [out]. Suspends until done; cancelling the coroutine cancels the export.
     * Must be called from a coroutine; Transformer itself runs on the main looper.
     */
    suspend fun export(context: Context, uri: Uri, edit: VideoEdit, info: VideoInfo, out: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            val clip = ExoItem.ClippingConfiguration.Builder()
            // Slow-mo flattening and clipping don't mix; the trim UI is off for SEF clips.
            if (!info.isSefSlowMo) {
                if (edit.trimStartMs > 0) clip.setStartPositionMs(edit.trimStartMs)
                if (edit.trimEndMs > 0 && edit.trimEndMs < info.durationMs) clip.setEndPositionMs(edit.trimEndMs)
            }
            val media = ExoItem.Builder().setUri(uri).setClippingConfiguration(clip.build()).build()
            val edited = EditedMediaItem.Builder(media)
                .setRemoveAudio(edit.muted)
                .setFlattenForSlowMotion(info.isSefSlowMo)
                .setEffects(Effects(emptyList(), effects(edit, info)))
                .build()

            coroutineScope {
                var transformer: Transformer? = null
                val ticker = launch {
                    val holder = ProgressHolder()
                    while (true) {
                        delay(150)
                        if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    }
                }
                try {
                    suspendCancellableCoroutine { cont ->
                        val t = Transformer.Builder(context)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (cont.isActive) cont.resume(Unit)
                                }

                                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                    if (cont.isActive) cont.resumeWithException(exportException)
                                }
                            })
                            .build()
                        transformer = t
                        cont.invokeOnCancellation { t.cancel() }
                        t.start(edited, out.absolutePath)
                    }
                } finally {
                    ticker.cancel()
                }
            }
        }
}
