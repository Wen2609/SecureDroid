package com.armorlab.securedroid.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.UUID

/**
 * 应用锁安全存储:
 * - 使用 EncryptedSharedPreferences(AES-256-GCM)加密落盘;
 * - PIN 仅存加盐 SHA-256 摘要,不存明文。
 *
 * 故障策略(重要):AndroidKeyStore 在极少数情况下不可用(密钥库损坏、受限环境、
 * 部分定制系统)。此时**绝不崩溃**,也**绝不退化为明文存储**,而是统一降级为
 * "应用锁未启用"(hasPin = false),并记录日志。加密存储不可用属于安全能力缺失,
 * 静默降级为明文保存 PIN 摘要会掩盖真实的安全状态,因此不做该退让。
 */
object AppLockStore {

    private const val TAG = "AppLockStore"
    private const val FILE = "app_lock_secure"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_SALT = "salt"
    private const val KEY_LOCKED = "locked_apps"
    private const val KEY_UNLOCK_AT = "last_unlock_at"
    private const val PIN_LENGTH = 4
    private const val UNLOCK_WINDOW_MS = 60_000L
    private const val KEY_ATTEMPTS = "pin_attempts"
    private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
    private const val KEY_DECOY = "decoy_enabled"
    private const val KEY_BIOMETRIC = "biometric_enabled"
    private const val FAILED_RETRY_MS = 5_000L

    /**
     * 打开加密存储(进程内单例缓存);AndroidKeyStore 不可用时返回 null,由调用方安全降级。
     *
     * 单例缓存是硬性能要求:EncryptedSharedPreferences.create() 每次 build MasterKey +
     * 派生密钥集,单次可达数十毫秒。而 isLocked() 处在两个高频热路径上 ——
     * 无障碍服务每个窗口事件、WebUI 应用锁列表的每一行 —— 逐次重建会让
     * 应用切换与列表加载付出成百上千次密钥派生的代价。
     * 创建失败保留 5 秒冷却:KeyStore 故障短期不会自愈,期间快速返回 null,
     * 冷却过后允许重试(故障可能是暂时的,例如密钥库尚未就绪)。
     */
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    private var failedAt = 0L

    private val createLock = Any()

    private fun prefs(context: Context): SharedPreferences? {
        cachedPrefs?.let { return it }
        synchronized(createLock) {
            cachedPrefs?.let { return it }
            if (System.currentTimeMillis() - failedAt < FAILED_RETRY_MS) return null
            return try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cachedPrefs = it }
            } catch (t: Throwable) {
                failedAt = System.currentTimeMillis()
                Log.w(TAG, "安全存储不可用,应用锁降级为未启用状态(不崩溃、不落明文)", t)
                null
            }
        }
    }

    /** 防暴力破解:记录失败次数,每 3 次错误递增锁定(attempts x 10 秒) */
    fun registerFailedAttempt(context: Context): Int {
        val p = prefs(context) ?: return 0
        val attempts = p.getInt(KEY_ATTEMPTS, 0) + 1
        val edit = p.edit().putInt(KEY_ATTEMPTS, attempts)
        if (attempts % 3 == 0) {
            edit.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + attempts * 10_000L)
        }
        edit.apply()
        return attempts
    }

    fun resetAttempts(context: Context) {
        prefs(context)?.edit()?.putInt(KEY_ATTEMPTS, 0)?.putLong(KEY_LOCKOUT_UNTIL, 0L)?.apply()
    }

    fun lockoutRemainingMs(context: Context): Long {
        val until = prefs(context)?.getLong(KEY_LOCKOUT_UNTIL, 0L) ?: return 0L
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun isDecoyEnabled(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_DECOY, false) ?: false

    fun setDecoyEnabled(context: Context, value: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_DECOY, value)?.apply()
    }

    /** 生物识别开关(仅作为已设 PIN 的替代解锁路径;能力检测独立于开关) */
    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_BIOMETRIC, false) ?: false

    fun setBiometricEnabled(context: Context, value: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_BIOMETRIC, value)?.apply()
    }

    /**
     * 设备是否具备可用生物识别:API 29+ 走 BiometricManager,
     * API 28 走 FingerprintManager(框架 BiometricPrompt 最低 28)。
     * 开启生物解锁前必须先通过本检查,避免在无指纹/面容设备上打开后无法解锁。
     */
    fun canBiometric(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 28) return false
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val bm = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                bm?.canAuthenticate() ==
                    android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                val fm = context.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)
                fm != null && fm.hasEnrolledFingerprints()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** @return 是否成功持久化(failure 通常意味着安全存储不可用) */
    fun setPin(context: Context, pin: String): Boolean {
        require(pin.length == PIN_LENGTH) { "PIN must be 4 digits" }
        val p = prefs(context) ?: return false
        val salt = UUID.randomUUID().toString()
        p.edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_PIN_HASH, sha256Hex(salt + pin))
            .apply()
        return true
    }

    fun hasPin(context: Context): Boolean =
        prefs(context)?.getString(KEY_PIN_HASH, null) != null

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context) ?: return false
        val salt = p.getString(KEY_SALT, null) ?: return false
        val stored = p.getString(KEY_PIN_HASH, null) ?: return false
        val candidate = sha256Hex(salt + pin)
        return MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            candidate.toByteArray(Charsets.UTF_8)
        )
    }

    fun isLocked(context: Context, pkg: String): Boolean =
        prefs(context)?.getStringSet(KEY_LOCKED, emptySet())?.contains(pkg) == true

    fun setLocked(context: Context, pkg: String, locked: Boolean) {
        val p = prefs(context) ?: return
        val current = LinkedHashSet(p.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet())
        if (locked) current.add(pkg) else current.remove(pkg)
        p.edit().putStringSet(KEY_LOCKED, current).apply()
    }

    fun lockedApps(context: Context): Set<String> =
        prefs(context)?.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()

    fun markUnlocked(context: Context) {
        prefs(context)?.edit()?.putLong(KEY_UNLOCK_AT, System.currentTimeMillis())?.apply()
    }

    fun isRecentlyUnlocked(context: Context): Boolean {
        val last = prefs(context)?.getLong(KEY_UNLOCK_AT, 0L) ?: return false
        return System.currentTimeMillis() - last < UNLOCK_WINDOW_MS
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
