# SecureDroid 设计系统 · 按用户设计稿重建(1.6.0)

![设计稿](mockup-sheet.png)

> 本图由 `design/render_mockup.py` **现读** `res/values/colors.xml`、`res/values-night/colors.xml`、`res/values/dimens.xml`
> 渲染而成(渲染器里不写死任何设计值),全部布局由 `design/generate_layouts.mjs` 从同一套令牌生成。
> 两者都不是真机截图 —— 本环境没有可用设备。
>
> 可**交互**的版本:浏览器打开 [design/mockup.html](mockup.html)(浅色)或 [design/mockup-dark.html](mockup-dark.html)(深色)。

## 一、来源:用户给定设计稿(唯一视觉基线)

设计稿文件:907×2018 JPEG,对应 1080×2400 @density 3(360×800dp),换算 1dp = 2.519px。
本模型无法直接看图,全部尺寸用 Pillow 逐像素/逐字簇探测得到(`_probe/probe_design.py` … `probe8.py`):

| 实测项 | 设计稿读数 | 落到的令牌 |
| --- | --- | --- |
| 页面标题 | 4 个 CJK 字,字宽 58px≈23dp,左缘 15px≈6dp | `sd_home_title_top` 8dp / 标题 24sp / `sd_home_margin` 6dp |
| 进度环 | 外径 190dp,描边 25dp,内盘 140dp,环底 #E4EDE2,环体 #31D027,内盘 #EAF4E8 | `sd_ring_size/stroke/disc`、`c_ring/c_ring_track/c_ring_disc` |
| 环内数字 | 字形高 41px≈16dp → 54sp;下方小字"分" | TextAppearance.SecureDroid.Score 54sp |
| 状态文案 | 10 簇(9 字 + 1 标点),字宽 39px≈15.5dp → 17–18sp,居中 | TextAppearance.SecureDroid.Status 17sp |
| 主按钮 | x137–804 / y844–965 = 48dp 高、左右内缩 48dp、全圆角,底色 #04BD19,白字 4 字 ≈16sp | `sd_cta_height/inset`、`c_primary/c_on_primary`、`sd_radius_pill` |
| 四宫格 | 外边距 5–6dp,列间距 9.5dp,行间距 10dp,卡高 124–133dp,圆角 24dp,阴影极淡 | `sd_home_margin/grid_gap/tile_height/radius_card` |
| 卡内 | 图标 24×21dp,距卡左 14.3dp、距卡顶 20.6dp;卡标题 4 字 ≈18sp;副标题 15sp | `sd_tile_icon/padding`、Headline 18sp、Tile 15sp |
| 底部导航 | 悬浮圆角条,高 60dp,贴底 3–4dp,标签 11–12sp,**选中态为品牌绿**,无指示条 | `sd_tabbar_height/nav_radius/nav_margin_*`、`color/tab_text.xml` |
| 页面底色 | 纵向渐变顶 #F3F8EE → 底 #EFF0F0 | `drawable/bg_page.xml`(`c_bg_top`→`c_bg_bottom`) |

技能(ui-ux-pro-max)不再是**风格**来源,但其中的**硬规则**继续有效并已被测试固化:
触摸目标 ≥48dp、不只靠颜色表意、多步流程必须有进度、卡片/图标同一风格族、底部标签 ≤5。

## 二、信息架构

| 板块 | 内容 |
| --- | --- |
| **首页** | 品牌绿评分环 + 状态文案 + "一键优化"主行动 + 2×2 功能宫格 |
| **检测** | 病毒查杀 · 木马查杀 · 深度扫描(板内分段) |
| **防护** | 应用锁 · 权限审计 · 工具箱(实时防护 / Root 开关 / 偏好 / 工具列表) |

设计稿的宫格与按钮**映射到真实功能**(不是装饰):

| 设计稿 | 落地 | 副标题数据来源 |
| --- | --- | --- |
| 一键优化 | `FullAuditActivity`(全量体检) | — |
| 清理存储 | `CleanerActivity` | `StatFs(filesDir).availableBytes`(免 root 的真实可用空间) |
| 病毒风险 | `VirusCenterActivity` | `scanRecordDao().threatCount()`,>0 时副标题转红 |
| 网络审计 | `NetworkAuditActivity` | `NetAudit.established()` 计数 |
| 应用管理 | 防护 → 应用锁分段 | `AppLockStore.lockedApps()` 计数 |

## 三、令牌

### 色彩(浅 / 深,取自设计稿实测值)

| 角色 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| primary / on-primary | `#04BD19` / `#FFFFFF` | `#31D027` / `#06210A` | 主按钮、选中态、进度环 |
| primary-container / on- | `#D8F4D9` / `#06330B` | `#1B3A20` / `#D8F4D9` | 品牌浅底(环内、标签) |
| accent / on-accent | `#15803D` / `#FFFFFF` | `#4ADE80` / `#052E16` | 次级强调 |
| background / foreground | `#F2F4EF` / `#1A1A1A` | `#0B0F0C` / `#EDF2EC` | 页面底 / 正文 |
| bg-top → bg-bottom | `#F3F8EE` → `#EFF0F0` | `#101711` → `#0B0E0C` | 页面纵向渐变 |
| card / nav | `#FFFFFF` / `#FDFDFD` | `#171B18` / `#191E1A` | 卡片 / 悬浮导航条 |
| muted / muted-foreground / border | `#E6EDE4` / `#6B6B6B` / `#DFE4DD` | `#232823` / `#A9B3A7` / `#2C332D` | 次级面 / 次级文字 / 分隔 |
| ring / ring-track / ring-disc | `#31D027` / `#E4EDE2` / `#EAF4E8` | `#31D027` / `#22301F` / `#16241A` | 评分环三件套 |
| success / gold / destructive | `#15803D` / `#A16207` / `#D03B37` | `#4ADE80` / `#FBBF24` / `#FF6B6B` | 安全 / 警示 / 危险 |

旧令牌名(`sd_*`、`status_*`、`ic_launcher_background`)保留为别名,不存在并行色板。

### 尺寸 / 字体

- 间距 4/8/12/16/24;页面左右留白 6(宫格)/16(内容);行高 56;触摸下限 48;分段命中 48;导航 60;
- 圆角:卡片 24、内层 16、标签 12、胶囊 = 高/2(24);环 190/25/140;按钮 48 高、左右内缩 48;
- 字阶:标题 24 / 环内数字 54 / 卡标题 18 / 状态 17 / 正文 16 / 卡副标题 15 / 小节 13 / 导航 12;
- 动效:列表错峰入场 250–350ms,按压涟漪,全部受系统动画时长缩放控制。

### 刻意记录的偏差(保真 vs 可读性)

| 项 | 设计稿 | 实现 | 原因 / 守卫 |
| --- | --- | --- | --- |
| 主按钮对比度 | 亮绿 #04BD19 + 白字 = **2.53:1**(低于 AA 4.5) | 原样保留 | 用户要求严格按设计稿;`ColorContrastTest` 把该配对下限**显式**记为 2.4:1,并由 `brandCtaContrastDeviationIsDocumented` 锁定在 2.4–4.4,一旦配色达标就失败以提醒撤销例外 |
| 副标题灰 | #828282 = 3.84:1 | 令牌改为 #6B6B6B = 5.33:1 | 灰色只是色值、不是结构;改用仍属同色系的深一档灰即可达 AA,深色主题 8.03:1 |
| 底部导航项数 | 图上测到 4 个图标簇 | 保留 3 项(首页/检测/防护) | 项目信息架构就是三板块,技能规则为 3–5 项;图中第 4 簇与第 3 簇间距异常,疑为同一条内的图标+文字簇 |
| 字体 | 图上为系统黑体 | Android 系统字体 | CJK 覆盖率优先,不内置字体 |
| 真机视觉验证 | — | **未做** | 环境无模拟器/真机;可自动化的部分全部写成测试 |

## 四、可复现:布局由令牌生成

`design/generate_layouts.mjs` 是 `app/src/main/res/layout/` 的**唯一写入方**,共 20 个布局:

    node design/generate_layouts.mjs     # 覆盖 20 个布局,输出 "generated 20 layouts from the design system"

CI(`.github/workflows/android.yml`)重跑该脚本并 `git diff --exit-code`,手改 XML 会被拦下;
`DesignRuleTest.everyLayoutIsGenerated` 在单测层再拦一次。
另有一条防回归规则 `dottedStylesDeclareExplicitParent`:带点号的样式名必须显式写 `parent`
(1.6.0 改版时把 `Widget.SecureDroid.Chip/Divider` 的 `parent=""` 丢了,aapt2 去找不存在的隐式父样式
`Widget.SecureDroid` 导致整包链接失败)。

## 五、可交互预览:HTML 视觉稿(不是截图)

`design/render_html.mjs` 与 PNG 渲染器同源:现读 `res/values`(浅)/ `res/values-night`(深)的
颜色 · 尺寸 · 文案,并解析 `res/color/*.xml` 选择器,渲染成**自包含单文件 HTML**
(内联 CSS + 内联 SVG 图标,零外部请求),浏览器直接打开即可:

    node design/render_html.mjs
    # -> design/mockup.html(浅色) design/mockup-dark.html(深色)

- 5 屏 360×800:首页(良好态 100 分 / 风险态 58 分)、检测、防护、工具箱;底部悬浮导航按所在板块高亮;
- 页脚三张表:8 组配对的**现算**对比度(主按钮 2.53:1 标为刻意偏差)、全部颜色令牌、全部尺寸令牌;
- **陷阱**:Android 的 `#AARRGGBB` 必须转成 CSS 的 `#RRGGBBAA`。直接写 8 位 hex 会被 CSS 当作
  带 25/255 透明度的颜色,整个预览会发粉 —— 第一版就是这样错的,渲染器里的 `cssHex()` 就是这道防线;
- 无头截图自检产物:`html-preview.png` / `html-preview-dark.png`。抽样像素(脚本 `_probe/probe_html.py`)
  确认品牌绿 亮 #04BD19 / 暗 #31D027、卡片 亮 #FFFFFF / 暗 #171B18、悬浮条 亮 #FDFDFD / 暗 #191E1A
  全部按令牌上色。

## 六、自检清单

- [x] 触摸目标 ≥48dp(按钮 48、行 56、分段 48、宫格卡 128)
- [x] 不只靠颜色表意:评分环有数字 + 状态文案;风险卡副标题变红且文案写明"N 个威胁待处理"
- [x] 深/浅两套令牌独立定义,`NightThemeTokenTest` 校验 13 个核心令牌覆盖 + ≥8 处字面值差异
- [x] 颜色与字号只来自令牌:布局里不允许出现字面色值 / 字面字号(`DesignRuleTest.layoutsUseTokensOnly`)
- [x] 底部导航 3 项(≤5),悬浮圆角条,选中态绿色图标 + 文字,无指示条
- [x] 渲染器与布局生成器共用同一套令牌,不会各说各话
- [x] HTML 视觉稿可直接打开(自包含、零外部请求),并与 PNG / 布局生成器共用同一套令牌
- [x] 可自动验收的部分已写成测试:`DesignRuleTest`、`ColorContrastTest`、`NightThemeTokenTest`、`ResourceReferenceTest`、smoke 组
- [ ] 真机 / 模拟器视觉验证 —— 环境无可用设备
- [ ] Lint 剩余警告为已知取舍(`SdCardPath`、`PrivateApi` 反射、`GradleDependency`、文案国际化)
