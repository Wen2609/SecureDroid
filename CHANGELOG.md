# Changelog

本文件记录各版本的重要变化。格式参考 Keep a Changelog,版本号遵循语义化版本。

## [1.9.17] - 2026-10-06

### Added

- **Pad / 大屏适配**:≥600dp 时快捷功能网格升级为 4 列,内容区放宽至 720px;≥960dp 进一步放宽至 880px,平板不再是一条窄竖条。
- **模式权限强制授予**:选择运行模式后,必须授予该模式所需权限才能进入主界面 —— 标准模式=通知权限;无线调试模式=通知+使用情况访问;超级用户模式=通知+Root 授权。新增 `openPermissionSettings` 桥接跳转系统设置,Roooot 授权结果实时回推,未授予前停留在权限引导页。
- **内置 Noto Sans SC 字体**:应用内嵌 Regular + Bold 字体文件,`index.html` 与 `lock.html` 均通过 `@font-face` 使用内置字体,界面不再依赖系统字体。
- **内置病毒库扩充**:新增 `android_family.ndb`(24 个家族标记)与 `android_family.hsb`(4 条哈希),病毒库统计规模与家族展示大幅提升。

### Changed

- **API 等级上调至 35(Android 15)**:compileSdk / targetSdk 34→35,适配安卓小白条 —— 导航栏改为透明,内容真正延伸至手势条后方,配合 API 27+ 导航栏图标明暗设置。
- **毛玻璃增强**:底部导航提高模糊/饱和度/亮度,半透明渐变玻璃底 + 多层高光描边,夜间自动适配。
- **底部导航交互升级**:按住有缩放回弹动画;新增可拖动「选择丸」,触摸可在四个 tab 间自由拖动,松手自动吸附最近 tab。

### Fixed

- `TrojanScanner` 适配 SDK 35:`PackageInfo.applicationInfo` 变可空后补空安全返回,避免扫描空指针。

### Test

- `ModeGuardTest` 扩展权限引导断言(`openPermissionSettings` / `modePerms` / `renderModePerms`),共 158 项 JVM 测试。

## [1.9.16] - 2026-10-06

### Added

- **初始化强制选择运行模式**:首次启动必须先选择模式 —— **标准模式 / 无线调试模式 / 超级用户模式**(产品定义名称,不可修改)。新增 `AppMode` 枚举 + `AppModeStore` 持久化 + `ModeHandler`,桥接注册 `getMode` / `setMode`;HTML 端全屏模式选择层(`modeOverlay`)在未选择前不进入主界面。
- **各模式只可用有权限功能**:病毒中心 30+ 工具按模式过滤 —— Root 级工具(`netkill / priv / integrity / lockfiles / unlockfiles / quarantine / residue`)仅超级用户模式开放;Shell 级工具(`newproc / learn`)需无线调试或超级用户模式;`Root 模式`开关仅超级用户模式可见,后端在非超级用户模式下直接拒绝调用。

### Changed

- **4 大分区重排**:隐私检测 / 漏洞扫描从扩展功能移入**应用防护 → 权限审计**页(「应用与系统检测」区);扩展功能分区聚焦防护开关 / 网络审计 / 安全设置,页标题带当前模式徽标。
- **系统栏适配**:导航栏图标明暗按日/夜显式设置(`android:windowLightNavigationBar`,API 27+ 属性放 `values-v27` / `values-night-v27`);主题重构为 `Theme.SecureDroid.Base` + 薄包装,状态栏透明与 safe-area 布局保持不变。

### Performance

- 病毒中心菜单按模式缓存,切换模式才重建(避免每次构建 30 项 JSON,高频打开子页面零重复分配)。

### Test

- 新增 `ModeGuardTest` 4 项:模式枚举三档名称、桥接注册 getMode/setMode、HTML 强制选择层、JS 选择流程 —— 共 158 项 JVM 测试。

## [1.9.15] - 2026-10-06

### Changed(UI 底层架构重构:异步桥 + 路由 + 微内核)

- **数据读取全面异步化(体感最大)**:`getDashboard / getLockState / getAudit /
  getToggles` 四个同步桥调用会阻塞 JS 线程(权限审计冷调用可达数百毫秒,
  期间页面完全卡死);现改为 **request→reply 异步应答** —— JS 发请求即返回,
  原生在应用级协程取数后按 id 回推统一信封,页面动画/点击全程不冻结。
- **NativeBridge 上帝对象拆分**:26 个 `@JavascriptInterface` 方法收敛为
  **唯一入口** `post(action, payload)` + `BridgeRouter` 路由,按职责分派到
  `DashboardHandler / ScanHandler / LockHandler / SettingsHandler` 四个
  可独立构造的 handler;统一应答信封 `{ok, data?, error?}`;
  线程约定明确 —— 调用线程只解析永不阻塞,数据/扫描挂 `BridgeScope`,
  对话框/跳转挂主线程。新增能力 = 路由注册一项,不再堆桥方法。
- **出站通道规范化**:扫描进度/结果等回推统一经 `WebEventSink`
  (`{kind:"event", type, data}` 信封,org.json 负责全部转义),
  消灭散落的 `evaluateJavascript("window.__sdEvent('$type', $json)")`
  字符串拼接注入;接口化设计,未来可平替 WebMessageCompat 实现。
- **app.js 微内核**:通道(`__sdChannel`)+ `api` 数据源适配器
  (bridge 实现与 demo 实现同接口,demo 从散布 8 处的 `if (demo)` 变成
  一个适配器)+ `store`(面板状态单一来源,渲染函数订阅数据,
  渲染与取数解耦);无参数请求按 action 合并在途调用。
- **扫描会话迁移到应用级协程**:8 个裸 `Thread{}`(持 Activity 引用,
  旋转即丢)迁入 `BridgeScope`(SupervisorJob + Default);
  会话状态提到进程级 `ScanSessions` —— Activity 重建后新页面经
  `getScanState` **重放进行中的扫描**(按钮禁用 + 进度条 + 取消按钮无缝续显)。

### Changed(全界面 HTML 化:消除全部安卓原生界面)

- **深层工具页全部改为 HTML 子页面**:深度查杀 / 病毒中心 / 网络审计 / 隐私检测 /
  漏洞扫描从独立原生 Activity 改为 index.html 内嵌子页面,由 `subStack`
  路由在单个 WebView 内管理多级页面;返回键优先回退子页面。
- **新增 `ToolsHandler` 桥接层**(约 700 行):统一承载全部深层工具逻辑 ——
  列表型工具(网络审计/隐私检测/漏洞扫描)、深度查杀进度与结果(事件推送)、
  病毒中心 30+ 工具菜单/运行/取消、信任列表、黑名单、处置策略等级、
  自动隔离、证书可信标记、更新检查与执行、关于、报告导出、修复命令执行。
- **所有对话框改为 HTML 模态框**:关于 / 检查更新 / PIN 设置 / 信任列表 /
  黑名单管理 / 证书标记 / 修复确认 / 策略选择,统一走 `uiAlert / uiConfirm /
  uiPrompt / uiListDialog` 模态框 API。
- **Toast 改为 HTML toast 条**,与设计语言一致(顶部滑入、自动消失)。
- **PIN 解锁页 HTML 化**:`LockActivity` 改为 WebView 加载 `assets/ui/lock.html`
  + `lock.js`(数字键盘、PIN 圆点、防抖动画),保留 FLAG_SECURE 防截屏、
  防暴力破解与假崩溃诱骗逻辑。
- **统一列表数据模型**:`TrojanAdapter` 收敛为纯数据 `UiItem`(不再含
  RecyclerView/ViewHolder),由桥接层序列化为 JSON 供 HTML 列表渲染。

### Removed

- **5 个原生工具 Activity**(DeepScanActivity / VirusCenterActivity /
  NetworkAuditActivity / PrivacyActivity / VulnerabilityActivity)、
  `BaseListToolActivity`、`VirusActionAdapter`、`ui/glass/` 玻璃背景自定义 View。
- **6 个原生布局**(activity_deep_scan / activity_virus_center /
  activity_result_list / activity_lock / item_trojan / item_tool / dialog_set_pin)。
- **旧同步桥方法与 `runBlocking` 桥内阻塞调用**(异步化后不再需要);
  `ScanHistoryTest` / `PerfGuardTest` 的源码断言同步指向新 handler 文件。

### Test

- WebUiGuardTest 新增 3 项架构守卫:桥入口唯一性(post 路由信封)、
  JS 数据流经 api 通道(禁止再出现同步 `bridge.getXxx`)、
  扫描状态重放接线 —— 共 154 项 JVM 测试。
- 随 HTML 化同步更新守卫测试:PerfGuardTest 改为 `allListPagesAreBridged`
  (断言 BridgeRouter 注册全部列表型工具 action)、ScanHistoryTest /
  ShellHardeningTest 的源码断言指向 `web/handlers/ToolsHandler.kt`。

## [1.9.14] - 2026-10-06

### Added

- **主状态卡威胁感知三态**:首页主卡此前无论实际状态恒显绿色"设备安全";
  现按威胁数据切换 —— 有历史威胁=红色"发现 N 项威胁",有高风险权限应用或
  病毒库待更新=琥珀"注意",否则绿色"设备安全"。图标底色/标题颜色随态联动
  (暗色令牌自动适配)。
- **统计条可点直达**:概览条三格从纯展示升级为可点按钮 —— 病毒库→木马查杀、
  已扫描→病毒查杀中心(原生页)、防护中→扩展功能开关区,触感 + 刷新联动。
- **应用锁搜索过滤**:锁定列表(可达数百行)上方新增搜索框,按应用名/包名
  即时过滤;过滤基于缓存数据不重新拉桥,面板刷新后关键字保持。
- **下拉刷新**:主布局以 `SwipeRefreshLayout` 包裹 WebView,下拉触发当前面板
  数据重新拉取(`__sdReady`);依赖 `androidx.swiperefreshlayout:1.1.0`,
  指示器配色走 `c_primary` 令牌;与页面内部滚动不冲突
  (依据 WebView `canScrollVertically` 判定)。
- **空态图标化**:扫描结果 / 木马结果 / 权限审计 / 应用锁列表的动态空态从
  纯文本升级为"圆形色调图标 + 文案"(盾形勾图标,色随语境)。

### Test

- WebUiGuardTest 新增 4 项守卫:hero 三态接线、应用锁搜索、统计格可点、
  下拉刷新接线;`resultListsMustBatchAppend` 增加 emptyHint 断言 ——
  共 151 项 JVM 测试。

### Removed(死代码清理)

- **不可达 Activity 链**:`FullAuditActivity`(一键全面体检)与
  `CleanerActivity`(应用缓存清理)自 v1.9.0 WebView 化后失去全部入口 ——
  WebUI 工具箱不再提供入口,仅存的 `NativeBridge.openFullAudit()/openCleaner()`
  桥方法也无任何调用方。连同其唯一依赖 `feature/CleanerTool.kt`、
  清单声明与 4 条标题字符串一并删除;`SystemBaseline` 仍被
  漏洞扫描页使用,保留。
- **8 个无调用方函数**:`TimeFmt.clockSecond`、`RootGuard.disableModule`
  (自动处置已用内联命令实现)、`PrivilegeManager.ensureFresh / existsAll /
  has(ctx,cap)`(设计期 API,生产从未接入;能力判断走 `capabilities()`)、
  `NetKill.isBlocked`(复核逻辑内联在 `unblock`)、`MainActivity.navigateTo`
  (旧原生导航兼容入口,宫格入口已删)、`AppDatabase.observeAll`
  (未接线的 Flow 查询)。
- **280 项未用资源**(Lint UnusedResources,收敛至 0):旧原生 UI 时代的
  44 个 drawable、6 个 color selector、122 条字符串(tab_*/sw_*/tile_* 等)、
  34+34(昼/夜)颜色令牌、50 个尺寸令牌、28 个样式 —— 多为 v1.9.9 删除
  旧 Fragment 界面后残留的设计系统资产;`values-night` 与 `values` 保持同名同步删除。
- 同步修正 `AGENT_NOTES` 中已失效的 API 速查(ensureFresh / has / execBatch /
  旧导航架构)。

## [1.9.13] - 2026-10-06

### Added

- **扫描可取消**:检测页病毒扫描 / 木马查杀进行中显示「取消扫描 / 取消查杀」按钮,
  经新增的 `AndroidBridge.cancelScan()` 走 `ScanControl` 通知引擎停止
  (木马查杀循环改为逐包检查取消标志;病毒扫描复用 `ParallelScanner` 既有取消语义),
  取消后的完成摘要标注「已取消，仅含已扫描部分」。取消按钮点击后进入
  「正在取消…」不可重入态,完成/失败后自动复位隐藏。
- **病毒扫描结果按威胁折叠**:结果列表默认只渲染感染/可疑项,
  干净应用收进「展开其余 N 个安全应用」展开器(点击一次性插入)。
  此前全盘扫描会把两三百行"安全"全部铺进 DOM,威胁项被淹没且渲染开销大;
  全部干净时直接显示摘要,不再铺任何行。
- **开关整行可点**:防护开关本体只有 46×28px,达不到 48px 触摸目标;
  现在点击开关所在整行任意位置都会切换(开关自身 `stopPropagation` 防双触发)。
- **进度百分比与读屏语义**:扫描提示带实时百分比
  (「全盘扫描中 120 / 240（50%）」);进度条补 `role="progressbar"` +
  `aria-valuenow`,扫描提示加 `aria-live="polite"` 供读屏器播报。

### Fixed

- **分段按钮触摸目标 42px → 48px**:项目硬规则触摸目标 ≥48dp,
  上传稿的 42px 分段按钮在 WebUI 侧一直未达标,现提到 48px。

### Test

- 新增守卫测试 5 项(WebUiGuardTest 4 + PerfGuardTest 1):
  取消按钮接线、分段 48px 触摸目标、病毒结果折叠、开关整行可点、
  桥层 `cancelScan` 与木马循环取消检查 —— 共 147 项 JVM 测试。
- **修复既有失败的环境依赖断言**:`privilegeLayerDegradesSafelyWithoutRoot`
  硬性期望探测结果为 `NONE`,但在装有 Git Bash / WSL 的宿主上本地 sh 管道
  真实可用,`probe()` 如实返回 `LOCAL_SHELL`(代码行为正确)。现改为断言
  环境无关的不变量:无 su 环境不得判为 ROOT、probe 结果落库、
  非 ROOT 层级提权执行必须拒绝。

## [1.9.12] - 2026-10-05

### Performance

- **应用锁存储单例缓存(AppLockStore)**:`prefs()` 每次调用都重新创建
  `EncryptedSharedPreferences`(KeyStore 密钥派生,单次数十毫秒),而 `isLocked()`
  处在两条高频热路径上 —— 无障碍服务每个窗口事件、WebUI 应用锁列表每一行 ——
  应用切换与列表加载付出成百上千次密钥派生的代价。现改为双检锁单例,
  并对创建失败加 5 秒冷却(故障环境下不再高频重试)。
- **首页概览改聚合查询**:`getDashboard` 以前把最多 2000 行扫描记录全量拉到内存,
  只为取 `size` 与最大时间戳;现改用 Room 新增的 `countAll()` /
  `lastScannedAt()` 聚合查询,IO 与内存开销各降一个数量级。
- **木马查杀进度事件节流**:`NativeBridge` 逐包推送 `trojanProgress`,
  UI 线程因此执行上百次 `evaluateJavascript`;现 300ms 一帧、末包必推,
  进度条 CSS transition 本身已提供视觉平滑,UI 线程负载显著下降。
- **WebUI 刷新收敛**:`__sdReady` / `__sdGoto` / 导航点击 / 分段切换
  常在同一次交互里多路触发 `refreshVisible`,导致 `getDashboard` 等同步桥调用
  连续重复执行。现统一走 `scheduleRefresh` 尾沿节流 + `refreshVisible` 时间窗
  去重,启动时 `init` 与 `onPageFinished` 的双加载也因此消除。
- **应用锁列表一次读锁 + 纯数据排序**:`getLockState` 以前对每个应用各读一次
  加密存储、再逐个反查 `JSONObject` 字符串排序;现先把锁定集合一次读出,
  内存判锁,在 `Triple(name, pkg, locked)` 纯数据上做排序。
- **列表渲染 DocumentFragment 批量插入**:病毒扫描 / 木马 / 权限审计 / 应用锁
  四个列表都改用 `createDocumentFragment` 一次性 append;数百行逐行插入时
  每行一次 layout 重排的开销归零。
- **应用图标 IntersectionObserver 懒加载**:视口外的列表行不再立即请求图标,
  首屏同时发起的原生位图解码/压缩请求从几百个降到 ~10 个;
  图标尺寸从 96px 升到 128px(覆盖 3x 屏 40dp 清晰度需求),
  格式从 PNG 改为 WEBP 无损(纯色图标体积约减半、解码更快)。
- **检查更新对话框磁盘 IO 移后台**:`ClamAvSignatures.ensureLoaded` 以前
  在主线程 `checkUpdate()` 弹窗构建里执行,签名库首次加载需读盘,
  低端机上可达数十毫秒级 ANR 风险;现先在后台线程加载完成再上主线程建弹窗。

### Added

- **页面不可见自动暂停装饰动画**:`index.html` 新增 `.page-hidden` CSS 规则
  (暂停光晕漂移 / 扫描脉冲 / 骨架扫光 / 底部胶囊入场等持续动画);
  `MainActivity.onPause/onResume` 与 `document.visibilitychange` 双路切换。
  转后台时 WebView 不可见,继续合成 4 个 `blur(64px)` 图层纯属耗电。
- **键盘可达性**:`role="button"` 的活动行支持 Enter / Space 触发。
- **WebUiGuardTest**(4 项 + PerfGuardTest 4 项 + DatabaseAndManagersTest 2 项):
  守住上述性能优化不被后续改动悄悄回退 —— AppLockStore 单例、聚合查询、
  进度节流、图标懒加载、DocumentFragment 批量插入、刷新节流、page-hidden 暂停、
  应用锁列表一次读锁,全部用源码级静态断言钉住。
  测试套件总数从 132 增至 142。

## [1.9.11] - 2026-10-05

### Fixed

- **Web 端交互缺陷**:PIN 码条目改为可点击按钮,点击条目或开关均可打开 PIN 设置弹窗
  (原开关仅为静态展示,点击无响应)。
- **权限审计摘要结构**:顶部摘要卡不再借用通用列表行,改为带警示图标的专用
  `summary-box` 组件,视觉与信息层级更清晰。
- **木马查杀工具入口图标**:Rootkit / 恶意模块 / 锁机软件 / 深度查杀 / 病毒查杀中心
  五个入口补齐彩色圆形图标,与其他列表行视觉一致。
- **内联样式清理**:扫描按钮边距、防护条目按钮布局、强调色标题等 7 处内联样式
  全部收敛为 CSS 类(`.scan-card .btn-primary` / `button.protection-item` /
  `.row-accent` 等),符合设计令牌体系。

### Changed

- **原生页面沉浸式(edge-to-edge)**:病毒查杀中心、深度查杀、列表型工具页(基类)、
  应用锁解锁页全部启用 `WindowCompat.setDecorFitsSystemWindows(false)`,
  内容延伸至状态栏/导航栏后方,与主页 WebView 沉浸式体验统一;系统栏 insets
  通过 `setOnApplyWindowInsetsListener` 映射为页面安全区 padding。
- **尺寸全面令牌化**:新增 `sd_widget_height` / `sd_lock_icon_wrap` /
  `sd_keypad_height` / `sd_chevron_size` 等 7 个尺寸令牌,替换锁屏、PIN 弹窗、
  结果列表、工具行、Splash 等布局中的 20 余处字面 dp 值,间距统一 4dp 网格。

## [1.9.10] - 2026-10-05

### Added

- **启动品牌过渡(Splash)**:冷启动时显示盾牌 Logo + 应用名 + 标语的品牌遮罩,
  WebView 首帧就绪后以 280ms 渐隐动画淡出,彻底消除白屏闪烁,
  与窗口背景渐变无缝衔接(新增 `splashOverlay` / `ic_launcher_foreground` /
  `splash_subtitle` 字符串)。
- **首屏骨架加载态**:首页数据加载前,主状态卡副标题、概览条数值、最近活动
  标题/时间显示 shimmer 扫光骨架占位,数据就绪后平滑过渡为真实内容,
  避免 demo 假数据跳变(新增 `.is-loading` / `.skeleton-text` / `@keyframes shimmer`)。
- **双击退出提示**:在首页板块按返回键时弹出「再按一次退出应用」Toast,
  2 秒内再按才退出,符合安卓应用基础体验约定。

### Changed

- 主界面根布局从 `LinearLayout` 改为 `FrameLayout`,支持启动遮罩叠加;
  WebView 明确设置 `LAYER_TYPE_HARDWARE` 硬件加速渲染,配合页面
  `will-change` 提升动画流畅度。

## [1.9.9] - 2026-10-04

### Removed

- **删除全部原生旧版 Fragment UI 死代码**(WebView 化之前的遗留界面):
  - 删除 16 个 Kotlin 文件:`DashboardFragment` / `DetectFragment` /
    `ScannerFragment` / `TrojanFragment` / `ProtectFragment` / `AppLockFragment` /
    `PermissionAuditFragment` / `ToolsFragment` / `ToolsAdapter` /
    `PermissionAuditAdapter` / `ScanViewModel` / `TrojanViewModel` /
    `InsetDividerDecoration` / `SectionHost` / `ScanAdapter` / `AppLockAdapter`;
  - 删除 11 个遗留布局:`fragment_dashboard` / `fragment_detect` /
    `fragment_scanner` / `fragment_trojan` / `fragment_protect` /
    `fragment_app_lock` / `fragment_permission_audit` / `fragment_tools` /
    `item_scan_result` / `item_lock_app` / `item_audit`。
  - 这些界面自 v1.9.0 起已完全被 WebView 主页取代,无任何 Activity 引用,
    纯属死代码;删除后 APK 更小、代码更干净。

### Fixed

- **修复文字溢出问题(全界面)**:
  - **Web 端**(`assets/ui/index.html`):结果行标题 `.row-title` 改为两行截断
    (`-webkit-line-clamp:2` + `overflow-wrap:anywhere` + `word-break:break-word`);
    新增通用防溢出规则:卡片标题 / 特性标题 / 弹窗标题等长文本自动断词,
    超长一律两行截断,杜绝英文长串撑破卡片;
  - **原生端**:结果列表 / 深度扫描 / 病毒中心 / 应用锁 / 完整性警示条 /
    桌面小部件等全部布局的标题、副标题、状态文本增加
    `maxLines` + `ellipsize=end` 截断,长文本不再换行撑破布局。

## [1.9.8] - 2026-10-04

### Fixed

- **界面统一修复:所有界面对齐主页设计令牌**:
  - **应用锁解锁页全面玻璃化**:旧扁平布局重绘为居中玻璃卡 —— 绿色图标瓦片 +
    标题副标题 + PIN 圆点 + 4 行数字键盘;键盘按键从旧描边按钮升级为
    `Widget.SecureDroid.Button.Keypad` 玻璃键(16dp 圆角、半透明玻璃底、
    细高光描边、24sp 数字、64dp 触控高度),末行「0 居中 + 删除键」三列排版;
  - **系统完整性警示条玻璃化**:从纯色扁平红条改为 12dp 圆角半透明红玻璃卡 +
    细描边 + 警示图标 + 页边距,与主页卡片语言一致(新增 `c_destructive_glass` /
    `c_destructive_border` 昼夜令牌);
  - **桌面小部件玻璃化**:背景从纯色卡改为 20dp 圆角 + 页面渐变 + 高光描边的
    玻璃卡(`bg_widget_card`),与主页卡片一致。

### Changed

- 新增玻璃键盘键样式 `Widget.SecureDroid.Button.Keypad`
  (Material3 OutlinedButton 派生,cornerRadius 16dp、玻璃底 + 描边、零 inset)。

## [1.9.7] - 2026-10-04

### Changed

- **全部原生弹窗统一为主页玻璃风格**(对齐 2026-10-04 设计令牌):
  - 新增 `Theme.SecureDroid.Dialog.Alert` 玻璃弹窗主题:24dp 大圆角半透明玻璃面、
    1dp 高光边框、32% 背景遮罩、品牌绿强调色与文字按钮;
  - 设置 PIN、检查更新、关于、查杀修复确认、病毒查杀中心(更新 / 信任列表 /
    证书标记 / 拦截名单 / 处置策略)等全部弹窗接入玻璃主题;
  - 设置 PIN 弹窗重排为完整玻璃卡片:品牌绿图标瓦片 + 标题副标题 + 双输入框 +
    「取消(描边胶囊)/ 保存(品牌绿胶囊)」按钮行,48dp 触控目标;
  - 检查更新弹窗去除硬编码字号,统一走设计令牌字阶;
  - 应用锁的无障碍「假崩溃」诱骗弹窗**保留系统原生样式**,维持仿真效果不暴露。

### Added

- **主页 HTML 令牌对齐**:`index.html` 的 `:root` 新增 `--color-*` 语义主令牌
  (primary / primary-hover / accent / surface / border-glass / text 系列等),
  历史 `--c-*` 变量改为别名引用;新增 `--blur-overlay`(40px 玻璃模糊)、
  `--z-overlay` / `--z-dialog` 层级令牌与 `.dialog-overlay` / `.dialog-card`
  玻璃弹窗组件 CSS,供页面侧弹窗复用。

## [1.9.6] - 2026-10-04

### Added

- **原生工具页全面玻璃拟态化**:全部深层工具页(一键全面体检 / 网络审计 / 垃圾清理 / 隐私检测 /
  漏洞扫描 / 深度查杀 / 病毒查杀中心)从简陋原生控件重绘为主页设计语言:
  - 每页新增顶部返回栏:圆形渐变品牌瓦片返回按钮 + 大标题 + 功能副标题;
  - 列表型工具页新增状态卡:品牌绿扫描图标 + 加载状态文字 + 流体进度条,
    分析完成后显示「共 N 项结果」;
  - 查杀结果列表项新增左侧风险色条(危险红 / 风险金 / 安全绿),风险等级一眼可辨;
  - 深度查杀页重排为「操作进度卡(图标 + 阶段文案 + 进度条)+ 全宽查杀按钮 + 结果列表卡」;
  - 病毒查杀中心重排为「菜单态整卡列表」与「运行态控制按钮 + 进度 + 结果列表」双布局,
    按钮成对排版,视觉层级与主页一致;
  - 所有页面沿用主页的半透明毛玻璃卡片、24dp 大圆角、渐变页面背景与夜间主题。

## [1.9.5] - 2026-10-04

### Added

- **真实应用图标**:病毒扫描结果、木马检测结果、权限审计列表、应用锁列表中的应用行
  现在显示应用的真实图标(经 WebView 资源拦截 `appicon.local` 由原生按包名提取),
  加载失败时优雅回退为「首字字母 + 品牌渐变」瓦片,列表辨识度大幅提升;
- **扫描动画**:病毒 / 木马扫描进行中,扫描圆环进入脉冲呼吸 + 图标旋转动画,
  扫描完成自动停止,进度状态一目了然;
- **触感反馈**:底部导航、分段切换、扫描开始、原生开关切换、首页立即扫描等关键交互
  触发系统级轻触感(Android 10+ 预定义 Click 振动,旧版本回退短振动);
- **结果计数**:病毒扫描结果与木马检测结果卡片标题实时显示命中项数
  (如「扫描结果 · 3 项」),空结果时保持原标题。

## [1.9.4] - 2026-10-04

### Changed

- **字体体系统一优化**:
  - 字号收敛为整级阶梯(25 / 22 / 16 / 15 / 14 / 13 / 12 px),清除 13.5 / 12.5 / 11.5 px
    半像素字号,中文小字号不再发虚;
  - 全站字距统一为 `0.01em`(区块眉题 0.02em,数字统计 -0.01em),去除针对拉丁字体的
    负字距,中文方块字间距均匀;
  - 正文基准字号 14px / 行高 1.5,多行文本(结果行标题、防护条目标题/副标题、活动时间、
    问候语、功能网格副标题)补齐 1.4–1.45 行高,中文长文本不再拥挤;
  - 字体栈追加 Android 品牌中文原生字体(HarmonyOS Sans SC / MiSans / OPPO Sans /
    Noto Sans SC),华为 / 小米 / OPPO / 一加等设备使用各自系统字体,渲染更清晰统一。

## [1.9.3] - 2026-10-04

### Fixed

- **安全区兼容兜底**:顶部问候、板块标题、内容底部留白、底部胶囊导航不再单独依赖
  `env(safe-area-inset-*)`,均补回固定像素兜底声明(旧 WebView 不支持 `env()` 时页面元素
  不再顶到状态栏 / 手势条之下)。
- **分段切换回顶**:安全防护(病毒扫描 ↔ 木马查杀)与应用防护(应用锁 ↔ 权限审计)分段切换时
  滚动位置不再残留,自动回到顶部,避免切到长列表时停在半页。
- **无障碍状态不撒谎**:应用防护·应用锁里的「开启无障碍权限」副标题改为真实状态
  (已开启 / 未开启),不再固定显示误导文案。

### Changed

- **扫描按钮防连点**:病毒扫描 / 木马查杀开始后按钮置灰(`opacity .55` + 禁用),对应扫描完成
  事件到达后恢复,避免重复触发;原生侧本就拒绝并发,前端再挡一层。
- **分段控件吸顶**:含子页签的板块滚动时,页签胶囊吸顶(贴住状态栏下方),长列表(木马查杀结果 /
  权限审计列表)滚动时随时可切换子页。
- **原生感打磨**:WebView 隐藏系统滚动条与边缘光晕(滚动交给页面自绘)、正文禁止文本选中与
  双击缩放,交互手感更接近原生应用。

## [1.9.2] - 2026-10-03

### Added

- **把上传稿 UI 的剩余占位全部补完**:
  - 顶部头像「A」→ 打开「关于 SecureDroid」;
  - 主页「最近活动」三行变为可点直达(病毒扫描完成 → 安全防护·病毒扫描;隐私权限审计 → 应用防护·权限审计;应用锁 → 应用防护·应用锁),内容为真实数据(上次扫描时间 / 威胁数 / 高风险应用数 / 锁定数);
  - 扫描结果与权限审计结果行可点击 → 打开对应应用的应用详情页(可改权限 / 卸载),由桥接 `openAppSettings(pkg)` 实现;
  - 以上条目在纯 HTML 预览(无原生桥)时仍为演示数据,行为安全空转。

## [1.9.1] - 2026-10-03

### Changed

- **底部导航改为四大板块**(上传稿 HTML 直接渲染,`assets/ui/index.html`):
  - **主页**:问候 + 主状态卡(设备安全 / 上次扫描 / 立即扫描)+ 概览条(病毒库 · 已扫描 · 防护中)+
    常用功能四宫格(病毒扫描 / 木马查杀 / 网络检测 / 应用锁)+ 最近活动;
  - **安全防护**:病毒扫描(全盘)+ 木马查杀(Rootkit / 恶意模块 / 锁机 / 深度查杀 / 病毒查杀中心);
  - **应用防护**:应用锁(PIN / 锁定开关 / 假崩溃 / 无障碍)+ 权限审计;
  - **扩展功能**:防护开关(实时防护 / 开机自启 / Root 模式)+ 安全设置(检查更新 / 关于)+
    工具(网络审计 / 隐私检测 / 漏洞扫描);
  - 首页四宫格与「立即扫描」的直达目标同步更新(安全防护·病毒扫描 / 安全防护·木马查杀 /
    扩展功能 / 应用防护·应用锁);
- **系统栏沉浸优化**:主界面开启 edge-to-edge(状态栏透明,页面的毛玻璃 / 光晕背景延伸到状态栏与
  系统导航栏之后);顶部问候与各板块标题用 `env(safe-area-inset-top)` 让出状态栏,
  底部胶囊导航用 `env(safe-area-inset-bottom)` 让出手势条;深色模式状态栏图标随主题切换。

## [1.9.0] - 2026-10-03

### Changed

- **主界面改为上传稿 HTML 直接渲染**(WebView UI):`MainActivity` 由原生 Fragment 容器改为加载
  `assets/ui/index.html`(即 `deepseek_html_20261003_008012.html` 的副本)的 WebView,
  新增 `web/NativeBridge`(`window.AndroidBridge`)JS 桥,把页面上的**每一个按钮 / 开关 / 列表**
  接到真实原生功能(全部为真实实现,不造空壳入口):
  - 首页:上次扫描时间 / 病毒库状态 / 已扫描数 / 防护中项数 / 最近活动(真实数据,`getDashboard`);
  - 检测:全盘病毒扫描 / 木马查杀 / Rootkit / 恶意模块 / 锁机检测 —— 后台线程跑真实引擎,进度与结果以
    事件推回页面渲染(`virusProgress/Done`、`trojanProgress/Done`、`rootkitDone`、`modulesDone`、`lockerDone`);
    深度查杀 / 病毒查杀中心拉起原生 `DeepScanActivity` / `VirusCenterActivity`;
  - 防护·应用锁:PIN 状态、已装应用锁定开关(`setLocked`)、假崩溃开关、无障碍入口、设置 PIN 原生弹窗;
  - 防护·权限审计:真实 `PermissionAuditor` 结果按风险分级渲染;
  - 防护·工具箱:实时防护 / 开机自启 / Root 三个原生开关(Root 先探测 su 再回推状态)、检查更新与关于
    (原生弹窗)、网络审计 / 隐私检测 / 漏洞扫描拉起原生页面;
  - 返回键:非首页板块先回首页,首页再按退出(与上传稿底部导航一致);
  - 无 `AndroidBridge` 时(纯 HTML 预览)回退到演示数据,页面依然可用。
- 原生 Fragment 界面层(首页 / 检测 / 防护及子页)保留在源码中(不再作为主界面),未删除;
  深层原生工具页(病毒中心 / 深度查杀 / 网络审计 / 隐私检测 / 漏洞扫描 / 全量体检 / 清理)原样保留,由桥拉起。

### Added

- **上传稿功能全部接通**(`deepseek_html_20261003_008012.html` 防护 · 工具箱,均为真实实现,不造空壳入口):
  - **开机自启防护**开关:默认开启;关闭时通过 `setComponentEnabledSetting` 禁用 `BootReceiver` 组件,
    开机不再拉起核心组件,开启时恢复;`BootReceiver` 开机时同时重排每日定时查杀(WorkManager);
  - **安全设置**卡片:检查更新(特征库在线更新 —— 显示内置 / ClamAV 特征规模,https + SHA-256 强制校验,
    URL / SHA 记忆在偏好中,更新结果弹窗反馈)与关于 SecureDroid(版本名 + 构建号);
  - **隐私检测**工具(`PrivacyActivity`):枚举持有敏感权限的应用,中文列出敏感项(短信 / 通讯录 / 定位 /
    录音 / 相机等),按风险分排序,高风险应用提供系统卸载入口;
  - **漏洞扫描**工具(`VulnerabilityActivity`):系统安全基线(调试内核 / ADB / SELinux / 屏幕锁 / 加密 /
    未知来源)+ 第三方组件暴露面(`AttackSurface`);
  - 工具行补齐上传稿风格的圆底图标(新增 `ic_sd_power` / `ic_sd_eye` / `ic_sd_refresh` 三个描边图标);
- 首页「立即扫描」改为直达「检测 · 病毒扫描」板块(原跳一键全面体检,与上传稿语义对齐)。

### Removed

- **整套设计系统移除**:删除 `design/` 全部 9 个文件(设计文档 DESIGN.md、布局生成器
  generate_layouts.mjs、HTML/PNG 渲染器与视觉稿),界面不再由脚本生成;
- 同步删除只服务于生成器的 `DesignRuleTest.everyLayoutIsGenerated` 与 CI 中"重跑生成器 +
  git diff"步骤;20 个布局 XML 保留为普通文件(仅去掉"请勿手改"的头注释),**外观零改动**;
- 清理 README / AGENT_NOTES / 颜色令牌注释里指向设计稿与 DESIGN.md 的引用:README 的
  "视觉设计系统(按设计稿重建)"一节改写为"界面外观(现状)",AGENT_NOTES 里并存的三套风格
  基线(Swiss / Apple HIG / 旧记录)合并为一条"设计系统已移除,布局可手改"的约定;
- 原生主界面的 Fragment 容器 / 悬浮胶囊导航(Dock)从 `MainActivity` 布局移除,由 HTML 渲染的
  同等界面替代;`MainActivity.navigateTo(panel, segment)` 保留为桥接入口(兼容宫格 / 小部件直达)。

## [1.8.0] - 2026-10-03

### Changed

- **界面按上传稿整体重做**:实现来源为用户上传的 `deepseek_html_20261003_008012.html`(毛玻璃 / 柔和流动光晕 / 悬浮胶囊导航),数值逐项对照该文件:
  - **背景光晕**:4 团大半径色斑(300/250/280/220dp)按上传稿的 drift1-4 关键帧缓慢漂移(26/30/28/32 秒往复,位移 + 缩放);用径向渐变 drawable + ViewPropertyAnimator 实现,动画跑在渲染线程,系统"动画时长缩放"为 0 时自动瞬时;
  - **卡片**:半透明"毛玻璃"底(浅色 rgba(255,255,255,.62) / 深色 rgba(23,27,24,.62))+ 1dp 高光描边 + 24dp 圆角 + 极淡阴影,替换原先的不透明白卡;
  - **底部导航**:自定义悬浮胶囊(高 60dp、左右 12dp、贴底 20dp、内部 5dp 内边距、胶囊圆角),3 个等分入口(22dp 图标 + 12sp 粗体文字),选中项为品牌绿渐变胶囊且**两侧分隔线淡出**,替换原来的 TabLayout;跨板块直达(navigateTo)与旋转恢复逻辑同步重写;
  - **首页**:改为「问候 + 主状态卡(设备安全 / 上次扫描 / 立即扫描)+ 状态概览条(病毒库 · 已扫描 · 防护中)+ 2×2 功能宫格(病毒扫描 / 木马查杀 / 网络检测 / 应用锁)+ 最近活动」,移除分数环与旧四宫格;概览条与活动流的数字全部来自真实数据(扫描记录数、待处理威胁、高风险权限应用、生效防护项);
  - **检测 / 防护**:药丸式分段控件(浅底轨道 + 3dp 内边距 + 选中白胶囊 + 粗体文字);扫描页改为「100dp 圆形图标 + 提示 + 4dp 渐变进度条 + 主按钮 + 结果卡(卡头 + 空态)」;应用锁与工具箱改为「34dp 圆形图标 + 15sp 标题 + 12.5sp 副标题 + 开关 / 右尖角」的保护项行;权限审计的分数改为胶囊徽标(底色与文字按风险等级切换);
  - **列表行 / 对话框 / 小组件**同步按上传稿重排(行高、分隔线缩进 56/60/62dp、徽标胶囊、开关)。

### Added

- 设计令牌:毛玻璃底/描边/高光、导航玻璃底、分段选中底、光晕 4 色、图标圆底 4 组(绿/蓝/琥珀/紫)、徽标 3 色、`c_fg3`、`c_section`(浅深两套);
- 尺寸令牌 30 项(导航 60/12/20/5、分段 48、主状态卡 88/44、扫描圆 100/42、宫格 132/44/22、活动行 32/16、保护项 34/18、徽标 10/3、分隔线缩进 60/62 等);
- 字阶 18 项(25sp 页标题、22sp 主状态标题、16sp 概览数值、15sp 宫格标题、12.5sp 副标题、12sp 徽标等);
- 矢量图标 16 个(路径逐条取自上传稿的内联 SVG);
- 形状 drawable 23 个(光晕 4、毛玻璃/导航/分段/徽标/圆底等)。

### Notes

- **与上传稿的已知差异(均为可验证的工程取舍)**:
  1. 毛玻璃不做真实的背景模糊:Android 没有跨视图的 backdrop-filter,改用"半透明底 + 高光描边 + 阴影"近似;底色本身是柔和渐变,观感接近且没有实时模糊的性能开销;
  2. 分段按钮高度取 **48dp**(上传稿是 42px):仓库既有的 `DesignRuleTest` 要求可点击控件触摸目标 ≥48dp;
  3. 工具箱保留应用**真实的 5 个开关**(实时防护 / 每日查杀 / SIM 卡防护 / 自动杀毒 / 自动卸载)+ Root 状态,以及 3 个真实工具入口;上传稿里的「开机自启防护」「检查更新」「隐私检测」「漏洞扫描」在应用中没有对应实现,不新造空壳入口;
  4. 「假崩溃诱骗模式」等文案沿用应用原有字符串(含义相同);
- 文字版结构文档 `design-export/ui-structure-text.md` 与 `design-export/` 下的 SVG 导出**对应的是改版前的界面**,重新运行 `export_ui.py` 后可同步到新界面。

## [1.7.3] - 2026-10-02

### Fixed

- **检测链系统性误报(用真实语料实测复现)**:行为规则用纯 `contains` 在 DEX 字符串碎片上匹配,
  且只要凑够模式数量就升级为感染。实测结果 —— 一加官方"备份与恢复"被判 10 条(含 CRITICAL
  短信扣费 / 提权 / 反向 Shell),Dute 等 4 个正常应用各 7-10 条,本应用扫描自己时 14 条规则全中。
  三处根因分别修复:
  ① 匹配改为**词边界**匹配(新增 `scan/TokenMatch.kt`):`exec` 不再命中 `execute`/`execSQL`/`executor`,
  `xposed` 不再命中查杀应用自带的 `xposedcheck`;
  ② 新增**强特征门槛**(`BehaviorRules.Rule.strong`):纯 API 组合(`Ljava/net/Socket;` + `exec`、
  `Ljavax/crypto/Cipher;` + BTC、`xposed`)在正常应用里到处都是,必须同时命中真正的恶意落点
  (真实 shell 路径 `/system/bin/sh`/`/system/xbin/sh`、`.locked`/`readme.txt`、`resetPassword`)才判该条;
  ③ **分级下调**:除反向 Shell / 勒索 / 锁机勒索三条有判别性证据的规则外,其余 11 条降为 LOW 提示级。
- **LOW 命中不再算"感染"**(`trojan/TrojanScanner.kt`):`Report.isInfected` 由
  `detections.isNotEmpty()` 改为"存在 MEDIUM 及以上命中";提示级信息仍留在结果里供 UI 展示。
- **扫描自己**:应用内置了全部"检测用"字符串常量,行为规则必然命中自己。`TrojanScanner.scanPackage`
  遇到本应用直接返回空报告;新增纯函数 `ParallelScanner.scanTargets()`,扫描目标同时排除本应用与信任列表。
- **锁机判定收紧**(`root/LockerDetector.kt`):原实现"命中 lockNow / resetPassword / wipeData 中任 2 项"
  即判锁机木马,而厂商设备管理组件普遍同时带 `lockNow` + `wipeData`;现要求必须命中
  `resetPassword`(重置锁屏密码勒索)才判 CRITICAL。
- **通用文件魔数不得作为特征**:演示特征库 `assets/signatures/trojan_demo.ndb` 中的
  `Test.Trojan.DexHeader`(绝对偏移 0 匹配 `dex\n035`)会让所有同版本 dex 的正常应用被整片判成木马,
  现替换为自检用唯一标记 `APK-HEADER-MARKER`。
- **判定缓存加语义版本号**(`vscan/DexVerdictCache.kt`):新增 `dex_verdict_cache_version`(当前 2),
  规则语义变化后自增 —— 否则老设备会继续沿用按旧规则写入的 HIGH 判定,误报修复等于无效。
- **自查不给自己扣分**:`PermissionAuditor.riskyAppCount()` 统计高风险应用数量时排除本应用
  (自身声明了 `QUERY_ALL_PACKAGES` 等权限)。

### Added

- `FalsePositiveTest` 10 项:真实第三方 APK 字符串画像不得判为感染、纯子串语料不产生判定、
  合成恶意样本(反向 Shell / 勒索 / 锁机勒索)仍必须命中、只有 MEDIUM+ 才算感染、
  锁机判定要求 `resetPassword`、扫描目标排除自己与信任列表、特征库不含通用魔数、自排除与边界匹配的源码守卫。
- ClamAV 引擎冒烟测试新增 `genericFileMagicIsNeverASignature`:dex\n035 / dex\n038 / zip / elf
  四类通用魔数都不得命中任何内置特征。

### Changed

- 检测能力的已知取舍:单纯"短信扣费"样本降为 LOW 提示(仅凭字符串无法与正常短信应用区分);
  `Trojan.Sms.Premium`、`Trojan.PrivEsc`、`WebViewRce`、`HookFramework` 等 11 条规则改为提示级。
  真实语料误报由 7-10 条 MEDIUM+ 降到 0 条,合成恶意样本仍全部命中。

## [1.7.2] - 2026-10-02

### Fixed

- **查杀历史指纹与风险分为空**:定时查杀(`feature/DailyScanRunner.kt`)与手动查杀(`ui/ScanViewModel.kt`)
  落库时把 `sha256` 写死为空串、`riskScore` 写死为 0,于是历史列表与威胁报告导出
  (`vscan/ThreatReport.kt`)`里的 APK 指纹、风险分全是空的 —— 记录条数看着正常,内容却是占位符。
  现由 `trojan/TrojanScanner.kt` 的 `Report` 携带真实值:指纹复用 `HashCache`(扫描时已算过,
  零额外开销),风险分取 `PermissionAuditor.scoreFor(info)`(与 `ScannerEngine.permissionRiskScore`
  同一语义),单包信息查询由 `flags = 0` 改为 `PackageManager.GET_PERMISSIONS`,与权限评分共用同一次绑定器调用。

### Changed

- 强停/清数据命令(`am force-stop` / `pm clear`)的包名改用 `ShellBridge.quote()` 包裹
  (`vscan/ParallelScanner.kt`、`ui/VirusCenterActivity.kt` 共 3 处)。包名来自 PackageManager、
  不含单引号,实际无可利用风险,本次仅为消除"同一类命令两种写法"的不一致。

### Removed

- 删除 `root/PrivilegeManager.kt` 的 `execBatch()`:全仓(含测试)无任何调用方,且内部仍在使用
  上一版已废弃的 `runSu(...) != null` 成功判据 —— 留着就是下一次误用的入口。

### Added

- `app/src/test/java/com/armorlab/securedroid/feature/ScanHistoryTest.kt`(4 项):报告携带真实
  指纹/风险分、两个写入方不得再落空值、`execBatch` 已被移除、强停命令包名走 `quote`。

## [1.7.1] - 2026-10-02

### Security

- **root 命令注入修复(P1)**:所有拼进 `su` 命令行的路径改为走新增的 `ShellBridge.quote()`
  (POSIX 单引号转义 `'\''`)。此前形如 `"rm -f '" + path + "'"` 的写法在文件名含单引号时可越出引号,
  在 `/data/local/tmp` 放一个特制文件名即可在用户点"立即处置"或自动处置时以 root 执行任意命令。
  涉及 `vscan/Quarantine.kt`、`vscan/NetKill.kt`、`vscan/ResidueScanner.kt`、`root/RootGuard.kt`、
  `root/ModuleScanner.kt`、`root/SystemIntegrity.kt`、`root/LockerDetector.kt`、`root/PrivilegeManager.kt`、
  `deep/FilesystemScanner.kt`、`deep/PartitionScanner.kt`、`trojan/RootkitDetector.kt`、
  `feature/CleanerTool.kt`、`realtime/RealtimeProtectionService.kt`、`ui/TrojanAdapter.kt`、
  `ui/VirusCenterActivity.kt` 共 15 个文件。

### Fixed

- **"假成功"修复(P0)**:`ShellBridge.runSu()` 合并 stdout+stderr 后恒返回非 null 字符串,
  而全仓 9 处把它当作成功判据(`runSu(...) != null`),导致 `su` 被拒或命令报错时,
  界面/通知/审计日志仍会宣称"已隔离 / 已断网 / 已自动处置"。
  新增 `runSuResult()`(带 `exitCode`)与 `runSuChecked()`(`PrivilegedPolicy` 放行 → 进程已启动 →
  `exitCode == 0` → 输出不含 permission denied / not found / read-only file system 等失败标记),
  所有会改动系统的调用点改为校验退出码,并做结果复核(隔离后确认文件真的存在/消失、
  断网后回读 iptables 规则)。
- **死代码清理**:`RealtimeProtectionService.recordAction()` 从未被调用(日志实际走 `RootGuard.record`),
  已删除。

### Added

- **回归测试** `root/ShellHardeningTest.kt`(9 项):引号转义/逆运算与恶意文件名往返、
  注入载荷必须仍是一个参数、`PrivilegedPolicy` 放行应用真实使用的 15 条命令且拦截 14 条灾难命令,
  外加源码守卫(主源码中 `runSu(...) != null` 必须为 0、9 类"引号内直接插值"写法必须为 0、
  处置调用点必须走加固入口)。

## [1.7.0] - 2026-10-02

### Performance

本轮只做性能与既有功能的健壮性优化,界面、能力集合与偏好键全部保持不变。

- **并行查杀真正并行**:`vscan/ParallelScanner.kt` 原为 `ThreadPoolExecutor(corePoolSize = 0, maximumPoolSize = 6, LinkedBlockingQueue())`。
  无界队列永远不会满,而 `execute()` 只在 `workerCount < corePoolSize` 时新建线程 —— 于是池里始终只有 1 个 worker,
  "并行全盘查杀"实际是串行。现在 `corePoolSize == maximumPoolSize == 本次并发度`(按 CPU 核数取 2–6),
  配合 `allowCoreThreadTimeOut(true)` 做到扫描时真并行、空闲时自动回收。
- **单遍多模式匹配**:新增 `scan/MultiPatternMatcher.kt`(首字符分桶 + 单遍扫描)。`trojan/BehaviorRules.kt` 原先对**每个模式**
  都跑一遍 `strings.any { it.contains(p) }`(单个应用对整个 DEX 字符串集约 35–45 遍全量扫描),现改为单遍;
  `root/LockerDetector.kt` 同步改造。命中语义与暴力实现逐模式等价,由随机语料等价性测试保证。
- **消除 N+1 绑定器调用**:新增 `core/PackageSnapshot.kt`(包列表 / 单包信息 TTL 30s + 同 key 单飞,应用标签进程内记忆化),
  14 处 `getInstalledPackages` / `getInstalledApplications` / `loadLabel` / `getPackagesForUid` 调用点全部收口;
  `scan/ScannerEngine.kt` 全盘扫描只枚举一次包列表,不再"列表一次 + 每包再 `getPackageInfo` 一次";
  `trojan/TrojanScanner.kt` 同样改为复用快照。
- **DEX 判定缓存重做**:`vscan/DexVerdictCache.kt` 改为内存 LRU 索引(命中 O(1),原实现每次命中都要逐条解析 ≤600 条字符串),
  补上真实可用的**负缓存**(未命中任何规则时写入 `~clean` 标记,同一 APK 不再重复做"读 dex + 提取字符串 + 规则匹配"),
  并改为批量落盘(满 32 条或扫描结束 flush 一次,替代原先每包整表重写 + `apply`)。
- **热点路径不再重复编译正则**:新增 `core/Re.kt` 共享正则实例,替换循环内的 `Regex("...")`(进程/分区/网络审计等 10 处)。
- **时间格式化不再重复构造**:新增 `core/TimeFmt.kt`(`ThreadLocal` 缓存 `SimpleDateFormat`,系统语言变化时自动重建),
  替换 4 处每次调用 `new SimpleDateFormat` 的写法。
- **其余热点**:本地特征库按 (路径, 大小, mtime) 记忆化;可信应用集合改用不可变快照;自定义规则映射记忆化;
  权限审计增加 30s TTL 缓存与 `riskyAppCount()`(只计数,不再取标签、不再排序);首页刷新加 1s 节流
  (修掉 `onViewCreated` + `onResume` 连续两次跑完整段 IO);网络审计按 uid 记忆应用归属(原每条连接三次跨进程查询);
  取消扫描后不再投递剩余任务;9 个 RecyclerView 声明 `setHasFixedSize(true)`;包变化时使快照失效。

### Added

- 测试:`core/TtlCacheTest`(命中 / 过期 / 同 key 单飞 / 容量上限)、`scan/MultiPatternMatcherTest`(与暴力实现等价)、
  `core/PackageSnapshotTest`、`vscan/DexVerdictCacheTest`(含负缓存落盘与重载)、`PerfGuardTest`(源码级性能不变量守卫,
  含并行池核心数断言)。

### Changed

- `versionCode` 9 → 10,`versionName` 1.6.0 → 1.7.0。

## [1.6.0] - 2026-10-01

### Changed

本轮按用户给定的设计稿**重建整套界面**:视觉语言从 Swiss 直角边框改为"大圆角白卡 + 品牌绿进度环 + 悬浮导航条"。

- 令牌层整体重写(`values/colors.xml`、`values-night/colors.xml`、`values/dimens.xml`、`values/styles.xml`、
  `values/themes.xml`、`values-night/themes.xml`):品牌色取设计稿实测值 `#04BD19` / `#31D027`,页面改为纵向渐变;
- 字号与几何按实测重建:标题 24sp、环内数字 54sp、卡标题 18sp、状态 17sp、卡副标题 15sp;卡片圆角 24dp、
  进度环 190dp(描边 25dp、内盘 140dp)、主按钮 48dp 高、悬浮导航 60dp 高;
- 首页重做:评分环 + 状态文案 + "一键优化"主行动 + 2×2 功能宫格(清理存储 / 病毒风险 / 网络审计 / 应用管理),
  宫格副标题全部来自真实数据(可用空间、威胁数、活动连接数、已锁应用数);
- `activity_main.xml` 由 Toolbar + 贴底标签栏改为**悬浮圆角导航条**(高 60dp、圆角 24dp、左右 6dp、贴底 4dp),
  选中态为品牌绿图标 + 文字,无指示条;三个标签改为 首页 / 检测 / 防护;
- 实时防护开关、Root 自动处置开关与 Root 状态 chip 从首页迁到"防护 → 工具箱",能力与偏好键不变;
- `design/render_mockup.py` 重写:改为**现读** `res/values` 令牌渲染验收图,渲染器不再写死任何设计值;
- `design/DESIGN.md` 重写:记录设计稿实测数据、令牌映射表与刻意的对比度偏差及其守卫测试;
- `ColorContrastTest` 把品牌主按钮(`#04BD19` + 白字 = 2.53:1)的下限**显式**记为 2.4:1,并新增
  `brandCtaContrastDeviationIsDocumented`:偏差一旦被修好就失败,提醒撤销例外;
- `DesignRuleTest` 新增 `dottedStylesDeclareExplicitParent`:带点号的样式名必须显式写 `parent`;
- `versionCode` 8 → 9,`versionName` 1.5.0 → 1.6.0。

### Fixed

- 修复 `Widget.SecureDroid.Chip` / `.Divider` 丢失显式 `parent=""` 导致的整包资源链接失败
  (`error: resource style/Widget.SecureDroid not found`)。

### Removed

- 删除已无引用的 `drawable/bg_fluid.xml`、`bg_tabbar.xml`、`tab_indicator.xml` 与 3 条字符串
  (`btn_realtime_start`、`btn_realtime_stop`、`sw_realtime_sub`)。

## [1.5.0] - 2026-10-01

### Removed

本轮按"精简功能、删去没必要的能力"做减法,移除 3 组共 8 项功能,**安装包不再申请任何短信权限**:

| 删除项 | 原因 |
| --- | --- |
| APK 提取器 / 应用冻结 / 输入法审计 / 剪贴板检查 / 安全日志导出 | 依赖 root 或价值低,与"体检 + 查杀 + 防护"主线无关 |
| SOS 紧急求助 | 需要 `SEND_SMS` 与电话硬件,与安全工具定位不符,且会让平板 / ChromeOS 无法安装 |
| 文件粉碎器 / 加密保险箱 | 闪存磨损均衡下粉碎为"尽力而为"、易误导;保险箱密码丢失不可恢复,风险高于收益 |

- 代码:删除 12 个 Kotlin 文件(`feature/` 6 个 + `ui/` 6 个)、3 个布局、6 条清单 `<activity>`、
  `SEND_SMS` 权限与 `android.hardware.telephony` 特性声明、31 条字符串;
- 工具箱由 **20 项收敛为 3 项**(一键全面体检 / 网络安全检测 / 应用缓存清理),列表不再有"点了没反应"的占位项;
- `ToolsAdapter.Kind` 由 11 个分支缩到 3 个,删除两个内联执行路径(剪贴板检查、日志导出)与其协程代码;
- 布局生成器同步删除 3 个页面,仍保持 **20 个布局全部由 `design/generate_layouts.mjs` 生成**这一不变量。

### Changed

- `versionCode` 7 → 8,`versionName` 1.4.1 → 1.5.0。

## [1.4.1] - 2026-10-01

### Fixed

- **清理未使用资源**:42 个颜色令牌、6 个尺寸、3 个字符串、3 个样式与 `values-night/dimens.xml` —— Lint 警告 **108 → 57**;
- **分段控件可点高度 44dp → 48dp**,满足 Android 触摸目标下限(由本轮新增的 `DesignRuleTest` 抓出);
- 布局中残留的**字面字号**改为文字样式(告警条 / 开关行 / 权限分数 / 解锁键盘数字);
- **补齐 6 个此前漏在设计系统之外的页面**:文件粉碎、SOS 求助、文件保险箱、设置 PIN 对话框、
  桌面小组件、解锁键盘 —— 现在 **23 个布局全部由 `design/generate_layouts.mjs` 从令牌生成**,
  不再存在"两套设计语言";小组件因 RemoteViews 限制单独注明(不参与主题解析);
- `NightThemeTokenTest` 改为校验当前语义令牌集(旧别名已随清理删除)。

### Added

- **`DesignRuleTest`(4 项)**:布局必须由生成器产出(防手改漂移)、可点击控件触摸目标 ≥48dp、
  颜色与字号必须令牌化(不许写字面值)、核心令牌在两个主题中齐全;
- **`ColorContrastTest`(2 项)**:按 WCAG 相对亮度公式计算浅色/深色两套主题的关键配对对比度,
  断言正文 ≥4.5:1 —— 把"没人能上真机看"的那部分设计验收变成会失败的断言。

> 说明:这两项测试替代不了真机视觉验收,但能挡住"令牌被改坏 / 触摸目标变小 / 页面忘了走设计系统"
> 这类在真机上只表现为"有点怪"的问题。目前测试总数 71 → **77**。

## [1.4.0] - 2026-10-01

### Changed

- **按 ui-ux-pro-max 的解析结果完全重写设计系统**:风格定为 **Minimalism & Swiss Style**
  (技能 `--design-system` 输出),配色采用其返回的 Trust navy 语义色板,深色表面层级来自
  `--domain color "dark mode surface elevation contrast"`;
  - 色彩:primary/accent/background/foreground/card/muted/border/destructive 全部按技能返回的
    十六进制值定义;旧的 `sd_*`/`ios_*`/`status_*` 令牌**保留为别名**指向同一套语义色,
    因此全应用共享一个色板,不存在并行体系;
  - **分隔语言改为 1dp 边框 + 留白**:卡片 0dp 阴影 1dp 描边(技能 Key Effects:"sharp shadows if any"),
    分区靠 24/32dp 留白而非分隔条;
  - 字体 6 级:Display 34 / Title 26 / Headline 20 / Body 16(行高 1.5)/ Label 14 / Section 12(大写 0.08 字距);
  - 尺寸:4/8dp 栅格,行高 56dp,触摸下限 48dp,标签栏 60dp,卡片圆角 12dp、控件 8dp;
  - 底部标签栏:实底 + 1dp 顶边框 + 3dp accent 指示条(替换上一版的通栏玻璃);
- **布局改为由脚本生成**:新增 `design/generate_layouts.mjs`,17 个布局文件从设计令牌生成,
  使"令牌 → 界面"的映射可执行、可复查,避免逐页手写造成的漂移;
- 技能中与设计冲突的建议按"先验证适配性再套用"处理并记录偏差:字体改用系统字体(CJK 与包体),
  深色 CTA 用 `#38BDF8` 而非返回值 `#EF4444`(避免与危险态同色,技能自身规则禁止一色两义)。

### Known

- Lint 警告由 74 增至 108:历史令牌别名(`ios_*` 等)与旧 drawable 已无引用点,属于待清理项,不影响 0 错误门禁。

## [1.3.0] - 2026-10-01

### Changed

- **界面改为 Apple HIG 风格**(iOS 系统色 + Dynamic Type + inset grouped 列表 + 分段控件 + 通栏标签栏):
  - **色彩**:全部改用 iOS 语义系统色(systemBlue / systemGreen / systemOrange / systemRed、
    label / secondaryLabel / tertiaryLabel / separator、systemGroupedBackground / secondarySystemGroupedBackground、
    systemFill),浅色与深色各一套,旧令牌名保留为别名;
  - **字体**:采用 Dynamic Type 命名尺度(Large Title 34 / Display 40 / Headline 17 semibold / Body 17 /
    Footnote 13 / Caption 11,单位 sp);
  - **列表**:改为 iOS inset grouped —— 10dp 圆角分组卡收纳行,行不再各自成卡;分隔线 0.5dp,
    只在行与行之间绘制(新增 `ui/InsetDividerDecoration`),左侧 52/16dp 内缩与文字对齐;
  - **导航**:底部通栏标签栏(顶部 0.5dp 分隔线),实心图标在上、11sp 标签在下,选中只用 systemBlue 着色、
    没有指示条;内容区仍是 2dp 线性图标 —— 实心/线性两层分级与 iOS 一致;
  - **控件**:iOS 分段控件(12% 填充轨道 + 选中段浮起的浅色块)、iOS 开关(打开为 systemGreen)、
    行尾披露指示符 `ic_chevron`(`importantForAccessibility="no"`,不进入无障碍树);
  - **版式**:去掉渐变与光晕,内容层改为纯色分组底(iOS 不使用装饰性背景);
  - **动效**:移除按压缩放(iOS 的按压反馈是高亮变暗,不是缩放),保留列表入场;
- 度量:屏幕边距 16dp、行高 48dp(**取 Android 触摸目标下限,严于 iOS 的 44pt**)、分组卡圆角 10dp;
- 构建:`gradle.properties` 堆内存降到 1.5GB 并关闭并行 —— 本机 14GB 内存下 2GB 堆会因内存不足启动失败。

### Fixed

- 补齐重构中遗漏的令牌与样式别名(`sd_glow`、`Widget.SecureDroid.Chip`),避免到 AAPT 链接期才暴露。

## [1.2.0] - 2026-10-01

### Changed

- **信息架构:顶层入口从 6 个收敛为 3 个板块**(状态 / 检测 / 防护)。上一版 6 个平级入口是"杂乱"的结构性原因:
  用户每次操作都要在 6 个等价选项之间做一次决策。现在:
  - **状态** = 评分环 + 快速入口 + 防护开关;
  - **检测** = 病毒扫描 · 木马查杀(板内分段控件);
  - **防护** = 应用锁 · 权限审计 · 工具箱(板内分段控件);
  - 新增 `DetectFragment` / `ProtectFragment` / `SectionHost`,`MainActivity.navigateTo(板块, 分段)` 支持跨板块直达;
  - 底部导航改 `fixed`(3 项无需横向滚动)。
- **视觉 v2**:
  - 内容卡片改为**色调层级**(深色纯色块 / 浅色 1dp 阴影),取消每张卡的描边;描边只保留在功能层(工具栏、入口条、分段控件);
  - 按钮墙改为**行式入口**(图标 + 文案 + 箭头),仪表盘与查杀页各只保留一个主行动;
  - 评分改为**环形进度**:环 = 分值本身,颜色随状态切换,不做纯装饰;
  - 正文 15sp → **16sp / 行高 1.5**;页面大标题 22sp;分节标签 12sp 加宽字距;
  - 背景从"渐变 + 两处光晕 + 玻璃"简化为**一次渐变 + 一处极淡光晕**;
  - 图标重绘为统一图标族(24dp 网格 / 2dp 描边 / 圆头 / 无填充),不再混用填充与线性;
  - 子页面去掉重复的左右边距(板块容器已提供),避免双重缩进。

### Fixed

- 回归防护:布局属性 `android:layoutAnimation` 曾被误写成 `android:layout_animation` 两次
  (AAPT 只报行号,排查成本高),现由 `ResourceReferenceTest` 新增断言拦截。

### Tests

- 测试 70 → **71**(新增布局属性拼写回归断言);布局膨胀测试以新主题真实膨胀全部布局。

## [1.1.0] - 2026-10-01

### Added

- **流体设计视觉系统**:依据 ColorOS 17「流体设计」(凝光视效 / 流体动效 / 柔性反馈)、
  Apple HIG 的材质与动效规范、UI/UX Pro Max 规则库重做整套界面:
  - 令牌层:`res/values` + `res/values-night` 全量色板(深浅双主题)、4dp 尺寸栅格、
    圆角(28 / 18 / 14 / 100dp)、五级文字层级(Display / Title / Section / Body / Label);
  - 「凝光」背景:线性渐变 + 两处径向光晕,零图片资源,任意密度清晰;
  - 功能层玻璃:工具栏与底部入口条使用半透明 + 1dp 光边 + 顶部高光
    (HIG 规定玻璃只用于浮在内容之上的控件层),内容层保持不透明表面;
  - 动效:列表错峰入场(每项延迟 8% 的 `layoutAnimation`)、按压缩放 0.97 / 110ms、回弹 240ms;
  - 组件:内容卡片、主/次按钮、状态胶囊、流体圆角渐变进度条、卡片化列表行;
- **设计文档与设计稿**:`design/DESIGN.md`(令牌表、组件规范、可访问性自检清单)与
  `design/render_mockup.py`(按令牌 1:1 渲染深浅双主题设计稿,非真机截图)。

### Changed

- 12 个界面按新体系重排:仪表盘改为「评分光球 + 2×2 快速操作 + 设置卡」;扫描页主行动置顶、
  进度条流体化;查杀页按主/次行动分层;列表项卡片化;**60 个既有 View ID 全部保持不变**,零 Kotlin 改动。

### Fixed

- **深色令牌曾被写进 `res/values/night/colors.xml`**:该目录被 AAPT 静默忽略,构建 / Lint / 测试全绿,
  但深色模式下界面会退回浅色取值。已迁到 `res/values-night/colors.xml`,并新增
  `NightThemeTokenTest`(目录结构、令牌覆盖、深浅差异、状态栏开关四条不变量)防止复发。

### Tests

- 新增 `NightThemeTokenTest`(4),测试总数 66 → 70;其中布局膨胀测试会以新主题真实膨胀全部布局。

## [1.0.1] - 2026-10-01

### Changed

- **查杀引擎索引改为 2 字节窗口直接索引**:任意偏移特征从"每字节遍历单字节锚点桶"改为按 2 字节窗口
  直接寻址(65536 直接表 + 链表,扫描期零分配),候选数摊薄约 256 倍;绝对偏移 / EOF 偏移仍按偏移直接定位。
  窗口取签名中最长连续固定字节片段的前 2 字节,命中后仍做半字节级复核,判定结果与旧实现完全一致;
- **记录一次被实测否决的优化**:曾实现 Aho-Corasick 自动机(CSR 存 goto + 每字节二分查找 + 失效链回溯),
  在 200 特征 × 1 MB 场景下比单字节锚点分桶慢 3.6 倍(127 ms vs 35 ms),故放弃。基准已固化在测试中;
- 新增 `ClamAvSignatures.importText()` / `unload()`,支持校验后热加载内存特征库,并让引擎可在无 Context 的纯 JVM 测试中直接注入合成特征。

### Added

- **运行时防重打包(完整性守卫)**:security/IntegrityGuard 读取本包签名证书的 SHA-256 指纹,
  首次运行 TOFU 记录,后续启动比对;不一致或存在多个签名者时主界面顶部显示红色告警条。
  取不到签名信息时返回 UNAVAILABLE(既不告警也**绝不**判定为可信);
- **安全加固**:主界面 integrityBanner 告警条 + integrity_tamper / integrity_multisigner 文案。

### Tests

- SignatureMatchIndexTest(6):新索引与旧版单字节锚点分桶、逐签名朴素扫描三种实现结果等价;半字节通配;
  缓冲区首尾边界;整条全通配特征退化为逐位置校验;1 字节窗口分支;200 特征同锚点最坏情形下的性能对比。
- IntegrityGuardTest(5):四种判定分支、TOFU 记录与二次校验、签名变更告警、指纹格式、重置后回到首次运行。

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

**由 Robolectric 运行时冒烟测试发现并修复(编译 / Lint / 静态测试均无法发现):**

- **应用启动即崩溃**:`SecureGuardApp.onCreate` → `ScanScheduler.sync` 无保护调用 `WorkManager.getInstance()`。
  一旦 androidx.startup 初始化器未生效(被裁剪、受限进程等),会抛 `IllegalStateException: WorkManager is not initialized properly`。
  改为官方推荐的**按需初始化**(Application 实现 `Configuration.Provider` + 清单移除默认初始化器),并在调度层全量兜底异常 —— 调度失败只应导致定时查杀不生效,绝不能让应用启动崩溃;
- **主界面 100% 无法启动**:`bottom_nav.xml` 定义了 6 个入口,而 Material `BottomNavigationView` 硬上限为 5 项,
  布局膨胀阶段直接抛 `IllegalArgumentException: Maximum number of items supported by BottomNavigationView is 5`。
  改用无条目上限的**可滚动 `TabLayout`**,6 个功能入口全部保留,菜单 XML 仍作为入口定义的唯一来源;
- **应用锁在安全存储不可用时崩溃**:`EncryptedSharedPreferences` 依赖 AndroidKeyStore,密钥库异常会抛 `KeyStoreException`。
  现统一降级为"应用锁未启用"并记录日志 —— 不崩溃,也**不静默退化为明文存储**(静默降级会掩盖真实安全状态);
- 应用锁 PIN 保存失败现在会明确提示用户(此前无论成功与否都提示"已保存")。

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

### Added / Corrected — 依据官方文档的核查与加固

对照 ClamAV 官方签名格式文档核查后发现**兼容性声明与实现不符**,已修正:

- **`.ndb` 解析器此前不兼容官方格式**:实现的是一套自定义分号分隔格式(`名称;偏移;HEX;严重度;目标`),
  而官方 `.ndb` 为冒号分隔的 `名称:目标类型:偏移:HEX[:min_flevel[:max_flevel]]`,
  真实 ClamAV 特征库无法加载。现已按官方格式重写解析,并保留历史格式的向后兼容;
- **新增偏移语义支持**:`*`(任意位置)、绝对偏移 `n`、`EOF-n`(文件尾偏移)、浮动区间 `n,MaxShift`;
  位置固定的特征改为直接定位,**零扫描开销**(此前所有特征都做全量扫描);
  `EP+/Sx+/SL+` 等仅对 PE/ELF/Mach-O 生效的语义统一退化为任意位置,避免漏报;
- **新增 `.hsu` / `.ndu` 扩展名识别**,与 ClamAV 命名一致;
- 内置演示特征改写为官方格式(任意位置 / 绝对偏移 / 文件尾三种形态各一条),并保留一条历史格式用于回归。

安全加固(参考 OWASP MASVS 与 Android 平台机制):

- 锁屏页与文件保险箱启用 `FLAG_SECURE`,禁止截屏 / 录屏 / 最近任务缩略图;
- 锁屏页根布局启用 `filterTouchesWhenObscured`,阻断悬浮窗覆盖点击劫持;
- 新增 `network_security_config.xml`:全局禁止明文流量、仅信任系统 CA;
- 新增 `dataExtractionRules`:敏感数据不进入云备份与设备迁移(修复 Lint 的 DataExtractionRules 警告);
- 特征库在线更新(`FeatureUpdater`)加固:仅接受 **https**(含重定向后复检)、**SHA-256 必填**、
  内容合理性校验(拒绝 HTML 错误页 / 空内容)—— 特征库投毒等于让查杀引擎失效。

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
