package com.armorlab.securedroid.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.armorlab.securedroid.security.IntegrityGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 完整性守卫测试。
 *
 * 这里的核心不是"能不能读到签名",而是**失败时绝不能静默放行**:
 * 一个把 UNAVAILABLE 当 TRUSTED 的完整性校验,比没有校验更危险——
 * 它会让用户以为应用是可信的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntegrityGuardTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        IntegrityGuard.resetPin(ctx)
    }

    @Test
    fun evaluateCoversAllVerdicts() {
        assertEquals(
            IntegrityGuard.Verdict.UNAVAILABLE,
            IntegrityGuard.evaluate("aa", null).verdict
        )
        assertEquals(
            IntegrityGuard.Verdict.FIRST_SEEN,
            IntegrityGuard.evaluate(null, "aa").verdict
        )
        assertEquals(
            IntegrityGuard.Verdict.TRUSTED,
            IntegrityGuard.evaluate("AABB", "aabb").verdict
        )
        val mismatch = IntegrityGuard.evaluate("aa", "bb")
        assertEquals(IntegrityGuard.Verdict.MISMATCH, mismatch.verdict)
        assertTrue("签名不一致必须触发告警", mismatch.shouldWarn)
    }

    @Test
    fun firstRunPinsSignatureThenTrusts() {
        val first = IntegrityGuard.check(ctx)
        if (first.currentDigest == null) {
            // 运行环境拿不到签名信息:必须报 UNAVAILABLE,不能报 TRUSTED
            assertEquals(IntegrityGuard.Verdict.UNAVAILABLE, first.verdict)
            assertEquals(IntegrityGuard.Verdict.UNAVAILABLE, IntegrityGuard.check(ctx).verdict)
            return
        }
        assertEquals(IntegrityGuard.Verdict.FIRST_SEEN, first.verdict)
        assertEquals(
            "首次运行应把当前指纹写入记录",
            first.currentDigest,
            IntegrityGuard.pinnedDigest(ctx)
        )
        assertEquals(
            "第二次校验应与记录一致",
            IntegrityGuard.Verdict.TRUSTED,
            IntegrityGuard.check(ctx).verdict
        )
    }

    @Test
    fun changedSignatureIsReportedAsMismatch() {
        val current = IntegrityGuard.signingDigest(ctx)
        if (current == null) {
            assertEquals(IntegrityGuard.Verdict.UNAVAILABLE, IntegrityGuard.check(ctx).verdict)
            return
        }
        // 模拟"首次记录的指纹来自另一个签名证书"(即被重打包覆盖安装)
        ctx.getSharedPreferences("sd_integrity", Context.MODE_PRIVATE)
            .edit()
            .putString("pinned_signer_sha256", "0".repeat(64))
            .commit()
        val r = IntegrityGuard.check(ctx)
        assertEquals(IntegrityGuard.Verdict.MISMATCH, r.verdict)
        assertTrue(r.shouldWarn)
        assertEquals("不应把错误指纹覆盖成新 pin", "0".repeat(64), IntegrityGuard.pinnedDigest(ctx))
    }

    @Test
    fun signingDigestIsSha256Shaped() {
        val d = IntegrityGuard.signingDigest(ctx)
        if (d != null) {
            assertEquals("证书指纹应为 64 位十六进制", 64, d.length)
            assertTrue("指纹应全为小写十六进制", d.all { it in "0123456789abcdef" })
            assertNotNull(IntegrityGuard.check(ctx).currentDigest)
        }
        // d == null 时不做断言:该环境不提供签名信息,已由上面的用例覆盖
    }

    @Test
    fun resetPinForcesNextCheckToFirstSeen() {
        val first = IntegrityGuard.check(ctx)
        if (first.currentDigest == null) return
        IntegrityGuard.resetPin(ctx)
        assertEquals(IntegrityGuard.Verdict.FIRST_SEEN, IntegrityGuard.check(ctx).verdict)
    }
}
