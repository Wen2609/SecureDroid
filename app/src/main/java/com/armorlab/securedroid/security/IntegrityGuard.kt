package com.armorlab.securedroid.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 运行时完整性守卫(OWASP MASVS-RESILIENCE-1/3:防重打包、防篡改)。
 *
 * 威胁模型与边界(必须诚实说明):
 * - **能防**:攻击者反编译本应用、注入代码后用自己的密钥重新签名发布。此时签名证书
 *   指纹与首次安装时记录的不一致,本守卫会告警。这对安全类应用尤其重要——
 *   被改造过的"安全软件"本身就是最好的木马载体。
 * - **不能防**:拥有 root 的攻击者可以直接改写本应用的私有数据、修改 pin、
 *   甚至 hook 本方法。这在移动端无解,只能提高攻击成本;真正的最后防线是
 *   用户从官方渠道安装,以及应用商店的签名校验。
 * - 首次启动时采用 TOFU(首次使用即信任)记录指纹,因此"先装正版再装盗版覆盖"
 *   会命中 MISMATCH;而"直接安装盗版"会被当作首次使用而放行,这是 TOFU 的固有上限。
 */
object IntegrityGuard {

    private const val PREFS = "sd_integrity"
    private const val KEY_PIN = "pinned_signer_sha256"

    /** 完整性判定结果 */
    enum class Verdict {
        /** 签名与首次记录一致 */
        TRUSTED,
        /** 首次运行,已记录本次指纹 */
        FIRST_SEEN,
        /** 签名与首次记录不一致(疑似重打包 / 被替换) */
        MISMATCH,
        /** 取不到签名信息(极简 ROM、被 hook 等),不能断定安全 */
        UNAVAILABLE
    }

    data class IntegrityResult(
        val verdict: Verdict,
        val currentDigest: String?,
        val pinnedDigest: String?,
        val multipleSigners: Boolean = false
    ) {
        /** 是否需要在界面上向用户告警 */
        val shouldWarn: Boolean get() = verdict == Verdict.MISMATCH
    }

    /** 纯逻辑判定,便于单元测试与在无 Context 环境复用 */
    fun evaluate(pinned: String?, current: String?): IntegrityResult {
        if (current.isNullOrEmpty()) return IntegrityResult(Verdict.UNAVAILABLE, null, pinned)
        if (pinned.isNullOrEmpty()) return IntegrityResult(Verdict.FIRST_SEEN, current, null)
        val same = pinned.equals(current, ignoreCase = true)
        return IntegrityResult(if (same) Verdict.TRUSTED else Verdict.MISMATCH, current, pinned)
    }

    /**
     * 当前安装包的签名证书指纹:SHA-256(小写十六进制)。
     * 取不到时返回 null(调用方必须按 UNAVAILABLE 处理,而不是当作通过)。
     */
    fun signingDigest(context: Context): String? = try {
        val signers = signersOf(context)
        val first = signers?.firstOrNull()
        if (first == null) null else sha256Hex(first.toByteArray())
    } catch (_: Exception) {
        null
    }

    /** 是否存在多个签名者(正常情况下 Android 应用只有一个签名者,多签名者本身可疑) */
    fun hasMultipleSigners(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo(context)?.signingInfo?.hasMultipleSigners() ?: false
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    /**
     * 读取已记录指纹并比对;首次运行时记录当前指纹(TOFU)。
     * 注意:本方法会写 SharedPreferences,应在主流程早期调用一次。
     */
    @Suppress("ApplySharedPref") // 必须同步落盘:pin 一旦丢失就会退化为"首次运行",不能依赖异步写
    fun check(context: Context): IntegrityResult {
        val current = signingDigest(context)
        val multi = hasMultipleSigners(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pinned = prefs.getString(KEY_PIN, null)
        val base = evaluate(pinned, current)
        if (base.verdict == Verdict.FIRST_SEEN && current != null) {
            prefs.edit().putString(KEY_PIN, current).commit()
        }
        return if (multi) {
            base.copy(verdict = Verdict.MISMATCH, multipleSigners = true)
        } else {
            base.copy(multipleSigners = false)
        }
    }

    /** 已记录指纹(诊断用) */
    fun pinnedDigest(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN, null)

    /** 抹除记录(重装、密钥轮换后的恢复入口) */
    @Suppress("ApplySharedPref") // 同上:恢复动作必须立即生效,避免与后续 check() 竞争
    fun resetPin(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PIN).commit()
    }

    @Suppress("DEPRECATION")
    private fun signersOf(context: Context): Array<android.content.pm.Signature>? {
        val info = packageInfo(context) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context): PackageInfo? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(Character.forDigit(v ushr 4, 16))
            sb.append(Character.forDigit(v and 0x0F, 16))
        }
        return sb.toString()
    }
}
