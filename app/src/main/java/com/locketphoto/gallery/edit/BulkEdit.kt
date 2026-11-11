package com.locketphoto.gallery.edit

import android.content.Context
import com.locketphoto.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** The same edit applied to many photos at once. */
data class BulkSpec(
    /** Clockwise quarter turns, 0..3. */
    val quarterTurns: Int = 0,
    val mirror: Boolean = false,
    /** Per-photo auto enhance (each photo gets its own correction). */
    val auto: Boolean = false,
    val filterId: String = Filters.none.id,
    val filterIntensity: Float = 1f,
    val adjust: Adjust = Adjust(),
    /** true = replace the originals; false = save edited copies next to them. */
    val overwrite: Boolean = false,
) {
    val isNoop: Boolean
        get() = quarterTurns == 0 && !mirror && !auto && filterId == Filters.none.id && adjust.isIdentity
}

data class BulkResult(val done: Int, val failed: Int, val copiesInstead: Int)

object BulkEdit {

    /**
     * Renders and saves each photo in turn (videos are skipped by the caller).
     * Formats that can't be overwritten in place (HEIC, RAW…) are saved as copies instead.
     */
    suspend fun run(
        context: Context,
        items: List<MediaItem>,
        spec: BulkSpec,
        onProgress: (index: Int, total: Int) -> Unit,
    ): BulkResult = withContext(Dispatchers.Default) {
        var done = 0
        var failed = 0
        var copies = 0
        items.forEachIndexed { i, item ->
            coroutineContext.ensureActive()
            onProgress(i, items.size)
            val ok = runCatching {
                val src = PhotoRenderer.decode(context.contentResolver, item.uri)
                var adjust = spec.adjust
                if (spec.auto) {
                    val a = PhotoRenderer.autoEnhance(src)
                    adjust = adjust.copy(
                        brightness = (adjust.brightness + a.brightness).coerceIn(-1f, 1f),
                        contrast = (adjust.contrast + a.contrast).coerceIn(-1f, 1f),
                        saturation = (adjust.saturation + a.saturation).coerceIn(-1f, 1f),
                        warmth = (adjust.warmth + a.warmth).coerceIn(-1f, 1f),
                        tint = (adjust.tint + a.tint).coerceIn(-1f, 1f),
                        highlights = (adjust.highlights + a.highlights).coerceIn(-1f, 1f),
                        shadows = (adjust.shadows + a.shadows).coerceIn(-1f, 1f),
                    )
                }
                var edit = PhotoEdit(adjust = adjust, filterId = spec.filterId, filterIntensity = spec.filterIntensity)
                repeat(spec.quarterTurns % 4) { edit = edit.rotatedCw() }
                if (spec.mirror) edit = edit.mirrored()
                val out = PhotoRenderer.render(src, edit)
                if (out !== src) src.recycle()
                try {
                    if (spec.overwrite && MediaSaver.canOverwritePhoto(item.mimeType)) {
                        MediaSaver.overwritePhoto(context, item, out)
                    } else {
                        MediaSaver.savePhotoCopy(context, item, out)
                        if (spec.overwrite) copies++
                    }
                } finally {
                    out.recycle()
                }
            }.isSuccess
            if (ok) done++ else failed++
        }
        onProgress(items.size, items.size)
        BulkResult(done, failed, copies)
    }
}
