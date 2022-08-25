package com.loren.component.view.composesmartrefresh

import androidx.compose.animation.core.*
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Loren on 2022/6/13
 * Description -> 支持下拉刷新&加载更多的通用组件
 */
@Composable
fun SmartSwipeRefresh(
    modifier: Modifier = Modifier,
    onRefresh: (suspend () -> Unit)? = null,
    onLoadMore: (suspend () -> Unit)? = null,
    state: SmartSwipeRefreshState,
    isNeedRefresh: Boolean = true,
    isNeedLoadMore: Boolean = true,
    headerThreshold: Dp? = null,
    footerThreshold: Dp? = null,
    headerIndicator: @Composable () -> Unit = { MyRefreshHeader(flag = state.refreshFlag) },
    footerIndicator: @Composable () -> Unit = { MyRefreshHeader(flag = state.loadMoreFlag) },
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        state.indicatorOffsetFlow.collect {
            val currentOffset = with(density) { state.indicatorOffset + it.toDp() }
            // 重点：解决触摸滑动展示header||footer的时候反向滑动另一边的布局被展示出一个白边出来
            // 当footer显示的情况下，希望父布局的偏移量最大只有0dp，防止尾布局会被偏移几个像素
            // 当header显示的情况下，希望父布局的偏移量最小只有0dp，防止头布局会被偏移几个像素
            // 其余的情况下，直接偏移
            state.snapToOffset(
                when {
                    state.footerIsShow ->
                        currentOffset.coerceAtMost(0.dp).coerceAtLeast(-(footerThreshold ?: Dp.Infinity))
                    state.headerIsShow ->
                        currentOffset.coerceAtLeast(0.dp).coerceAtMost(headerThreshold ?: Dp.Infinity)
                    else -> currentOffset
                }
            )
        }
    }
    LaunchedEffect(state.refreshFlag) {
        when (state.refreshFlag) {
            SmartSwipeStateFlag.REFRESHING -> {
                onRefresh?.invoke()
                state.smartSwipeRefreshAnimateFinishing = state.smartSwipeRefreshAnimateFinishing.copy(isFinishing = false, isRefresh = true)
            }
            SmartSwipeStateFlag.SUCCESS, SmartSwipeStateFlag.ERROR -> {
                delay(1000)
                state.animateToOffset(0.dp)
            }
            else -> {}
        }
    }
    LaunchedEffect(state.loadMoreFlag) {
        when (state.loadMoreFlag) {
            SmartSwipeStateFlag.REFRESHING -> {
                onLoadMore?.invoke()
                state.smartSwipeRefreshAnimateFinishing = state.smartSwipeRefreshAnimateFinishing.copy(isFinishing = false, isRefresh = false)
            }
            SmartSwipeStateFlag.SUCCESS, SmartSwipeStateFlag.ERROR -> {
                delay(1000)
                state.animateToOffset(0.dp)
            }
            else -> {}
        }
    }
    Box(modifier = modifier.zIndex(-1f)) {
        SubComposeSmartSwipeRefresh(
            headerIndicator = headerIndicator,
            footerIndicator = footerIndicator,
            isNeedRefresh,
            isNeedLoadMore
        ) { header, footer ->
            val smartSwipeRefreshNestedScrollConnection = remember(state, header, footer) {
                SmartSwipeRefreshNestedScrollConnection(state, header, footer)
            }
            Box(
                modifier.nestedScroll(smartSwipeRefreshNestedScrollConnection),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isNeedRefresh) {
                    Box(Modifier.offset(y = -header + state.indicatorOffset)) {
                        headerIndicator()
                    }
                }
                // 因为无法测量出content的高度 所以footer偏移到content布局之下
                Box(modifier = Modifier.offset(y = state.indicatorOffset)) {
                    content()
                    if (isNeedLoadMore) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = footer)
                        ) {
                            footerIndicator()
                        }
                    }
                }
            }
        }
    }
}

enum class SmartSwipeStateFlag {
    IDLE, REFRESHING, SUCCESS, ERROR, TIPS_DOWN, TIPS_RELEASE
}

@Composable
fun rememberSmartSwipeRefreshState(): SmartSwipeRefreshState {
    return remember {
        SmartSwipeRefreshState()
    }
}

data class SmartSwipeRefreshAnimateFinishing(
    val isFinishing: Boolean = true,
    val isRefresh: Boolean = true
)

class SmartSwipeRefreshState {
    private val mutatorMutex = MutatorMutex()
    private val indicatorOffsetAnimatable = Animatable(0.dp, Dp.VectorConverter)
    val indicatorOffset get() = indicatorOffsetAnimatable.value

    private val _indicatorOffsetFlow = MutableStateFlow(0f)
    val indicatorOffsetFlow: Flow<Float> get() = _indicatorOffsetFlow

    val headerIsShow by derivedStateOf { indicatorOffset > 0.dp }
    val footerIsShow by derivedStateOf { indicatorOffset < 0.dp }

    var refreshFlag: SmartSwipeStateFlag by mutableStateOf(SmartSwipeStateFlag.IDLE)
    var loadMoreFlag: SmartSwipeStateFlag by mutableStateOf(SmartSwipeStateFlag.IDLE)
    var smartSwipeRefreshAnimateFinishing: SmartSwipeRefreshAnimateFinishing by mutableStateOf(
        SmartSwipeRefreshAnimateFinishing(
            isFinishing = true,
            isRefresh = true
        )
    )

    fun isRefreshing() =
        refreshFlag == SmartSwipeStateFlag.REFRESHING || loadMoreFlag == SmartSwipeStateFlag.REFRESHING || !smartSwipeRefreshAnimateFinishing.isFinishing

    fun updateOffsetDelta(value: Float) {
        _indicatorOffsetFlow.value = value
    }

    suspend fun snapToOffset(value: Dp) {
        mutatorMutex.mutate(MutatePriority.UserInput) {
            indicatorOffsetAnimatable.snapTo(value)
        }
    }

    suspend fun animateToOffset(value: Dp) {
        mutatorMutex.mutate {
            indicatorOffsetAnimatable.animateTo(value, tween(300)) {
                if (this.value == 0.dp && !smartSwipeRefreshAnimateFinishing.isFinishing) {
                    // 此时动画完全停止
                    smartSwipeRefreshAnimateFinishing = smartSwipeRefreshAnimateFinishing.copy(isFinishing = true)
                }
            }
        }
    }
}

private class SmartSwipeRefreshNestedScrollConnection(
    val state: SmartSwipeRefreshState,
    val headerHeight: Dp,
    val footerHeight: Dp
) : NestedScrollConnection {

    /**
     * 预先劫持滑动事件，消费后再交由子布局
     *
     * header展示如果反向滑动优先父布局处理 做动画并拦截滑动事件
     * footer展示如果反向滑动优先父布局处理 做动画并拦截滑动事件
     */
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return if (source == NestedScrollSource.Drag && !state.isRefreshing()) {
            if (state.headerIsShow || state.footerIsShow) {
                when {
                    state.headerIsShow -> {
                        // header已经在展示
                        state.refreshFlag =
                            if (state.indicatorOffset > headerHeight) SmartSwipeStateFlag.TIPS_RELEASE else SmartSwipeStateFlag.TIPS_DOWN
                        if (available.y < 0f) {
                            // 头部已经展示并且上滑动
                            state.updateOffsetDelta(available.y / 2)
                            Offset(x = 0f, y = available.y)
                        } else {
                            Offset.Zero
                        }
                    }
                    state.footerIsShow -> {
                        // footer已经在展示
                        state.loadMoreFlag =
                            if (state.indicatorOffset < -footerHeight) SmartSwipeStateFlag.TIPS_RELEASE else SmartSwipeStateFlag.TIPS_DOWN
                        if (available.y > 0f) {
                            // 尾部已经展示并且上滑动
                            state.updateOffsetDelta(available.y / 2)
                            Offset(x = 0f, y = available.y)
                        } else {
                            Offset.Zero
                        }
                    }
                    else -> Offset.Zero
                }
            } else Offset.Zero
        } else {
            if (state.isRefreshing()) {
                Offset(x = 0f, y = available.y)
            } else {
                Offset.Zero
            }
        }
    }

    /**
     * 获取子布局处理后的滑动事件
     *
     * consumed==0代表子布局没有消费事件 即列表没有被滚动
     * 此时事件在available中 把其中的事件传递给header||footer
     * 调用state.updateOffsetDelta(available.y / 2)做父布局动画
     * 并且消费掉滑动事件
     *
     * 刷新中不消费事件 拦截子布局即列表的滚动
     */
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return if (source == NestedScrollSource.Drag && !state.isRefreshing()) {
            if (available.y != 0f && consumed.y == 0f) {
                if (headerHeight != 0.dp && available.y > 0f) {
                    state.updateOffsetDelta(available.y / 2)
                }
                if (footerHeight != 0.dp && available.y < 0f) {
                    state.updateOffsetDelta(available.y / 2)
                }
                Offset(x = 0f, y = available.y)
            } else {
                Offset.Zero
            }
        } else {
            Offset.Zero
        }
    }

    /**
     * indicatorOffset>=0 header显示 indicatorOffset<=0 footer显示
     * 拖动到头部快速滑动时 如果indicatorOffset>headerHeight则
     */
    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!state.isRefreshing()) {
            if (state.indicatorOffset >= headerHeight) {
                state.animateToOffset(headerHeight)
                state.refreshFlag = SmartSwipeStateFlag.REFRESHING
            } else if (state.indicatorOffset <= -footerHeight) {
                state.animateToOffset(-footerHeight)
                state.loadMoreFlag = SmartSwipeStateFlag.REFRESHING
            } else {
                if (state.indicatorOffset != 0.dp)
                    state.animateToOffset(0.dp)
            }
        }
        return super.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return super.onPostFling(consumed, available)
    }
}

@Composable
private fun SubComposeSmartSwipeRefresh(
    headerIndicator: @Composable () -> Unit,
    footerIndicator: @Composable () -> Unit,
    isNeedRefresh: Boolean,
    isNeedLoadMore: Boolean,
    content: @Composable (header: Dp, footer: Dp) -> Unit
) {
    SubcomposeLayout { constraints: Constraints ->
        val headerIndicatorPlaceable = subcompose("headerIndicator", headerIndicator).first().measure(constraints)
        val footerIndicatorPlaceable = subcompose("footerIndicator", footerIndicator).first().measure(constraints)
        val contentPlaceable = subcompose("content") {
            content(
                if (isNeedRefresh) headerIndicatorPlaceable.height.toDp() else 0.dp,
                if (isNeedLoadMore) footerIndicatorPlaceable.height.toDp() else 0.dp
            )
        }.map {
            it.measure(constraints)
        }.first()
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
fun MyRefreshHeader(flag: SmartSwipeStateFlag, isNeedTimestamp: Boolean = true) {
    var lastRecordTime by remember {
        mutableStateOf(System.currentTimeMillis())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.White)
    ) {
        val refreshAnimate by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing))
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
                modifier = Modifier.rotate(if (flag == SmartSwipeStateFlag.REFRESHING) refreshAnimate else arrowDegrees),
                imageVector = when (flag) {
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
                },
                contentDescription = null
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
        mutableStateOf(System.currentTimeMillis())
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.White)
    ) {
        val refreshAnimate by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing))
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
                modifier = Modifier.rotate(if (flag == SmartSwipeStateFlag.REFRESHING) refreshAnimate else arrowDegrees),
                imageVector = when (flag) {
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
                },
                contentDescription = null
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
