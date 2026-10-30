package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class ScrollbarMetrics(
    val fraction: Float,
    val thumbFraction: Float,
    val maxScroll: Float
)

@Composable
fun VerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    barWidth: Dp = 4.dp,
    touchWidth: Dp = 14.dp,
    minThumbFraction: Float = 0.07f
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val metrics by remember(minThumbFraction) {
        derivedStateOf { scrollbarMetrics(listState, minThumbFraction) }
    }
    val latestMetrics = rememberUpdatedState(metrics)
    val current = metrics ?: return
    val shape = RoundedCornerShape(percent = 50)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(touchWidth)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val active = latestMetrics.value ?: return@detectVerticalDragGestures
                    val thumbTravel = size.height - size.height * active.thumbFraction
                    if (thumbTravel > 0f) {
                        scope.launch {
                            listState.scrollBy(dragAmount * (active.maxScroll / thumbTravel))
                        }
                    }
                }
            }
    ) {
        val viewport = constraints.maxHeight.toFloat()
        if (viewport <= 0f || current.thumbFraction >= 1f) return@BoxWithConstraints
        val thumbHeightPx = viewport * current.thumbFraction
        val thumbTravel = (viewport - thumbHeightPx).coerceAtLeast(0f)
        val thumbTop = thumbTravel * current.fraction

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(barWidth)
                .fillMaxHeight()
                .background(trackColor, shape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { thumbTop.toDp() })
                .width(barWidth)
                .height(with(density) { thumbHeightPx.toDp() })
                .background(thumbColor, shape)
        )
    }
}

private fun scrollbarMetrics(state: LazyListState, minThumbFraction: Float): ScrollbarMetrics? {
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    val visible = info.visibleItemsInfo
    if (totalItems <= 0 || visible.isEmpty()) return null

    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (viewport <= 0f) return null

    val first = visible.first()
    val last = visible.last()
    val span = (last.offset + last.size - first.offset).toFloat()
    val averageItemSize = if (span > 0f) span / visible.size else 1f
    val contentSize = averageItemSize * totalItems
    if (contentSize <= viewport) return null

    val scrolled = first.index * averageItemSize - first.offset
    val maxScroll = (contentSize - viewport).coerceAtLeast(1f)
    return ScrollbarMetrics(
        fraction = (scrolled / maxScroll).coerceIn(0f, 1f),
        thumbFraction = (viewport / contentSize).coerceIn(minThumbFraction, 1f),
        maxScroll = maxScroll
    )
}
