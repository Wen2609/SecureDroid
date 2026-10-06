package com.armorlab.securedroid.ui.glass

/**
 * 毛玻璃的"真背景模糊"实现(不依赖 RenderEffect,API 26 起可用)。
 *
 * 做法:把背景层(页面渐变 + 光晕)按 1/[SCALE] 分辨率画进一张小位图 —— 降采样本身就是一次强模糊 ——
 * 再在小位图上做三次可分离盒式模糊(每轮水平 + 垂直,O(像素数))逼近高斯,
 * 毛玻璃卡片按自己的位置从这张模糊位图里取景(见 [GlassBackdropLayout])。
 *
 * 于是卡片下面**是真的糊了**,而不是"半透明蒙一层"的近似。
 */
internal object BlurEngine {

    /** 快照缩放比:360dp 屏上约 60 像素宽,再大的模糊半径也不会有成本压力 */
    const val SCALE = 6

    /** 三次盒式模糊(水平 + 垂直各一遍算一轮) */
    fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        if (radius < 1 || width < 2 || height < 2) return
        val tmp = IntArray(pixels.size)
        repeat(passes) {
            horizontal(pixels, tmp, width, height, radius)
            vertical(tmp, pixels, width, height, radius)
        }
    }

    private fun horizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val v = src[row + k.coerceIn(0, width - 1)]
                a += (v ushr 24) and 0xFF
                r += (v shr 16) and 0xFF
                g += (v shr 8) and 0xFF
                b += v and 0xFF
            }
            for (x in 0 until width) {
                dst[row + x] = (((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window))
                val out = src[row + (x - radius).coerceIn(0, width - 1)]
                val inn = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                a += ((inn ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                r += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val v = src[k.coerceIn(0, height - 1) * width + x]
                a += (v ushr 24) and 0xFF
                r += (v shr 16) and 0xFF
                g += (v shr 8) and 0xFF
                b += v and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = (((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window))
                val out = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val inn = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                a += ((inn ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                r += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
