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

## 核心架构速查
- TrojanScanner.Report = 全库统一的扫描结果(detections/isInfected/worstLevel),别用旧名 ScanResult
- ParallelScanner.scanReports(ctx,workers,onProgress,onEachResult)->Outcome(数据层) / toUiItems(Outcome)(UI) / scanAll(兼容)
- TrojanAdapter.UiItem 9 参:(title, sub, detail, level, suggestion, uninstallPkg, evidence, fixCommand, fixLabel),尾部参数调用途务必用具名传参
- ScanControl.reset()/cancelled = 取消查杀;HashCache.cachedSha256 = APK 指纹缓存;TrustStore = 白名单
- Room: AppDatabase.get(ctx).scanRecordDao()(insertAll/trim/threatCount) + autoActionDao
- 字符串资源在 res/values/strings.xml,新增 UI 引用前先查有没有

## 省钱须知(给 AI)
- 用 pwsh 工具时不支持 && 和 call;跑 gradle 用 Start-Process + 文件重定向,别用 Out-File(按行截断)
- 编译错误集中修完再编译,别一轮一修
- 读大文件用 offset/limit;build 日志读 build_err.log 里 ^e: 开头的行即可
