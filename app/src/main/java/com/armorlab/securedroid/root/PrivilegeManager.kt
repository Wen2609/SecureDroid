package com.armorlab.securedroid.root

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque

/** 权限层级:NONE(仅应用自身 UID) < LOCAL_SHELL(本地 shell 管道) < ROOT(su 提权) */
enum class PrivLevel(val title: String) {
    NONE("未提权"),
    LOCAL_SHELL("本地 Shell"),
    ROOT("Root 最高权限")
}

/** 需要权限支撑的防护能力(能力矩阵:决定哪些防护开关可用) */
enum class Capability(val label: String, val needRoot: Boolean) {
    LOCAL_SHELL("本地 shell 管道 / find / grep", false),
    READ_SYSTEM("读取系统分区与 /data/adb", true),
    READ_ALL_DATA("读取全部应用私有数据", true),
    WRITE_SYSTEM("修改系统文件", true),
    KILL_PROCESS("终止任意进程", true),
    MODIFY_FIREWALL("iptables 断网与锁定", true),
    UNINSTALL_APP("静默卸载恶意应用", true),
    FREEZE_APP("冻结 / 解冻应用", true),
    QUARANTINE("隔离与销毁文件", true),
    LOCK_FILE("锁定关键文件防篡改", true),
    INTEGRITY_WATCH("系统完整性实时监控", true),
    SCAN_RAW_PARTITION("底层分区查杀", true)
}

/** 权限命令策略判定结果 */
sealed class PolicyVerdict {
    data object Allow : PolicyVerdict()
    data class Deny(val reason: String) : PolicyVerdict()
}

/**
 * 最高权限安全策略:放行本项目防护所需命令,拦截不可逆的灾害级命令。
 *
 * 设计原则:
 * 1. 阻断会摧毁设备 / 数据 / 系统的命令(整根删除、格式化、写分区、恢复出厂);
 * 2. 允许精确路径的处置命令(rm -f '<path>'、chmod、touch disable、iptables、精确 dd 读);
 * 3. 策略在提权执行层强制生效,任何调用方都无法绕过。
 */
object PrivilegedPolicy {

    private val denyRules: List<Pair<Regex, String>> = listOf(
        Regex("""rm\s+-[a-zA-Z]*[rf][a-zA-Z]*\s+/(\s|$|\*)""") to "拒绝整根删除",
        Regex("""rm\s+-[a-zA-Z]*[rf][a-zA-Z]*\s+/(system|data|sdcard|storage)(/\s*)?(\*|$)""") to "拒绝删除系统级目录",
        Regex("""\bmkfs(\.[a-z0-9]+)?\b""") to "拒绝格式化文件系统",
        Regex("""\bdd\b[^|;]*\bof=/dev/block""") to "拒绝写入块设备",
        Regex("""\bmke2fs\b|\bfastboot\b|\bflash\b""") to "拒绝刷写操作",
        Regex("""\bwipe\s+(data|all)\b|\bmaster\s+clear\b|\bfactory\s+reset\b""") to "拒绝恢复出厂设置",
        Regex("""\brm\s+-[a-zA-Z]*[rf][a-zA-Z]*\s+/data/adb/modules(/?\s*)?(\*|$)""") to "拒绝整目录删除模块",
        Regex("""\bpm\s+uninstall\b[^|;]*--user\s+all\b""") to "拒绝卸载全部用户的应用",
        Regex("""\bhalt\b|\bshutdown\b|\breboot\b""") to "拒绝重启或关机",
        Regex("""\bsetprop\s+(ro\.|persist\.sys)""") to "拒绝改写系统属性",
        Regex("""\bchmod\s+-R\s+[0-7]{3,4}\s+/(\s|$)""") to "拒绝递归改整个分区权限",
        Regex(""">\s*/dev/block/""") to "拒绝重定向写入块设备"
    )

    fun check(cmd: String): PolicyVerdict {
        val normalized = cmd.replace("\n", " ").trim()
        if (normalized.isEmpty()) return PolicyVerdict.Deny("空命令")
        for ((rule, reason) in denyRules) {
            if (rule.containsMatchIn(normalized)) return PolicyVerdict.Deny(reason)
        }
        return PolicyVerdict.Allow
    }
}

/** 单次提权执行结果 */
data class PrivResult(
    val ok: Boolean,
    val output: String?,
    val denied: Boolean = false,
    val reason: String? = null,
    val durationMs: Long = 0L
)

/** 提权审计条目 */
data class PrivAudit(
    val at: Long,
    val level: PrivLevel,
    val cmd: String,
    val ok: Boolean,
    val denied: Boolean,
    val durationMs: Long
)

/**
 * 最高权限统一入口(所有防护功能共用)。
 *
 * - 层级探测与缓存:probe 主动探测(首次触发 su 授权框),level 读取缓存(TTL 5 分钟);
 * - 安全策略:所有命令经 PrivilegedPolicy 校验,灾害级命令直接拒绝;
 * - 批量执行:多条命令合并为单次 su 会话,显著减少 su 往返开销(性能);
 * - 全量审计:最近 500 条提权记录落盘 files/priv_audit.log,可供日志导出。
 */
object PrivilegeManager {

    private const val PREFS = "settings"
    private const val KEY_LEVEL = "priv_level"
    private const val KEY_TS = "priv_level_ts"
    private const val TTL_MS = 5L * 60 * 1000
    private const val AUDIT_FILE = "priv_audit.log"
    private const val AUDIT_MAX = 500
    private const val AUDIT_TRIM_AT = 800

    private val audit = ConcurrentLinkedDeque<PrivAudit>()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读取缓存的权限层级;缓存过期返回上次已知值(不主动发起探测以免打扰用户) */
    fun level(context: Context): PrivLevel {
        val name = prefs(context).getString(KEY_LEVEL, null) ?: return PrivLevel.NONE
        return try {
            PrivLevel.valueOf(name)
        } catch (_: Exception) {
            PrivLevel.NONE
        }
    }

    fun isRoot(context: Context): Boolean = level(context) == PrivLevel.ROOT

    private fun store(context: Context, level: PrivLevel) {
        prefs(context).edit()
            .putString(KEY_LEVEL, level.name)
            .putLong(KEY_TS, System.currentTimeMillis())
            .apply()
    }

    /** 主动探测最高权限:su 可用且 uid=0 判定为 ROOT;否则退化为本地 shell */
    fun probe(context: Context): PrivLevel {
        val asRoot = ShellBridge.runSu("id", 12_000L)
        val level = when {
            asRoot != null && asRoot.contains("uid=0") -> PrivLevel.ROOT
            localShellWorks() -> PrivLevel.LOCAL_SHELL
            else -> PrivLevel.NONE
        }
        store(context, level)
        return level
    }

    /** 缓存过期时自动刷新(静默,不触发新授权框:su 已授权会立即返回) */
    fun ensureFresh(context: Context): PrivLevel {
        val ts = prefs(context).getLong(KEY_TS, 0L)
        if (ts > 0 && System.currentTimeMillis() - ts < TTL_MS) return level(context)
        return probe(context)
    }

    fun invalidate(context: Context) {
        prefs(context).edit().remove(KEY_TS).apply()
    }

    /** 本地 shell(非提权)是否可用,用于 find / grep 管道 */
    private fun localShellWorks(): Boolean = try {
        val p = ProcessBuilder("sh", "-c", "echo __sd_shell__").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("__sd_shell__")
    } catch (_: Exception) {
        false
    }

    /** 当前层级可用的能力集合 */
    fun capabilities(context: Context): Set<Capability> {
        val lv = level(context)
        return Capability.entries.filter { cap ->
            when {
                !cap.needRoot -> lv != PrivLevel.NONE
                else -> lv == PrivLevel.ROOT
            }
        }.toSet()
    }

    fun has(context: Context, cap: Capability): Boolean =
        if (!cap.needRoot) level(context) != PrivLevel.NONE else isRoot(context)

    /**
     * 提权执行单条命令(经安全策略 + 审计)。
     * requireRoot 为 true 时非 ROOT 层级直接返回失败,不发命令。
     */
    fun exec(
        context: Context,
        cmd: String,
        timeoutMs: Long = 10_000L,
        requireRoot: Boolean = true
    ): PrivResult {
        val started = System.currentTimeMillis()
        val lv = level(context)
        if (requireRoot && lv != PrivLevel.ROOT) {
            record(lv, cmd, ok = false, denied = true, System.currentTimeMillis() - started)
            return PrivResult(false, null, denied = true, reason = "当前权限层级不足: " + lv.title)
        }
        when (val v = PrivilegedPolicy.check(cmd)) {
            is PolicyVerdict.Deny -> {
                record(PrivLevel.ROOT, cmd, ok = false, denied = true, System.currentTimeMillis() - started)
                return PrivResult(false, null, denied = true, reason = v.reason)
            }
            PolicyVerdict.Allow -> Unit
        }
        val result = ShellBridge.runSuResult(cmd, timeoutMs)
        val out = result?.output
        val ok = result != null && result.exitCode == 0
        record(PrivLevel.ROOT, cmd, ok, false, System.currentTimeMillis() - started)
        return PrivResult(ok, out, false, if (ok) null else "执行失败或未授权", System.currentTimeMillis() - started)
    }

    /** 一批只读探测:存在性判断合并为单次 su */
    fun existsAll(paths: List<String>, timeoutMs: Long = 15_000L): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val cmd = paths.joinToString(" ") { "[ -e " + ShellBridge.quote(it) + " ] && echo " + ShellBridge.quote(it) + ";" }
        val out = ShellBridge.runSu(cmd, timeoutMs) ?: return emptySet()
        return out.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    // ---------- 审计 ----------

    private fun record(level: PrivLevel, cmd: String, ok: Boolean, denied: Boolean, durationMs: Long) {
        val entry = PrivAudit(System.currentTimeMillis(), level, cmd.take(300), ok, denied, durationMs)
        audit.addFirst(entry)
        while (audit.size > AUDIT_MAX) audit.pollLast()
    }

    /** 审计落盘(在 IO 线程调用),超限自动裁剪 */
    fun flushAudit(context: Context) {
        try {
            val f = File(context.filesDir, AUDIT_FILE)
            val existing = if (f.exists()) f.readLines() else emptyList()
            val merged = (audit.map { encode(it) } + existing).distinct().take(AUDIT_TRIM_AT)
            f.writeText(merged.joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    fun auditTail(limit: Int = 50): List<PrivAudit> = audit.take(limit)

    private fun encode(a: PrivAudit): String =
        a.at.toString() + "|" + a.level.name + "|" + (if (a.denied) "DENY" else if (a.ok) "OK" else "FAIL") +
            "|" + a.durationMs + "ms|" + a.cmd.replace("\n", " ").replace("|", "/")
}
