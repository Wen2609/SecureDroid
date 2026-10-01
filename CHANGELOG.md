# Changelog

本文件记录各版本的重要变化。格式参考 Keep a Changelog,版本号遵循语义化版本。

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
