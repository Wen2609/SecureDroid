# SecureDroid 界面结构导出(第三方设计软件可用)

本目录是 **从源码反向导出的界面结构**,不是设计稿,也不是真机截图。
信息源只有两个:`app/src/main/res/layout/*.xml` 与 `app/src/main/res/values*/`;
导出器是 `export_ui.py`,只用 Python 标准库,读取源码、只写本目录,不参与 APK 构建,CI 不运行。

## 1. 目录与产物

| 路径 | 内容 | 谁用 |
| --- | --- | --- |
| `screens/light/*.svg` `screens/night/*.svg` | 6 个成品画板(外壳 activity_main + 板块 Fragment 组合) | Figma / Sketch / Axure / Illustrator / 即时设计 / Penpot |
| `parts/light/*.svg` `parts/night/*.svg` | 19 个单独组件板:Activity / Fragment / 对话框 / 小组件 / 列表项 | 同上,做组件库用 |
| `icons/*.svg` | 13 个图标(vector drawable 逐个转出) | 组件库 / 图标资产 |
| `tokens/design-tokens.json` | W3C DTCG 令牌:颜色(浅色基准 + 深色覆盖)、尺寸、字阶、两个主题 | Tokens Studio / Penpot / Style Dictionary |
| `tokens/tokens.csv` | 同一批令牌的表格版 | Excel / 手改 / 不支持 JSON 的插件 |
| `ui-structure.json` | 结构化数据:信息架构 + 每个画板全部图层的 id/类型/文本/x/y/w/h | 脚本 / 二次加工 |
| `ui-structure.md` | 上面这份数据的人读版(含 IA 树、令牌表、每个画板的图层表) | 人 |
| `index.html` | 本地预览:6 + 19 个画板缩略图 + 深浅色切换 | 浏览器直接打开 |
| `manifest.json` | 清单:画板数、图层数、画布约定 | 自动化核对 |

## 2. 画布与坐标约定

- 画布宽 **360dp**(设计基准 1080px / 3x);屏幕高 800dp,超出部分按真机滚动裁切。
- 1dp = 1 SVG 用户单位;SVG 的 `width/height` 已放大 3 倍(1080 宽),导入后按 3x 处理。
- 每个图层是一个 `<g>`,带 `id`(源码里的 `android:id`,没有 id 的用「类型: 文本」)与 `data-name/data-type/data-x/data-y/data-w/data-h`。
  不带 id 的容器名按类型给出,`data-*` 只是备注,Figma 会保留 `id`/`data-name` 作为图层名。
- 颜色、圆角、描边、字号都取真实令牌值(浅色/深色两套);文字按 `TextAppearance.SecureDroid.*` 字号与字距渲染。

## 3. 导入指引

**Figma**:直接把 SVG 拖进画布(或 File → Place image)。每个 SVG 是一个 Frame,图层树与源码层级一致;需要组件化时对 `parts/` 里的图做成 Component。令牌:装 Tokens Studio 插件 → Import → 选 `tokens/design-tokens.json`,两个 Theme(Light/Night)对应 `core` 与 `core+night`。

**Sketch**:File → Open 选 SVG,或拖入;Sketch 会保留分组名,可直接当 Symbol 用。

**Axure / Illustrator / Inkscape**:直接打开 SVG(矢量,可继续编辑);令牌用 CSV 手工建样式。

**即时设计 / 摹客 / Penpot**:支持 SVG 导入;Penpot 也支持 DTCG 令牌文件。

**Excel / 表格**:用 `tokens/tokens.csv`(列:Token / Type / Light / Night / Android 资源 / 说明)。

没有提供 Figma Variables 或各家插件的私有 JSON/CSV 表头 —— 那些格式没有稳定公开规范,这里给的是 W3C DTCG 标准格式与朴素 CSV,如果你的插件要求别的表头,改表头即可,数据列是齐的。

## 4. 保真度声明(重要)

这是一份 **结构级线框**,可用尺寸和颜色对齐设计,但不是像素级复刻,也不代表运行时真实数据:

1. **文字宽度是估算**:中文按 1em、其余按 0.55em 累加换行,真机字体度量会有差异;换行位置以设备为准。
2. **列表内容是示意**:RecyclerView 画 3 个占位行(标注 item 布局名),不代表真实条目数与数据。
3. **进度值是静态示意**:分数环 72%、横向进度条 40%;真机首页分数默认显示 100(干净设备),不是设计稿上的 86。
4. **没有阴影/涟漪/动画**:Material 高度、按压涟漪、过渡动画都不在 SVG 里。
5. **字体是替代名**:SVG 里写的是 Noto Sans CJK SC → Source Han Sans SC → PingFang SC → Microsoft YaHei → Roboto 的候选链,没有嵌入字体文件。
6. **只画默认态**:错误/空态/按压态没有单独出图。分段控件按画板所属板块渲染选中态 —— 选中段取 `color/seg_bg` 的 `state_checked`(白底)、未选中段取 `color/seg_text` 的默认态(灰字);底部导航与开关同样按 `state_checked`/`state_selected` 选择器取色。
7. **画板是组合示意**:`screens/` 里把板块 Fragment 塞进 `container`、把二级 Fragment 塞进 `detectContainer`/`protectContainer`,对应代码里 `MainActivity.openTab` 与 `DetectFragment`/`ProtectFragment` 的 Fragment 替换。
8. **深色是同结构两套配色**,不是两套布局。

## 5. 重新生成

```
cd D:\DSH WORK\SecureDroid
python design-export\export_ui.py
```

无第三方依赖(仅 `xml.etree` / `json` / `math` 等标准库)。改源码后重跑即可覆盖本目录内容;本目录不影响 `build.cmd` 与单元测试,也不在 GitHub Actions 里跑。

重跑是确定性的:同一份源码重复导出,所有 SVG、令牌与图层数据字节一致;唯一会变的是 `index.html` / `manifest.json` / `ui-structure.json` / `ui-structure.md` 里的 `generatedAt` 生成时间戳。

## 6. 界面结构速览

- 底部 3 板块:`nav_status` 首页 / `nav_detect` 检测 / `nav_protect` 防护(旧版六平级入口已废弃)。
- 检测板块 2 段:病毒扫描(`fragment_scanner.xml`)、木马查杀(`fragment_trojan.xml`)。
- 防护板块 3 段:应用锁(`fragment_app_lock.xml`)、权限审计(`fragment_permission_audit.xml`)、工具箱(`fragment_tools.xml`)。
- 板块之外:深度扫描、解锁键盘、结果列表、病毒风险中心 4 个 Activity,设置 PIN 对话框,桌面小组件。
- 首页快捷卡与桌面小组件通过 `MainActivity.navigateTo(itemId, segment)` 跨板块直达。

细节以 `ui-structure.md` 与源码为准(源码是唯一事实源,本目录是它的投影)。
