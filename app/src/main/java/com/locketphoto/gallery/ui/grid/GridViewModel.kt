package com.locketphoto.gallery.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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

    data class Cell(val item: MediaItem, val index: Int, override val scrubLabel: String) : GridEntry {
        override val key: String get() = "m:${item.id}"
    }
}

data class GridUiState(
    val loading: Boolean = true,
    val entries: List<GridEntry> = emptyList(),
    val items: List<MediaItem> = emptyList(),
)

class GridViewModel(repo: MediaRepository) : ViewModel() {

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val state: StateFlow<GridUiState> = repo.observeAll()
        .map { items -> GridUiState(loading = false, entries = buildEntries(items), items = items) }
        .onEach { s -> // drop selections whose files vanished
            val ids = s.items.mapTo(HashSet()) { it.id }
            _selection.update { sel -> sel.filterTo(LinkedHashSet()) { it in ids } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState())

    // ---- Selection (drag-range bookkeeping) ----
    private var dragBase: Set<Long> = emptySet()
    private var dragAnchor = -1
    private var dragAdding = true

    fun toggle(id: Long) = _selection.update { if (id in it) it - id else it + id }

    fun clearSelection() { _selection.value = emptySet() }

    fun selectAll() { _selection.value = state.value.items.mapTo(LinkedHashSet()) { it.id } }

    /** Long-press landed on item [index]: select it, and remember it as the anchor of a drag range. */
    fun startDragSelect(index: Int) {
        val item = state.value.items.getOrNull(index) ?: return
        dragBase = _selection.value
        dragAnchor = index
        // Long-press on an already-selected photo drags to *deselect*.
        dragAdding = item.id !in dragBase
        dragSelectTo(index)
    }

    fun dragSelectTo(index: Int) {
        if (dragAnchor < 0) return
        val items = state.value.items
        val lo = minOf(dragAnchor, index).coerceAtLeast(0)
        val hi = maxOf(dragAnchor, index).coerceAtMost(items.lastIndex)
        val range = (lo..hi).mapTo(HashSet()) { items[it].id }
        _selection.value = if (dragAdding) dragBase + range else dragBase - range
    }

    fun endDragSelect() { dragAnchor = -1 }

    private fun buildEntries(items: List<MediaItem>): List<GridEntry> {
        val zone = ZoneId.systemDefault()
        val thisYear = LocalDate.now(zone).year
        val locale = Locale.getDefault()
        val monthFull = DateTimeFormatter.ofPattern("MMMM", locale)
        val monthYearFull = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val monthYearShort = DateTimeFormatter.ofPattern("MMM yyyy", locale)

        val out = ArrayList<GridEntry>(items.size + items.size / 20 + 1)
        var currentKey = -1
        var currentShort = ""
        items.forEachIndexed { index, item ->
            val date = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()
            val key = date.year * 100 + date.monthValue
            if (key != currentKey) {
                currentKey = key
                currentShort = monthYearShort.format(date)
                val title = if (date.year == thisYear) monthFull.format(date) else monthYearFull.format(date)
                out += GridEntry.Header("h:$key", title, currentShort)
            }
            out += GridEntry.Cell(item, index, currentShort)
        }
        return out
    }
}
