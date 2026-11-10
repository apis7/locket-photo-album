package com.locketphoto.gallery.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String,
    /** Best-known capture time, epoch millis. */
    val takenAt: Long,
    val modifiedAt: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    /** Clockwise rotation (0/90/180/270) to apply to the stored pixels. */
    val orientation: Int,
    val bucket: String?,
    val isFavorite: Boolean,
) {
    /** Width/height as the photo is meant to be seen (after rotation); 0 if unknown. */
    val displayWidth: Int get() = if (orientation % 180 == 90) height else width
    val displayHeight: Int get() = if (orientation % 180 == 90) width else height
    val aspect: Float get() = if (displayWidth > 0 && displayHeight > 0) displayWidth.toFloat() / displayHeight else 1f
}
