package com.armorlab.securedroid.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 线程安全的时间格式化:
 * - SimpleDateFormat 非线程安全,原来每处调用各自 new(ThreatReport.fmt() 甚至每次 new 一个),
 *   这里用 ThreadLocal 缓存实例,避免热路径反复编译模式;
 * - 同时跟踪 Locale 变化:用户在系统里切换语言后格式立即跟随新语言,
 *   不会像"静态缓存一个 Formatter"那样停留在旧语言(原 ThreatReport 的注释诉求);
 * - 需要新格式时用 [of] 取一个共享实例,不要在调用点 new SimpleDateFormat。
 */
object TimeFmt {

    private val minute = Holder("yyyy-MM-dd HH:mm")
    private val second = Holder("yyyy-MM-dd HH:mm:ss")
    private val day = Holder("yyyy-MM-dd")
    private val clock = Holder("HH:mm:ss")

    fun dateMinute(ms: Long): String = minute.format(ms)

    fun dateSecond(ms: Long): String = second.format(ms)

    fun dateDay(ms: Long): String = day.format(ms)

    fun clockSecond(ms: Long): String = clock.format(ms)

    /** 自定义模式:同一 pattern 复用同一实例 */
    fun of(pattern: String): Holder = Holder(pattern)

    /** 每个 pattern 一份 ThreadLocal 实例,Locale 变化时自动重建 */
    class Holder internal constructor(private val pattern: String) {

        private class Impl(@Volatile var locale: Locale, @Volatile var fmt: SimpleDateFormat)

        private val local = ThreadLocal.withInitial {
            val l = Locale.getDefault()
            Impl(l, SimpleDateFormat(pattern, l))
        }

        fun format(ms: Long): String {
            val impl = local.get()
            val now = Locale.getDefault()
            if (now != impl.locale) {
                impl.locale = now
                impl.fmt = SimpleDateFormat(pattern, now)
            }
            return impl.fmt.format(Date(ms))
        }

        fun format(date: Date): String = format(date.time)
    }
}
