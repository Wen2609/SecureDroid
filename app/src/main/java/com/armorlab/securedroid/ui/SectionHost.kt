package com.armorlab.securedroid.ui

/**
 * 板块页面:三个顶层板块(状态 / 检测 / 防护)内部用分段控件承载二级功能。
 * 顶层入口从 6 个收敛到 3 个 —— 底部导航每多一项,决策成本就上升一档。
 */
interface SectionHost {
    /** 切换到第 [index] 个二级分段(0 起) */
    fun selectSegment(index: Int)
}
