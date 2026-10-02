package com.armorlab.securedroid.vscan

import com.armorlab.securedroid.core.PackageSnapshot
import android.content.Context
import android.content.pm.PackageManager
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter
import java.security.MessageDigest

/** 证书信任审计:签名指纹白名单 + 严格模式(非白名单证书即告警) */
object CertStore {

    private const val KEY_GOOD = "trusted_certs"
    private const val KEY_STRICT = "cert_strict"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun good(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_GOOD, emptySet()) ?: emptySet()

    fun markGood(context: Context, hash: String) {
        val p = prefs(context)
        val set = LinkedHashSet(p.getStringSet(KEY_GOOD, emptySet()) ?: emptySet())
        set.add(hash)
        p.edit().putStringSet(KEY_GOOD, set).apply()
    }

    fun strict(context: Context): Boolean = prefs(context).getBoolean(KEY_STRICT, false)

    fun setStrict(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRICT, value).apply()
    }

    fun certHash(context: Context, pkg: String): String? = try {
        val sigs = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        if (sigs.isNullOrEmpty()) null
        else MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray())
            .joinToString("") { String.format("%02x", it) }
    } catch (_: Exception) { null }

    fun items(context: Context): List<TrojanAdapter.UiItem> {
        val pm = context.packageManager
        val good = good(context)
        val strict = strict(context)
        val items = mutableListOf<TrojanAdapter.UiItem>()
        var untrusted = 0
        for (info in PackageSnapshot.installedPackages(context, 0).take(120)) {
            val app = info.applicationInfo ?: continue
            if ((app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val hash = certHash(context, info.packageName) ?: continue
            val known = good.any { hash.startsWith(it) }
            val label = PackageSnapshot.label(context, app)
            val level = when {
                known -> ThreatLevel.LOW
                strict -> ThreatLevel.HIGH
                else -> ThreatLevel.MEDIUM
            }
            if (level != ThreatLevel.LOW) untrusted++
            items.add(
                TrojanAdapter.UiItem(
                    (if (known) "Cert.Trusted · " else "Cert.Untrusted · ") + label,
                    info.packageName + " · " + hash.take(24) + "…",
                    if (known) "签名指纹在信任白名单中" else "签名指纹不在白名单" + (if (strict) "(严格模式)" else ""),
                    level,
                    if (!known) "确认可信后可在\"标记证书白名单\"中加入指纹" else null,
                    null, null, null, null
                )
            )
        }
        items.add(
            0,
            TrojanAdapter.UiItem(
                "Cert.Summary", "第三方应用证书审计:白名单 " + good.size +
                    " 条,未信任 " + untrusted + " 个,严格模式 " + (if (strict) "开" else "关"),
                "", ThreatLevel.LOW, null, null
            )
        )
        return items
    }
}
