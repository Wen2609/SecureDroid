# 安卫安全助手(SecureDroid)

一款开源的 Android 安全应用,从零构建,使用 Kotlin + Jetpack
(Material 3 / MVVM / Room)开发。

> **开源许可:本项目基于 [Apache License 2.0](LICENSE) 开源。**
> 欢迎学习、修改与贡献;二次分发请保留版权与许可声明,商用需遵守许可证条款。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 病毒扫描 | 计算已安装 APK 的 SHA-256,与本地特征库精确比对;结合敏感权限权重输出风险评分;扫描结果落库(Room) |
| 权限审计 | 枚举全部应用,按敏感权限(短信、通讯录、定位、麦克风、安装包等)权重累加评分,分级展示 |
| 实时防护 | 前台服务监听应用安装 / 更新广播,新应用自动扫描,命中特征时发送高优先级告警通知;开机自启 |
| 应用锁 | 加密存储 PIN(仅存加盐 SHA-256);无障碍服务检测前台应用,受保护应用启动时弹出 PIN 锁屏 |
| 木马查杀 | 多引擎:ClamAV 兼容签名(.hsb/.ndb)+ DEX 行为规则(YARA 风格)+ rkhunter 式 Rootkit 检测;发现感染应用支持一键卸载引导 |
| 恶意模块防护 | 针对 KernelSU / APatch / SukiSU-Ultra / Magisk:扫描模块启动脚本与 su 开机脚本,加权评分判定恶意行为,支持一键禁用模块、删除恶意 su 脚本 |
| Root 即时检测 / 自动杀毒 | Root 模式下:Root 守护循环(开机即扫 + 每 5 分钟巡检)即时检测恶意模块与 su 脚本;命中高危 / 严重项自动禁用模块、删除恶意脚本;应用安装即检,恶意应用经 root 自动卸载;全部处置写入审计表(auto_actions)并发通知 |
| 防锁机软件 | 检测第三方设备管理员 + lockNow / resetPassword / wipeData 组合行为;Root 模式下守护循环即时检测,判定锁机木马即自动执行 dpm remove-active-admin 解除管理员并卸载;普通模式提供一键解除处置按钮 |
| 安全工具箱(20 项) | 一键全面体检 / 系统安全基线 9 项 / DNS 劫持检测(DoH 对比) / hosts 篡改检测 / 网络连接审计(UID 归属) / 剪贴板敏感信息检查 / 文件保险箱(AES-256-GCM + PBKDF2 60 万次迭代) / 文件粉碎器 / 应用缓存清理(root) / 应用冻结解冻(root) / APK 提取器(root) / 输入法安全审计 / SOS 紧急求助(短信+警报) / SIM 卡变更防盗告警 / 每日定时自动查杀 / 快捷设置磁贴 / 桌面小部件 / 应用锁防暴力破解 / 应用锁假崩溃诱骗 / 安全日志导出 |
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
    │  ├─ MainActivity.kt            # 底部导航宿主
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
    ├─ app/build.gradle.kts
    └─ LICENSE                       # Apache-2.0 开源许可

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

    ./gradlew testDebugUnitTest     # 38 项 JVM 单元测试
    ./gradlew assembleRelease       # R8 混淆 + 签名发布包

### 运行时冒烟测试(Robolectric)

在 JVM 上真实启动应用,不需要真机或模拟器:

| 测试套件 | 项数 | 校验内容 |
| --- | --- | --- |
| ApplicationSmokeTest | 2 | **应用能启动**:走完 Application.onCreate、通知渠道创建、定时任务同步 |
| LayoutInflationTest | 2 | **21 个布局全部可膨胀**(布局/主题/自定义属性问题当场暴露) |
| ActivityLaunchTest | 2 | **13 个 Activity 全部可拉起**(create → start → resume),含锁屏页无 PIN 自动结束 |
| DatabaseAndManagersTest | 4 | Room 建表读写往返、提权层无 root 安全降级、完整性模块给出明确提示、开机广播安全无操作 |

### 静态与逻辑测试

| 测试套件 | 项数 | 校验内容 |
| --- | --- | --- |
| PrivilegedPolicyTest | 15 | 提权安全策略:灾害级命令必须拒绝、防护命令必须放行、拒绝理由可读 |
| FamilyClassifierTest | 9 | 木马家族分类:关键词命中稳定、混合信号结果确定 |
| ResourceReferenceTest | 7 | 资源引用完整性:@string/@drawable/@color/@xml/@mipmap/@id 全部可解析(此前 ic_tool 缺失类构建失败由此拦截) |
| ManifestInvariantsTest | 4 | 清单不变量:前台服务类型与权限一致、组件类真实存在、通知权限已声明 |
| ScannerEngineTest | 3 | SHA-256 十六进制编码格式正确 |

运行时验证已抓出并修复两个**致命缺陷**(编译、Lint、静态测试均无法发现):
WorkManager 未初始化导致启动即崩溃、`BottomNavigationView` 6 项超限导致主界面无法启动。详见 CHANGELOG。

> Robolectric 需要 `android-all` 运行库(约 150MB)。构建脚本会在项目内存在 `robolectric-deps/` 时走离线模式,
> 否则自动从可用镜像下载;该目录已 gitignore。

### 静态检查(Lint)

    ./gradlew lintDebug            # 错误级问题阻断构建

已启用 `abortOnError = true`:权限缺失、API 误用、清单不一致等**真实崩溃风险**会直接让构建失败。
初次启用即发现并修复 4 个真实缺陷(详见 CHANGELOG):SOS 震动缺少 VIBRATE 权限、锁屏页弃用 API、
磁贴弃用 API、SMS 权限导致无电话硬件设备不可安装。硬编码系统路径(`/data/local/tmp`、`/sdcard`)
与 `SystemProperties` 反射属**故意为之**(查杀必须使用真实系统路径,反射有 `getprop` 兜底),保留为警告。

CI:.github/workflows/android.yml 在每次 push / PR 上自动跑单元测试、Lint、构建 debug 包并上传产物。

## 视觉设计系统(流体设计)

界面依据 **ColorOS 17「流体设计」**(凝光视效 / 流体动效 / 柔性反馈)、Apple HIG 的材质与动效规范、
以及 UI/UX Pro Max 规则库重做。完整令牌表、组件规范与可访问性自检见 [design/DESIGN.md](design/DESIGN.md)。

![设计稿](design/mockup-sheet.png)

> 上图由 `design/render_mockup.py` 按令牌 1:1 渲染生成,**不是真机截图**。

- 令牌集中在 `res/values`(浅色)与 `res/values-night`(深色):色板、4dp 栅格、圆角、五级文字层级;
- 「凝光」背景 = 线性渐变 + 两处径向光晕,零图片资源;卡片用 1dp 光边 + 极低阴影代替重投影;
- **玻璃只出现在功能层**(工具栏、底部入口条),内容层保持不透明表面以保证正文对比度 ≥4.5:1;
- 动效:列表错峰入场(每项延迟 8%)、按压缩放 0.97 / 110ms、回弹 240ms;页面转场交给系统,避免低端机掉帧;
- 触摸目标 ≥48dp、相邻点击区间距 ≥8dp、状态不靠颜色单独表意(有无障碍硬性项自检清单)。

## 安全加固

参考 OWASP MASVS 与 Android 平台安全机制实施:

| 措施 | 位置 | 作用 |
| --- | --- | --- |
| `FLAG_SECURE` | 锁屏页、文件保险箱 | 禁止截屏 / 录屏 / 最近任务缩略图,防止 PIN 与文件内容泄露 |
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
| `.hdb`(MD5)/ `.hdu` | `md5:大小:名称` | 不支持:本引擎按 SHA-256 计算文件指纹 |

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
- 已产出的可安装签名包见 apks/SecureDroid-v1.1.0-release-signed.apk(APK Signature Scheme v2 + v3,RSA 4096):

      SHA-256 25b859aef603aacdd03ba246abfd752eabfe1c7cd8eff9552497fd6590789ca2
      大小    1,980,745 字节    versionCode 3 / versionName 1.1.0(流体设计版)

  上一版 apks/SecureDroid-v1.0.0-release-signed.apk 保留用于回退。

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
- 补齐 ClamAV 其余格式:`.hdb` / `.hdu`(MD5 文件指纹)与 `.mdb`,当前引擎只计算 SHA-256;
- atom 选择按字节频率择优(在真实特征库上进一步压低误命中率);
- 接入 Play Integrity 或服务端签名校验,弥补 TOFU 无法识别"直接安装盗版"的固有缺口;
- APK 深度静态分析(Smali 指令级、嵌入子 APK、证书链异常检测);
- 网络流量与 DNS 防护(VpnService);
- 反钓鱼短信 / 骚扰拦截模块;
- Crash 与安全事件上报(私有后端)。
