package com.loren.component.view.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalOverScrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loren.component.view.composesmartrefresh.*
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
            SmartSwipeRefresh(
                onRefresh = {
                    viewModel.fillData(true)
                },
                onLoadMore = {
                    viewModel.fillData(false)
                },
                state = refreshState,
                isNeedRefresh = true,
                isNeedLoadMore = true,
                headerIndicator = {
                    MyRefreshHeader(refreshState.refreshFlag, true)
                },
                footerIndicator = {
                    MyRefreshFooter(refreshState.loadMoreFlag, true)
                }) {

                LaunchedEffect(refreshState.smartSwipeRefreshAnimateFinishing) {
                    if (refreshState.smartSwipeRefreshAnimateFinishing.isFinishing && !refreshState.smartSwipeRefreshAnimateFinishing.isRefresh) {
                        scrollState.animateScrollToItem(scrollState.firstVisibleItemIndex + 1)
                    }
                }
                LaunchedEffect(mainUiState.value) {
                    mainUiState.value?.let {
                        if (!it.isLoading) {
                            refreshState.refreshFlag = when (it.refreshSuccess) {
                                true -> SmartSwipeStateFlag.SUCCESS
                                false -> SmartSwipeStateFlag.ERROR
                                else -> SmartSwipeStateFlag.IDLE
                            }
                            refreshState.loadMoreFlag = when (it.loadMoreSuccess) {
                                true -> SmartSwipeStateFlag.SUCCESS
                                false -> SmartSwipeStateFlag.ERROR
                                else -> SmartSwipeStateFlag.IDLE
                            }
                        }
                    }
                }

                CompositionLocalProvider(LocalOverScrollConfiguration.provides(null)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = scrollState
                    ) {
                        mainUiState.value?.data?.let {
                            itemsIndexed(it) { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .background(if (index % 2 == 0) Color.LightGray else Color.White)
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

class MainViewModel : ViewModel() {

    private val _mainUiState = MutableLiveData<MainUiState>()
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

    init {
        _mainUiState.value = MainUiState(data = topics)
    }

    private var flag = true // 模拟成功失败
    fun fillData(isRefresh: Boolean) {
        viewModelScope.launch {
            runCatching {
                _mainUiState.value = _mainUiState.value?.copy(isLoading = true)
                delay(1000)
                if (isRefresh) {
                    if (flag) {
                        _mainUiState.value = _mainUiState.value?.copy(refreshSuccess = true, data = topics.toMutableList().apply {
                            this[0] = this[0].copy(title = System.currentTimeMillis().toString())
                        }, isLoading = false)
                    } else {
                        _mainUiState.value = _mainUiState.value?.copy(refreshSuccess = false, isLoading = false)
                    }

                } else {
                    if (flag) {
                        _mainUiState.value =
                            _mainUiState.value?.copy(loadMoreSuccess = true, data = _mainUiState.value?.data?.toMutableList()?.apply {
                                addAll(topics)
                            } ?: emptyList(), isLoading = false)
                    } else {
                        _mainUiState.value = _mainUiState.value?.copy(loadMoreSuccess = false, isLoading = false)
                    }
                }
                flag = !flag
            }.onSuccess {
                Log.v("Loren", "fillData success")
            }.onFailure {
                Log.v("Loren", "fillData error = ${it.message}")
                if (isRefresh) {
                    _mainUiState.value = _mainUiState.value?.copy(refreshSuccess = false, isLoading = false)
                } else {
                    _mainUiState.value = _mainUiState.value?.copy(loadMoreSuccess = false, isLoading = false)
                }
            }

        }
    }
}

data class TopicModel(
    val title: String,
    @DrawableRes
    val icon: Int
)

data class MainUiState(
    val isLoading: Boolean = false,
    val refreshSuccess: Boolean? = null,
    val loadMoreSuccess: Boolean? = null,
    val data: List<TopicModel>
)