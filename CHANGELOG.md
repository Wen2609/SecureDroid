# Changelog

本文件记录各版本的重要变化。格式参考 Keep a Changelog,版本号遵循语义化版本。

## [1.0.0] - 2026-10-01

首个可安装的发布版本(已签名 APK,minSdk 26 / targetSdk 34)。

### Added

- **最高权限防护层**:`PrivilegeManager` 统一提权入口 —— 权限三级分级(NONE / LOCAL_SHELL / ROOT)、12 项能力矩阵、灾害级命令安全策略强制拦截、批量命令合并为单次 su 会话、最近 500 条提权命令审计落盘;
- **系统完整性监控**:`SystemIntegrity` 采集关键系统路径基线(单次 su 会话),比对新增 / 删除 / 篡改三类差异,守护循环周期校验并告警;关键文件(hosts、su 等)支持 `chattr +i` 锁定(降级 `chmod 0444`)与解锁;
- **病毒查杀中心新增动作**:最高权限面板(能力矩阵 + 审计)、系统完整性监控、关键文件锁定 / 解锁;
- **WorkManager 定时查杀**:`DailyScanWorker` + `DailyScanRunner`,支持低电量不唤醒约束,前台服务与后台调度共用同一实现;
- **单元测试 38 项**(JVM):提权安全策略 15、木马家族分类 9、资源引用不变量 7、清单不变量 4、哈希编码 3;
- **工程化**:Gradle Wrapper(仅需 JDK 17)、GitHub Actions CI、发布签名配置(条件启用)、便捷构建脚本 `build.cmd`、AI 协作速查 `AGENT_NOTES.md`;
- **文档**:README 重构(功能表 / 构建 / 测试 / 签名流程)、本 CHANGELOG。

### Fixed

**由 Android Lint 门禁发现并修复(启用 `abortOnError = true` 后首轮):**

- `SosActivity`:调用 `Vibrator.vibrate()` **却未声明 VIBRATE 权限** —— 真机上一键 SOS 会抛 SecurityException 崩溃,恰好在最需要它的时候失效;
- `LockActivity`:覆盖已弃用的 `onBackPressed()` → 改用 `OnBackPressedDispatcher`(锁定期间继续拦截返回键,行为不变);
- `ScanTileService`:Android 14 起 `startActivityAndCollapse(Intent)` 弃用 → 按 API 分派 PendingIntent 版本,旧版本分支精确抑制对应 lint 检查项;
- `AndroidManifest`:`SEND_SMS` 隐含电话硬件要求,导致平板 / ChromeOS 无法安装 → 增加 `uses-feature telephony required=false`;
- `CleanerActivity`:`String.format` 隐式使用默认 Locale(土耳其语区会格式化异常)→ 显式 `Locale.US`。

**构建期缺陷:**

- 修复 **175 处编译错误**,项目恢复可构建:缺失布局与 drawable、`UiItem` 参数缺失、扫描结果类型不统一、错误 import、KDoc 中 `/*` 触发 Kotlin 嵌套注释未闭合等;
- **前台服务类型**:`dataSync` → `dataSync|specialUse` 并按 API 分派,规避 Android 15 从 BOOT_COMPLETED 启动受限类型导致崩溃;
- **后台启动限制**:定时查杀不再由闹钟直接启动前台服务(Android 12+ 会抛 `ForegroundServiceStartNotAllowedException`),改由 WorkManager 调度;开机 / 磁贴 / 小部件启动路径全部加异常保护;
- **ANR**:证书白名单对话框的应用枚举与证书解析移出主线程;
- **运行时缺陷**:`RootkitDetector` 变量先用后声明、`ShredTool` 在 `OutputStream` 上调用 `fd.sync()`、`SystemBaseline` 的 `KeyguardManager` 包名错误、`VaultActivity` 缺少 `toast(Int)` 重载、`SosActivity` 缺少 `RingtoneManager` import 与弃用 API 替换;
- `.gitignore` 重写为合法 UTF-8(此前为 ANSI,工具无法解析)。

### Changed

- **性能**:扫描线程池跨扫描复用(自适应 2-6 线程)、APK 哈希缓存内存 LRU + 批量落盘、ClamAV 字节特征改为单遍锚点索引匹配、`HashCache` 超限按 LRU 淘汰而非整表清空;
- **体积**:release 启用 R8 混淆 + 资源裁剪 + 仅中文资源,产物 1.9 MB;
- **结构**:新增 `SecurityNotifier`(统一告警与去重)、`DailyScanRunner`(服务与 Worker 共用)、`ScanReports`/`toUiItems` 拆分(数据层与 UI 层解耦)。

### Quality gates

- Android Lint 启用错误级门禁(`abortOnError = true`):权限缺失、API 误用、清单不一致等问题直接阻断构建;
- 38 项 JVM 单元测试 + 资源引用不变量 + 清单不变量,CI 全量执行;
- 保留为警告的项均为刻意设计:硬编码系统路径(`/data/local/tmp`、`/sdcard`,查杀必须用真实路径)、`SystemProperties` 反射(有 `getprop` 兜底)、动态拼接文案。

### Security

- 提权仅通过用户授权 su(Magisk / KernelSU / APatch / SukiSU)实现,**不包含任何漏洞利用或静默提权代码**;
- 提权执行层强制策略校验,拦截整根删除、格式化、写块设备、恢复出厂等不可逆命令,并全部留痕审计;
- 签名密钥与 `keystore.properties` 均不入库。
