package com.loren.component.view.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loren.component.view.composesmartrefresh.FlingScrollStrategy
import com.loren.component.view.composesmartrefresh.MyRefreshFooter
import com.loren.component.view.composesmartrefresh.MyRefreshHeader
import com.loren.component.view.composesmartrefresh.SmartSwipeRefresh
import com.loren.component.view.composesmartrefresh.SmartSwipeStateFlag
import com.loren.component.view.composesmartrefresh.rememberSmartSwipeRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ExperimentalFoundationApi
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scrollState = rememberLazyListState()
            val viewModel by viewModels<MainViewModel>()
            val mainUiState = viewModel.mainUiState.observeAsState()
            val refreshState = rememberSmartSwipeRefreshState()
            // 快速滚动头尾允许的阈值
            with(LocalDensity.current) {
                refreshState.flingHeaderIndicatorStrategy = FlingScrollStrategy.Hide
//                refreshState.flingHeaderIndicatorStrategy = FlingScrollStrategy.Show(80.dp.toPx())
                refreshState.flingFooterIndicatorStrategy = FlingScrollStrategy.Show(80.dp.toPx())
            }
            refreshState.needFirstRefresh = true
            Column {
                SmartSwipeRefresh(
                    modifier = Modifier.fillMaxSize(),
                    onRefresh = {
                        viewModel.fillData(true)
                    },
                    onLoadMore = {
                        viewModel.fillData(false)
                    },
                    state = refreshState,
                    headerIndicator = {
                        MyRefreshHeader(refreshState.refreshFlag, true)
                    },
                    footerIndicator = {
                        MyRefreshFooter(refreshState.loadMoreFlag, true)
                    },
                    contentScrollState = scrollState
                ) {

                    LaunchedEffect(mainUiState.value) {
                        mainUiState.value?.let {
                            if (it.isLoadMore) {
                                refreshState.loadMoreFlag = when (it.flag) {
                                    true -> SmartSwipeStateFlag.SUCCESS
                                    false -> SmartSwipeStateFlag.ERROR
                                }
                            } else {
                                refreshState.refreshFlag = when (it.flag) {
                                    true -> SmartSwipeStateFlag.SUCCESS
                                    false -> SmartSwipeStateFlag.ERROR
                                }
                            }
                        }
                    }

                    CompositionLocalProvider(LocalOverscrollConfiguration.provides(null)) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = scrollState
                        ) {
                            mainUiState.value?.data?.let {
                                items(it) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .background(Color.LightGray)
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            modifier = Modifier
                                                .width(32.dp)
                                                .height(32.dp),
                                            painter = painterResource(id = item.icon),
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = item.title)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class MainViewModel : ViewModel() {

    private val _mainUiState: MutableLiveData<MainUiState> = MutableLiveData()
    val mainUiState: LiveData<MainUiState>
        get() = _mainUiState

    private val topics = listOf(
        TopicModel("Arts & Crafts", RandomIcon.icon()),
        TopicModel("Beauty", RandomIcon.icon()),
        TopicModel("Books", RandomIcon.icon()),
        TopicModel("Business", RandomIcon.icon()),
        TopicModel("Comics", RandomIcon.icon()),
        TopicModel("Culinary", RandomIcon.icon()),
        TopicModel("Design", RandomIcon.icon()),
        TopicModel("Writing", RandomIcon.icon()),
        TopicModel("Religion", RandomIcon.icon()),
        TopicModel("Technology", RandomIcon.icon()),
        TopicModel("Social sciences", RandomIcon.icon()),
        TopicModel("Arts & Crafts", RandomIcon.icon()),
        TopicModel("Beauty", RandomIcon.icon()),
        TopicModel("Books", RandomIcon.icon()),
        TopicModel("Business", RandomIcon.icon()),
        TopicModel("Comics", RandomIcon.icon()),
        TopicModel("Culinary", RandomIcon.icon()),
        TopicModel("Design", RandomIcon.icon()),
        TopicModel("Writing", RandomIcon.icon()),
        TopicModel("Religion", RandomIcon.icon()),
        TopicModel("Technology", RandomIcon.icon()),
        TopicModel("Social sciences", RandomIcon.icon())
    )

    private var flag = true // 模拟成功失败
    fun fillData(isRefresh: Boolean) {
        viewModelScope.launch {
            runCatching {
                delay(2000)
                if (!flag) {
                    throw Exception("error")
                }
                if (isRefresh) {
                    MainUiState(isLoadMore = false, data = topics.toMutableList().apply {
                        this[0] = this[0].copy(title = System.currentTimeMillis().toString())
                    }, flag = true)
                } else {
                    MainUiState(
                        isLoadMore = true,
                        data = (_mainUiState.value?.data ?: mutableListOf()).apply {
                            addAll(topics)
                        }, flag = true
                    )
                }
            }.onSuccess {
                Log.v("Loren", "fillData success")
                _mainUiState.value = it
            }.onFailure {
                _mainUiState.value = _mainUiState.value?.copy(isLoadMore = !isRefresh, flag = false)
            }
            flag = !flag
        }
    }
}

data class TopicModel(
    val title: String,
    @DrawableRes
    val icon: Int
)

data class MainUiState(
    val data: MutableList<TopicModel>? = null,
    val isLoadMore: Boolean = false,
    val flag: Boolean = true
)