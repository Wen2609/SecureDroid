package com.armorlab.securedroid.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提权安全策略测试。
 *
 * 这是全项目最关键的安全边界:策略一旦失效,防护应用自身就能执行
 * 摧毁设备的命令。用例锁定「必须拒绝」与「必须放行」两侧行为。
 */
class PrivilegedPolicyTest {

    private fun denied(cmd: String): Boolean = PrivilegedPolicy.check(cmd) is PolicyVerdict.Deny

    private fun allowed(cmd: String): Boolean = PrivilegedPolicy.check(cmd) is PolicyVerdict.Allow

    // ---------- 必须拒绝:灾害级 / 不可逆 ----------

    @Test
    fun rejectsRecursiveRootDeletion() {
        assertTrue(denied("rm -rf /"))
        assertTrue(denied("rm -rf /*"))
        assertTrue(denied("rm -fr /"))
        assertTrue(denied("rm -Rf /"))
    }

    @Test
    fun rejectsSystemWideDirectoryDeletion() {
        assertTrue(denied("rm -rf /system"))
        assertTrue(denied("rm -rf /data"))
        assertTrue(denied("rm -rf /sdcard"))
    }

    @Test
    fun rejectsFilesystemFormattingAndFlashing() {
        assertTrue(denied("mkfs.ext4 /dev/block/sda1"))
        assertTrue(denied("mkfs /dev/block/sda"))
        assertTrue(denied("fastboot flash boot boot.img"))
    }

    @Test
    fun rejectsBlockDeviceWrites() {
        assertTrue(denied("dd if=/dev/zero of=/dev/block/sda bs=1M"))
        assertTrue(denied("echo x > /dev/block/by-name/boot"))
    }

    @Test
    fun rejectsFactoryResetAndPowerCommands() {
        assertTrue(denied("wipe data"))
        assertTrue(denied("reboot"))
        assertTrue(denied("shutdown -h now"))
    }

    @Test
    fun rejectsUninstallForAllUsers() {
        assertTrue(denied("pm uninstall --user all com.example.app"))
    }

    @Test
    fun rejectsRecursiveRootChmod() {
        assertTrue(denied("chmod -R 777 /"))
    }

    @Test
    fun rejectsRootModuleDirectoryWipe() {
        assertTrue(denied("rm -rf /data/adb/modules"))
        assertTrue(denied("rm -rf /data/adb/modules/*"))
    }

    @Test
    fun rejectsBlankCommand() {
        assertTrue(denied(""))
        assertTrue(denied("   "))
    }

    // ---------- 必须放行:防护功能依赖的精确操作 ----------

    @Test
    fun allowsTargetedFileRemoval() {
        assertTrue(allowed("rm -f '/data/local/tmp/payload.sh'"))
        assertTrue(allowed("rm -f '/data/adb/modules/evil/service.sh'"))
    }

    @Test
    fun allowsModuleDisableAndFirewall() {
        assertTrue(allowed("touch '/data/adb/modules/evil/disable'"))
        assertTrue(allowed("iptables -A OUTPUT -m owner --uid-owner 10001 -j DROP"))
        assertTrue(allowed("iptables -D OUTPUT -m owner --uid-owner 10001 -j DROP"))
    }

    @Test
    fun allowsPrecisePermissionRepair() {
        assertTrue(allowed("chmod 644 '/system/etc/hosts'"))
        assertTrue(allowed("chmod 0444 '/system/etc/hosts'"))
    }

    @Test
    fun allowsSingleUserUninstallAndProcessKill() {
        assertTrue(allowed("pm uninstall --user 0 com.example.malware"))
        assertTrue(allowed("kill -9 12345"))
    }

    @Test
    fun allowsReadOnlyPartitionAndFileInspection() {
        // 只读 dd 是分区查杀的基础能力,不能被误伤
        assertTrue(allowed("dd if=/dev/block/by-name/boot bs=4096 count=1"))
        assertTrue(allowed("cat '/data/adb/modules/evil/service.sh'"))
        assertTrue(allowed("stat -c '%n|%s|%Y' '/system/bin/app_process'"))
        assertTrue(allowed("[ -e '/system/xbin/su' ] && echo '/system/xbin/su';"))
    }

    // ---------- 拒绝理由需可读,便于审计与排障 ----------

    @Test
    fun denyCarriesHumanReadableReason() {
        val verdict = PrivilegedPolicy.check("rm -rf /")
        assertTrue(verdict is PolicyVerdict.Deny)
        val reason = (verdict as PolicyVerdict.Deny).reason
        assertTrue("拒绝理由不应为空", reason.isNotBlank())
        assertEquals("拒绝整根删除", reason)
    }
}
