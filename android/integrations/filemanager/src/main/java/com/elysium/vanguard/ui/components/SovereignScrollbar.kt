package com.elysium.vanguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import com.elysium.vanguard.ui.theme.TitanColors
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * SOVEREIGN SCROLLBAR
 *
 * Visible AND draggable scroll indicator. Two jobs:
 *
 *  1. SHOW THE SIZE OF THE LIST — the track is the whole content, the thumb is
 *     proportional to the visible fraction, so a short thumb means "there is a
 *     lot more below".
 *  2. ALLOW GOING FROM TOP TO BOTTOM — the thumb can be grabbed and dragged, or
 *     the track tapped, and the list jumps to that exact position.
 *
 * The visible bar is thin but the touch target is wider, so the bar can be
 * grabbed with a thumb without stealing taps from the content next to it.
 */
private data class ScrollbarGeometry(
    /** Where the viewport currently is, 0 = top, 1 = bottom. */
    val fraction: Float,
    /** Thumb length in pixels. */
    val thumbPx: Float,
    /** Distance the thumb can travel inside the track. */
    val travelPx: Float
)

/**
 * Shared drawing: a faint track plus the thumb, both flush to the trailing edge.
 */
@Composable
private fun SovereignScrollbarSurface(
    geometry: ScrollbarGeometry?,
    barWidthPx: Float,
    color: Color,
    showTrack: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val g = geometry ?: return@Canvas
        val barX = (size.width - barWidthPx).coerceAtLeast(0f)
        if (showTrack) {
            drawRoundRect(
                color = color.copy(alpha = 0.14f),
                topLeft = Offset(barX, 0f),
                size = Size(barWidthPx, size.height),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
        val thumbY = (g.fraction * g.travelPx).coerceIn(0f, g.travelPx)
        drawRoundRect(
            color = color.copy(alpha = 0.9f),
            topLeft = Offset(barX, thumbY),
            size = Size(barWidthPx, g.thumbPx),
            cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
        )
    }
}

/**
 * Geometry of a lazy list: the scroll position is expressed in items.
 */
private fun listGeometry(state: LazyListState, minThumbPx: Float): ScrollbarGeometry? {
    val info = state.layoutInfo
    val viewport = info.viewportSize.height.toFloat()
    val total = info.totalItemsCount
    if (total <= 0 || viewport <= 0f) return null

    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null

    val first = visible.first()
    val last = visible.last()
    val average = ((last.offset + last.size - first.offset).toFloat() / visible.size)
        .coerceAtLeast(1f)
    val contentHeight = total * average
    if (contentHeight <= viewport) return null

    val thumb = (viewport * (viewport / contentHeight))
        .coerceIn(minThumbPx.coerceAtMost(viewport), viewport)
    val travel = (viewport - thumb).coerceAtLeast(0f)

    // How many items fit in one screen: the scroll range expressed in items.
    val scrollRangeItems = (total - visible.size).coerceAtLeast(1)
    val fraction = (first.index.toFloat() / scrollRangeItems).coerceIn(0f, 1f)

    return ScrollbarGeometry(fraction, thumb, travel)
}

/**
 * Geometry of a lazy grid: the scroll position is expressed in rows.
 */
private fun gridGeometry(state: LazyGridState, minThumbPx: Float): ScrollbarGeometry? {
    val info = state.layoutInfo
    val viewport = info.viewportSize.height.toFloat()
    val total = info.totalItemsCount
    if (total <= 0 || viewport <= 0f) return null

    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null

    val columns = (visible.maxOf { it.column } + 1).coerceAtLeast(1)
    val firstRow = visible.minOf { it.row }
    val lastRow = visible.maxOf { it.row }
    val visibleRows = (lastRow - firstRow + 1).coerceAtLeast(1)
    val totalRows = ceil(total.toDouble() / columns).toInt().coerceAtLeast(1)

    val topY = visible.minOf { it.offset.y }
    val bottomY = visible.maxOf { it.offset.y + it.size.height }
    val rowHeight = ((bottomY - topY).toFloat() / visibleRows).coerceAtLeast(1f)
    val contentHeight = totalRows * rowHeight
    if (contentHeight <= viewport) return null

    val thumb = (viewport * (viewport / contentHeight))
        .coerceIn(minThumbPx.coerceAtMost(viewport), viewport)
    val travel = (viewport - thumb).coerceAtLeast(0f)

    val scrollRangeRows = (totalRows - visibleRows).coerceAtLeast(1)
    val fraction = (firstRow.toFloat() / scrollRangeRows).coerceIn(0f, 1f)

    return ScrollbarGeometry(fraction, thumb, travel)
}

/**
 * SOVEREIGN SCROLLBAR — list variant. Visible and draggable.
 */
@Composable
fun SovereignScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    width: Dp = 5.dp,
    color: Color = TitanColors.NeonCyan,
    touchWidth: Dp = 20.dp,
    showTrack: Boolean = true
) {
    val density = LocalDensity.current
    val minThumbPx = with(density) { 26.dp.toPx() }
    val barWidthPx = with(density) { width.toPx() }
    val scope = rememberCoroutineScope()

    val geometry by remember(state) {
        derivedStateOf { listGeometry(state, minThumbPx) }
    }

    Box(
        modifier = modifier
            .width(touchWidth)
            .fillMaxHeight()
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { position ->
                        val g = listGeometry(state, minThumbPx) ?: return@detectVerticalDragGestures
                        val travel = (size.height - g.thumbPx).coerceAtLeast(1f)
                        val target = ((position.y - g.thumbPx / 2f) / travel).coerceIn(0f, 1f)
                        val total = state.layoutInfo.totalItemsCount
                        val visible = state.layoutInfo.visibleItemsInfo.size
                        val range = (total - visible).coerceAtLeast(1)
                        val index = (target * range).roundToInt().coerceIn(0, (total - 1).coerceAtLeast(0))
                        scope.launch { state.scrollToItem(index) }
                    },
                    onVerticalDrag = { change, _ ->
                        val g = listGeometry(state, minThumbPx) ?: return@detectVerticalDragGestures
                        val travel = (size.height - g.thumbPx).coerceAtLeast(1f)
                        val target = ((change.position.y - g.thumbPx / 2f) / travel).coerceIn(0f, 1f)
                        val total = state.layoutInfo.totalItemsCount
                        val visible = state.layoutInfo.visibleItemsInfo.size
                        val range = (total - visible).coerceAtLeast(1)
                        val index = (target * range).roundToInt().coerceIn(0, (total - 1).coerceAtLeast(0))
                        scope.launch { state.scrollToItem(index) }
                    }
                )
            }
    ) {
        SovereignScrollbarSurface(geometry, barWidthPx, color, showTrack)
    }
}

/**
 * SOVEREIGN SCROLLBAR — grid variant. Visible and draggable.
 */
@Composable
fun SovereignGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    width: Dp = 5.dp,
    color: Color = TitanColors.NeonCyan,
    touchWidth: Dp = 20.dp,
    showTrack: Boolean = true
) {
    val density = LocalDensity.current
    val minThumbPx = with(density) { 26.dp.toPx() }
    val barWidthPx = with(density) { width.toPx() }
    val scope = rememberCoroutineScope()

    val geometry by remember(state) {
        derivedStateOf { gridGeometry(state, minThumbPx) }
    }

    Box(
        modifier = modifier
            .width(touchWidth)
            .fillMaxHeight()
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { position ->
                        val g = gridGeometry(state, minThumbPx) ?: return@detectVerticalDragGestures
                        val travel = (size.height - g.thumbPx).coerceAtLeast(1f)
                        val target = ((position.y - g.thumbPx / 2f) / travel).coerceIn(0f, 1f)
                        val total = state.layoutInfo.totalItemsCount
                        val visible = state.layoutInfo.visibleItemsInfo
                        if (visible.isEmpty()) return@detectVerticalDragGestures
                        val columns = (visible.maxOf { it.column } + 1).coerceAtLeast(1)
                        val visibleRows = (visible.maxOf { it.row } - visible.minOf { it.row } + 1).coerceAtLeast(1)
                        val totalRows = ceil(total.toDouble() / columns).toInt().coerceAtLeast(1)
                        val range = (totalRows - visibleRows).coerceAtLeast(1)
                        val row = (target * range).roundToInt().coerceIn(0, (totalRows - 1).coerceAtLeast(0))
                        val index = (row * columns).coerceIn(0, (total - 1).coerceAtLeast(0))
                        scope.launch { state.scrollToItem(index) }
                    },
                    onVerticalDrag = { change, _ ->
                        val g = gridGeometry(state, minThumbPx) ?: return@detectVerticalDragGestures
                        val travel = (size.height - g.thumbPx).coerceAtLeast(1f)
                        val target = ((change.position.y - g.thumbPx / 2f) / travel).coerceIn(0f, 1f)
                        val total = state.layoutInfo.totalItemsCount
                        val visible = state.layoutInfo.visibleItemsInfo
                        if (visible.isEmpty()) return@detectVerticalDragGestures
                        val columns = (visible.maxOf { it.column } + 1).coerceAtLeast(1)
                        val visibleRows = (visible.maxOf { it.row } - visible.minOf { it.row } + 1).coerceAtLeast(1)
                        val totalRows = ceil(total.toDouble() / columns).toInt().coerceAtLeast(1)
                        val range = (totalRows - visibleRows).coerceAtLeast(1)
                        val row = (target * range).roundToInt().coerceIn(0, (totalRows - 1).coerceAtLeast(0))
                        val index = (row * columns).coerceIn(0, (total - 1).coerceAtLeast(0))
                        scope.launch { state.scrollToItem(index) }
                    }
                )
            }
    ) {
        SovereignScrollbarSurface(geometry, barWidthPx, color, showTrack)
    }
}
