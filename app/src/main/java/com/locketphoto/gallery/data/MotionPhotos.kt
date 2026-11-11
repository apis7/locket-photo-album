package com.locketphoto.gallery.data

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Motion photos (Google "Motion Photo"/"MicroVideo", Samsung "Motion photo") are a JPEG with a
 * short MP4 appended. We detect them cheaply (look at the XMP near the start and the trailer at
 * the end), then copy the MP4 part to the cache once so it can be played.
 */
object MotionPhotos {
    private val HEAD_MARKERS = listOf("MotionPhoto", "MicroVideo", "motionphoto").map { it.toByteArray() }
    private val TAIL_MARKERS = listOf("MotionPhoto_Data").map { it.toByteArray() }
    private val BRANDS = listOf("mp4", "iso", "qt  ", "avc", "3gp", "hei", "msnv").map { it.toByteArray() }

    /** Returns a playable MP4 file for this photo, or null if it isn't a motion photo. */
    fun videoFor(context: Context, item: MediaItem): File? {
        if (item.isVideo || !item.mimeType.contains("jpeg", ignoreCase = true) && !item.mimeType.contains("heic", true)) return null
        val dir = File(context.cacheDir, "motion").apply { mkdirs() }
        val out = File(dir, "${item.id}_${item.modifiedAt}.mp4")
        if (out.exists() && out.length() > 0) return out
        val noneMarker = File(dir, "${item.id}_${item.modifiedAt}.none")
        if (noneMarker.exists()) return null

        val result = runCatching { extract(context, item, out) }.getOrNull()
        if (result == null) runCatching { noneMarker.createNewFile() }
        return result
    }

    private fun extract(context: Context, item: MediaItem, out: File): File? {
        val pfd = context.contentResolver.openFileDescriptor(item.uri, "r") ?: return null
        pfd.use {
            val path = "/proc/self/fd/${it.fd}"
            RandomAccessFile(path, "r").use { f ->
                val len = f.length()
                if (len < 1024) return null
                val head = ByteArray(minOf(len, 256 * 1024L).toInt()).also { b -> f.seek(0); f.readFully(b) }
                val tail = ByteArray(minOf(len, 16 * 1024L).toInt()).also { b -> f.seek(len - b.size); f.readFully(b) }
                val candidate = HEAD_MARKERS.any { m -> indexOf(head, m, 0) >= 0 } || TAIL_MARKERS.any { m -> indexOf(tail, m, 0) >= 0 }
                if (!candidate) return null

                // The embedded video starts 4 bytes before its "ftyp" box. Scan from the end in chunks.
                val chunk = 1 shl 20
                var end = len
                val ftyp = "ftyp".toByteArray()
                while (end > 0) {
                    val start = maxOf(0L, end - chunk - 16)
                    val buf = ByteArray((end - start).toInt())
                    f.seek(start)
                    f.readFully(buf)
                    var i = lastIndexOf(buf, ftyp, buf.size - ftyp.size)
                    while (i >= 4) {
                        val brandOk = i + 8 <= buf.size && BRANDS.any { b -> startsWith(buf, i + 4, b) }
                        val size = ((buf[i - 4].toInt() and 0xFF) shl 24) or ((buf[i - 3].toInt() and 0xFF) shl 16) or
                            ((buf[i - 2].toInt() and 0xFF) shl 8) or (buf[i - 1].toInt() and 0xFF)
                        val abs = start + i - 4
                        if (brandOk && size in 8..256 && abs > 1024) {
                            f.seek(abs)
                            val tmp = File(out.path + ".tmp")
                            tmp.outputStream().use { o ->
                                val b = ByteArray(64 * 1024)
                                var left = len - abs
                                while (left > 0) {
                                    val n = f.read(b, 0, minOf(b.size.toLong(), left).toInt())
                                    if (n <= 0) break
                                    o.write(b, 0, n)
                                    left -= n
                                }
                            }
                            return if (tmp.renameTo(out)) out else null
                        }
                        i = lastIndexOf(buf, ftyp, i - 1)
                    }
                    if (start == 0L) break
                    end = start + 16
                }
                return null
            }
        }
    }

    private fun startsWith(a: ByteArray, at: Int, p: ByteArray): Boolean {
        if (at + p.size > a.size) return false
        for (k in p.indices) if (a[at + k] != p[k]) return false
        return true
    }

    private fun indexOf(a: ByteArray, p: ByteArray, from: Int): Int {
        var i = from
        while (i <= a.size - p.size) {
            if (startsWith(a, i, p)) return i
            i++
        }
        return -1
    }

    private fun lastIndexOf(a: ByteArray, p: ByteArray, from: Int): Int {
        var i = minOf(from, a.size - p.size)
        while (i >= 0) {
            if (startsWith(a, i, p)) return i
            i--
        }
        return -1
    }
}
