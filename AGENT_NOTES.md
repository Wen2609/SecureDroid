# SecureDroid Agent Notes(给 AI 会话的速查,先读这个再干活)

## 构建环境(直接用 build.cmd,不要自己拼环境变量)
- JDK17: C:\Android_build\jdk-17 / SDK: C:\Android_build\sdk / Gradle 8.7: C:\Android_build\gradle-8.7\bin\gradle.bat
- 禁止调用 C:\Android_build\env.bat(中文注释在 UTF-8 代码页下乱码会炸)
- 一键构建: 项目根目录 `build.cmd [task]`,只打印错误行和 BUILD 结果
- release 已验证可构建(R8 通过):proguard-rules.pro 已含 -dontwarn javax.annotation.*(Tink 缺失注解);产物 app-release-unsigned.apk 约 1.78MB(debug 7.36MB)
- defaultConfig 配了 resourceConfigurations(zh/zh-rCN),别删;ParallelScanner 线程数用 defaultWorkers()(2-6 自适应),别写死
- Kotlin 1.9.24 / AGP 8.5.2 / minSdk 26 / targetSdk 34 / viewBinding / Room+KSP

## 语言陷阱(本项目已踩过,别再踩)
- Kotlin 块注释支持嵌套:KDoc 里绝不能出现 `/*`(如 glob 路径 /data/data/*/cache 会把注释撑爆)
- Kotlin 1.9 禁止在 object/class 内写 typealias,只能顶层
- 父类泛型里引用本类嵌套类必须用限定名:ListAdapter<ToolsAdapter.ToolEntry,...>
- KeyguardManager 在 android.app 不是 android.os;FileOutputStream 才有 .fd

## 最高权限层(新增,改动前先读)
- PrivilegeManager: PrivLevel(NONE/LOCAL_SHELL/ROOT) 分级;probe(ctx) 主动提权(触发 su 授权框),level(ctx) 读缓存,ensureFresh 过期自动刷新
- 所有防护命令必须走 PrivilegeManager.exec(ctx,cmd) / execBatch(cmds) —— 内置 PrivilegedPolicy 拦截灾害级命令(整根删除/格式化/写分区/恢复出厂),并自动审计
- Capability 枚举 = 能力矩阵(12 项),has(ctx,cap) 判断某防护功能当前是否可用
- SystemIntegrity: captureBaseline/diff/scan(UI)/lockCriticalFiles/unlockCriticalFiles,守护循环用 quickGuardSummary
- 法规约束:不要实现任何漏洞利用或静默提权;提权只能经用户授权 su(符合 Magisk/KernelSU 规范)

## 核心架构速查
- TrojanScanner.Report = 全库统一的扫描结果(detections/isInfected/worstLevel),别用旧名 ScanResult
- ParallelScanner.scanReports(ctx,workers,onProgress,onEachResult)->Outcome(数据层) / toUiItems(Outcome)(UI) / scanAll(兼容)
- TrojanAdapter.UiItem 9 参:(title, sub, detail, level, suggestion, uninstallPkg, evidence, fixCommand, fixLabel),尾部参数调用途务必用具名传参
- ScanControl.reset()/cancelled = 取消查杀;HashCache.cachedSha256 = APK 指纹缓存;TrustStore = 白名单
- Room: AppDatabase.get(ctx).scanRecordDao()(insertAll/trim/threatCount) + autoActionDao
- 字符串资源在 res/values/strings.xml,新增 UI 引用前先查有没有

## 后台任务与前台服务约定(改动前必读)
- 定时查杀 = WorkManager:ScanScheduler 入队 DailyScanWorker(唯一名 securedroid_daily_scan)→ DailyScanRunner.run();禁止再引入「AlarmManager + startForegroundService」(Android 12+ 会抛 ForegroundServiceStartNotAllowedException 崩溃);AlarmReceiver 已删除,勿复活
- 前台服务类型:manifest 为 dataSync|specialUse,代码按 API 34 分派(ServiceCompat.startForeground);增删类型必须同步权限,否则启动崩溃
- 所有启动前台服务的入口(开机 / 磁贴 / 小部件)必须 try-catch;开机路径失败不得崩溃
- 告警通知统一走 SecurityNotifier.alert(ctx, text),不要另写去重逻辑

## 单元测试(改安全逻辑或清单后必须跑)
- 运行:build.cmd testDebugUnitTest(或 gradle testDebugUnitTest)
- 位置:app/src/test/java/com/armorlab/securedroid/
- 覆盖:PrivilegedPolicyTest(提权安全策略)/ FamilyClassifierTest / ScannerEngineTest / ManifestInvariantsTest
- ManifestInvariantsTest 校验:清单声明的 activity/service/receiver 类必须真实存在、FGS 类型与权限一致 —— 改 manifest 或删类后务必重跑

## 省钱须知(给 AI)
- 用 pwsh 工具时不支持 && 和 call;跑 gradle 用 Start-Process + 文件重定向,别用 Out-File(按行截断)
- 编译错误集中修完再编译,别一轮一修
- 读大文件用 offset/limit;build 日志读 build_err.log 里 ^e: 开头的行即可
