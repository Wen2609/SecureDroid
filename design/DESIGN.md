# SecureDroid 设计系统 · Minimalism & Swiss Style

![设计稿](mockup-sheet.png)

> 设计稿由 `design/render_mockup.py` 按本文件令牌 1:1 渲染;`design/generate_layouts.mjs` 由同一套令牌**生成**全部布局。
> 两者都不是真机截图。

## 一、来源:ui-ux-pro-max 的解析结果(不是我的口味)

| 技能调用 | 返回 | 采纳方式 |
| --- | --- | --- |
| `--design-system "mobile security utility antivirus app dark protective" --variance 5 --motion 4 --density 5` | **Style: Minimalism & Swiss Style**(Clean, simple, spacious, functional, white space, high contrast, geometric, grid-based);**Pattern: Trust & Authority** | 定为唯一风格基线;分区靠留白(24/32dp),卡片靠 1dp 边框而不是阴影 |
| 同上 · Colors | primary `#0F172A` / accent `#0369A1` / background `#F8FAFC` / foreground `#020617` / card `#FFFFFF` / muted `#E8ECF1` / muted-fg `#475569` / border `#E2E8F0` / destructive `#DC2626` | 原样采纳为浅色令牌;accent 之外的颜色一律不引入 |
| 同上 · Key Effects | "Subtle hover 200-250ms, smooth transitions, **sharp shadows if any**, clear type hierarchy" | 卡片 0dp 阴影 + 1dp 边框;动效 200-320ms |
| `--domain color "dark mode surface elevation contrast"` | 深色表面层级 Background `#020617` / Card `#0E1223` / Muted `#1A1E2F` / Muted-fg `#94A3B8` / Border `#334155` | 原样采纳为深色令牌 |
| `--domain web "bottom navigation tab bar app section"` | Bottom tabs ≤5(Do: 3–5 tabs);Back behavior must be predictable | 沿用三大板块(状态/检测/防护) |
| `--domain ux "list row density information hierarchy scan"` | Color Only(High):不得只靠颜色表意 | 状态一律"颜色 + 文字"双通道(评分环 + 状态文案;结果行含状态词) |
| `--domain ux "loading feedback ... reduced motion"` | Reduced Motion(High):尊重系统动效设置;Progress Indicators:多步流程必须给进度 | 动效受系统动画时长缩放控制;扫描/查杀全程进度条 + 阶段文案 |
| 规则表 §1–§10(SKILL.md) | 触摸 ≥48dp、相邻 ≥8dp、正文 16px/1.5、对比度 ≥4.5:1、不用 emoji 当图标、图标同一风格 | 全部落到令牌与自检清单 |

### 明确记录的偏差(技能要求"先验证适配性再套用")

| 项 | 技能建议 | 实际做法 | 原因 |
| --- | --- | --- | --- |
| 字体 | IBM Plex Sans(Google Fonts) | Android 系统字体 | 界面是中文,CJK 覆盖率优先;内置西文字体会造成中英混排断层并增加包体 |
| 深色 accent | 返回值为 `#EF4444`(与 destructive 同色) | CTA 用 `#38BDF8` | 技能自身的规则禁止"同一颜色表达两种含义";`#EF4444` 在 #020617 上做 CTA 会与危险态混淆 |
| 行高 | Body 16px / 1.5 | 16sp + `lineSpacingExtra 8dp`(≈1.5) | Android 以额外行距表达行高 |

## 二、信息架构(沿用并保持)

| 板块 | 二级功能(板内分段控件) |
| --- | --- |
| **状态** | 评分环 + 快速入口 + 防护开关 |
| **检测** | 病毒扫描 · 木马查杀 |
| **防护** | 应用锁 · 权限审计 · 工具箱 |

## 三、令牌

### 色彩(浅 / 深)

| 角色 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| primary / on-primary | `#0F172A` / `#FFFFFF` | `#F8FAFC` / `#0F172A` | 主按钮、标题 |
| accent | `#0369A1` | `#38BDF8` | 唯一的强调色:进度、选中态、主行动 |
| background / foreground | `#F8FAFC` / `#020617` | `#020617` / `#F8FAFC` | 页面底 / 正文 |
| card / muted / border | `#FFFFFF` / `#E8ECF1` / `#E2E8F0` | `#0E1223` / `#1A1E2F` / `#334155` | 卡片 / 次级面 / 1dp 边框 |
| muted-foreground | `#475569` | `#94A3B8` | 次级文字 |
| success / gold / destructive | `#15803D` / `#A16207` / `#DC2626` | `#22C55E` / `#FBBF24` / `#EF4444` | 安全 / 警示 / 危险 |

旧令牌名(`sd_*`、`ios_*`、`status_*`)全部保留为**别名**指向上面这套语义色,因此整套界面共享一个色板,不存在并行体系。

### 尺寸 / 字体 / 动效

- 间距 4/8/12/16/24/32/48;页面边距 16;行高 56;触摸下限 48;标签栏 60;
- 圆角:卡片 12、控件 8、标签 6;
- 字体 6 级:Display 34 / Title 26 / Headline 20 / Body 16(行高 1.5)/ Label 14 / Section 12(大写 +0.08 字距);
- 动效:列表错峰入场 250–350ms 缓出;按压用涟漪反馈;全部受系统动画时长缩放控制。

## 四、可复现:布局由令牌生成

`design/generate_layouts.mjs` 是本次的落地方式 —— 17 个布局文件由脚本从令牌生成,而不是逐页手写:

    node design/generate_layouts.mjs     # 覆盖 app/src/main/res/layout/ 下的 17 个布局

好处是"设计令牌 → 界面"的映射是**可执行、可复查**的:改令牌或改分区结构,重跑脚本即可,
不会出现"某个页面忘了改"的漂移。代价是脚本与 XML 需保持同步(脚本是唯一写入方)。

## 五、自检清单(技能 §1–§10 + pro-rules)

- [x] 触摸目标 ≥48dp;相邻点击区 ≥8dp(行高 56dp,分段 44dp 可点高度)
- [x] 正文 16sp、行高约 1.5;次级文字两套主题对比度均 ≥4.5:1
- [x] 不只靠颜色表意(状态 = 颜色 + 文字)
- [x] 深/浅两套令牌独立定义,`NightThemeTokenTest` 校验覆盖与差异
- [x] 图标统一:内容区 2dp 线性、标签栏实心,两层分级;无 emoji 图标
- [x] 底部导航 3 项(≤5),无横向滚动,不存在禁用/隐藏项
- [x] 多步流程有进度条与阶段文案;动效尊重系统设置
- [ ] 真机 / 模拟器视觉验证 —— 环境无可用设备
- [ ] 未清理的历史令牌别名仍有 Lint UnusedResources 告警(见 CHANGELOG 已知项)
