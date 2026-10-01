# SecureDroid 视觉设计系统 · 流体设计(Fluid Design)

![设计稿](mockup-sheet.png)

> 上图由 `design/render_mockup.py` 按本文件的令牌 1:1 渲染生成,**不是真机截图**。
> 它用于在没有模拟器/真机的环境下自检层级、间距、对比度、圆角与光效;
> 令牌改动后重跑脚本即可看到界面随之变化。

## 一、设计依据(三份来源,各有分工)

| 来源 | 取什么 | 不取什么 |
| --- | --- | --- |
| **ColorOS 17「流体设计」**(2026-09-17 ODC26 发布) | 三大元素:**凝光视效**(亮边与光晕)、**流体动效**(连贯缓出)、**柔性反馈**(可压缩的柔软材质感);界面更通透、光影更精细 | 不照搬壁纸/主题/AI 取色等系统级能力 —— 第三方应用无法控制这些 |
| **Apple HIG · 材质与动效** | "两层模型":玻璃只属于**功能层**(工具栏、入口条等浮在内容之上的控件);色彩克制,只为需要强调的元素上色;动效要短暂、精确、可被系统设置关闭 | 不照搬 iOS 的组件外形与命名 |
| **UI/UX Pro Max 规则库** | 硬性项:触摸目标 **≥48dp**、相邻点击区间距 **≥8dp**、正文对比度 **≥4.5:1**、不以 emoji 充当图标、状态不能只靠颜色表意 | 面向 Web 的断点/hover/CLS 等规则与原生 Android 无关 |

## 二、令牌(Token)

所有令牌集中在 `res/values`(浅色)与 `res/values-night`(深色),布局只引用令牌,不写字面值。

### 1. 色彩

| 令牌 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `sd_brand` | `#0B57D0` | `#4C8DFF` | 品牌与主行动(每屏只允许一个主行动用实色) |
| `sd_glow` | `#0FB5D6` | `#37D6FF` | 凝光青:进度、光球渐变末端 |
| `sd_bg` / `sd_bg_alt` | `#F3F7FD` / `#E6EFFB` | `#070B14` / `#0C1626` | 内容层渐变底 |
| `sd_bloom` / `sd_bloom_2` | 蓝 20% / 青 12% | 蓝 30% / 青 20% | 两处径向光晕(右上暖光、左下青光) |
| `sd_surface` | `#FFFFFF` | `#131C2E` | 内容卡片表面(不透明优先,保证正文对比度) |
| `sd_glass` | 白 95% | 白 8% | **功能层**玻璃(工具栏、底部入口条) |
| `sd_hairline` | 墨 12% | 白 18% | 1dp 光边(代替重投影) |
| `sd_text` / `sd_text_secondary` / `sd_text_tertiary` | `#0B1220` / `#51617C` / `#7A88A0` | `#EAF0FF` / `#A3B2CC` / `#7C8CA6` | 三级文字层级 |
| `sd_safe` / `sd_warn` / `sd_danger` | `#1B7F4B` / `#9A5B00` / `#C62828` | `#34D399` / `#FBBF24` / `#FF7A7A` | 语义状态,均满足白/深底上的 4.5:1 |

旧名 `primary`、`status_safe/risky/malicious` 保留为兼容别名(既有布局与适配器仍在引用)。

### 2. 尺寸与形状

| 令牌 | 值 | 说明 |
| --- | --- | --- |
| `sd_space_1..6` | 4 / 8 / 12 / 16 / 24 / 32 dp | 4dp 基准栅格 |
| `sd_gutter` | 20dp | 页面左右边距 |
| `sd_touch_min` | 48dp | 触摸目标下限(硬性) |
| `sd_radius_card` / `sd_radius_inner` / `sd_radius_chip` / `sd_radius_pill` | 28 / 18 / 14 / 100 dp | 卡片 / 内嵌块 / 图标砖与胶囊 / 全圆 |
| `sd_hairline_width` | 1dp | 光边宽度 |
| `sd_btn_height` | 52dp | 主按钮高度 |

### 3. 文字层级

`TextAppearance.SecureDroid.*`:`Display`(64sp,评分)、`Title`(20sp,页面标题)、
`Section`(13sp,加宽字距的分节标签)、`Body`(15sp,行高 +4dp)、`Label`(13sp,辅助信息)。

### 4. 动效

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `anim/sd_item_in.xml` | 淡入 + 上浮 10% + 0.98→1 缩放,300ms 级 `fast_out_slow_in` | 列表项入场 |
| `anim/sd_list_layout.xml` | 每项延迟 8% | 列表错峰(流体波) |
| `animator/sd_soft_press.xml` | 按下 0.97 / 110ms,回弹 1.0 / 240ms | 柔性反馈 |

页面转场交给系统默认,不自定义 —— 低端机上自定义转场是掉帧的主要来源。

## 三、组件规范

| 组件 | 样式 | 规则 |
| --- | --- | --- |
| 内容卡片 | `Widget.SecureDroid.Card` | 圆角 28dp,0dp 阴影 + 1dp 光边,按下有柔性缩放 |
| 功能层玻璃 | `Widget.SecureDroid.CardGlass`、`bg_glass_panel` | 半透明填充 + 光边 + 顶部高光;**只用于工具栏与底部入口条** |
| 主按钮 | `Widget.SecureDroid.Button` / `.Pill` | 52dp 高,实色品牌底,每屏至多一个 |
| 次按钮 | `Widget.SecureDroid.Button.Outlined` | 玻璃底 + 光边描边,文字用主文本色 |
| 列表行 | `item_*.xml` 卡片化 | 每行独立卡片,最小高度 48dp,行距 8dp |
| 状态胶囊 | `Widget.SecureDroid.Chip` + `bg_chip` | 中性底 + 语义色文字,不靠底色表意 |
| 进度条 | `progress_fluid` | 圆角轨道 + 品牌→凝光青渐变 |

## 四、可访问性自检(交付前逐条核对)

- [x] 触摸目标 ≥48dp(`sd_touch_min` 应用于按钮、开关、列表行)
- [x] 相邻点击区间距 ≥8dp(`sd_space_2` 用于网格与行距)
- [x] 正文对比度 ≥4.5:1(浅色 `#0B1220`/白 ≈ 17:1;深色 `#EAF0FF`/`#070B14` ≈ 16:1;次级文字两套均 ≥5:1)
- [x] 状态同时有文字与颜色(结果行既显示状态词,也用语义色)
- [x] 支持系统深色模式(`values-night` 全套令牌 + `Theme.Material3.DayNight`)
- [x] 图标一律使用矢量 drawable,不使用 emoji
- [x] 动效为系统动画时长缩放所控制(未硬编码无限动画),不阻断操作

## 五、重新生成设计稿

    # 需要 Python + Pillow,路径用本机 bundled python
    python design/render_mockup.py     # 输出 design/mockup-sheet.png

脚本内的色值与 `res/values/colors.xml`、`values-night/colors.xml` 手工保持一致;
改令牌时**两处都要改**,否则设计稿会与实现漂移(这是本文件唯一的已知维护成本)。
