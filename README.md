## ComposeSmartRefresh
UI参照SmartRefreshLayout仿写，基于compose实现。有下拉刷新&上拉加载功能(无需Paging3)，并且可设置拖动阈值以及自定义头尾布局。

[详细介绍](https://juejin.cn/post/7113733273561333797)

## 如何使用
```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation "io.github.loren-moon:composesmartrefresh:2.1.0"
}
```

```kotlin
val refreshState = rememberSmartSwipeRefreshState()
SmartSwipeRefresh(
    onRefresh = {
        // refresh
    },
    onLoadMore = {
        // loadMore
    },
    state = refreshState,
    headerIndicator = {
        MyRefreshHeader(refreshState.refreshFlag, true)
    },
    footerIndicator = {
        MyRefreshFooter(refreshState.loadMoreFlag, true)
    }) {
    
}
```

刷新开关`enableRefresh`

加载更多开关`enableLoadMore`

粘性设置`stickLevel`

滑动阈值设置`dragHeaderIndicatorStrategy`、`dragFooterIndicatorStrategy`、`flingHeaderIndicatorStrategy`、`flingFooterIndicatorStrategy`

首次进入页面触发刷新动画自动加载数据`needFirstRefresh=true`

## :camera_flash: Screenshots

| <img src="/snapshot/refresh_success.gif" width="260"> | <img src="/snapshot/refresh_error.gif" width="260"> |
|-------------------------------------------------------|-----------------------------------------------------|
| <img src="/snapshot/load_success.gif" width="260">    | <img src="/snapshot/load_error.gif" width="260">    |
