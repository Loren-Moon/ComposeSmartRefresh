## ComposeSmartRefresh
UI参照SmartRefreshLayout仿写，基于compose实现。有下拉刷新&上拉加载功能(无需Paging3)，并且可设置拖动阈值以及自定义头尾布局。

## 如何使用
[详细介绍](https://juejin.cn/post/7113733273561333797)

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation "io.github.loren-moon:composesmartrefresh:1.0.0"
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
    isNeedRefresh = true,
    isNeedLoadMore = true,
    headerIndicator = {
        MyRefreshHeader(refreshState.refreshFlag, true)
    },
    footerIndicator = {
        MyRefreshFooter(refreshState.loadMoreFlag, true)
    }) {
    
}
```

## :camera_flash: Screenshots

<img src="/snapshot/refresh_success.gif" width="260">|<img src="/snapshot/refresh_error.gif" width="260">
---|---
<img src="/snapshot/load_success.gif" width="260">|<img src="/snapshot/load_error.gif" width="260">
