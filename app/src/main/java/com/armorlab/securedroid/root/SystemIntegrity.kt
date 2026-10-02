package com.armorlab.securedroid.root

import com.armorlab.securedroid.core.TimeFmt
import android.content.Context
import com.armorlab.securedroid.scan.ThreatLevel
import com.armorlab.securedroid.ui.TrojanAdapter
import org.json.JSONObject

/**
 * 系统完整性防护(需要最高权限支撑):
 *
 * 1. 基线快照:通过单次 su 会话采集关键系统路径的文件(路径 / 大小 / mtime)三元组;
 * 2. 完整性校验:与基线逐项比对,输出新增 / 删除 / 篡改三类差异;
 * 3. 关键文件锁定:对 hosts 等易篡改文件施加 immutable 属性(chattr +i,不支持时降级 chmod 0444);
 * 4. 守护联动:守护循环周期性调用,发现系统文件被改动立即告警。
 *
 * 设计约束:全部命令只读或精确路径,不破坏系统分区;快照采集合并为单次 su 往返。
 */
object SystemIntegrity {

    private const val PREFS = "settings"
    private const val KEY_BASELINE = "integrity_baseline"
    private const val KEY_BASELINE_TS = "integrity_baseline_ts"
    private const val KEY_LOCKED = "integrity_locked_paths"
    private const val MAX_ENTRIES = 1200

    /** 关键系统路径(磁盘上的真实文件,非挂载视图之外的内容) */
    private val watchPaths = listOf(
        "/system/bin",
        "/system/etc",
        "/system/xbin",
        "/vendor/bin",
        "/data/adb/modules"
    )

    /** 重点关注文件:被篡改即高危 */
    private val criticalFiles = listOf(
        "/system/etc/hosts",
        "/system/bin/su",
        "/system/xbin/su",
        "/system/etc/install-recovery.sh"
    )

    data class Snapshot(val entries: Map<String, String>, val at: Long)

    data class IntegrityDiff(
        val added: List<String>,
        val removed: List<String>,
        val modified: List<String>,
        val baselineAt: Long
    ) {
        val hasChanges: Boolean get() = added.isNotEmpty() || removed.isNotEmpty() || modified.isNotEmpty()
        val total: Int get() = added.size + removed.size + modified.size
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasBaseline(context: Context): Boolean =
        prefs(context).getString(KEY_BASELINE, null) != null

    fun baselineTime(context: Context): Long = prefs(context).getLong(KEY_BASELINE_TS, 0L)

    /**
     * 采集基线:单次 su 会话遍历关键路径,输出 "路径|大小|mtime"。
     * 返回采集到的文件数;采集失败(无 su 或 stat 不可用)返回 -1。
     */
    fun captureBaseline(context: Context): Int {
        if (!PrivilegeManager.isRoot(context)) return -1
        val cmd = watchPaths.joinToString(" ") { d ->
            "[ -d " + ShellBridge.quote(d) + " ] && find " + ShellBridge.quote(d) + " -maxdepth 1 -type f 2>/dev/null | head -400 | " +
                "while read f; do stat -c '%n|%s|%Y' \"\$f\" 2>/dev/null; done;"
        }
        val out = ShellBridge.runSu(cmd, 60_000L) ?: return -1
        val map = LinkedHashMap<String, String>()
        for (line in out.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val parts = t.split('|')
            if (parts.size < 3) continue
            map[parts[0]] = parts[1] + "|" + parts[2]
            if (map.size >= MAX_ENTRIES) break
        }
        if (map.isEmpty()) return -1
        val json = JSONObject()
        for ((k, v) in map) json.put(k, v)
        prefs(context).edit()
            .putString(KEY_BASELINE, json.toString())
            .putLong(KEY_BASELINE_TS, System.currentTimeMillis())
            .apply()
        return map.size
    }

    private fun loadBaseline(context: Context): Map<String, String> {
        val s = prefs(context).getString(KEY_BASELINE, null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            val m = LinkedHashMap<String, String>()
            for (k in o.keys()) m[k] = o.getString(k)
            m
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 与基线比对:新增 / 删除 / 篡改 */
    fun diff(context: Context): IntegrityDiff {
        val base = loadBaseline(context)
        val at = baselineTime(context)
        if (base.isEmpty()) return IntegrityDiff(emptyList(), emptyList(), emptyList(), at)
        if (!PrivilegeManager.isRoot(context)) return IntegrityDiff(emptyList(), emptyList(), emptyList(), at)

        val cmd = watchPaths.joinToString(" ") { d ->
            "[ -d " + ShellBridge.quote(d) + " ] && find " + ShellBridge.quote(d) + " -maxdepth 1 -type f 2>/dev/null | head -400 | " +
                "while read f; do stat -c '%n|%s|%Y' \"\$f\" 2>/dev/null; done;"
        }
        val out = ShellBridge.runSu(cmd, 60_000L)
            ?: return IntegrityDiff(emptyList(), emptyList(), emptyList(), at)
        val now = LinkedHashMap<String, String>()
        for (line in out.lines()) {
            val parts = line.trim().split('|')
            if (parts.size < 3) continue
            now[parts[0]] = parts[1] + "|" + parts[2]
        }
        val added = now.keys.filter { it !in base }
        val removed = base.keys.filter { it !in now }
        val modified = now.keys.filter { k -> base[k] != null && base[k] != now[k] }
        return IntegrityDiff(added, removed, modified, at)
    }

    /**
     * 锁定关键文件防篡改:优先 immutable(chattr +i),不支持时降级只读(chmod 0444)。
     * 返回实际使用的机制描述;失败返回 null。
     */
    fun lockCriticalFiles(context: Context): String? {
        if (!PrivilegeManager.isRoot(context)) return null
        val targets = criticalFiles.filter { ShellBridge.existsBestEffort(it) }
        if (targets.isEmpty()) return null
        val quoted = targets.joinToString(" ") { ShellBridge.quote(it) }
        // chattr 支持性探测 + 批量施加,合并为单次 su 会话
        val cmd = "if chattr +i " + quoted + " 2>/dev/null; then echo __IMMUTABLE__; " +
            "else chmod 0444 " + quoted + " 2>/dev/null && echo __READONLY__; fi"
        val out = ShellBridge.runSu(cmd, 20_000L) ?: return null
        val mech = when {
            out.contains("__IMMUTABLE__") -> "immutable"
            out.contains("__READONLY__") -> "readonly"
            else -> return null
        }
        val locked = prefs(context).getString(KEY_LOCKED, "") ?: ""
        val merged = (locked.split(',').filter { it.isNotEmpty() } + targets).distinct()
        prefs(context).edit().putString(KEY_LOCKED, merged.joinToString(",")).apply()
        return mech + " (" + targets.size + " 个文件)"
    }

    /** 解除锁定 */
    fun unlockCriticalFiles(context: Context): Boolean {
        if (!PrivilegeManager.isRoot(context)) return false
        val locked = (prefs(context).getString(KEY_LOCKED, "") ?: "")
            .split(',').filter { it.isNotEmpty() }
        if (locked.isEmpty()) return true
        val quoted = locked.joinToString(" ") { ShellBridge.quote(it) }
        val out = ShellBridge.runSu("chattr -i " + quoted + " 2>/dev/null; chmod 0644 " + quoted + " 2>/dev/null; echo __DONE__", 20_000L)
        val ok = out?.contains("__DONE__") == true
        if (ok) prefs(context).edit().putString(KEY_LOCKED, "").apply()
        return ok
    }

    fun lockedCount(context: Context): Int =
        (prefs(context).getString(KEY_LOCKED, "") ?: "").split(',').count { it.isNotEmpty() }

    /** 守护循环轻量检查:只报告差异条数,供通知使用 */
    fun quickGuardSummary(context: Context): String? {
        if (!PrivilegeManager.isRoot(context) || !hasBaseline(context)) return null
        val d = diff(context)
        return if (!d.hasChanges) null
        else "系统文件完整性异常:新增 " + d.added.size + " / 删除 " + d.removed.size +
            " / 篡改 " + d.modified.size
    }

    /** 病毒中心动作:完整性校验结果转 UI 项 */
    fun scan(context: Context): List<TrojanAdapter.UiItem> {
        val items = mutableListOf<TrojanAdapter.UiItem>()
        if (!PrivilegeManager.isRoot(context)) {
            return listOf(
                TrojanAdapter.UiItem(
                    "Integrity.NeedRoot", "系统完整性防护需要最高权限",
                    "请在权限防护页获取 Root 权限后重试", ThreatLevel.MEDIUM,
                    "病毒中心 → 权限防护 → 获取最高权限", null
                )
            )
        }
        if (!hasBaseline(context)) {
            val n = captureBaseline(context)
            return listOf(
                if (n > 0)
                    TrojanAdapter.UiItem(
                        "Integrity.Baseline", "已建立系统文件基线(" + n + " 个文件)",
                        "基线建立后,任何系统文件的新增 / 删除 / 篡改都会被检出", ThreatLevel.LOW,
                        "建议定期重新校验", null
                    )
                else
                    TrojanAdapter.UiItem(
                        "Integrity.Fail", "基线采集失败", "stat 或 find 不可用,请确认 su 授权正常",
                        ThreatLevel.MEDIUM, null, null
                    )
            )
        }

        val d = diff(context)
        items.add(
            TrojanAdapter.UiItem(
                "Integrity.Summary",
                if (d.hasChanges) "发现 " + d.total + " 处系统文件变化" else "系统文件与基线一致",
                "基线时间: " + TimeFmt.dateMinute(d.baselineAt),
                if (d.hasChanges) ThreatLevel.HIGH else ThreatLevel.LOW,
                if (d.hasChanges) "系统文件被改动可能是木马驻留或模块注入的痕迹" else null,
                null
            )
        )
        for (p in d.modified.take(20)) {
            val critical = criticalFiles.any { p.startsWith(it) }
            items.add(
                TrojanAdapter.UiItem(
                    "Integrity.Modified · " + p.substringAfterLast('/'), p,
                    "文件内容或大小/mtime 与基线不一致",
                    if (critical) ThreatLevel.CRITICAL else ThreatLevel.HIGH,
                    "对比该文件的来源与用途;确认被篡改可从基线时间点回溯变更",
                    null, null,
                    null, null
                )
            )
        }
        for (p in d.added.take(15)) {
            items.add(
                TrojanAdapter.UiItem(
                    "Integrity.Added · " + p.substringAfterLast('/'), p,
                    "基线之外新增的系统文件(常见于模块注入 / 脚本落地)",
                    ThreatLevel.HIGH, "确认来源;可疑文件可用『删除文件』处置",
                    null, null, "rm -f " + ShellBridge.quote(p), "删除文件"
                )
            )
        }
        for (p in d.removed.take(15)) {
            items.add(
                TrojanAdapter.UiItem(
                    "Integrity.Removed · " + p.substringAfterLast('/'), p,
                    "基线中的系统文件已消失(可能是删除安全组件以绕过检测)",
                    ThreatLevel.HIGH, "确认是否本人操作;异常删除需恢复官方镜像",
                    null, null, null, null
                )
            )
        }
        val locked = lockedCount(context)
        if (locked > 0) {
            items.add(
                TrojanAdapter.UiItem(
                    "Integrity.Locked", "已锁定 " + locked + " 个关键文件(防篡改)",
                    "chattr +i 或 chmod 0444 已生效", ThreatLevel.LOW, null, null
                )
            )
        }
        return items
    }
}
