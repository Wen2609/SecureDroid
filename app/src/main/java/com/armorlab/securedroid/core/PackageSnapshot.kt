package com.armorlab.securedroid.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/**
 * PackageManager 查询快照层。
 *
 * 背景:全项目有十几处直接调用 getInstalledPackages / getInstalledApplications,
 * 并且最常见的写法是"先取列表、再对每个包单独 getPackageInfo + loadLabel",
 * 于是每个应用一次绑定器(IPC)调用,一次全盘扫描就要几十到几百次跨进程往返。
 * 本对象把列表、单包信息、UID 归属、应用标签统一缓存到进程内:
 * - 列表 / 单包信息 / UID 归属:TTL 30s,同 key 单飞;
 * - 应用标签:进程内记忆化(loadLabel 涉及 IPC + 资源解析);
 * - 包发生变化时由 RealtimeProtectionService 调用 invalidate(),避免读到陈旧数据。
 */
object PackageSnapshot {

    private const val TTL_MS = 30_000L
    private const val LABEL_MAX = 4096

    private class Box<T>(val value: T?)

    private val packages = TtlCache<Int, List<PackageInfo>>(TTL_MS, maxEntries = 4)
    private val applications = TtlCache<Int, List<ApplicationInfo>>(TTL_MS, maxEntries = 4)
    private val names = TtlCache<Int, List<String>>(TTL_MS, maxEntries = 4)
    private val infos = TtlCache<String, Box<PackageInfo>>(TTL_MS, maxEntries = 1024)
    private val uidPackages = TtlCache<Int, List<String>>(TTL_MS, maxEntries = 512)
    private val labels = ConcurrentHashMap<String, String>()

    /** 已安装包列表(flags 参与缓存 key,GET_PERMISSIONS 与 0 互不污染) */
    fun installedPackages(context: Context, flags: Int = 0): List<PackageInfo> {
        val app = context.applicationContext
        return packages.getOrLoad(flags) { app.packageManager.getInstalledPackages(flags) }
    }

    fun installedApplications(context: Context, flags: Int = 0): List<ApplicationInfo> {
        val app = context.applicationContext
        return applications.getOrLoad(flags) { app.packageManager.getInstalledApplications(flags) }
    }

    fun packageNames(context: Context, flags: Int = 0): List<String> =
        names.getOrLoad(flags) { installedPackages(context, flags).map { it.packageName } }

    /** 单包信息(失败/不存在返回 null,不缓存异常) */
    fun packageInfo(context: Context, pkg: String, flags: Int = 0): PackageInfo? {
        val app = context.applicationContext
        return infos.getOrLoad(pkg + "|" + flags) {
            Box(runCatching { app.packageManager.getPackageInfo(pkg, flags) }.getOrNull())
        }.value
    }

    /** UID → 包名(空列表代表查不到) */
    fun packagesForUid(context: Context, uid: Int): List<String> {
        if (uid < Process.FIRST_APPLICATION_UID) return emptyList()
        val app = context.applicationContext
        return uidPackages.getOrLoad(uid) {
            app.packageManager.getPackagesForUid(uid)?.toList() ?: emptyList()
        }
    }

    /** 应用标签记忆化:拿不到或为空时回退包名 */
    fun label(context: Context, info: ApplicationInfo?): String {
        val pkg = info?.packageName ?: return ""
        labels[pkg]?.let { return it }
        val text = try {
            info.loadLabel(context.packageManager)?.toString()
        } catch (_: Exception) {
            null
        }
        val value = if (text.isNullOrBlank()) pkg else text
        if (labels.size >= LABEL_MAX) labels.clear()
        labels[pkg] = value
        return value
    }

    fun labelFor(context: Context, pkg: String): String {
        labels[pkg]?.let { return it }
        val info = packageInfo(context, pkg, 0)?.applicationInfo ?: return pkg
        return label(context, info)
    }

    /** 包安装 / 卸载 / 更新后必须调用,否则 30s 内可能读到旧列表与旧标签 */
    fun invalidate() {
        packages.clear()
        applications.clear()
        names.clear()
        infos.clear()
        uidPackages.clear()
        labels.clear()
    }

    fun stats(): String {
        val ps = packages.stats()
        return "packages(hits=" + ps.hits + ",loads=" + ps.loads + ",size=" + ps.size + ") labels=" + labels.size
    }
}
