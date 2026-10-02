# SecureDroid 界面结构(自动导出)

> 本文件由 `design-export/export_ui.py` 从 `app/src/main/res` 生成,生成时间 2026-10-02 15:16:52。信息源是布局 XML 与令牌 XML,不是截图。
> 重新生成:`python design-export/export_ui.py`。

## 0. 画布与命名约定

| 项 | 值 |
| --- | --- |
| 画布宽度 | 360dp(SVG viewBox 宽 360,width/height 放大 3 倍 = 1080 宽)|
| 屏幕高度 | 800dp;内容超出部分按真机滚动裁切 |
| 单位 | dp / sp 原值;1dp = 1 SVG 用户单位 |
| 图层命名 | 有 `android:id` 的取 id;没有的取「类型: 文本」|
| 进度类 | 分数环 72%、横向进度条 40%,是静态示意值 |
| 主题 | 同结构两套配色:light / night,见 tokens/design-tokens.json |

## 1. 信息架构

```
Theme.SecureDroid(activity_main.xml:FrameLayout + bg_page 渐变底)
├── container(FrameLayout,weight=1,paddingBottom sd_nav_clearance=72dp)
│   ├── 首页    DashboardFragment          fragment_dashboard.xml
│   ├── 检测    DetectFragment             fragment_detect.xml
│   │   ├── seg 0  病毒扫描   ScannerFragment          fragment_scanner.xml
│   │   └── seg 1  木马查杀   TrojanFragment           fragment_trojan.xml
│   └── 防护    ProtectFragment            fragment_protect.xml
│       ├── seg 0  应用锁     AppLockFragment          fragment_app_lock.xml
│       ├── seg 1  权限审计   PermissionAuditFragment  fragment_permission_audit.xml
│       └── seg 2  工具箱     ToolsFragment            fragment_tools.xml
└── bottomNav(TabLayout,贴底 4dp,高 60dp,圆角 24dp,左右外边距 6dp)
    menu/bottom_nav.xml:nav_status 首页 / nav_detect 检测 / nav_protect 防护
```

跨板块直达:MainActivity.navigateTo(itemId, segment) —— 首页快捷卡与桌面小组件走这条路。

不在底部三板块内的页面:

| 宿主 | 布局 | 说明 |
| --- | --- | --- |
| DeepScanActivity | `activity_deep_scan.xml` | 深度扫描进度 |
| LockActivity | `activity_lock.xml` | PIN 解锁键盘 |
| BaseListToolActivity | `activity_result_list.xml` | 通用结果列表 |
| VirusCenterActivity | `activity_virus_center.xml` | 病毒风险中心 |
| AppLockFragment:96 | `dialog_set_pin.xml` | 设置 PIN 对话框 |
| SecurityWidgetProvider | `widget_security.xml` | 桌面小组件(RemoteViews)|

列表项布局(RecyclerView item)与适配器一一对应:

| 适配器 | item 布局 |
| --- | --- |
| ScanAdapter | `item_scan_result.xml` |
| TrojanAdapter | `item_trojan.xml` |
| AppLockAdapter | `item_lock_app.xml` |
| PermissionAuditAdapter | `item_audit.xml` |
| ToolsAdapter / VirusActionAdapter | `item_tool.xml` |

## 2. 设计令牌

### 2.1 颜色(浅色 / 深色)

| 令牌 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `c_accent` | #15803D | #4ADE80 | 强调(深绿) |
| `c_background` | #F2F4EF | #0B0F0C | 窗口底色 |
| `c_bg_bottom` | #EFF0F0 | #0B0E0C | 渐变底 / 系统导航栏 |
| `c_bg_top` | #F3F8EE | #101711 | 渐变顶 |
| `c_border` | #DFE4DD | #2C332D | 描边 / 分隔线 |
| `c_card` | #FFFFFF | #171B18 | 卡片 |
| `c_destructive` | #D03B37 | #FF6B6B | 风险红 |
| `c_foreground` | #1A1A1A | #EDF2EC | 正文 |
| `c_gold` | #A16207 | #FBBF24 | 警告金 |
| `c_muted` | #E6EDE4 | #232823 | 次级底 / 涟漪 |
| `c_muted_foreground` | #6B6B6B | #A9B3A7 | 次级文字 / 图标 |
| `c_nav` | #FDFDFD | #191E1A | 悬浮导航 |
| `c_on_accent` | #FFFFFF | #052E16 | 强调上的文字 |
| `c_on_destructive` | #FFFFFF | #08120A | 风险红上的文字 |
| `c_on_primary` | #FFFFFF | #06210A | 主按钮文字 |
| `c_on_primary_container` | #06330B | #D8F4D9 | 容器浅绿上的文字 |
| `c_primary` | #04BD19 | #31D027 | 品牌绿 / 主按钮 |
| `c_primary_container` | #D8F4D9 | #1B3A20 | 容器浅绿 |
| `c_ring` | #31D027 | #31D027 | 分数环弧线 |
| `c_ring_disc` | #EAF4E8 | #16241A | 环内圆盘 |
| `c_ring_track` | #E4EDE2 | #22301F | 分数环底轨 |
| `c_success` | #15803D | #4ADE80 | 安全绿 |
| `ic_launcher_background` | #0B7A1C | #0B7A1C | 应用图标底色 |
| `sd_danger` | #D03B37 | #FF6B6B | 别名 → @color/c_destructive |
| `sd_hairline` | #DFE4DD | #2C332D | 别名 → @color/c_border |
| `sd_warn` | #A16207 | #FBBF24 | 别名 → @color/c_gold |
| `status_malicious` | #D03B37 | #FF6B6B | 别名 → @color/c_destructive |
| `status_risky` | #A16207 | #FBBF24 | 别名 → @color/c_gold |
| `status_safe` | #15803D | #4ADE80 | 别名 → @color/c_success |

### 2.2 尺寸与圆角

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `sd_btn_height` | 48dp | 通用按钮高 |
| `sd_cta_height` | 48dp | 主按钮高 |
| `sd_cta_inset` | 48dp | 主按钮左右内缩 |
| `sd_cta_top` | 16dp | 主按钮上边距 |
| `sd_divider_inset` | 56dp | 列表分隔线缩进 |
| `sd_grid_gap` | 10dp | 宫格间距 |
| `sd_grid_top` | 24dp | 宫格上边距 |
| `sd_gutter` | 16dp | 页面左右留白 |
| `sd_hairline_width` | 1dp | 分隔线粗细 |
| `sd_home_margin` | 6dp | 首页外边距 |
| `sd_home_title_top` | 8dp | 标题上边距 |
| `sd_nav_clearance` | 72dp | 内容为导航预留的高度 |
| `sd_nav_margin_bottom` | 4dp | 导航贴底边距 |
| `sd_nav_margin_h` | 6dp | 导航左右外边距 |
| `sd_nav_radius` | 24dp | 导航圆角 |
| `sd_radius_card` | 24dp | 卡片圆角 |
| `sd_radius_chip` | 12dp | Chip 圆角 |
| `sd_radius_inner` | 16dp | 内层圆角 |
| `sd_radius_pill` | 24dp | 胶囊(按钮/导航) |
| `sd_ring_disc` | 140dp | 环内圆盘直径 |
| `sd_ring_size` | 190dp | 分数环直径 |
| `sd_ring_stroke` | 25dp | 分数环描边 |
| `sd_ring_top` | 24dp | 环上边距 |
| `sd_row_height` | 56dp | 列表行高 |
| `sd_segment_hit` | 48dp | 分段控件触摸高 |
| `sd_space_1` | 4dp | 4dp 网格 |
| `sd_space_2` | 8dp | 8dp |
| `sd_space_3` | 12dp | 12dp |
| `sd_space_4` | 16dp | 16dp |
| `sd_space_5` | 24dp | 24dp |
| `sd_status_top` | 20dp | 状态文案上边距 |
| `sd_tabbar_height` | 60dp | 悬浮导航高 |
| `sd_tile_height` | 128dp | 宫格卡高 |
| `sd_tile_icon` | 26dp | 宫格图标 |
| `sd_tile_padding` | 16dp | 宫格卡内边距 |
| `sd_touch_min` | 48dp | 最小触摸目标 |

### 2.3 字阶(TextAppearance.SecureDroid.*)

| 样式 | 字号 | 粗细 | 字距 | 用在哪 |
| --- | --- | --- | --- | --- |
| `Body` | 16sp | regular | 0 | 正文 |
| `Caption` | 13sp | regular | 0 | 说明文字 |
| `Headline` | 18sp | bold | 0 | 卡片标题 |
| `Keypad` | 24sp | regular | 0 | PIN 键盘数字 |
| `Label` | 14sp | regular | 0 | 小标签 |
| `Nav` | 12sp | bold | 0 | 底部导航文字 |
| `Score` | 54sp | bold | -0.04 | 首页环内分数 |
| `Section` | 13sp | bold | 0.02 | 小节标签 |
| `Status` | 17sp | regular | 0 | 状态文案 |
| `Tile` | 15sp | regular | 0 | 宫格副标题 |
| `Title` | 24sp | bold | -0.01 | 页面标题 |

### 2.4 组件样式(Widget.SecureDroid.*)

| 样式 | 父样式 | 关键取值 |
| --- | --- | --- |
| `Widget.SecureDroid.Button` | `Widget.Material3.Button` | cornerRadius=@dimen/sd_radius_pill, backgroundTint=@color/c_primary, minHeight=@dimen/sd_cta_height, textSize=16sp, iconTint=@color/c_on_primary |
| `Widget.SecureDroid.Button.Accent` | `Widget.SecureDroid.Button` | cornerRadius=@dimen/sd_radius_pill, backgroundTint=@color/c_accent, minHeight=@dimen/sd_cta_height, textSize=16sp, iconTint=@color/c_on_accent |
| `Widget.SecureDroid.Button.Outlined` | `Widget.Material3.Button.OutlinedButton` | cornerRadius=@dimen/sd_radius_pill, minHeight=@dimen/sd_touch_min, iconTint=@color/c_foreground |
| `Widget.SecureDroid.Button.Row` | `Widget.Material3.Button.TextButton` | cornerRadius=0dp, minHeight=@dimen/sd_row_height, textSize=16sp, paddingStart=@dimen/sd_space_4, paddingEnd=44dp, rippleColor=@color/c_muted, iconTint=@color/c_muted_foreground |
| `Widget.SecureDroid.Card` | `Widget.Material3.CardView.Elevated` | cardBackgroundColor=@color/c_card, cardCornerRadius=@dimen/sd_radius_card, cardElevation=2dp, strokeWidth=0dp, rippleColor=@color/c_muted |
| `Widget.SecureDroid.Chip` | `—` | textSize=12sp, paddingStart=@dimen/sd_space_2, paddingEnd=@dimen/sd_space_2 |
| `Widget.SecureDroid.Divider` | `—` |  |
| `Widget.SecureDroid.Segment` | `Widget.Material3.Button.TextButton` | strokeWidth=0dp, cornerRadius=@dimen/sd_radius_inner, backgroundTint=@color/seg_bg, minHeight=@dimen/sd_segment_hit, textSize=14sp, rippleColor=@color/c_muted |
| `Widget.SecureDroid.Tile` | `Widget.SecureDroid.Card` | cardBackgroundColor=@color/c_card, cardCornerRadius=@dimen/sd_radius_card, cardElevation=2dp, strokeWidth=0dp, rippleColor=@color/c_muted |

## 3. 画板与结构树

每个画板下面列出全部图层:图层 id、类型、文本、以及它在 360dp 画布里的 x/y/w/h(单位 dp)。

### 01-home · 首页 · 设备状态

- 源文件:`app/src/main/res/layout/activity_main.xml`
- 画布:360 × 800 dp
- SVG:`screens/light/01-home.svg` / `screens/night/01-home.svg`
- 说明:默认板块:分数环 + 一键优化 + 四宫格入口

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvHomeTitle` | TextView | 安全中心 | 6 | 8 | 95.3 | 32.4 |
| `View` | View |  | 110 | 89.4 | 140 | 140 |
| `tvScore` | TextView | 100 | 137.6 | 113.5 | 84.8 | 72.9 |
| `tvScoreUnit` | TextView | 分 | 173 | 186.4 | 14 | 18.9 |
| `LinearLayout` | LinearLayout |  | 137.6 | 113.5 | 84.8 | 91.8 |
| `FrameLayout` | FrameLayout |  | 85 | 64.4 | 190 | 190 |
| `tvState` | TextView | 设备状态良好,未发现威胁 | 6 | 274.4 | 348 | 23 |
| `btnOptimize` | MaterialButton | 一键优化 | 54 | 313.4 | 252 | 48 |
| `ImageView` | ImageView |  | 22 | 401.4 | 26 | 26 |
| `TextView: 清理存储` | TextView | 清理存储 | 22 | 451.4 | 72 | 24.3 |
| `tvCleanSub` | TextView | 查看可释放空间 | 22 | 479.7 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 6 | 385.4 | 169 | 128 |
| `tileClean` | MaterialCardView |  | 6 | 385.4 | 169 | 128 |
| `ImageView` | ImageView |  | 201 | 401.4 | 26 | 26 |
| `TextView: 病毒风险` | TextView | 病毒风险 | 201 | 451.4 | 72 | 24.3 |
| `tvVirusSub` | TextView | 尚未扫描 | 201 | 479.7 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 185 | 385.4 | 169 | 128 |
| `tileVirus` | MaterialCardView |  | 185 | 385.4 | 169 | 128 |
| `LinearLayout` | LinearLayout |  | 6 | 385.4 | 348 | 128 |
| `ImageView` | ImageView |  | 22 | 539.4 | 26 | 26 |
| `TextView: 网络审计` | TextView | 网络审计 | 22 | 589.4 | 72 | 24.3 |
| `tvNetworkSub` | TextView | 查看网络连接 | 22 | 617.6 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 6 | 523.4 | 169 | 128 |
| `tileNetwork` | MaterialCardView |  | 6 | 523.4 | 169 | 128 |
| `ImageView` | ImageView |  | 201 | 539.4 | 26 | 26 |
| `TextView: 应用管理` | TextView | 应用管理 | 201 | 589.4 | 72 | 24.3 |
| `tvApplockSub` | TextView | 管理应用锁 | 201 | 617.6 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 185 | 523.4 | 169 | 128 |
| `tileApplock` | MaterialCardView |  | 185 | 523.4 | 169 | 128 |
| `LinearLayout` | LinearLayout |  | 6 | 523.4 | 348 | 128 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 728 |
| `ScrollView` | ScrollView |  | 0 | 0 | 360 | 728 |
| `container` | FrameLayout |  | 0 | 0 | 360 | 800 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 800 |
| `bottomNav` | TabLayout |  | 6 | 736 | 348 | 60 |
| `FrameLayout` | FrameLayout |  | 0 | 0 | 360 | 800 |

### 02-detect-virus · 检测 · 病毒扫描

- 源文件:`app/src/main/res/layout/activity_main.xml`
- 画布:360 × 800 dp
- SVG:`screens/light/02-detect-virus.svg` / `screens/night/02-detect-virus.svg`
- 说明:检测板块第 1 段:扫描入口与结果

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 检测` | TextView | 检测 | 16 | 16 | 47.8 | 32.4 |
| `segDetectVirus` | MaterialButton | 病毒扫描 | 18 | 66.4 | 162 | 48 |
| `segDetectTrojan` | MaterialButton | 木马查杀 | 180 | 66.4 | 162 | 48 |
| `segDetect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `btnStartScan` | MaterialButton | 开始全盘扫描 | 16 | 132.4 | 328 | 48 |
| `tvStatus` | TextView | 点击下方按钮开始扫描 | 16 | 212.4 | 328 | 18.9 |
| `rvResults` | RecyclerView |  | 16 | 247.3 | 328 | 480.7 |
| `MaterialCardView` | MaterialCardView |  | 16 | 247.3 | 328 | 480.7 |
| `LinearLayout` | LinearLayout |  | 16 | 132.4 | 328 | 595.6 |
| `detectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 595.6 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 728 |
| `container` | FrameLayout |  | 0 | 0 | 360 | 800 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 800 |
| `bottomNav` | TabLayout |  | 6 | 736 | 348 | 60 |
| `FrameLayout` | FrameLayout |  | 0 | 0 | 360 | 800 |

### 03-detect-trojan · 检测 · 木马查杀

- 源文件:`app/src/main/res/layout/activity_main.xml`
- 画布:360 × 800 dp
- SVG:`screens/light/03-detect-trojan.svg` / `screens/night/03-detect-trojan.svg`
- 说明:检测板块第 2 段:木马查杀与感染列表

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 检测` | TextView | 检测 | 16 | 16 | 47.8 | 32.4 |
| `segDetectVirus` | MaterialButton | 病毒扫描 | 18 | 66.4 | 162 | 48 |
| `segDetectTrojan` | MaterialButton | 木马查杀 | 180 | 66.4 | 162 | 48 |
| `segDetect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `btnTrojanScan` | MaterialButton | 开始木马查杀 | 16 | 132.4 | 328 | 48 |
| `tvStatus` | TextView | 点击开始,多引擎检测木马与后门 | 16 | 212.4 | 328 | 18.9 |
| `btnRootkit` | MaterialButton | Rootkit / 提权后门检测 | 16 | 247.3 | 328 | 56 |
| `ImageView` | ImageView |  | 308 | 265.3 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 16 | 247.3 | 328 | 56 |
| `View` | View |  | 72 | 303.3 | 272 | 1 |
| `btnModules` | MaterialButton | 恶意模块 / SU 脚本检测 | 16 | 304.3 | 328 | 56 |
| `ImageView` | ImageView |  | 308 | 322.3 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 16 | 304.3 | 328 | 56 |
| `View` | View |  | 72 | 360.3 | 272 | 1 |
| `btnLocker` | MaterialButton | 锁机软件检测 | 16 | 361.3 | 328 | 56 |
| `ImageView` | ImageView |  | 308 | 379.3 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 16 | 361.3 | 328 | 56 |
| `View` | View |  | 72 | 417.3 | 272 | 1 |
| `btnDeepScan` | MaterialButton | 深度查杀(内存 · 全盘 · 分区) | 16 | 418.3 | 328 | 56 |
| `ImageView` | ImageView |  | 308 | 436.3 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 16 | 418.3 | 328 | 56 |
| `View` | View |  | 72 | 474.3 | 272 | 1 |
| `btnVirusCenter` | MaterialButton | 病毒查杀中心 | 16 | 475.3 | 328 | 56 |
| `ImageView` | ImageView |  | 308 | 493.3 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 16 | 475.3 | 328 | 56 |
| `LinearLayout` | LinearLayout |  | 16 | 247.3 | 328 | 284 |
| `MaterialCardView` | MaterialCardView |  | 16 | 247.3 | 328 | 284 |
| `rvTrojan` | RecyclerView |  | 16 | 543.3 | 328 | 184.7 |
| `MaterialCardView` | MaterialCardView |  | 16 | 543.3 | 328 | 184.7 |
| `LinearLayout` | LinearLayout |  | 16 | 132.4 | 328 | 595.6 |
| `detectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 595.6 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 728 |
| `container` | FrameLayout |  | 0 | 0 | 360 | 800 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 800 |
| `bottomNav` | TabLayout |  | 6 | 736 | 348 | 60 |
| `FrameLayout` | FrameLayout |  | 0 | 0 | 360 | 800 |

### 04-protect-applock · 防护 · 应用锁

- 源文件:`app/src/main/res/layout/activity_main.xml`
- 画布:360 × 800 dp
- SVG:`screens/light/04-protect-applock.svg` / `screens/night/04-protect-applock.svg`
- 说明:防护板块第 1 段:应用锁列表与 PIN 设置入口

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 防护` | TextView | 防护 | 16 | 16 | 47.8 | 32.4 |
| `segProtectLock` | MaterialButton | 应用锁 | 18 | 66.4 | 108 | 48 |
| `segProtectAudit` | MaterialButton | 权限审计 | 126 | 66.4 | 108 | 48 |
| `segProtectTools` | MaterialButton | 工具箱 | 234 | 66.4 | 108 | 48 |
| `segProtect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `tvPinState` | TextView |  | 16 | 132.4 | 328 | 24 |
| `View` | View |  | 72 | 156.4 | 272 | 1 |
| `btnSetPin` | MaterialButton | 设置 / 修改 PIN 码 | 16 | 157.4 | 328 | 56 |
| `View` | View |  | 72 | 213.4 | 272 | 1 |
| `View` | View |  | 72 | 270.4 | 272 | 1 |
| `btnAccessibility` | MaterialButton | 开启无障碍权限(用于应用锁) | 16 | 271.4 | 328 | 56 |
| `LinearLayout` | LinearLayout |  | 16 | 132.4 | 328 | 195 |
| `MaterialCardView` | MaterialCardView |  | 16 | 132.4 | 328 | 195 |
| `rvLockApps` | RecyclerView |  | 16 | 343.4 | 328 | 384.6 |
| `MaterialCardView` | MaterialCardView |  | 16 | 343.4 | 328 | 384.6 |
| `LinearLayout` | LinearLayout |  | 16 | 132.4 | 328 | 595.6 |
| `protectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 595.6 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 728 |
| `container` | FrameLayout |  | 0 | 0 | 360 | 800 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 800 |
| `bottomNav` | TabLayout |  | 6 | 736 | 348 | 60 |
| `FrameLayout` | FrameLayout |  | 0 | 0 | 360 | 800 |

### 05-protect-audit · 防护 · 权限审计

- 源文件:`app/src/main/res/layout/activity_main.xml`
- 画布:360 × 800 dp
- SVG:`screens/light/05-protect-audit.svg` / `screens/night/05-protect-audit.svg`
- 说明:防护板块第 2 段:权限风险清单

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 防护` | TextView | 防护 | 16 | 16 | 47.8 | 32.4 |
| `segProtectLock` | MaterialButton | 应用锁 | 18 | 66.4 | 108 | 48 |
| `segProtectAudit` | MaterialButton | 权限审计 | 126 | 66.4 | 108 | 48 |
| `segProtectTools` | MaterialButton | 工具箱 | 234 | 66.4 | 108 | 48 |
| `segProtect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `TextView: 权限审计` | TextView | 权限审计 | 32 | 156.4 | 52.8 | 17.6 |
| `tvSummary` | TextView |  | 16 | 181.9 | 328 | 24 |
| `MaterialCardView` | MaterialCardView |  | 16 | 181.9 | 328 | 24 |
| `rvAudit` | RecyclerView |  | 16 | 221.9 | 328 | 506.1 |
| `MaterialCardView` | MaterialCardView |  | 16 | 221.9 | 328 | 506.1 |
| `LinearLayout` | LinearLayout |  | 16 | 132.4 | 328 | 595.6 |
| `protectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 595.6 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 728 |
| `container` | FrameLayout |  | 0 | 0 | 360 | 800 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 800 |
| `bottomNav` | TabLayout |  | 6 | 736 | 348 | 60 |
| `FrameLayout` | FrameLayout |  | 0 | 0 | 360 | 800 |

### 06-protect-tools · 防护 · 工具箱

- 源文件:`app/src/main/res/layout/activity_main.xml`
- 画布:360 × 800 dp
- SVG:`screens/light/06-protect-tools.svg` / `screens/night/06-protect-tools.svg`
- 说明:防护板块第 3 段:工具箱

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 防护` | TextView | 防护 | 16 | 16 | 47.8 | 32.4 |
| `segProtectLock` | MaterialButton | 应用锁 | 18 | 66.4 | 108 | 48 |
| `segProtectAudit` | MaterialButton | 权限审计 | 126 | 66.4 | 108 | 48 |
| `segProtectTools` | MaterialButton | 工具箱 | 234 | 66.4 | 108 | 48 |
| `segProtect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `TextView: 防护开关` | TextView | 防护开关 | 32 | 156.4 | 52.8 | 17.6 |
| `View` | View |  | 72 | 237.9 | 272 | 1 |
| `tvRootState` | TextView | Root 模式:未启用(开启下方开关将请求 su 授权) | 32 | 250.9 | 300.2 | 24.2 |
| `View` | View |  | 72 | 287.1 | 272 | 1 |
| `View` | View |  | 72 | 344.1 | 272 | 1 |
| `LinearLayout` | LinearLayout |  | 16 | 181.9 | 328 | 219.2 |
| `MaterialCardView` | MaterialCardView |  | 16 | 181.9 | 328 | 219.2 |
| `TextView: 安全设置` | TextView | 安全设置 | 32 | 425.1 | 52.8 | 17.6 |
| `View` | View |  | 72 | 506.7 | 272 | 1 |
| `LinearLayout` | LinearLayout |  | 16 | 450.7 | 328 | 113 |
| `MaterialCardView` | MaterialCardView |  | 16 | 450.7 | 328 | 113 |
| `TextView: 工具` | TextView | 工具 | 32 | 587.7 | 26.3 | 17.6 |
| `rvTools` | RecyclerView |  | 16 | 613.2 | 328 | 114.8 |
| `MaterialCardView` | MaterialCardView |  | 16 | 613.2 | 328 | 114.8 |
| `LinearLayout` | LinearLayout |  | 16 | 132.4 | 328 | 595.6 |
| `protectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 595.6 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 728 |
| `container` | FrameLayout |  | 0 | 0 | 360 | 800 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 800 |
| `bottomNav` | TabLayout |  | 6 | 736 | 348 | 60 |
| `FrameLayout` | FrameLayout |  | 0 | 0 | 360 | 800 |

### act-deep-scan · 独立页 · 深度扫描

- 源文件:`app/src/main/res/layout/activity_deep_scan.xml`
- 画布:360 × 290 dp
- SVG:`parts/light/act-deep-scan.svg` / `parts/night/act-deep-scan.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvTitle` | TextView |  | 16 | 0 | 328 | 16 |
| `btnStart` | MaterialButton | 开始深度查杀 | 16 | 16 | 328 | 48 |
| `tvPhase` | TextView | 三阶段:进程内存 → 全设备目录 → 底层分区。Root 模式下效果完整,耗时较长。 | 16 | 96 | 328 | 37.8 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 133.8 |
| `rvList` | RecyclerView |  | 16 | 149.8 | 328 | 124.2 |
| `MaterialCardView` | MaterialCardView |  | 16 | 149.8 | 328 | 124.2 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 290 |

### act-lock · 独立页 · 解锁(PIN 键盘)

- 源文件:`app/src/main/res/layout/activity_lock.xml`
- 画布:360 × 434 dp
- SVG:`parts/light/act-lock.svg` / `parts/night/act-lock.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `ImageView` | ImageView |  | 152 | 24 | 56 | 56 |
| `tvDots` | TextView | 请输入 PIN 码解锁 | 78 | 96 | 204 | 21.6 |
| `btn1` | MaterialButton | 1 | 28 | 145.6 | 96 | 64 |
| `btn2` | MaterialButton | 2 | 132 | 145.6 | 96 | 64 |
| `btn3` | MaterialButton | 3 | 236 | 145.6 | 96 | 64 |
| `LinearLayout` | LinearLayout |  | 24 | 141.6 | 312 | 72 |
| `btn4` | MaterialButton | 4 | 28 | 217.6 | 96 | 64 |
| `btn5` | MaterialButton | 5 | 132 | 217.6 | 96 | 64 |
| `btn6` | MaterialButton | 6 | 236 | 217.6 | 96 | 64 |
| `LinearLayout` | LinearLayout |  | 24 | 213.6 | 312 | 72 |
| `btn7` | MaterialButton | 7 | 28 | 289.6 | 96 | 64 |
| `btn8` | MaterialButton | 8 | 132 | 289.6 | 96 | 64 |
| `btn9` | MaterialButton | 9 | 236 | 289.6 | 96 | 64 |
| `LinearLayout` | LinearLayout |  | 24 | 285.6 | 312 | 72 |
| `Space` | Space |  | 24 | 357.6 | 98.7 | 1 |
| `btn0` | MaterialButton | 0 | 126.7 | 361.6 | 98.7 | 64 |
| `btnDel` | MaterialButton |  | 233.3 | 361.6 | 98.7 | 64 |
| `LinearLayout` | LinearLayout |  | 24 | 357.6 | 312 | 72 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 434 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 434 |

### act-result-list · 独立页 · 结果列表

- 源文件:`app/src/main/res/layout/activity_result_list.xml`
- 画布:360 × 208 dp
- SVG:`parts/light/act-result-list.svg` / `parts/night/act-result-list.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvTitle` | TextView |  | 0 | 0 | 360 | 32 |
| `rvList` | RecyclerView |  | 16 | 68 | 328 | 124 |
| `MaterialCardView` | MaterialCardView |  | 16 | 68 | 328 | 124 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 208 |

### act-virus-center · 独立页 · 病毒风险中心

- 源文件:`app/src/main/res/layout/activity_virus_center.xml`
- 画布:360 × 292 dp
- SVG:`parts/light/act-virus-center.svg` / `parts/night/act-virus-center.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvTitle` | TextView |  | 16 | 0 | 328 | 16 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 16 |
| `rvActions` | RecyclerView |  | 16 | 32 | 328 | 244 |
| `MaterialCardView` | MaterialCardView |  | 16 | 32 | 328 | 244 |
| `menuState` | LinearLayout |  | 16 | 32 | 328 | 244 |
| `FrameLayout` | FrameLayout |  | 16 | 32 | 328 | 244 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 292 |

### frag-dashboard · Fragment · 首页

- 源文件:`app/src/main/res/layout/fragment_dashboard.xml`
- 画布:360 × 655.5 dp
- SVG:`parts/light/frag-dashboard.svg` / `parts/night/frag-dashboard.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvHomeTitle` | TextView | 安全中心 | 6 | 8 | 95.3 | 32.4 |
| `View` | View |  | 110 | 89.4 | 140 | 140 |
| `tvScore` | TextView | 100 | 137.6 | 113.5 | 84.8 | 72.9 |
| `tvScoreUnit` | TextView | 分 | 173 | 186.4 | 14 | 18.9 |
| `LinearLayout` | LinearLayout |  | 137.6 | 113.5 | 84.8 | 91.8 |
| `FrameLayout` | FrameLayout |  | 85 | 64.4 | 190 | 190 |
| `tvState` | TextView | 设备状态良好,未发现威胁 | 6 | 274.4 | 348 | 23 |
| `btnOptimize` | MaterialButton | 一键优化 | 54 | 313.4 | 252 | 48 |
| `ImageView` | ImageView |  | 22 | 401.4 | 26 | 26 |
| `TextView: 清理存储` | TextView | 清理存储 | 22 | 451.4 | 72 | 24.3 |
| `tvCleanSub` | TextView | 查看可释放空间 | 22 | 479.7 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 6 | 385.4 | 169 | 128 |
| `tileClean` | MaterialCardView |  | 6 | 385.4 | 169 | 128 |
| `ImageView` | ImageView |  | 201 | 401.4 | 26 | 26 |
| `TextView: 病毒风险` | TextView | 病毒风险 | 201 | 451.4 | 72 | 24.3 |
| `tvVirusSub` | TextView | 尚未扫描 | 201 | 479.7 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 185 | 385.4 | 169 | 128 |
| `tileVirus` | MaterialCardView |  | 185 | 385.4 | 169 | 128 |
| `LinearLayout` | LinearLayout |  | 6 | 385.4 | 348 | 128 |
| `ImageView` | ImageView |  | 22 | 539.4 | 26 | 26 |
| `TextView: 网络审计` | TextView | 网络审计 | 22 | 589.4 | 72 | 24.3 |
| `tvNetworkSub` | TextView | 查看网络连接 | 22 | 617.6 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 6 | 523.4 | 169 | 128 |
| `tileNetwork` | MaterialCardView |  | 6 | 523.4 | 169 | 128 |
| `ImageView` | ImageView |  | 201 | 539.4 | 26 | 26 |
| `TextView: 应用管理` | TextView | 应用管理 | 201 | 589.4 | 72 | 24.3 |
| `tvApplockSub` | TextView | 管理应用锁 | 201 | 617.6 | 137 | 20.2 |
| `LinearLayout` | LinearLayout |  | 185 | 523.4 | 169 | 128 |
| `tileApplock` | MaterialCardView |  | 185 | 523.4 | 169 | 128 |
| `LinearLayout` | LinearLayout |  | 6 | 523.4 | 348 | 128 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 655.5 |
| `ScrollView` | ScrollView |  | 0 | 0 | 360 | 655.5 |

### frag-detect · Fragment · 检测(分段外壳)

- 源文件:`app/src/main/res/layout/fragment_detect.xml`
- 画布:360 × 192.5 dp
- SVG:`parts/light/frag-detect.svg` / `parts/night/frag-detect.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 检测` | TextView | 检测 | 16 | 16 | 47.8 | 32.4 |
| `segDetectVirus` | MaterialButton | 病毒扫描 | 18 | 66.4 | 162 | 48 |
| `segDetectTrojan` | MaterialButton | 木马查杀 | 180 | 66.4 | 162 | 48 |
| `segDetect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `detectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 60.1 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 192.5 |

### frag-protect · Fragment · 防护(分段外壳)

- 源文件:`app/src/main/res/layout/fragment_protect.xml`
- 画布:360 × 192.5 dp
- SVG:`parts/light/frag-protect.svg` / `parts/night/frag-protect.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 防护` | TextView | 防护 | 16 | 16 | 47.8 | 32.4 |
| `segProtectLock` | MaterialButton | 应用锁 | 18 | 66.4 | 108 | 48 |
| `segProtectAudit` | MaterialButton | 权限审计 | 126 | 66.4 | 108 | 48 |
| `segProtectTools` | MaterialButton | 工具箱 | 234 | 66.4 | 108 | 48 |
| `segProtect` | MaterialButtonToggleGroup |  | 18 | 66.4 | 324 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 64.4 | 328 | 52 |
| `protectContainer` | FrameLayout |  | 16 | 132.4 | 328 | 60.1 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 192.5 |

### frag-scanner · Fragment · 病毒扫描

- 源文件:`app/src/main/res/layout/fragment_scanner.xml`
- 画布:360 × 239 dp
- SVG:`parts/light/frag-scanner.svg` / `parts/night/frag-scanner.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `btnStartScan` | MaterialButton | 开始全盘扫描 | 0 | 0 | 360 | 48 |
| `tvStatus` | TextView | 点击下方按钮开始扫描 | 0 | 80 | 360 | 18.9 |
| `rvResults` | RecyclerView |  | 0 | 114.9 | 360 | 124.1 |
| `MaterialCardView` | MaterialCardView |  | 0 | 114.9 | 360 | 124.1 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 239 |

### frag-trojan · Fragment · 木马查杀

- 源文件:`app/src/main/res/layout/fragment_trojan.xml`
- 画布:360 × 535 dp
- SVG:`parts/light/frag-trojan.svg` / `parts/night/frag-trojan.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `btnTrojanScan` | MaterialButton | 开始木马查杀 | 0 | 0 | 360 | 48 |
| `tvStatus` | TextView | 点击开始,多引擎检测木马与后门 | 0 | 80 | 360 | 18.9 |
| `btnRootkit` | MaterialButton | Rootkit / 提权后门检测 | 0 | 114.9 | 360 | 56 |
| `ImageView` | ImageView |  | 324 | 132.9 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 0 | 114.9 | 360 | 56 |
| `View` | View |  | 56 | 170.9 | 304 | 1 |
| `btnModules` | MaterialButton | 恶意模块 / SU 脚本检测 | 0 | 171.9 | 360 | 56 |
| `ImageView` | ImageView |  | 324 | 189.9 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 0 | 171.9 | 360 | 56 |
| `View` | View |  | 56 | 227.9 | 304 | 1 |
| `btnLocker` | MaterialButton | 锁机软件检测 | 0 | 228.9 | 360 | 56 |
| `ImageView` | ImageView |  | 324 | 246.9 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 0 | 228.9 | 360 | 56 |
| `View` | View |  | 56 | 284.9 | 304 | 1 |
| `btnDeepScan` | MaterialButton | 深度查杀(内存 · 全盘 · 分区) | 0 | 285.9 | 360 | 56 |
| `ImageView` | ImageView |  | 324 | 303.9 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 0 | 285.9 | 360 | 56 |
| `View` | View |  | 56 | 341.9 | 304 | 1 |
| `btnVirusCenter` | MaterialButton | 病毒查杀中心 | 0 | 342.9 | 360 | 56 |
| `ImageView` | ImageView |  | 324 | 360.9 | 20 | 20 |
| `FrameLayout` | FrameLayout |  | 0 | 342.9 | 360 | 56 |
| `LinearLayout` | LinearLayout |  | 0 | 114.9 | 360 | 284 |
| `MaterialCardView` | MaterialCardView |  | 0 | 114.9 | 360 | 284 |
| `rvTrojan` | RecyclerView |  | 0 | 410.9 | 360 | 124.1 |
| `MaterialCardView` | MaterialCardView |  | 0 | 410.9 | 360 | 124.1 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 535 |

### frag-app-lock · Fragment · 应用锁

- 源文件:`app/src/main/res/layout/fragment_app_lock.xml`
- 画布:360 × 335 dp
- SVG:`parts/light/frag-app-lock.svg` / `parts/night/frag-app-lock.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvPinState` | TextView |  | 0 | 0 | 360 | 24 |
| `View` | View |  | 56 | 24 | 304 | 1 |
| `btnSetPin` | MaterialButton | 设置 / 修改 PIN 码 | 0 | 25 | 360 | 56 |
| `View` | View |  | 56 | 81 | 304 | 1 |
| `View` | View |  | 56 | 138 | 304 | 1 |
| `btnAccessibility` | MaterialButton | 开启无障碍权限(用于应用锁) | 0 | 139 | 360 | 56 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 195 |
| `MaterialCardView` | MaterialCardView |  | 0 | 0 | 360 | 195 |
| `rvLockApps` | RecyclerView |  | 0 | 211 | 360 | 124 |
| `MaterialCardView` | MaterialCardView |  | 0 | 211 | 360 | 124 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 335 |

### frag-audit · Fragment · 权限审计

- 源文件:`app/src/main/res/layout/fragment_permission_audit.xml`
- 画布:360 × 214 dp
- SVG:`parts/light/frag-audit.svg` / `parts/night/frag-audit.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 权限审计` | TextView | 权限审计 | 16 | 24 | 52.8 | 17.6 |
| `tvSummary` | TextView |  | 0 | 49.5 | 360 | 24 |
| `MaterialCardView` | MaterialCardView |  | 0 | 49.5 | 360 | 24 |
| `rvAudit` | RecyclerView |  | 0 | 89.5 | 360 | 124.4 |
| `MaterialCardView` | MaterialCardView |  | 0 | 89.5 | 360 | 124.4 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 214 |

### frag-tools · Fragment · 工具箱

- 源文件:`app/src/main/res/layout/fragment_tools.xml`
- 画布:360 × 605 dp
- SVG:`parts/light/frag-tools.svg` / `parts/night/frag-tools.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `TextView: 防护开关` | TextView | 防护开关 | 16 | 24 | 52.8 | 17.6 |
| `View` | View |  | 56 | 105.5 | 304 | 1 |
| `tvRootState` | TextView | Root 模式:未启用(开启下方开关将请求 su 授权) | 16 | 118.5 | 300.2 | 24.2 |
| `View` | View |  | 56 | 154.8 | 304 | 1 |
| `View` | View |  | 56 | 211.8 | 304 | 1 |
| `LinearLayout` | LinearLayout |  | 0 | 49.5 | 360 | 219.2 |
| `MaterialCardView` | MaterialCardView |  | 0 | 49.5 | 360 | 219.2 |
| `TextView: 安全设置` | TextView | 安全设置 | 16 | 292.8 | 52.8 | 17.6 |
| `View` | View |  | 56 | 374.3 | 304 | 1 |
| `LinearLayout` | LinearLayout |  | 0 | 318.3 | 360 | 113 |
| `MaterialCardView` | MaterialCardView |  | 0 | 318.3 | 360 | 113 |
| `TextView: 工具` | TextView | 工具 | 16 | 455.3 | 26.3 | 17.6 |
| `rvTools` | RecyclerView |  | 0 | 480.9 | 360 | 124.1 |
| `MaterialCardView` | MaterialCardView |  | 0 | 480.9 | 360 | 124.1 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 605 |

### dialog-set-pin · 对话框 · 设置 PIN

- 源文件:`app/src/main/res/layout/dialog_set_pin.xml`
- 画布:360 × 152 dp
- SVG:`parts/light/dialog-set-pin.svg` / `parts/night/dialog-set-pin.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `etPin` | TextInputEditText |  | 24 | 24 | 312 | 56 |
| `TextInputLayout` | TextInputLayout |  | 24 | 24 | 312 | 56 |
| `etPinConfirm` | TextInputEditText |  | 24 | 92 | 312 | 56 |
| `TextInputLayout` | TextInputLayout |  | 24 | 92 | 312 | 56 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 152 |

### widget-security · 桌面小组件 · 安全状态

- 源文件:`app/src/main/res/layout/widget_security.xml`
- 画布:360 × 124 dp
- SVG:`parts/light/widget-security.svg` / `parts/night/widget-security.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `widgetIcon` | ImageView |  | 16 | 42 | 40 | 40 |
| `widgetTitle` | TextView | 安卫安全助手 | 68 | 41.8 | 192 | 21.6 |
| `widgetScan` | TextView | 病毒扫描 | 68 | 63.4 | 56 | 18.9 |
| `LinearLayout` | LinearLayout |  | 68 | 41.8 | 192 | 40.5 |
| `widgetOpen` | TextView | 安卫安全助手 | 260 | 52.5 | 84 | 18.9 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 124 |

### item-audit · 列表项 · 权限审计行

- 源文件:`app/src/main/res/layout/item_audit.xml`
- 画布:360 × 124 dp
- SVG:`parts/light/item-audit.svg` / `parts/night/item-audit.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvAppName` | TextView |  | 16 | 61 | 0 | 0 |
| `tvPerms` | TextView |  | 16 | 63 | 0 | 0 |
| `LinearLayout` | LinearLayout |  | 16 | 61 | 0 | 2 |
| `tvScore` | TextView |  | 28 | 62 | 328 | 0 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 124 |

### item-lock-app · 列表项 · 应用锁行

- 源文件:`app/src/main/res/layout/item_lock_app.xml`
- 画布:360 × 124 dp
- SVG:`parts/light/item-lock-app.svg` / `parts/night/item-lock-app.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvName` | TextView |  | 16 | 62 | 284 | 0 |
| `tvPkg` | TextView |  | 16 | 62 | 284 | 0 |
| `LinearLayout` | LinearLayout |  | 16 | 62 | 284 | 0 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 124 |

### item-scan-result · 列表项 · 扫描结果行

- 源文件:`app/src/main/res/layout/item_scan_result.xml`
- 画布:360 × 124 dp
- SVG:`parts/light/item-scan-result.svg` / `parts/night/item-scan-result.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvAppName` | TextView |  | 16 | 12 | 328 | 0 |
| `tvPackage` | TextView |  | 16 | 12 | 328 | 0 |
| `tvStatus` | TextView |  | 16 | 14 | 328 | 0 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 124 |

### item-tool · 列表项 · 工具/动作行

- 源文件:`app/src/main/res/layout/item_tool.xml`
- 画布:360 × 124 dp
- SVG:`parts/light/item-tool.svg` / `parts/night/item-tool.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvTitle` | TextView |  | 16 | 12 | 328 | 0 |
| `tvSub` | TextView |  | 16 | 14 | 328 | 0 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 124 |

### item-trojan · 列表项 · 木马检测行

- 源文件:`app/src/main/res/layout/item_trojan.xml`
- 画布:360 × 124 dp
- SVG:`parts/light/item-trojan.svg` / `parts/night/item-trojan.svg`

| 图层 id | 类型 | 文本 | x | y | w | h |
| --- | --- | --- | --- | --- | --- | --- |
| `tvTitle` | TextView |  | 16 | 12 | 328 | 0 |
| `tvSub` | TextView |  | 16 | 12 | 328 | 0 |
| `tvDetail` | TextView |  | 16 | 14 | 328 | 0 |
| `tvSuggestion` | TextView |  | 16 | 22 | 328 | 0 |
| `btnUninstall` | MaterialButton | 立即卸载 | 16 | 30 | 64 | 48 |
| `btnFix` | MaterialButton | 执行 | 88 | 30 | 32 | 48 |
| `LinearLayout` | LinearLayout |  | 16 | 30 | 104 | 48 |
| `LinearLayout` | LinearLayout |  | 0 | 0 | 360 | 124 |

## 4. 图标(drawable/*.xml 里的 vector)

| 名称 | viewport | 路径数 | SVG |
| --- | --- | --- | --- |
| `ic_backspace` | 24×24 | 1 | `icons/ic_backspace.svg` |
| `ic_bug` | 24×24 | 6 | `icons/ic_bug.svg` |
| `ic_chevron` | 24×24 | 1 | `icons/ic_chevron.svg` |
| `ic_clean` | 24×24 | 2 | `icons/ic_clean.svg` |
| `ic_grid` | 24×24 | 4 | `icons/ic_grid.svg` |
| `ic_launcher_foreground` | 108×108 | 2 | `icons/ic_launcher_foreground.svg` |
| `ic_lock` | 24×24 | 2 | `icons/ic_lock.svg` |
| `ic_network` | 24×24 | 3 | `icons/ic_network.svg` |
| `ic_scan` | 24×24 | 2 | `icons/ic_scan.svg` |
| `ic_shield` | 24×24 | 2 | `icons/ic_shield.svg` |
| `ic_tab_detect` | 24×24 | 2 | `icons/ic_tab_detect.svg` |
| `ic_tab_protect` | 24×24 | 2 | `icons/ic_tab_protect.svg` |
| `ic_tab_status` | 24×24 | 1 | `icons/ic_tab_status.svg` |

## 5. 形状类 drawable(非 vector)

| drawable | 类型 | 说明 |
| --- | --- | --- |
| `bg_chip` | shape | shape · solid @color/c_muted · radius @dimen/sd_radius_chip |
| `bg_nav_bar` | shape | shape · solid @color/c_nav · radius @dimen/sd_nav_radius |
| `bg_page` | shape | shape · gradient @color/c_bg_top → @color/c_bg_bottom |
| `bg_ring_disc` | shape | shape · solid @color/c_ring_disc |
| `bg_segment_track` | shape | shape · solid @color/c_muted · radius @dimen/sd_radius_inner |
| `progress_fluid` | layer-list | layer-list |

