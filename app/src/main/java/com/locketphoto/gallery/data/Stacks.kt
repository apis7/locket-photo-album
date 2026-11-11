package com.locketphoto.gallery.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * "Stack similar photos": groups bursts and near-duplicates taken moments apart.
 *
 * Each photo gets a 64-bit difference hash (dHash) from the system's small thumbnail —
 * cheap, on-device, cached in the local DB so it's computed once per photo. Neighbours in
 * time (within [WINDOW_MS]) whose hashes differ in at most [MAX_DISTANCE] bits are stacked.
 */
object Stacks {
    private const val WINDOW_MS = 90_000L
    private const val MAX_DISTANCE = 11

    /** Returns top id -> member ids (newest first), only for groups of 2+. */
    suspend fun compute(
        resolver: ContentResolver,
        store: LibraryStore,
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Map<Long, List<Long>> = withContext(Dispatchers.IO) {
        val cached = store.cachedHashes()
        val hashes = HashMap<Long, Long>(items.size)
        val fresh = ArrayList<Triple<Long, Long, Long>>()
        // Only photos that have a neighbour within the time window can ever stack; skip the rest.
        val candidates = items.filterIndexed { i, it ->
            !it.isVideo && (
                (i > 0 && !items[i - 1].isVideo && items[i - 1].takenAt - it.takenAt <= WINDOW_MS) ||
                    (i < items.lastIndex && !items[i + 1].isVideo && it.takenAt - items[i + 1].takenAt <= WINDOW_MS)
                )
        }
        candidates.forEachIndexed { n, item ->
            coroutineContext.ensureActive()
            val hit = cached[item.id]
            if (hit != null && hit.first == item.modifiedAt) {
                hashes[item.id] = hit.second
            } else {
                runCatching { dHash(resolver.loadThumbnail(item.uri, Size(96, 96), null)) }.getOrNull()?.let {
                    hashes[item.id] = it
                    fresh += Triple(item.id, item.modifiedAt, it)
                }
                if (fresh.size >= 200) {
                    store.saveHashes(fresh)
                    fresh.clear()
                }
            }
            if (n % 25 == 0) onProgress(n, candidates.size)
        }
        if (fresh.isNotEmpty()) store.saveHashes(fresh)
        onProgress(candidates.size, candidates.size)

        val out = LinkedHashMap<Long, List<Long>>()
        var group = ArrayList<MediaItem>()
        fun flush() {
            if (group.size > 1) out[group.first().id] = group.map { it.id }
            group = ArrayList()
        }
        for (item in items) {
            val h = hashes[item.id]
            val last = group.lastOrNull()
            val joins = h != null && last != null &&
                last.takenAt - item.takenAt <= WINDOW_MS &&
                java.lang.Long.bitCount(h xor (hashes[last.id] ?: 0L)) <= MAX_DISTANCE
            if (!joins) flush()
            if (h != null) group += item else flush()
        }
        flush()
        out
    }

    /** 9x8 grayscale, compare horizontal neighbours -> 64 bits. */
    private fun dHash(src: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(src, 9, 8, true)
        val px = IntArray(72)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        if (small !== src) small.recycle()
        src.recycle()
        var bits = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (luma(px[y * 9 + x]) > luma(px[y * 9 + x + 1])) bits = bits or (1L shl bit)
            bit++
        }
        return bits
    }

    private fun luma(c: Int): Int = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
}
