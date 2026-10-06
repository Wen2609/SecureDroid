package com.armorlab.securedroid.ui.glass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.armorlab.securedroid.R
import com.google.android.material.card.MaterialCardView

/**
 * 毛玻璃背景层(对应上传稿的 .aurora + backdrop-filter: blur(22px))。
 *
 * 它做三件事:
 * 1. 画页面渐变 + 4 团缓慢漂移的光晕(与上传稿的 drift1-4 关键帧一一对应);
 * 2. 把「渐变 + 光晕」按 1/[BlurEngine.SCALE] 分辨率渲染并做三次盒式模糊,得到一张**真正模糊**的背景快照;
 * 3. 在子视图绘制之前,把这张快照按各"玻璃面"(半透明卡 / 悬浮导航)的位置裁进它们的圆角矩形里 ——
 *    卡片自身的半透明底再叠上去,于是卡片下面是**背景真的被糊了**,而不是"半透明蒙一层"的近似。
 *
 * 背景模糊的快照按 12fps 刷新(光晕动画本身很慢,肉眼无差),模糊半径按设计稿的 22px 换算;
 * 全部是纯 Bitmap + Canvas 运算,API 26 起都能跑,不依赖 RenderEffect / RenderScript。
 */
class GlassBackdropLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 一团光晕:尺寸、漂移终点、缩放终点、时长,以及四条边里用哪一条(-1 表示不用) */
    private class Blob(
        val colorRes: Int,
        val sizeDp: Float,
        val dxDp: Float,
        val dyDp: Float,
        val scaleTo: Float,
        val durationMs: Long,
        val leftDp: Float,
        val topDp: Float,
        val rightDp: Float,
        val bottomDp: Float
    ) {
        var progress = 0f
        var paint: Paint? = null
        var radiusPx = 0f
        var baseX = 0f
        var baseY = 0f
    }

    private val density = resources.displayMetrics.density

    private val blobs = listOf(
        Blob(R.color.c_aurora_1, 300f, 46f, 34f, 1.16f, 26_000L, -80f, -90f, Float.NaN, Float.NaN),
        Blob(R.color.c_aurora_2, 250f, -50f, 40f, 0.90f, 30_000L, Float.NaN, 100f, -100f, Float.NaN),
        Blob(R.color.c_aurora_3, 280f, 38f, -44f, 1.20f, 28_000L, -60f, Float.NaN, Float.NaN, -70f),
        Blob(R.color.c_aurora_4, 220f, -34f, -30f, 0.88f, 32_000L, Float.NaN, Float.NaN, -70f, 120f)
    )

    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snapshotPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glassPath = Path()
    private val glassRect = Rect()
    private val glassRectF = RectF()
    private val srcRect = Rect()
    private val dstRect = Rect()

    private var snapshot: Bitmap? = null
    private var snapshotPixels: IntArray? = null
    private var snapshotAt = 0L

    private var glassCache: List<View>? = null
    private var glassScanAt = 0L

    private val animators = mutableListOf<ValueAnimator>()

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resolveBackdrop()
        snapshotAt = 0L
    }

    /** 计算光晕落点与着色器(尺寸/主题色变了就重建) */
    private fun resolveBackdrop() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (b in blobs) {
            val size = b.sizeDp * density
            b.radiusPx = size / 2f
            b.baseX = if (b.leftDp.isNaN()) w - b.rightDp * density - size else b.leftDp * density
            b.baseY = if (b.topDp.isNaN()) h - b.bottomDp * density - size else b.topDp * density
            val color = ContextCompat.getColor(context, b.colorRes)
            b.paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    0f, 0f, b.radiusPx, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP
                )
            }
        }
        pagePaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            ContextCompat.getColor(context, R.color.c_bg_top),
            ContextCompat.getColor(context, R.color.c_bg_bottom),
            Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBlobs()
    }

    override fun onDetachedFromWindow() {
        stopBlobs()
        super.onDetachedFromWindow()
    }

    private fun startBlobs() {
        if (animators.isNotEmpty()) return
        for (b in blobs) {
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = b.durationMs
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    b.progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            animators.add(animator)
        }
    }

    private fun stopBlobs() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pagePaint.shader == null) resolveBackdrop()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pagePaint)
        for (b in blobs) drawBlob(canvas, b)
    }

    private fun drawBlob(canvas: Canvas, b: Blob) {
        val paint = b.paint ?: return
        val p = b.progress
        val scale = 1f + (b.scaleTo - 1f) * p
        canvas.save()
        canvas.translate(b.baseX + b.dxDp * density * p, b.baseY + b.dyDp * density * p)
        canvas.scale(scale, scale)
        canvas.drawCircle(0f, 0f, b.radiusPx, paint)
        canvas.restore()
    }

    /** 玻璃面在子视图之前绘制:卡片自己的半透明底随后叠上去,形成真正的"磨砂玻璃" */
    override fun dispatchDraw(canvas: Canvas) {
        val snap = ensureSnapshot()
        if (snap != null) {
            srcRect.set(0, 0, snap.width, snap.height)
            dstRect.set(0, 0, width, height)
            for (v in glassSurfaces()) {
                if (v.visibility != VISIBLE || v.alpha < 0.05f) continue
                offsetDescendantRectToMyCoords(v, glassRect)
                if (glassRect.width() <= 1 || glassRect.height() <= 1) continue
                if (!glassRect.intersect(0, 0, width, height)) continue
                val radius = radiusOf(v)
                glassPath.reset()
                glassRectF.set(glassRect)
                glassPath.addRoundRect(glassRectF, radius, radius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(glassPath)
                canvas.drawBitmap(snap, srcRect, dstRect, snapshotPaint)
                canvas.restore()
            }
        }
        super.dispatchDraw(canvas)
    }

    /** 低分辨率快照:渲染渐变 + 光晕 → 取像素 → 三次盒式模糊 → 写回 */
    private fun ensureSnapshot(): Bitmap? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        val lw = (w / BlurEngine.SCALE).coerceAtLeast(1)
        val lh = (h / BlurEngine.SCALE).coerceAtLeast(1)
        var bmp = snapshot
        if (bmp == null || bmp.width != lw || bmp.height != lh) {
            bmp = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
            snapshot = bmp
            snapshotPixels = IntArray(lw * lh)
            snapshotAt = 0L
        }
        val now = SystemClock.uptimeMillis()
        if (now - snapshotAt < SNAPSHOT_INTERVAL_MS) return bmp
        snapshotAt = now
        val canvas = Canvas(bmp)
        canvas.scale(lw / w.toFloat(), lh / h.toFloat())
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), pagePaint)
        for (b in blobs) drawBlob(canvas, b)
        val pixels = snapshotPixels ?: return bmp
        bmp.getPixels(pixels, 0, lw, 0, 0, lw, lh)
        BlurEngine.boxBlur(pixels, lw, lh, blurRadiusLowRes())
        bmp.setPixels(pixels, 0, lw, 0, 0, lw, lh)
        return bmp
    }

    /** 设计稿的 22px 模糊(≈ 5.5% 屏宽);换算到低分辨率快照上是 1/[BlurEngine.SCALE] */
    private fun blurRadiusLowRes(): Int =
        (DESIGN_BLUR_DP * density / BlurEngine.SCALE).toInt().coerceIn(2, 30)

    /** 收集"玻璃面":半透明卡片 + 悬浮导航。列表每 400ms 重扫一次(分页/切板块会增减卡片) */
    private fun glassSurfaces(): List<View> {
        val now = SystemClock.uptimeMillis()
        val cached = glassCache
        if (cached != null && now - glassScanAt < GLASS_RESCAN_MS) return cached
        val out = ArrayList<View>(8)
        collectGlass(this, out)
        glassCache = out
        glassScanAt = now
        return out
    }

    private fun collectGlass(group: ViewGroup, out: MutableList<View>) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (isGlass(child)) out.add(child)
            if (child is ViewGroup) collectGlass(child, out)
        }
    }

    private fun isGlass(v: View): Boolean = when {
        v is MaterialCardView -> Color.alpha(v.cardBackgroundColor.defaultColor) < 0xFF
        else -> false
    }

    private fun radiusOf(v: View): Float = when {
        v is MaterialCardView -> v.radius
        else -> 24f * density
    }

    private companion object {
        /** 背景快照刷新间隔:光晕很慢,12fps 足够,模糊成本可忽略 */
        const val SNAPSHOT_INTERVAL_MS = 80L

        /** 玻璃面列表重扫间隔 */
        const val GLASS_RESCAN_MS = 400L

        /** 上传稿的 backdrop-filter: blur(22px) */
        const val DESIGN_BLUR_DP = 22f

        /** 悬浮导航胶囊圆角(bg_dock 的 30dp) */
        const val DOCK_RADIUS_DP = 30f
    }
}
