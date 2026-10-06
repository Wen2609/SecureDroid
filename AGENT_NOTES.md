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
- PrivilegeManager: PrivLevel(NONE/LOCAL_SHELL/ROOT) 分级;probe(ctx) 主动提权(触发 su 授权框),level(ctx) 读缓存
- 所有防护命令必须走 PrivilegeManager.exec(ctx,cmd) —— 内置 PrivilegedPolicy 拦截灾害级命令(整根删除/格式化/写分区/恢复出厂),并自动审计
- Capability 枚举 = 能力矩阵(12 项),capabilities(ctx) 返回当前层级可用集合
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
- Lint 已开启 abortOnError:新增 API 调用若缺少权限声明会直接构建失败(例:振动反馈需要 VIBRATE);本地跑 lintDebug 校验
- 故意保留的 lint 警告(勿盲目修):SdCardPath(查杀必须用真实系统路径)、PrivateApi(SystemProperties 反射有 getprop 兜底)、HardcodedText/SetTextI18n(动态拼接文案)

## 运行时验证(Robolectric,改 UI/启动逻辑后必须跑)
- 命令:build.cmd testDebugUnitTest(全套含冒烟),或 gradle testDebugUnitTest --tests "com.armorlab.securedroid.smoke.*"
- 覆盖:Application 启动、res/layout 下**全部布局**逐个膨胀(反射枚举,现仅 activity_main + widget_security)、清单内全部 Activity 拉起(仅 MainActivity + LockActivity)、Room 读写、权限层降级
- android-all 运行库:本机已预置在 robolectric-deps/(150MB,已 gitignore);缺失时自动走镜像下载
- 已由该套件抓出的两个致命缺陷:WorkManager 未初始化崩溃、旧 BottomNavigationView 6 项超限崩溃 —— 改动导航或启动逻辑后务必重跑
- 导航约定:无原生底部导航(旧 res/menu/bottom_nav.xml 已删),入口是 index.html 的悬浮胶囊 dock;ActivityLaunchTest 反射枚举清单,新增 Activity 自动纳入

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
- 顶层导航在 assets/ui/index.html 的悬浮胶囊 dock(主页/安全防护/应用防护/扩展功能),二级功能用板内分段控件;原生侧无 bottom_nav.xml(v1.9.9 起旧原生导航已删)
- 深层工具页(VirusCenter/DeepScan/NetworkAudit/Privacy/Vulnerability)是 index.html 内嵌的 **HTML 子页面**,经 BridgeRouter 的 openSubPage 事件切换;数据由 `web/handlers/ToolsHandler.kt` 桥接。新增工具页 = index.html 加子页面 DOM + app.js 渲染/路由 + BridgeRouter 注册 action + ToolsHandler 提供数据
- **运行模式(v1.9.16)**:首次启动必须经 `modeOverlay` 强制选择 **标准模式 / 无线调试模式 / 超级用户模式**(名称不可改)。`web/AppMode.kt` 定义三档,`AppModeStore` 持久化,`ModeHandler` 提供 getMode/setMode。**各模式只可用有权限功能**:Root 级工具仅超级用户,Shell 级工具需无线调试/超级用户,ToolsHandler 的 `toolAllowed()` 统一门控,`getVirusCenterMenu` 按模式过滤并缓存;Root 开关仅超级用户模式可见(SettingsHandler.toggleRoot 后端拒绝非超级用户)。
- **模式权限强制(v1.9.17)**:选择模式后必须授予该模式所需权限才能进入主界面 —— 标准=通知;无线调试=通知+使用情况访问(`PACKAGE_USAGE_STATS`);超级用户=通知+Root。`ModeHandler` 返回 `permissions` 数组,前端 `renderModePerms` 引导;`openPermissionSettings` 桥接跳转系统设置,root 键触发 su 授权并把结果经 `modePermissions` 事件回推。
- **分区布局**:隐私检测/漏洞扫描入口在应用防护→权限审计页(应用与系统检测区);扩展功能只放防护开关/网络审计/安全设置,页标题 `#modeBadge` 显示当前模式。
- **Pad 适配(v1.9.17)**:index.html CSS 有 `@media (min-width:600px/960px)` 把 `.app` 放宽到 720/880px、快捷网格改 4 列。改大屏布局时同步维护媒体查询,勿把手机单列布局写死。
- 布局属性是 android:layoutAnimation(不是 layout_animation);ResourceReferenceTest 已加断言拦截

## 界面约定(2026-10-03 起 · 基线是用户上传稿)
- **基线**:用户上传的 `deepseek_html_20261003_008012.html`(毛玻璃 / 柔和流动光晕 / 悬浮胶囊导航)。
  布局与令牌按它逐项实现;改界面前先看这份稿子,不要自行发明风格或配色
- 映射关系:背景光晕 = `index.html` 的 `.aurora` CSS(4 个 blur 图层,`page-hidden` 时冻结);`activity_main.xml` 仅剩 WebView + 启动遮罩 `splashOverlay`;
  卡片 = `Widget.SecureDroid.Card`(半透明 `c_glass` + 1dp `c_glass_border` 高光);导航 = 自定义 3 等分胶囊(已不是 TabLayout);
  分段 = `Widget.SecureDroid.SegmentTrack` + `Widget.SecureDroid.Segment`;徽标 = `Widget.SecureDroid.Badge`;
  图标统一 `ic_sd_*` 前缀(描边型,路径取自上传稿的内联 SVG)
- 毛玻璃是**近似实现**:Android 没有跨视图的 backdrop-filter,用"半透明底 + 高光描边 + 阴影"表达,
  不要去找真模糊(需要 RenderEffect 且拿不到背后内容,白折腾)
- 仍然生效、会被测试拦的硬规则:触摸目标 ≥48dp(上传稿的 42px 分段按钮在 Android 上取 48dp)、
  颜色与字号必须走 res/values 令牌不许写字面值、带点号样式名必须显式写 parent、深色令牌必须放在与 values 平级的 res/values-night
- 配色/尺寸的当前实际取值见 README「界面外观(现状)」;新增颜色令牌必须同时在 `values/colors.xml` 与 `values-night/colors.xml` 定义
- `陷阱`:限定符目录必须与 values 平级 —— 写成 res/values/night/ 会被 AAPT 静默忽略,
  构建/Lint/测试全绿但深色模式失效;NightThemeTokenTest 已盯住这一点
- **主题结构(v1.9.16)**:`Theme.SecureDroid` 已拆为 `Theme.SecureDroid.Base`(全部令牌)+ 薄包装 `Theme.SecureDroid`;`windowLightNavigationBar` 是 API 27+ 属性,必须放 `values-v27/themes.xml` / `values-night-v27/themes.xml`,放默认 values 会被 lint NewApi 拦(minSdk 26)。新增 API 级属性遵循同样做法。
- 布局里 View ID 经 `ActivityMainBinding` 引用(webView / swipeRefresh / splashOverlay / integrityBanner),重排 activity_main.xml 时这些 ID 不能改
- 装饰性图标必须 importantForAccessibility="no";列表分隔线已随原生列表删除(HTML 列表用 CSS 分隔线)

## v1.9.17 工程注意事项
- **内置字体(v1.9.18 子集化)**:Noto Sans SC 在 `assets/ui/fonts/`(Regular/Bold 均为 **WOFF2 子集**,合计 ~476KB)。子集字符来自全项目 HTML/JS/XML/Kotlin 文案提取(1160 字符)。**新增文案若含未收录字符会回退系统字体** —— 需要重新子集化时:cd /tmp/fontsubset(需先 `npm i subset-font`),用 `extract.js` 重新提取字符集、`subset.js` 重生成 woff2(注意 subset-font 签名是 `subsetFont(font, text, options)`)。
- **SDK 35**:compileSdk/targetSdk=35。SDK 35 起 `PackageInfo.applicationInfo` 标注可空,所有 `info.applicationInfo` 直接赋值处必须 `?: return/continue`(TrojanScanner 已踩)。导航栏已改透明(Android 15 强制 edge-to-edge),内容靠 `env(safe-area-inset-*)` 留白,勿改回不透明 `navigationBarColor`。
- **底部导航**:dock 有 `.dock-thumb` 选择丸(可拖动吸附),`showPanel` 会同步移动 thumb;`dock-item` 不再有自己的背景高亮,改样式时保持 thumb 是唯一选中指示器。按住动画在 `.dock-item:active`。
- **内置签名库**:`assets/signatures/` 下 `trojan_demo.*`(测试锚点,勿删)+ `android_family.ndb/.hsb`(家族演示库)+ `real_malware.hsb`(v1.9.19 真实银行木马哈希种子)。ClamAvSignatures 自动遍历加载,新增 `.ndb/.hsb` 即被统计;行格式必须严格 ClamAV 规范,否则 ClamAvSignatureEngineTest 会拦。
- **真实病毒库接入(v1.9.19)**:官方 ClamAV `daily.cvd` 用 `tools/cvd2clamav.js`(Node,`node tools/cvd2clamav.js daily.cvd <outdir>`)解包成 `clamav.ndb`/`clamav.hsb`;经应用内「特征库在线更新」URL+SHA-256 加载到 `files/clamav/` 生效。`FeatureUpdater` 强制 https + SHA-256 必填。
- **权限引导文案**:权限名/用途在 `strings.xml` 的 `perm_*` 字符串,`ModeHandler.permissionsArray` 按模式返回 `{key,label,desc,granted}`。改权限文案只动 strings.xml,不要硬编码在 Kotlin。

## 省钱须知(给 AI)
- 用 pwsh 工具时不支持 && 和 call;跑 gradle 用 Start-Process + 文件重定向,别用 Out-File(按行截断)
- 编译错误集中修完再编译,别一轮一修
- 读大文件用 offset/limit;build 日志读 build_err.log 里 ^e: 开头的行即可
## 性能不变量(v1.7.0 起 · PerfGuardTest 会拦)
- 热路径不许随手 new Regex / SimpleDateFormat:统一走 core/Re 与 core/TimeFmt(ThreadLocal 缓存 + Locale 变更重建)
- 所有 PackageManager 枚举与 loadLabel 一律经 core/PackageSnapshot(默认 30s TTL);安装/卸载广播后调 PackageSnapshot.invalidate()
- dex 行为规则用 MultiPatternMatcher 单遍扫描(BehaviorRules 走 matcherFor 缓存),不要再逐模式扫全量字符串集合
- 并行查杀线程池 core == maximum 且 allowCoreThreadTimeOut(true):无界队列下真实并发度 = corePoolSize,设 0 就退化成串行
- DexVerdictCache 的负缓存(~clean)也要落盘;写入攒批,任务收尾必须 flush(ParallelScanner 已调)
- 新增 RecyclerView 列表页记得 setHasFixedSize(true);首页/面板刷新走 1s 节流,别在每个生命周期回调里重复拉数据

## root 提权执行不变量(v1.7.1 起 · ShellHardeningTest 会拦)
- `ShellBridge.runSu()` 合并了 stderr,**返回值非 null 不代表成功**(空字符串也是非 null):只读查询才用它
- 一切会改动系统的命令走 `ShellBridge.runSuChecked()`(策略放行 → 进程启动 → exitCode == 0 → 输出无 permission denied / not found / read-only 等标记);
  需要看输出时用 `runSuResult()` 自己判 exitCode
- 任何拼进 shell 的路径/参数必须过 `ShellBridge.quote()`(`'` → `'\''`):破坏性动作 + 可控文件名 = root 命令注入
- 处置结果要复核:隔离后确认文件真的移动了、断网后回读 iptables 规则,别只信命令回显
- `PrivilegedPolicy.check()` 是唯一的灾害命令闸门,新增处置命令前先确认它不会被误判为 Allow/Deny (PrivilegedPolicyTest + ShellHardeningTest 会跑真实命令清单)
- 查杀历史必须落真实值:`TrojanScanner.Report` 已携带 `sha256` / `riskScore`,写入方(DailyScanRunner / ScanViewModel)
  不许再写 `sha256 = ""` / `riskScore = 0`;`PrivilegeManager.execBatch()` 已删除,别再复活(ScanHistoryTest 会拦)

## 误报防线不变量(v1.7.3 起 · FalsePositiveTest / ClamAvSignatureEngineTest 会拦)

- **行为规则必须"整词"匹配**:DEX 字符串级匹配一律走 `scan/TokenMatch.kt` 的 `occurs/occursIn`,
  不得再用裸 `contains` —— `exec` 命中 `execute`/`execSQL`、`xposed` 命中 `xposedcheck` 是实测的误报主因;
- **判感染需要"强特征"**:`BehaviorRules.Rule.strong` 非空时,必须命中其中至少一个模式才算该规则
  (纯 API 组合如 Socket+exec、Cipher+BTC 在正常应用里遍地都是,不足以定罪);
- **只有 MEDIUM 及以上算感染**:`TrojanScanner.Report.isInfected` 只认 `level.ordinal >= MEDIUM`,
  LOW 只是提示;改这条等于改"什么算病毒",必须同步改 UI 文案与 FalsePositiveTest;
- **不得拿通用文件魔数当特征**:`dex\n035`、`PK\x03\x04`、`ELF` 之类不得写进 `.ndb`/`.ndu`
  (会把所有同版本 dex 的正常应用整片判成木马);演示库只放 `APK-HEADER-MARKER` 这类自检唯一串;
- **不扫自己**:`TrojanScanner.scanPackage` 遇到本应用直接返回空报告,`ParallelScanner.scanTargets`
  同时排除本应用与信任列表(应用内置全部检测用字符串,必然命中自己);
- **判定缓存带语义版本**:改动行为规则或特征库后,必须自增 `DexVerdictCache.VERSION`
  (并同步 `KEY_VERSION` 的期望值),否则老设备继续沿用旧判定,修复无效。

