# SecureDroid Agent Notes(给 AI 会话的速查,先读这个再干活)

## 构建环境
- 优先用项目自带 Wrapper:gradlew.bat <task>(Windows)/ ./gradlew <task>;本机已把 Gradle 8.7 发行包播种到 C:\Android_build\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu,故 --offline 可用
- 便捷脚本 build.cmd [task](只回显错误行 + BUILD 结果)
- 签名:keystore 在 D:\DSH WORK\keystore\securedroid-release.jks,凭据在项目根 keystore.properties(已 gitignore);文件缺失时 release 自动未签名,CI 安全
- 发布产物:apks/SecureDroid-v1.2.0-release-signed.apk(v2+v3 已签名,可安装)
- **GRADLE_USER_HOME 已迁到 D:\Android_build\.gradle**:C 盘曾满到 0 字节,反复把 Gradle 守护进程杀掉
  (症状:"daemon disappeared unexpectedly" 或 "磁盘空间不足");报这类错先看 C 盘剩余空间,再清 %TEMP%

### 不要自己拼环境变量
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
- 覆盖(38 项):PrivilegedPolicyTest(15)/ FamilyClassifierTest(9)/ ResourceReferenceTest(7,资源引用完整性)/ ManifestInvariantsTest(4)/ ScannerEngineTest(3)
- ManifestInvariantsTest 校验:清单声明的 activity/service/receiver 类必须真实存在、FGS 类型与权限一致 —— 改 manifest 或删类后务必重跑
- Lint 已开启 abortOnError:新增 API 调用若缺少权限声明会直接构建失败(例:SOS 震动需要 VIBRATE);本地跑 lintDebug 校验
- 故意保留的 lint 警告(勿盲目修):SdCardPath(查杀必须用真实系统路径)、PrivateApi(SystemProperties 反射有 getprop 兜底)、HardcodedText/SetTextI18n(动态拼接文案)

## 运行时验证(Robolectric,改 UI/启动逻辑后必须跑)
- 命令:build.cmd testDebugUnitTest(全套含冒烟),或 gradle testDebugUnitTest --tests "com.armorlab.securedroid.smoke.*"
- 覆盖:Application 启动、21 个布局膨胀、13 个 Activity 拉起、Room 读写、权限层降级
- android-all 运行库:本机已预置在 robolectric-deps/(150MB,已 gitignore);缺失时自动走镜像下载
- 已由该套件抓出的两个致命缺陷:WorkManager 未初始化崩溃、BottomNavigationView 6 项超限崩溃 —— 改动导航或启动逻辑后务必重跑
- 导航约定:底部入口用可滚动 TabLayout(BottomNavigationView 上限 5 项);入口定义源仍是 res/menu/bottom_nav.xml

## 签名引擎与安全约定
- ClamAV 兼容以官方文档为准:https://docs.clamav.net/manual/Signatures/ExtendedSignatures.html
  .ndb = 名称:目标类型:偏移:HEX(冒号分隔);偏移支持 * / n / EOF-n / n,MaxShift;旧分号格式为历史兼容,勿再新增
- 改动签名解析后必须跑 ClamAvSignatureEngineTest(真实加载 assets 并校验三种偏移语义)
- 匹配索引:ClamAvSignatures.buildIndex 建 2 字节窗口直接表(65536 直接表 + 链表,扫描期零分配);
  改算法后必须跑 SignatureMatchIndexTest —— 它用旧版单字节锚点分桶与朴素扫描做交叉等价验证,防的是漏报/误报
  (曾按教科书实现 Aho-Corasick:实测比锚点分桶慢 3.6 倍,已放弃;除非特征库到万级否则别改回去)
- 完整性守卫:security/IntegrityGuard(签名 SHA-256 + TOFU);UNAVAILABLE 不得当作 TRUSTED
- 安全约定:涉及秘密的界面必须 FLAG_SECURE;可被覆盖点击的关键界面加 filterTouchesWhenObscured;
  网络请求必须 https;特征库/规则类更新必须校验 SHA-256(FeatureUpdater 已强制)

## 信息架构(改导航前必读)
- 顶层只有 3 个板块:状态 / 检测 / 防护(res/menu/bottom_nav.xml);二级功能用板内分段控件
- 新板块页面实现 ui/SectionHost,MainActivity.navigateTo(板块 id, 分段下标) 可跨板块直达
- 布局属性是 android:layoutAnimation(不是 layout_animation);ResourceReferenceTest 已加断言拦截

## 视觉设计约定(改 UI 前必读)
- 令牌在 res/values(浅色)+ res/values-night(深色):配色/圆角/间距只改这两处,别在布局里写字面值
- **陷阱**:限定符目录必须与 values 平级 —— 写成 res/values/night/ 会被 AAPT 静默忽略,
  构建/Lint/测试全绿但深色模式失效;NightThemeTokenTest 已盯住这一点
- 布局里 60 个 View ID 被 Kotlin 引用(见 ui/ 下 binding.xxx),重排布局时 ID 一个都不能改
- 玻璃材质只用于功能层(工具栏/底部入口条);内容卡片用 Widget.SecureDroid.Card 的 1dp 光边
- 设计稿:python design/render_mockup.py 重新渲染 design/mockup-sheet.png;令牌与脚本内的色值需同步

## 省钱须知(给 AI)
- 用 pwsh 工具时不支持 && 和 call;跑 gradle 用 Start-Process + 文件重定向,别用 Out-File(按行截断)
- 编译错误集中修完再编译,别一轮一修
- 读大文件用 offset/limit;build 日志读 build_err.log 里 ^e: 开头的行即可
