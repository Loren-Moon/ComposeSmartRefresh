package com.loren.component.view.sample

import kotlin.random.Random

/**
 * Created by Loren on 2022/4/6
 * Description -> 随机icon工具类
 */
object RandomIcon {
    private val icons = intArrayOf(
        R.drawable.ic_baitu,
        R.drawable.ic_bianfu,
        R.drawable.ic_bianselong,
        R.drawable.ic_daishu,
        R.drawable.ic_daxiang,
        R.drawable.ic_eyu,
        R.drawable.ic_gongji,
        R.drawable.ic_gou,
        R.drawable.ic_hainiu,
        R.drawable.ic_haiyaoyu,
        R.drawable.ic_hashiqi,
        R.drawable.ic_hema,
        R.drawable.ic_houzi,
        R.drawable.ic_huanxiong,
        R.drawable.ic_hudie,
        R.drawable.ic_jiakechong,
        R.drawable.ic_jingangyingwu,
        R.drawable.ic_jingyu
    )

    fun icon() = icons[Random.nextInt(0, icons.size - 1)]
}