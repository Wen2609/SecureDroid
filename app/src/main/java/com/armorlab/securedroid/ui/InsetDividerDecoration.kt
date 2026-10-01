package com.armorlab.securedroid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.armorlab.securedroid.R

/**
 * iOS inset grouped 的分隔线:只画在**行与行之间**(最后一行下方不画),
 * 左侧按 [insetStartDp] 内缩,与行内文字对齐 —— 这是 iOS 分组列表的标志性细节。
 *
 * 之所以用 ItemDecoration 而不是在每个 item 布局里塞一条线:后者会让最后一行下方
 * 也出现分隔线,在分组卡片底部形成一条多余的双线。
 */
class InsetDividerDecoration(
    context: Context,
    private val insetStartDp: Int = 52
) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.sd_hairline)
        isAntiAlias = true
    }
    private val thickness = (0.5f * density).coerceAtLeast(1f)

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter ?: return
        val lastIndex = adapter.itemCount - 1
        if (lastIndex < 1) return
        val left = insetStartDp * density
        val bounds = Rect()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position == lastIndex) continue
            parent.getDecoratedBoundsWithMargins(child, bounds)
            val y = bounds.bottom.toFloat()
            c.drawRect(left, y - thickness, parent.width.toFloat(), y, paint)
        }
    }
}
