# 安卫安全助手(SecureDroid)

一款开源的 Android 安全应用,从零构建,使用 Kotlin + Jetpack
(Material 3 / MVVM / Room)开发。

> **开源许可:本项目基于 [Apache License 2.0](LICENSE) 开源。**
> 欢迎学习、修改与贡献;二次分发请保留版权与许可声明,商用需遵守许可证条款。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 界面(WebView UI) | 主界面直接用上传稿 `deepseek_html_20261003_008012.html` 渲染:首页 / 检测 / 防护三板块、悬浮胶囊导航、分段控件、全部按钮 / 开关 / 列表经 JS 桥(`web/NativeBridge`)接到真实原生功能;深层工具保留原生页面 |
| 病毒扫描 | 计算已安装 APK 的 SHA-256,与本地特征库精确比对;结合敏感权限权重输出风险评分;扫描结果落库(Room) |
| 权限审计 | 枚举全部应用,按敏感权限(短信、通讯录、定位、麦克风、安装包等)权重累加评分,分级展示 |
| 实时防护 | 前台服务监听应用安装 / 更新广播,新应用自动扫描,命中特征时发送高优先级告警通知;开机自启 |
| 应用锁 | 加密存储 PIN(仅存加盐 SHA-256);无障碍服务检测前台应用,受保护应用启动时弹出 PIN 锁屏 |
| 木马查杀 | 多引擎:ClamAV 兼容签名(.hsb/.ndb)+ DEX 行为规则(YARA 风格)+ rkhunter 式 Rootkit 检测;发现感染应用支持一键卸载引导 |
| 恶意模块防护 | 针对 KernelSU / APatch / SukiSU-Ultra / Magisk:扫描模块启动脚本与 su 开机脚本,加权评分判定恶意行为,支持一键禁用模块、删除恶意 su 脚本 |
| Root 即时检测 / 自动杀毒 | Root 模式下:Root 守护循环(开机即扫 + 每 5 分钟巡检)即时检测恶意模块与 su 脚本;命中高危 / 严重项自动禁用模块、删除恶意脚本;应用安装即检,恶意应用经 root 自动卸载;全部处置写入审计表(auto_actions)并发通知 |
| 防锁机软件 | 检测第三方设备管理员 + lockNow / resetPassword / wipeData 组合行为;Root 模式下守护循环即时检测,判定锁机木马即自动执行 dpm remove-active-admin 解除管理员并卸载;普通模式提供一键解除处置按钮 |
| 安全工具箱(精简版) | 只保留需 root 或需主动触发的高价值工具:网络审计 / 隐私检测 / 漏洞扫描(系统安全基线 9 项 / DNS 劫持检测 / hosts 篡改检测);另有 SIM 卡变更防盗告警 / 每日定时自动查杀 / 快捷设置磁贴 / 桌面小部件 / 应用锁防暴力破解 / 应用锁假崩溃诱骗 |
| 深度查杀(内存·全盘·分区) | 三阶段极致扫描:①运行内存进程检测(双视角枚举抓 Rootkit 隐匿进程、已删除可执行文件驻留、临时目录可执行、伪装系统进程、rwxp 匿名内存注入、挖矿级 CPU 双采样);②全设备目录查杀(tmp 载荷 + find 全盘可执行脚本/dex/jar + 脚本评分 + 系统分区全局可写 + root 残留 + ClamAV 字节特征经 base64 通道读取);③底层分区查杀(/dev/block/by-name 枚举 + dd 原始分区特征扫描 + boot 镜像 magisk/tmp 引用痕迹);守护循环自动清除临时目录载荷 |
| 病毒查杀中心(20 项专项) | 签名库状态统计 / 最近安装应用综合深扫(7 天) / APK 证书与签名完整性 / 嵌入式 APK 与隐藏 DEX 检测(PK 头与 dex magic 计数) / assets 原生库异常 / 旧 targetSdk 风险 / 危险权限组合(安装+短信) / 综合威胁评分引擎(权限+行为+元数据三路合成) / 信任列表白名单(全链路过滤) / 隔离区(移动+销毁) / APK 哈希缓存加速 / 差异快速扫描(仅变更应用) / 进程基线学习与新增进程检测(守护循环联动) / 特征库在线更新(URL+SHA-256 校验) / 特征库热重载 / 威胁情报报告导出 / 查杀统计仪表 / 恶意应用强停清数据处置 / 卸载残留目录检查 |
| 最高权限防护 | 统一提权层:主动探测 su 授权(ROOT)/ 本地 shell 分级,能力矩阵可视化(12 项能力),灾害级命令安全策略强制拦截,批量命令合并为单次 su 会话,最近 500 条提权命令审计落盘 |
| 系统完整性监控 | root 加持:关键系统路径(/system/bin、/system/etc、/vendor/bin、/data/adb/modules)基线快照,与基线比对新增/删除/篡改三类差异,守护循环周期校验并即时告警;关键文件(hosts、su 等)chattr +i 锁定防静默篡改,不支持时降级 chmod 0444 |

### 极致增强优化记录

- **性能**:病毒扫描页与定时查杀改用 4 线程并行引擎;APK 哈希 / DEX 行为判定双层指纹缓存,未变更应用近零开销;APK 结构检测改为 1MB 分块流式计数(不再整包读入内存);模块采集由 N 次 su 往返压缩为单次批量脚本;仪表盘安全评分 5 分钟缓存;
- **降噪**:系统应用跳过行为/字节码启发(保留哈希证据),消除大部分误报;自动处置通知 60 秒去重;
- **健壮性**:扫描记录超 2000 条自动裁剪;实时防护跳过自身安装事件;修复 VpnAppsScanner 位运算优先级、InstallerOrigin 弃用 API 注解两处编译问题;
- **体验**:全部结果列表按威胁等级降序排列;锁屏页拦截返回键防绕过;守护循环低电量自适应拉长周期。
- **性能优化第三轮**:扫描线程池改为跨扫描复用(不再每次新建/销毁,空闲线程自动回收);APK 哈希缓存加内存 LRU 命中,落盘改为每 16 条批量 apply(原实现每包一次 SharedPreferences 读写),超限按 LRU 淘汰不再整表清空;ClamAV 字节特征匹配由「逐签名全 buffer 扫描」改为「单遍锚点索引 + 候选校验」,数百签名 × 数 MB 分区数据的比较次数下降一到两个数量级;
- **最高权限防护层**:新增 PrivilegeManager(权限分级/能力矩阵/安全策略/审计/批量执行)与 SystemIntegrity(完整性基线/差异校验/关键文件锁定),RootGuard 与守护循环已接入;
- **性能优化第二轮**:引擎4(ClamAV 字节码)纳入指纹缓存(命中免重复解压扫描);APK 结构结论缓存(analyzeCached,嵌入检测/综合评分免重复读包);自定义规则 JSON 记忆化(并行扫描免逐应用解析);Room insertAll 批量单事务落库;NetKill 单次拉取 iptables 规则表;RootkitDetector 直接探测+单次批量 su 兜底;quickTmpProbe 四目录合并为单次 find;
- **真机稳定性与可验证性(第四轮)**:定时查杀从「AlarmManager + 启动前台服务」迁移到 WorkManager(DailyScanWorker + DailyScanRunner),规避 Android 12+ 后台启动前台服务限制与 Android 15 BOOT_COMPLETED 类型禁用;前台服务类型改为 dataSync|specialUse 并按 API 分派;服务启动入口全部加异常保护;证书白名单对话框移出主线程消除 ANR;新增 JVM 单元测试(提权安全策略 / 家族分类 / 哈希编码 / 清单不变量),命令 gradle testDebugUnitTest;
- **性能优化第五轮(v1.7.0)**:修复并行查杀的线程池缺陷 —— 原实现 `corePoolSize = 0` 配无界队列,池里永远只有 1 个 worker,"并行查杀"实际串行,现令 `core == maximum == CPU 自适应并发度` 且允许核心线程超时回收;DEX 行为规则从"每个模式都全量扫一遍 dex 字符串集合"(单应用 35~45 遍)改为**单遍多模式匹配**(新增 MultiPatternMatcher),锁机检测同步改造;新增 PackageSnapshot 快照层,14 处 PackageManager 枚举与 loadLabel 收口,全盘扫描的 N+1 绑定器调用归零;DexVerdictCache 改为内存 LRU 索引 + 真实负缓存(未命中写 `~clean` 标记)+ 满 32 条批量落盘;正则与 SimpleDateFormat 提为共享实例(core/Re、core/TimeFmt);首页刷新加 1s 节流;网络审计按 uid 记忆应用归属;9 个 RecyclerView 声明 `setHasFixedSize(true)`;新增 5 个性能测试套件(共 109 项 JVM 测试);
- **root 执行加固(v1.7.1)**:审计发现的两处提权执行缺陷已修复 —— ①`ShellBridge.runSu()` 把 stdout+stderr 合并后恒返回非 null,全仓 9 处把它当作成功判据,`su` 被拒绝或命令报错时界面/通知/审计日志仍会宣称"已隔离 / 已断网 / 已自动处置";现新增 `runSuResult()`(带退出码)与 `runSuChecked()`(策略放行 → 进程已启动 → `exitCode == 0` → 输出不含 permission denied / not found / read-only 等失败标记),并对隔离、断网结果做存在性/规则复核;②所有拼进 `su` 命令行的路径改为 `ShellBridge.quote()`(`'` → `'\''`),此前文件名含单引号即可越出引号、以 root 执行任意命令,涉及 15 个文件。新增 `ShellHardeningTest` 9 项(共 118 项 JVM 测试);

- **查杀历史与死代码修复(v1.7.2)**:定时查杀 `DailyScanRunner` 与手动查杀 `ScanViewModel` 落库时把 `sha256` 写死为空串、`riskScore` 写死为 0 —— 历史列表与威胁报告导出的指纹/风险分因此全为空。现由 `TrojanScanner.Report` 携带真实值:指纹复用 `HashCache`(已算过,零额外开销),风险分取 `PermissionAuditor.scoreFor(info)`(与 `ScannerEngine.permissionRiskScore` 同语义),包信息查询由 flags=0 改为 `GET_PERMISSIONS` 与评分共用同一次绑定器调用;同时删除无调用方的 `PrivilegeManager.execBatch()`(且仍在用已废弃的 `runSu(...) != null` 判据),强停/清数据命令(`am force-stop` / `pm clear`)的包名统一走 `ShellBridge.quote()`。新增 `ScanHistoryTest` 4 项(共 122 项 JVM 测试)。

- **误报修复(v1.7.3)**:行为规则此前用纯 `contains` 匹配 DEX 字符串碎片,且凑够模式数量就升级为感染 —— 实测一加官方"备份与恢复"被判 10 条(含 CRITICAL 短信扣费/提权/反向 Shell)、Dute 等 4 个正常应用各 7-10 条、本应用扫描自己时 14 条规则全中。现三处修复:①匹配改为**词边界**(新增 `scan/TokenMatch.kt`,`exec` 不再命中 `execute`/`execSQL`);②新增**强特征门槛**(`BehaviorRules.Rule.strong`,纯 API 组合必须叠加真实恶意落点才算);③**分级下调**(11 条规则降为 LOW 提示),且 `Report.isInfected` 只认 MEDIUM 及以上;同时排除扫描自己、收紧锁机判定(必须命中 `resetPassword`)、删除演示特征库里的通用 dex 头魔数、给判定缓存加语义版本号(`DexVerdictCache` VERSION=2,否则老设备沿用旧误报)。真实语料误报 0 条 MEDIUM+,合成恶意样本仍全部命中。新增 `FalsePositiveTest` 10 项(共 132 项 JVM 测试)。
- **UI 与 WebView 性能优化(v1.9.12)**:`AppLockStore.prefs()` 改为双检锁单例(修复无障碍服务逐事件重复 KeyStore 派生的最大热路径);`getDashboard` 改 Room 聚合查询(不再整表拉 2000 行);木马查杀进度 300ms 节流;WebUI 刷新统一收敛(启动双加载 + 多监听重复触发消除);应用锁列表一次读锁集合 + 纯数据排序;四个结果列表 DocumentFragment 批量插入;应用图标 IntersectionObserver 懒加载 + 128px WEBP;`checkUpdate` 签名库加载移后台;转后台自动暂停光晕等装饰动画;新增 `WebUiGuardTest` 4 项 + `PerfGuardTest` 4 项 + `DatabaseAndManagersTest` 2 项(共 142 项 JVM 测试)。
- **WebUI 交互升级(v1.9.13)**:病毒扫描 / 木马查杀支持**取消**(新增 `AndroidBridge.cancelScan()`,复用 `ScanControl`,取消后摘要标注"仅含已扫描部分");病毒扫描结果**按威胁折叠**(默认只渲染感染项,干净应用收进展开器,不再一次铺几百行);防护开关**整行可点**(开关本体 46×28px 达不到触摸目标);分段按钮 42px→48px(项目硬规则);进度百分比 + `role="progressbar"` + `aria-live` 读屏支持;修复既有环境依赖断言(`privilegeLayerDegradesSafelyWithoutRoot` 改为断言无 ROOT 不变量)。新增守卫测试 5 项(共 147 项 JVM 测试)。
- **WebUI 体验升级(v1.9.14)**:首页主状态卡**威胁感知三态**(安全=绿 / 有风险权限或病毒库待更新=琥珀 / 有威胁=红,此前恒显"设备安全");统计条三格**可点直达**(病毒库→木马查杀、已扫描→病毒查杀中心、防护中→防护开关);应用锁列表**搜索过滤**(数百行按名称/包名即时过滤,跨刷新保持);**下拉刷新**(SwipeRefreshLayout + `__sdReady`);空态**图标化**(盾形勾图标 + 语境色调)。守卫测试 +4(共 151 项 JVM 测试)。
- **全界面 HTML 化(v1.9.15)**:消除全部安卓原生界面 —— 深度查杀 / 病毒中心 / 网络审计 / 隐私检测 / 漏洞扫描 5 个原生 Activity 改为 **HTML 子页面**(单 WebView 内 `subStack` 路由);新增 `ToolsHandler` 桥接层(约 700 行)承载全部深层工具逻辑;**所有对话框改为 HTML 模态框**(关于/更新/PIN/信任列表/黑名单/证书标记/修复确认/策略选择),Toast 改为 HTML toast 条;`LockActivity` 改为 WebView + `lock.html`,保留 FLAG_SECURE / 防暴力破解 / 假崩溃诱骗;删除全部原生工具 Activity、Adapter、玻璃背景自定义 View 与 6 个原生布局。守卫测试同步指向新桥接层(共 154 项 JVM 测试)。
- **初始化模式选择与分区重排(v1.9.16)**:首次启动**强制选择运行模式** —— **标准模式 / 无线调试模式 / 超级用户模式**(产品定义名称);新增 `AppMode` 枚举 + `ModeHandler`,`getMode` / `setMode` 桥接 + HTML 全屏模式选择层,未选择不进入主界面。**各模式只可用有权限功能**:病毒中心 Root 级工具(断网应急 / 关键文件锁定 / 完整性 / 隔离 / 卸载残留)仅超级用户模式开放,Shell 级工具(进程基线 / 新增进程)需无线调试或超级用户;`Root 模式`开关仅超级用户可见。**4 大分区重排**:隐私检测 / 漏洞扫描移入应用防护的权限审计页,扩展功能聚焦防护开关 / 网络审计 / 设置。**系统栏适配**:导航栏图标明暗按日/夜显式设置(API 27+ 走 `values-v27`)。病毒中心菜单按模式缓存。守卫测试 +4(共 158 项 JVM 测试)。
- **Pad 适配 · 权限强制 · 内置字体 · 病毒库扩充(v1.9.17)**:**Pad/大屏适配**(≥600dp 快捷入口 4 列、内容区放宽至 720/880px);**模式权限强制授予**(标准=通知、无线调试=通知+使用情况访问、超级用户=通知+Root,未授予前无法进入主界面,新增 `openPermissionSettings` 桥接);**内置 Noto Sans SC 字体**(Regular+Bold,HTML 全界面 @font-face 使用);**内置病毒库扩充**(24 家族 .ndb + 4 条 .hsb);**API 等级上调至 35**(适配安卓小白条,导航栏透明);**毛玻璃增强 + 底部导航可拖动选择丸**(按住动画反馈、触摸拖动吸附)。守卫测试扩展(共 158 项 JVM 测试)。
- **字体子集化 · 权限文案 · 排版优化(v1.9.18)**:内置 Noto Sans SC **子集化为 WOFF2**(约 17MB→476KB,仅含应用实际使用的 1160 个字符);**权限申请文案优化**(每项权限附带用途说明,文案统一收进字符串资源,引导页排版优化);**排版优化**(正文行高 1.6、标题字距收紧、卡片标题/摘要与分区间距微调)。共 158 项 JVM 测试。
- **接入真实病毒库(v1.9.19)**:内置**真实银行木马 SHA-256 哈希种子**(`real_malware.hsb`);新增 **ClamAV 官方 CVD 转换工具** `tools/cvd2clamav.js` —— 从官方 `daily.cvd` 解包生成 `clamav.ndb`/`clamav.hsb`,经「特征库在线更新」即可加载**百万级真实病毒库**;更新对话框增加接入引导。共 158 项 JVM 测试。
- **UI 优化(v1.9.20)**:底部导航栏**始终显示**(子页面打开时仍可点,点击先返回根页);主页移除头像与日期,标题区更精简;**字体粗细统一为 400/700**(消除 faux bold);布局与文字位置微调(行高/字距)。共 158 项 JVM 测试。
- **体验与格式补强(v1.9.21)**:**深色模式三态切换**(跟随系统 / 明亮 / 深色,原生偏好持久化,首帧前即应用,防白屏闪烁);**WebView 启动预热**(Application 阶段提前初始化 Chromium 内核,缩短首屏白屏);**ClamAV `.hdb`/`.hdu`(MD5)整文件哈希格式支持**(与 SHA-256 同一次读盘计算,不增加 IO),内置自检演示库。共 160 项 JVM 测试。
- **大数据量健壮性(v1.9.22)**:**特征库内存兜底**(百万级官方库载入设条数预算 + 堆水位双闸门,超限截断并在更新结果亮出标记,防低内存设备 OOM);**应用锁长列表分页**(每页 200 行,列表尾「加载更多」按需追加,搜索过滤/数据刷新自动重置页码)。共 161 项 JVM 测试。

## 技术栈

- Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.7 / JDK 17
- minSdk 26,targetSdk 34
- ViewBinding + Material 3,MVVM(LiveData + Coroutines)
- Room(扫描历史)、EncryptedSharedPreferences(PIN 与锁定列表)
- KSP(Room 编译器)

## 工程结构

    SecureDroid/
    ├─ app/src/main/java/com/armorlab/securedroid/
    │  ├─ SecureGuardApp.kt          # Application:通知渠道初始化
    │  ├─ MainActivity.kt            # 主界面宿主(WebView 加载上传稿 HTML + 返回键逻辑)
    │  ├─ web/                       # WebView→原生 JS 桥(NativeBridge:首页/扫描/应用锁/审计/工具箱)
    │  ├─ data/                      # Room 实体 / DAO / 数据库
    │  ├─ scan/                      # 扫描引擎、特征库、扫描结果适配器
    │  ├─ permissions/               # 权限风险审计
    │  ├─ realtime/                  # 实时防护前台服务、开机自启
    │  ├─ lock/                      # 应用锁:安全存储、无障碍服务、锁屏
    │  ├─ trojan/                    # 木马查杀:ClamAV 签名兼容 / DEX 行为规则 / Rootkit 检测
    │  ├─ deep/                      # 深度查杀:进程内存 / 全设备目录 / 底层分区
    │  └─ ui/                        # 仪表盘 / 扫描 / 审计 / 应用锁 / 木马查杀 / 工具箱页面
    ├─ app/src/main/res/             # 布局、字符串、图标、无障碍配置
    ├─ app/src/main/assets/signatures/  # 内置演示签名(ClamAV .hsb/.ndb 格式)
    ├─ design-export/                # 界面结构导出(SVG/令牌/结构文档;不参与构建)
    ├─ app/build.gradle.kts
    └─ LICENSE                       # Apache-2.0 开源许可

## 界面结构导出(design-export/)

- `design-export/` 是从 `app/src/main/res` 反向导出的界面结构:6 个成品画板 + 19 个组件板的 SVG(浅色/深色各一套)、13 个图标 SVG、DTCG 令牌 JSON 与 CSV、结构化 JSON/Markdown,以及本地预览 `index.html`。
- 用途:把界面交给 Figma / Sketch / Axure / 即时设计 / Penpot 等工具继续设计、做标注或建组件库;不看图也可以直接读 `ui-structure.md`。
- 重新生成:`python design-export/export_ui.py`(仅 Python 标准库)。该目录不参与 APK 构建,CI 不运行,也不影响单元测试。
- 保真度:结构级线框 —— 颜色/尺寸/字号取真实令牌值,但文字宽度为估算值、列表内容与进度值为静态示意,详见 `design-export/README.md`。

## 构建步骤

只需 JDK 17(项目自带 Gradle Wrapper,无需预装 Gradle):

    # Linux / macOS
    ./gradlew assembleDebug

    # Windows
    gradlew.bat assembleDebug

    # 或使用便捷脚本(只回显错误行与构建结果)
    build.cmd

用 Android Studio 打开本目录亦可,等待 Gradle Sync 完成即可构建。
产物位于 app/build/outputs/apk/debug/app-debug.apk。

### 测试与验证

    ./gradlew testDebugUnitTest     # 158 项 JVM 单元测试(含 Robolectric 冒烟)
    ./gradlew assembleRelease       # R8 混淆 + 签名发布包

### 运行时冒烟测试(Robolectric)

在 JVM 上真实启动应用,不需要真机或模拟器:

| 测试套件 | 项数 | 校验内容 |
| --- | --- | --- |
| ApplicationSmokeTest | 2 | **应用能启动**:走完 Application.onCreate、通知渠道创建、定时任务同步 |
| LayoutInflationTest | 2 | **20 个布局全部可膨胀**(布局/主题/自定义属性问题当场暴露) |
| ActivityLaunchTest | 2 | **清单里全部 Activity 全部可拉起**(create → start → resume),含锁屏页无 PIN 自动结束 |
| DatabaseAndManagersTest | 6 | Room 建表读写往返 + 聚合查询(countAll / lastScannedAt)、提权层无 root 安全降级(环境无关不变量)、应用锁加密存储降级、完整性模块给出明确提示、开机广播安全无操作 |

### 静态与逻辑测试

| 测试套件 | 项数 | 校验内容 |
| --- | --- | --- |
| PrivilegedPolicyTest | 15 | 提权安全策略:灾害级命令必须拒绝、防护命令必须放行、拒绝理由可读 |
| FamilyClassifierTest | 9 | 木马家族分类:关键词命中稳定、混合信号结果确定 |
| ResourceReferenceTest | 7 | 资源引用完整性:@string/@drawable/@color/@xml/@mipmap/@id 全部可解析(此前 ic_tool 缺失类构建失败由此拦截) |
| ManifestInvariantsTest | 4 | 清单不变量:前台服务类型与权限一致、组件类真实存在、通知权限已声明 |
| ScannerEngineTest | 3 | SHA-256 十六进制编码格式正确 |
| TtlCacheTest | 7 | 通用 TTL 缓存:命中不重载、过期重载、同 key 并发只加载一次(单飞)、容量有界淘汰 |
| MultiPatternMatcherTest | 5 | 单遍多模式匹配与暴力实现逐模式等价(随机语料)、空模式语义、中文模式 |
| PackageSnapshotTest | 5 | 包列表/单包信息 TTL 复用同一快照、标签记忆化、非法 UID 与缺失包安全降级 |
| DexVerdictCacheTest | 5 | DEX 判定缓存:负缓存(`~clean`)落盘与重载、威胁名含 `|` 往返、空 SHA 不入缓存 |
| PerfGuardTest | 8 | 性能不变量守卫:并行池核心线程数、正则/时间格式化集中、PM 调用收口快照层、列表 `setHasFixedSize` |
| ShellHardeningTest | 9 | root 执行加固:退出码/失败标记才是成功判据、路径引号转义与逆运算、注入载荷只算一个参数、策略放行真实命令且拦截灾难命令、源码守卫(禁止 `runSu(...) != null` 与引号内直接插值) |
| ScanHistoryTest | 4 | 查杀历史落库:报告携带真实指纹/风险分、两个写入方不得再落空值、死代码 `execBatch` 已移除、强停命令包名走 `quote` |
| FalsePositiveTest | 10 | 检测误报回归:真实第三方 APK 字符串画像不得判为感染、纯子串不算命中、合成恶意样本仍必须命中、只有 MEDIUM+ 才计感染、锁机判定要求 `resetPassword`、扫描目标排除自己与信任列表、特征库不含通用魔数(dex/zip/elf)、自排除与词边界匹配的源码守卫 |

运行时验证已抓出并修复两个**致命缺陷**(编译、Lint、静态测试均无法发现):
WorkManager 未初始化导致启动即崩溃、`BottomNavigationView` 6 项超限导致主界面无法启动。详见 CHANGELOG。

> Robolectric 需要 `android-all` 运行库(约 150MB)。构建脚本会在项目内存在 `robolectric-deps/` 时走离线模式,
> 否则自动从可用镜像下载;该目录已 gitignore。

### 静态检查(Lint)

    ./gradlew lintDebug            # 错误级问题阻断构建

已启用 `abortOnError = true`:权限缺失、API 误用、清单不一致等**真实崩溃风险**会直接让构建失败。
初次启用即发现并修复真实缺陷(详见 CHANGELOG):振动反馈缺少 VIBRATE 权限、锁屏页弃用 API、
磁贴弃用 API。硬编码系统路径(`/data/local/tmp`、`/sdcard`)
与 `SystemProperties` 反射属**故意为之**(查杀必须使用真实系统路径,反射有 `getprop` 兜底),保留为警告。

CI:.github/workflows/android.yml 在每次 push / PR 上自动跑单元测试、Lint、构建 debug 包并上传产物。

## 界面外观(现状)

**主界面 = 上传稿直接渲染**:`deepseek_html_20261003_008012.html` 本身就是 UI —— `MainActivity` 用 WebView
加载 `assets/ui/index.html`(上传稿副本 + `assets/ui/app.js` 桥接脚本),首页 / 检测 / 防护三个板块、
悬浮胶囊导航、分段控件、进度条、开关与列表全部由该 HTML 承载,经 `web/NativeBridge`(window.AndroidBridge)
JS 桥接到真实原生功能;每个入口都是真实实现,不造空壳。纯 HTML 打开(无桥)时回退到演示数据。

- 三个板块与二级功能(与上传稿一一对应):

| 板块 | 二级功能 |
| --- | --- |
| **首页** | 问候 + 主状态卡(设备安全 / 上次扫描 / 立即扫描)+ 状态概览条(病毒库 · 已扫描 · 防护中)+ 2×2 功能宫格(病毒扫描 · 木马查杀 · 网络检测 · 应用锁)+ 最近活动 |
| **检测** | 病毒扫描(全盘)· 木马查杀(Rootkit / 恶意模块 / 锁机 / 深度查杀 / 病毒查杀中心) |
| **防护** | 应用锁(PIN · 锁定开关 · 假崩溃 · 无障碍)· 权限审计 · 工具箱(实时防护 / 开机自启 / Root 模式;安全设置:检查更新 · 关于;工具:网络审计 · 隐私检测 · 漏洞扫描) |

- 首页概览条与"最近活动"的数字全部来自**真实数据**(`getDashboard`):扫描记录数、最近扫描时间(Room)、
  待处理威胁、高风险权限应用数、生效防护项数(与偏好同一份);扫描进度与结果以事件推回 HTML 渲染
  (全盘病毒扫描 / 木马查杀 / Rootkit / 恶意模块 / 锁机检测都在后台线程跑真实引擎);
- 防护开关(实时防护 / 开机自启 / Root / 假崩溃)直接在 HTML 里切换并写同一份偏好
  (`settings` / `realtime_enabled` 等),覆盖安装不丢配置;`boot_enabled` 默认开启,关闭时同步禁用
  `BootReceiver` 组件,开机不再拉起核心组件;Root 开关开启前先探测 su,失败会把开关回弹;
- 深层原生页面保留,由 HTML 检测页与工具箱行拉起:病毒查杀中心 / 深度查杀 / 网络审计 / 隐私检测 /
  漏洞扫描 / 全量体检 / 清理;原生 Fragment 界面层保留在源码中(不再作为主界面);
- 返回键:非首页板块先回首页,首页再按退出(与上传稿底部导航一致);
- HTML 自带上传稿的内联样式(毛玻璃 / 柔和流动光晕 / `prefers-color-scheme` 深色模式),颜色与几何和
  原生令牌同源(主色 `#04BD19`、深色提亮等);`res/values` 与 `res/values-night` 令牌继续服务原生深层页、
  对话框与告警条;原生布局仍由 `DesignRuleTest` / `ColorContrastTest` 自动验收(触摸目标 / 令牌化 / 对比度)。

## 安全加固

参考 OWASP MASVS 与 Android 平台安全机制实施:

| 措施 | 位置 | 作用 |
| --- | --- | --- |
| `FLAG_SECURE` | 锁屏页 | 禁止截屏 / 录屏 / 最近任务缩略图,防止 PIN 泄露 |
| `filterTouchesWhenObscured` | 锁屏页根布局 | 阻止悬浮窗覆盖点击劫持(tapjacking)窃取 PIN |
| 禁止明文流量 | `res/xml/network_security_config.xml` | 全局 `cleartextTrafficPermitted=false`,仅信任系统 CA |
| 禁止备份 / 迁移 | `allowBackup=false` + `dataExtractionRules` | 敏感数据不进入云备份与设备迁移 |
| 特征库更新强制完整性 | `FeatureUpdater` | 仅接受 https(含重定向后)、SHA-256 **必填**、内容合理性校验 —— 防止特征库被投毒导致查杀失效 |
| 运行时防重打包 | `IntegrityGuard` + 主界面告警条 | 启动时比对安装包签名证书 SHA-256 与首次安装记录(TOFU),不一致或多签名者立即告警 —— 被改造过的"安全软件"本身就是最好的木马载体 |
| 提权命令策略 | `PrivilegedPolicy` | 灾害级命令(整根删除 / 格式化 / 写分区 / 恢复出厂)在提权层强制拦截并留痕审计 |

## ClamAV 特征库兼容性

格式实现依据 ClamAV 官方文档:
[Extended Signatures](https://docs.clamav.net/manual/Signatures/ExtendedSignatures.html) ·
[Hash Signatures](https://docs.clamav.net/manual/Signatures/HashSignatures.html)

| 文件 | 行格式 | 支持情况 |
| --- | --- | --- |
| `.hsb` / `.hsu` | `hash(64 hex):文件大小:名称` | 完整支持(大小为 0 表示忽略长度) |
| `.ndb` / `.ndu` | `名称:目标类型:偏移:HEX[:min_flevel[:max_flevel]]` | 支持;偏移语义:`*` 任意位置、`n` 绝对偏移、`EOF-n` 文件尾偏移、`n,MaxShift` 浮动区间;`EP+/Sx+/SL+`(仅 PE/ELF/Mach-O)安全退化为任意位置以免漏报 |
| `.hdb` / `.hdu` | `md5:大小:名称` | 支持:与 `.hsb` 同语义,按 MD5 整文件哈希匹配(扫描时与 SHA-256 同一次读盘计算) |

扫描性能:任意位置特征走**单遍扫描 + 2 字节窗口直接索引**(65536 直接表 + 链表,扫描期零分配),
每字节只做一次数组寻址,候选数约为单字节锚点分桶的 1/256;绝对偏移与 EOF 偏移特征按偏移直接定位,零扫描开销。

- 窗口取每条签名中最长"连续固定字节"片段的前 2 字节;只能取到 1 字节时走单字节索引,整条全通配时退化为逐位置校验
  —— 因此这次提速**不改变任何判定结果**;
- 等价性由 `SignatureMatchIndexTest` 用两种独立参考实现(旧版单字节锚点分桶 + 逐签名朴素扫描)
  在 4 MB 随机数据上交叉验证,三者输出集合必须完全一致;
- **实测数据**(200 条同锚点特征 × 1 MB,最不利于旧实现的构造):单字节锚点分桶 39.3 ms → 2 字节窗口索引 17.9 ms;
  典型数据下两者相当,但旧实现的单个桶会随特征条数线性变胖,新实现不会;
- **实测否决过一个"更高级"的方案**:先按教科书实现了 Aho-Corasick 自动机(CSR + 每字节二分查找 + 失效链回溯),
  在同一场景下反而是 127 ms(比单字节锚点分桶还慢 3.6 倍),于是换回直接索引。
  性能优化必须用数据说话 —— 该对比基准已固化进测试套件,防止以后凭直觉改回去。

> 历史兼容:早期版本使用的自定义分号格式 `名称;偏移;HEX;严重度;目标` 仍可解析,旧演示特征无需改动。

## 发布版签名与混淆

- release 已启用 R8 混淆与资源压缩(规则见 app/proguard-rules.pro)与资源裁剪;
- 签名配置读取项目根目录的 keystore.properties(已 gitignore),密钥生成方式:

      keytool -genkeypair -v -keystore <path>/securedroid-release.jks -alias securedroid -keyalg RSA -keysize 4096 -validity 10950

  然后创建项目根目录 keystore.properties:

      storeFile=<path>/securedroid-release.jks
      storePassword=******
      keyAlias=securedroid
      keyPassword=******

- 该文件缺失时 release 自动回退为未签名构建,保证 CI 与协作者无需密钥也能构建;
- 已产出的可安装签名包见 apks/SecureDroid-v1.9.16-release-signed.apk(APK Signature Scheme v2 + v3,RSA 4096):

    SHA-256 5B1D2795DEFC0C4A4E8AC4BD5B2E4060DC779DC095B3815C037B695AD47A276C
    大小    1,958,117 字节    versionCode 31 / versionName 1.9.16(初始化强制模式选择:标准/无线调试/超级用户 + 分区重排 + 系统栏适配)

  更早版本 apks/SecureDroid-v1.9.15-release-signed.apk(versionCode 30,全界面 HTML 化:消除全部原生 Activity/对话框,深层工具页改 HTML 子页面 + 异步桥路由微内核)、
  apks/SecureDroid-v1.9.13-release-signed.apk(versionCode 28,WebUI 交互升级:扫描可取消 + 结果按威胁折叠 + 开关整行可点 + 触摸目标达标)、
  apks/SecureDroid-v1.9.12-release-signed.apk(versionCode 27,UI 与 WebView 性能优化:加密存储单例 / 聚合查询 / 进度节流 / 图标懒加载)、
  apks/SecureDroid-v1.9.11-release-signed.apk(versionCode 26,UI 全面检查修复:Web 端交互/摘要卡/工具图标 + 原生页面沉浸式 + 尺寸令牌化)、
  apks/SecureDroid-v1.9.10-release-signed.apk(versionCode 25,基础体验优化:启动品牌 Splash 过渡 + 首屏骨架加载态 + 双击退出提示)、
  apks/SecureDroid-v1.9.9-release-signed.apk(versionCode 24,删除全部旧 Fragment 死代码 + 全界面文字溢出修复)、
  apks/SecureDroid-v1.9.8-release-signed.apk(versionCode 23,应用锁解锁页/完整性警示条/桌面小部件玻璃化)、
  apks/SecureDroid-v1.9.6-release-signed.apk(versionCode 21,原生工具页全面玻璃拟态化:顶部返回栏 + 副标题 + 状态卡 + 风险色条)、
  apks/SecureDroid-v1.9.5-release-signed.apk(versionCode 20,真实应用图标 + 扫描动画 + 触感反馈 + 结果计数)、
  apks/SecureDroid-v1.9.4-release-signed.apk(versionCode 19,字体排版优化:整级字号阶梯 25/22/16/15/14/13/12px、字距统一 0.01em、行高补齐 1.4–1.5、字体栈追加 HarmonyOS Sans SC/MiSans/OPPO Sans/Noto Sans SC)、
  apks/SecureDroid-v1.9.3-release-signed.apk(versionCode 18,UI 修复与打磨:安全区兜底、分段切换回顶、扫描按钮防连点、页签吸顶、隐藏滚动条/光晕、禁止文本选中、无障碍状态真实显示)、
  apks/SecureDroid-v1.9.2-release-signed.apk(versionCode 17,上传稿 UI 全部补完:头像=关于,最近活动可点直达,扫描/审计结果行可点开应用详情)、
  apks/SecureDroid-v1.9.1-release-signed.apk(versionCode 16,四大板块导航 + 系统栏沉浸)、
  apks/SecureDroid-v1.9.0-release-signed.apk(versionCode 15,HTML WebView UI 初版)以及
  v1.8.0 / v1.7.3 / v1.7.2 / v1.7.1 / v1.7.0 / v1.6.0 / v1.5.0 / v1.4.0 / v1.3.0 / v1.2.0 / v1.1.0 / v1.0.1 / v1.0.0 保留用于回退。

## 注意事项

- QUERY_ALL_PACKAGES:该权限不允许上架 Google Play(私有分发 / 企业侧载不受限),
  如需上架需改为 <queries> 白名单;
- 应用锁依赖无障碍服务,首次使用需引导用户在系统设置中开启;
- 特征库:内置演示特征仅用于自检。可在应用私有目录放置 signatures.txt
  (格式:hash|名称|级别0-3|描述)进行本地扩充;真实产品应接入
  自有云端特征下发通道(带签名校验),此处未包含以避免公开攻击面;
- 解锁后 60 秒内再次进入受保护应用不再要求 PIN(简单宽限窗口,
  生产版建议改为"灭屏即失效");
- 恶意模块 / SU 脚本检测需要读取 /data/adb(仅 root 可读):应用先尝试直接读取,
  失败后通过 su 执行**只读命令**(cat / ls / test),首次会弹出管理器授权;
  处置命令(禁用模块 touch disable / 删除恶意脚本 rm)在**手动模式下**需用户
  点击按钮并二次确认;**开启自动杀毒开关后**,守护循环对高危 / 严重项将自动
  执行处置并落库审计(auto_actions 表)与通知,轻微项仍只报告不处置;
- 自动卸载恶意应用使用 root 执行 pm uninstall --user 0,为独立开关,
  默认关闭;开启前请确认了解其影响;

## 木马查杀引擎与开源致谢

木马查杀模块借鉴了三个经典开源 Linux 安全软件的思路(只借鉴设计与文件格式,均未复用其代码,故与本项目 Apache-2.0 无许可证冲突):

| 借鉴来源 | 许可证 | 借鉴内容 | 本项目实现 |
| --- | --- | --- | --- |
| ClamAV | GPL(未复用代码,仅格式互操作) | 签名文件格式 | 兼容 .hsb(整文件 SHA-256)与 .ndb(十六进制字节特征,支持 ? 半字节通配),可直接加载 ClamAV 特征文件 |
| YARA | BSD-3(仅借鉴规则思想) | 多模式组合规则 | DEX 字符串级规则引擎,7 条内置规则(短信扣费 / 提权 / 反向 Shell / 勒索 / 动态加载 / 间谍 / 伪装),minHits 组合命中 |
| rkhunter / chkrootkit | GPL(仅借鉴检测项思路) | Rootkit 检查清单 | su 二进制路径、Magisk/KernelSU 指纹、/proc/mounts 覆盖挂载与可写 /system、SELinux 宽容模式、root 管理器应用 |
| KernelSU / APatch / SukiSU-Ultra | GPL(仅兼容模块规范,未复用代码) | 模块目录与启动脚本规范 | 扫描 /data/adb/modules、/data/adb/ksu/modules、/data/adb/ap/modules 及 service.d / post-fs-data.d 脚本;Shell 加权评分引擎识别恶意模块;利用各框架通用的 disable 文件机制一键禁用 |

### 更新特征库(接入 ClamAV 官方数据)

1. 从 ClamAV 官方下载 daily.cvd(CVD 为压缩归档,可用 7-Zip / tar 解包);
2. 取出其中的 .hsb / .ndb 文件;
3. 推入应用私有目录(需 debug 包或 root):

       adb push daily.hsb /data/data/com.armorlab.securedroid/files/clamav/
       adb push daily.ndb /data/data/com.armorlab.securedroid/files/clamav/

4. 重新进入"木马查杀"页点击扫描,特征即热加载。

> 说明:行为规则为字符串级启发式,可能存在误报;生产部署建议叠加云端多引擎与人工复核。

## 后续路线建议

- 云端特征库与增量更新(签名校验 + Certificate Pinning);
- 补齐 ClamAV 其余格式:`.mdb`(逻辑签名,表达式级匹配);
- atom 选择按字节频率择优(在真实特征库上进一步压低误命中率);
- 接入 Play Integrity 或服务端签名校验,弥补 TOFU 无法识别"直接安装盗版"的固有缺口;
- APK 深度静态分析(Smali 指令级、嵌入子 APK、证书链异常检测);
- 网络流量与 DNS 防护(VpnService);
- 反钓鱼短信 / 骚扰拦截模块;
- Crash 与安全事件上报(私有后端)。
