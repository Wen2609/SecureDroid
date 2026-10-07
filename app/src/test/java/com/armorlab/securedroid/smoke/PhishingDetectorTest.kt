package com.armorlab.securedroid.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.vscan.PhishingDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 恶意链接 / 仿冒网址检测引擎测试。
 *
 * 判级分数是产品语义(≥70 危险 / ≥40 可疑),测试同时锁分数边界与特征文案,
 * 防止后续调阈值时悄悄改变"什么算危险"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhishingDetectorTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun legitimateUrlIsClean() {
        val v = PhishingDetector.check(ctx, "https://www.google.com/search?q=test")
        assertEquals("clean", v.level)
        assertTrue(v.findings.contains("未发现异常"))
    }

    @Test
    fun brandImitationPlusRiskyTldIsDangerous() {
        // 品牌伪装 45 + 高风险 TLD 20 + 凭证词 10 = 75
        val v = PhishingDetector.check(ctx, "https://google.com-verify.xyz/account/login")
        assertEquals("danger", v.level)
        assertTrue(v.findings.any { it.contains("伪装") })
    }

    @Test
    fun userinfoTrickIsFlagged() {
        // @ 隐藏主机 25 + http 明文 15 + 高风险 TLD 20 = 60
        val v = PhishingDetector.check(ctx, "http://google.com@evil.xyz/pay")
        assertEquals("warn", v.level)
        assertTrue(v.findings.any { it.contains("@") })
    }

    @Test
    fun punycodeIsSuspect() {
        val v = PhishingDetector.check(ctx, "https://xn--80ak6aa92e.com/download")
        assertEquals("warn", v.level)
        assertTrue(v.findings.any { it.contains("Punycode") })
    }

    @Test
    fun shortenerAloneIsWeakNotSuspicious() {
        // 短链是弱特征(30 分):单独命中不足以判可疑,避免误报正常分享链接
        val v = PhishingDetector.check(ctx, "https://bit.ly/abc")
        assertEquals("clean", v.level)
    }

    @Test
    fun blocklistHitPlusHttpIsDangerous() {
        // 内置黑名单 .onion 60 + http 明文 15 = 75
        val v = PhishingDetector.check(ctx, "http://abcdef.onion/path")
        assertEquals("danger", v.level)
        assertTrue(v.findings.any { it.contains("黑名单") })
    }

    @Test
    fun bareIpIsFlagged() {
        // IP 直连 30 + http 15 + 凭证词 10 = 55
        val v = PhishingDetector.check(ctx, "http://93.184.216.34/login")
        assertEquals("warn", v.level)
    }

    @Test
    fun privateNetworkIsSkipped() {
        val v = PhishingDetector.check(ctx, "http://192.168.1.1/admin")
        assertEquals("clean", v.level)
    }

    @Test
    fun invalidInputHandled() {
        assertEquals("invalid", PhishingDetector.check(ctx, "").level)
        assertEquals("invalid", PhishingDetector.check(ctx, "随便说说").level)
    }
}
