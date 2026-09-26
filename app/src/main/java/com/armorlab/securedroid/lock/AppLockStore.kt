package com.armorlab.securedroid.lock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.UUID

/**
 * 应用锁安全存储:
 * - 使用 EncryptedSharedPreferences(AES-256-GCM)加密落盘;
 * - PIN 仅存加盐 SHA-256 摘要,不存明文。
 */
object AppLockStore {

    private const val FILE = "app_lock_secure"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_SALT = "salt"
    private const val KEY_LOCKED = "locked_apps"
    private const val KEY_UNLOCK_AT = "last_unlock_at"
    private const val PIN_LENGTH = 4
    private const val UNLOCK_WINDOW_MS = 60_000L

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setPin(context: Context, pin: String) {
        require(pin.length == PIN_LENGTH) { "PIN must be 4 digits" }
        val salt = UUID.randomUUID().toString()
        prefs(context).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_PIN_HASH, sha256Hex(salt + pin))
            .apply()
    }

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) != null

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_SALT, null) ?: return false
        val stored = p.getString(KEY_PIN_HASH, null) ?: return false
        val candidate = sha256Hex(salt + pin)
        return MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            candidate.toByteArray(Charsets.UTF_8)
        )
    }

    fun isLocked(context: Context, pkg: String): Boolean =
        prefs(context).getStringSet(KEY_LOCKED, emptySet())?.contains(pkg) == true

    fun setLocked(context: Context, pkg: String, locked: Boolean) {
        val p = prefs(context)
        val current = LinkedHashSet(p.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet())
        if (locked) current.add(pkg) else current.remove(pkg)
        p.edit().putStringSet(KEY_LOCKED, current).apply()
    }

    fun lockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()

    fun markUnlocked(context: Context) {
        prefs(context).edit().putLong(KEY_UNLOCK_AT, System.currentTimeMillis()).apply()
    }

    fun isRecentlyUnlocked(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_UNLOCK_AT, 0L) < UNLOCK_WINDOW_MS

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
