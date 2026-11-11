package com.locketphoto.gallery.ui.grid

import com.locketphoto.gallery.data.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface GridEntry {
    val key: String

    /** Short label for the scroll bubble, e.g. "Nov 2026". */
    val scrubLabel: String

    data class Header(override val key: String, val title: String, override val scrubLabel: String) : GridEntry

    /**
     * One cell. [index] is the item's position in the screen's full item list (what the
     * viewer pages through). [stack] holds every id in a similar-photo stack (size 1 if none).
     */
    data class Cell(val item: MediaItem, val index: Int, override val scrubLabel: String, val stack: List<Long> = listOf(item.id)) : GridEntry {
        override val key: String get() = "m:${item.id}"
    }
}

data class GridUiState(
    val loading: Boolean = true,
    val entries: List<GridEntry> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    /** Position in [entries] of each item in [items] (-1 if hidden inside a stack). */
    val entryIndexOfItem: IntArray = IntArray(0),
)

/**
 * Turns a newest-first list into month sections. If [stacks] is given (top id -> member ids,
 * members consecutive in [items]), each stack shows as its top photo with a count badge.
 */
fun buildGridState(items: List<MediaItem>, stacks: Map<Long, List<Long>> = emptyMap(), monthHeaders: Boolean = true): GridUiState {
    val zone = ZoneId.systemDefault()
    val thisYear = LocalDate.now(zone).year
    val locale = Locale.getDefault()
    val monthFull = DateTimeFormatter.ofPattern("MMMM", locale)
    val monthYearFull = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val monthYearShort = DateTimeFormatter.ofPattern("MMM yyyy", locale)

    val hidden = HashSet<Long>()
    stacks.forEach { (top, members) -> members.forEach { if (it != top) hidden += it } }

    val out = ArrayList<GridEntry>(items.size + items.size / 20 + 1)
    val pos = IntArray(items.size) { -1 }
    var currentKey = -1
    var currentShort = ""
    items.forEachIndexed { index, item ->
        val date = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()
        val key = date.year * 100 + date.monthValue
        if (key != currentKey) {
            currentKey = key
            currentShort = monthYearShort.format(date)
            if (monthHeaders) {
                val title = if (date.year == thisYear) monthFull.format(date) else monthYearFull.format(date)
                out += GridEntry.Header("h:$key", title, currentShort)
            }
        }
        if (item.id in hidden) return@forEachIndexed
        pos[index] = out.size
        out += GridEntry.Cell(item, index, currentShort, stacks[item.id] ?: listOf(item.id))
    }
    return GridUiState(loading = false, entries = out, items = items, entryIndexOfItem = pos)
}

/**
 * Multi-select for whichever grid is on screen. Drag ranges are in item-index space of the
 * list passed to [startDrag].
 */
class GridSelection {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    private var base: Set<Long> = emptySet()
    private var anchor = -1
    private var adding = true
    private var dragItems: List<MediaItem> = emptyList()
    private var dragStacks: Map<Long, List<Long>> = emptyMap()

    fun toggle(ids: Collection<Long>) = _ids.update { cur -> if (ids.all { it in cur }) cur - ids.toSet() else cur + ids }
    fun clear() { _ids.value = emptySet() }
    fun set(ids: Collection<Long>) { _ids.value = LinkedHashSet(ids) }
    fun retainOnly(valid: Set<Long>) = _ids.update { cur -> cur.filterTo(LinkedHashSet()) { it in valid } }

    fun startDrag(index: Int, items: List<MediaItem>, stacks: Map<Long, List<Long>>) {
        val item = items.getOrNull(index) ?: return
        dragItems = items
        dragStacks = stacks
        base = _ids.value
        anchor = index
        adding = item.id !in base
        dragTo(index)
    }

    fun dragTo(index: Int) {
        if (anchor < 0) return
        val lo = minOf(anchor, index).coerceAtLeast(0)
        val hi = maxOf(anchor, index).coerceAtMost(dragItems.lastIndex)
        val range = HashSet<Long>()
        for (i in lo..hi) {
            val id = dragItems[i].id
            range += dragStacks[id] ?: listOf(id)
        }
        _ids.value = if (adding) base + range else base - range
    }

    fun endDrag() { anchor = -1 }
}
