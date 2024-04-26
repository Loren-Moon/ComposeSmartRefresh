package com.loren.component.view.composesmartrefresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Created by Loren on 2022/6/13
 * Description -> 支持下拉刷新&加载更多的通用组件
 */
@Composable
fun SmartSwipeRefresh(
    modifier: Modifier = Modifier,
    state: SmartSwipeRefreshState,
    onRefresh: (suspend () -> Unit)? = null,
    onLoadMore: (suspend () -> Unit)? = null,
    headerIndicator: @Composable (() -> Unit)? = { MyRefreshHeader(flag = state.refreshFlag) },
    footerIndicator: @Composable (() -> Unit)? = { MyRefreshHeader(flag = state.loadMoreFlag) },
    contentScrollState: ScrollableState? = null,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val connection = remember(coroutineScope) {
        SmartSwipeRefreshNestedScrollConnection(state, coroutineScope)
    }

    LaunchedEffect(state.refreshFlag) {
        when (state.refreshFlag) {
            SmartSwipeStateFlag.REFRESHING -> {
                state.animateIsOver = false
                onRefresh?.invoke()
            }

            SmartSwipeStateFlag.ERROR, SmartSwipeStateFlag.SUCCESS -> {
                delay(500)
                state.animateOffsetTo(0f)
            }

            else -> {}
        }
    }

    LaunchedEffect(state.loadMoreFlag) {
        when (state.loadMoreFlag) {
            SmartSwipeStateFlag.REFRESHING -> {
                state.animateIsOver = false
                onLoadMore?.invoke()
            }

            SmartSwipeStateFlag.ERROR, SmartSwipeStateFlag.SUCCESS -> {
                delay(500)
                state.animateOffsetTo(0f)
            }

            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        if (state.needFirstRefresh) {
            state.initRefresh()
        }
    }

    LaunchedEffect(state.indicatorOffset) {
        if (state.indicatorOffset < 0 && state.loadMoreFlag != SmartSwipeStateFlag.SUCCESS) {
            contentScrollState?.dispatchRawDelta(-state.indicatorOffset)
        }
    }

    Box(
        modifier = modifier.clipToBounds()
    ) {
        SubComposeSmartSwipeRefresh(
            headerIndicator = headerIndicator, footerIndicator = footerIndicator
        ) { header, footer ->
            state.headerHeight = header.toFloat()
            state.footerHeight = footer.toFloat()

            Box(modifier = Modifier.nestedScroll(connection)) {
                val p = with(LocalDensity.current) { state.indicatorOffset.toDp() }
                val contentModifier = when {
                    p > 0.dp -> Modifier.padding(top = p)
                    p < 0.dp && contentScrollState != null -> Modifier.padding(bottom = -p)
                    p < 0.dp -> Modifier.graphicsLayer { translationY = state.indicatorOffset }
                    else -> Modifier
                }
                Box(modifier = contentModifier) {
                    content()
                }
                headerIndicator?.let {
                    Box(modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer { translationY = -header.toFloat() + state.indicatorOffset }) {
                        headerIndicator()
                    }
                }
                footerIndicator?.let {
                    Box(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { translationY = footer.toFloat() + state.indicatorOffset }) {
                        footerIndicator()
                    }
                }
            }
        }
    }
}

enum class SmartSwipeStateFlag {
    IDLE, REFRESHING, SUCCESS, ERROR, TIPS_DOWN, TIPS_RELEASE
}

sealed interface FlingScrollStrategy {
    data object Hide : FlingScrollStrategy

    data class Show(val height: Float) : FlingScrollStrategy
}

@Composable
fun rememberSmartSwipeRefreshState(): SmartSwipeRefreshState {
    return remember {
        SmartSwipeRefreshState()
    }
}

class SmartSwipeRefreshState {
    //
    var stickinessLevel = 0.5f

    // fling strategy of Indicator
    var flingHeaderIndicatorStrategy: FlingScrollStrategy = FlingScrollStrategy.Hide
    var flingFooterIndicatorStrategy: FlingScrollStrategy = FlingScrollStrategy.Hide

    // enter page call refresh
    var needFirstRefresh = false

    fun flingIndicatorHeight(strategy: FlingScrollStrategy): Float = when (strategy) {
        FlingScrollStrategy.Hide -> 0f
        is FlingScrollStrategy.Show -> strategy.height
    }

    suspend fun initRefresh() {
        snapOffsetTo(headerHeight)
        refreshFlag = SmartSwipeStateFlag.REFRESHING
    }

    var headerHeight = 0f
    var footerHeight = 0f

    // fling释放的时候header|footer是否有显示 显示则刷新 没显示动画回到原位
    var releaseIsEdge = false

    var refreshFlag by mutableStateOf(SmartSwipeStateFlag.IDLE)
    var loadMoreFlag by mutableStateOf(SmartSwipeStateFlag.IDLE)

    var enableRefresh = true
    var enableLoadMore = true

    var animateIsOver by mutableStateOf(true)

    fun isLoading() = !animateIsOver || refreshFlag == SmartSwipeStateFlag.REFRESHING || loadMoreFlag == SmartSwipeStateFlag.REFRESHING

    private val _indicatorOffset = Animatable(0f)
    private val mutatorMutex = MutatorMutex()

    val indicatorOffset: Float
        get() = _indicatorOffset.value

    suspend fun animateOffsetTo(offset: Float) {
        mutatorMutex.mutate {
            _indicatorOffset.animateTo(offset) {
                if (this.value == 0f) {
                    animateIsOver = true
                }
            }
        }
    }

    suspend fun snapOffsetTo(offset: Float) {
        mutatorMutex.mutate(MutatePriority.UserInput) {
            _indicatorOffset.snapTo(offset)

            if (indicatorOffset >= headerHeight) {
                refreshFlag = SmartSwipeStateFlag.TIPS_RELEASE
            } else if (indicatorOffset <= -footerHeight) {
                loadMoreFlag = SmartSwipeStateFlag.TIPS_RELEASE
            } else {
                if (indicatorOffset > 0) {
                    refreshFlag = SmartSwipeStateFlag.TIPS_DOWN
                }
                if (indicatorOffset < 0) {
                    loadMoreFlag = SmartSwipeStateFlag.TIPS_DOWN
                }
            }
        }
    }
}

private class SmartSwipeRefreshNestedScrollConnection(
    val state: SmartSwipeRefreshState, private val coroutineScope: CoroutineScope
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return when {
            state.indicatorOffset != 0f -> {
                // header||footer is show
                scroll(available, source)
            }

            else -> Offset.Zero
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        return scroll(available, source)
    }

    private fun scroll(available: Offset, source: NestedScrollSource): Offset {
        val headerIsShow = state.indicatorOffset > 0
        val footerIsShow = state.indicatorOffset < 0
        val canConsumed = when {

            state.isLoading() -> {
                return Offset.Zero
            }

            available.y < 0 && headerIsShow -> {
                // header can drag [state.indicatorOffset, 0]
                (available.y * state.stickinessLevel).coerceAtLeast(0 - state.indicatorOffset)
            }

            available.y > 0 && footerIsShow -> {
                // footer can drag [state.indicatorOffset, 0]
                (available.y * state.stickinessLevel).coerceAtMost(0 - state.indicatorOffset)
            }

            available.y > 0 && state.enableRefresh && state.headerHeight != 0f -> {
                if (source == NestedScrollSource.Fling) {
                    (available.y * state.stickinessLevel).coerceAtMost(state.flingIndicatorHeight(state.flingHeaderIndicatorStrategy) - state.indicatorOffset)
                } else {
                    // header can drag any range
                    available.y * state.stickinessLevel
                }
            }

            available.y < 0 && state.enableLoadMore && state.footerHeight != 0f -> {
                if (source == NestedScrollSource.Fling) {
                    (available.y * state.stickinessLevel).coerceAtLeast(-state.flingIndicatorHeight(state.flingFooterIndicatorStrategy) - state.indicatorOffset)
                } else {
                    // footer can drag any range
                    available.y * state.stickinessLevel
                }
            }

            else -> 0f
        }
        coroutineScope.launch {
            state.snapOffsetTo(state.indicatorOffset + canConsumed)
        }
        return Offset(0f, canConsumed / state.stickinessLevel)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.isLoading()) {
            return Velocity.Zero
        }

        state.releaseIsEdge = state.indicatorOffset != 0f

        if (state.indicatorOffset >= state.headerHeight && state.releaseIsEdge) {
            if (state.refreshFlag != SmartSwipeStateFlag.REFRESHING) {
                state.refreshFlag = SmartSwipeStateFlag.REFRESHING
                state.animateOffsetTo(state.headerHeight)
                return available
            }
        }

        if (state.indicatorOffset <= -state.footerHeight && state.releaseIsEdge) {
            if (state.loadMoreFlag != SmartSwipeStateFlag.REFRESHING) {
                state.loadMoreFlag = SmartSwipeStateFlag.REFRESHING
                state.animateOffsetTo(-state.footerHeight)
                return available
            }
        }
        return super.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (state.isLoading()) {
            return Velocity.Zero
        }
        if (state.refreshFlag != SmartSwipeStateFlag.REFRESHING && state.indicatorOffset > 0) {
            state.refreshFlag = SmartSwipeStateFlag.IDLE
            state.animateOffsetTo(0f)
        }
        if (state.loadMoreFlag != SmartSwipeStateFlag.REFRESHING && state.indicatorOffset < 0) {
            state.loadMoreFlag = SmartSwipeStateFlag.IDLE
            state.animateOffsetTo(0f)
        }
        return super.onPostFling(consumed, available)
    }
}

@Composable
private fun SubComposeSmartSwipeRefresh(
    headerIndicator: (@Composable () -> Unit)?, footerIndicator: (@Composable () -> Unit)?, content: @Composable (header: Int, footer: Int) -> Unit
) {
    SubcomposeLayout { constraints ->
        val headerPlaceable = subcompose("header", headerIndicator ?: {}).firstOrNull()?.measure(constraints)
        val footerPlaceable = subcompose("footer", footerIndicator ?: {}).firstOrNull()?.measure(constraints)
        val contentPlaceable =
            subcompose("content") { content(headerPlaceable?.height ?: 0, footerPlaceable?.height ?: 0) }.first().measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
fun MyRefreshHeader(flag: SmartSwipeStateFlag, isNeedTimestamp: Boolean = true) {
    var lastRecordTime by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.White)
    ) {
        val refreshAnimate by rememberInfiniteTransition(label = "MyRefreshHeader").animateFloat(
            initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing)), label = "MyRefreshHeader"
        )
        val transitionState = remember { MutableTransitionState(0) }
        val transition = updateTransition(transitionState, label = "arrowTransition")
        val arrowDegrees by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 500) }, label = "arrowDegrees"
        ) {
            if (it == 0) 0f else 180f
        }
        transitionState.targetState = if (flag == SmartSwipeStateFlag.TIPS_RELEASE) 1 else 0
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                modifier = Modifier.rotate(if (flag == SmartSwipeStateFlag.REFRESHING) refreshAnimate else arrowDegrees), imageVector = when (flag) {
                    SmartSwipeStateFlag.IDLE -> Icons.Default.KeyboardArrowDown
                    SmartSwipeStateFlag.REFRESHING -> Icons.Default.Refresh
                    SmartSwipeStateFlag.SUCCESS -> {
                        lastRecordTime = System.currentTimeMillis()
                        Icons.Default.Done
                    }

                    SmartSwipeStateFlag.ERROR -> {
                        lastRecordTime = System.currentTimeMillis()
                        Icons.Default.Warning
                    }

                    SmartSwipeStateFlag.TIPS_DOWN -> Icons.Default.KeyboardArrowDown
                    SmartSwipeStateFlag.TIPS_RELEASE -> Icons.Default.KeyboardArrowDown
                }, contentDescription = null
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = when (flag) {
                        SmartSwipeStateFlag.REFRESHING -> "刷新中..."
                        SmartSwipeStateFlag.SUCCESS -> "刷新成功"
                        SmartSwipeStateFlag.ERROR -> "刷新失败"
                        SmartSwipeStateFlag.IDLE, SmartSwipeStateFlag.TIPS_DOWN -> "下拉可以刷新"
                        SmartSwipeStateFlag.TIPS_RELEASE -> "释放立即刷新"
                    }, fontSize = 18.sp
                )
                if (isNeedTimestamp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "上次刷新：${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(lastRecordTime)}", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun MyRefreshFooter(flag: SmartSwipeStateFlag, isNeedTimestamp: Boolean = true) {
    var lastRecordTime by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.White)
    ) {
        val refreshAnimate by rememberInfiniteTransition(label = "MyRefreshFooter").animateFloat(
            initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing)), label = "MyRefreshFooter"
        )
        val transitionState = remember { MutableTransitionState(0) }
        val transition = updateTransition(transitionState, label = "arrowTransition")
        val arrowDegrees by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 500) }, label = "arrowDegrees"
        ) {
            if (it == 0) 0f else 180f
        }
        transitionState.targetState = if (flag == SmartSwipeStateFlag.TIPS_RELEASE) 1 else 0
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(
                modifier = Modifier.rotate(if (flag == SmartSwipeStateFlag.REFRESHING) refreshAnimate else arrowDegrees), imageVector = when (flag) {
                    SmartSwipeStateFlag.IDLE -> Icons.Default.KeyboardArrowUp
                    SmartSwipeStateFlag.REFRESHING -> Icons.Default.Refresh
                    SmartSwipeStateFlag.SUCCESS -> {
                        lastRecordTime = System.currentTimeMillis()
                        Icons.Default.Done
                    }

                    SmartSwipeStateFlag.ERROR -> {
                        lastRecordTime = System.currentTimeMillis()
                        Icons.Default.Warning
                    }

                    SmartSwipeStateFlag.TIPS_DOWN -> Icons.Default.KeyboardArrowUp
                    SmartSwipeStateFlag.TIPS_RELEASE -> Icons.Default.KeyboardArrowUp
                }, contentDescription = null
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = when (flag) {
                        SmartSwipeStateFlag.REFRESHING -> "正在加载..."
                        SmartSwipeStateFlag.SUCCESS -> "加载成功"
                        SmartSwipeStateFlag.ERROR -> "加载失败"
                        SmartSwipeStateFlag.IDLE, SmartSwipeStateFlag.TIPS_DOWN -> "上拉加载更多"
                        SmartSwipeStateFlag.TIPS_RELEASE -> "释放立即加载"
                    }, fontSize = 18.sp
                )
                if (isNeedTimestamp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "上次加载：${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(lastRecordTime)}", fontSize = 14.sp)
                }
            }
        }
    }
}
