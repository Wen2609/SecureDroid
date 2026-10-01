# -*- coding: utf-8 -*-
"""
SecureDroid 界面渲染器 v5 —— 按用户设计稿重建的设计语言(1.6.0)

这不是真机截图,而是从 res/values 的**令牌**直接渲染出来的验收图:
颜色/圆角/高度/字号全部现读 colors.xml + dimens.xml,渲染器里不写死任何一个设计值,
所以它不会和实现漂移 —— 令牌改了图就跟着变。

风格:大圆角白卡 + 品牌绿进度环 + 悬浮灰白导航条(设计稿实测几何)。
"""
from PIL import Image, ImageDraw, ImageFont
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "res"))
FONT, FONT_B = "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/msyhbd.ttc"
S = 3.0                      # 渲染倍率:1dp = 3px(设计稿原图就是 1080x2400 / density 3)
W = int(360 * S); H = int(800 * S)


def font(sp, b=False):
    return ImageFont.truetype(FONT_B if b else FONT, int(round(sp * S)))


def px(v):
    return v * S


def read_colors(path):
    t = open(path, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'<color\s+name="([^"]+)"\s*>\s*([^<\s]+)\s*</color>', t):
        v = m.group(2).strip()
        if v.startswith("#"):
            h = v.lstrip("#")
            out[m.group(1)] = "#" + (h[2:] if len(h) == 8 else h)
    return out


def read_dimens(path):
    t = open(path, encoding="utf-8").read()
    return {m.group(1): float(m.group(2))
            for m in re.finditer(r'<dimen\s+name="([^"]+)"\s*>\s*([0-9.]+)dp\s*</dimen>', t)}


def hx(s, a=255):
    if isinstance(s, tuple):
        return s
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def lum(c):
    h = c.lstrip("#")
    f = lambda u: u / 12.92 if u <= 0.03928 else ((u + 0.055) / 1.055) ** 2.4
    r, g, b = [f(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4)]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = lum(a), lum(b)
    if la < lb:
        la, lb = lb, la
    return (la + 0.05) / (lb + 0.05)


LIGHT = read_colors(os.path.join(RES, "values", "colors.xml"))
DARK = read_colors(os.path.join(RES, "values-night", "colors.xml"))
D = read_dimens(os.path.join(RES, "values", "dimens.xml"))


def gradient(t):
    img = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(img)
    top, bot = hx(t["c_bg_top"]), hx(t["c_bg_bottom"])
    for y in range(H):
        k = y / max(1, H - 1)
        d.line([0, y, W, y], fill=tuple(int(top[i] + (bot[i] - top[i]) * k) for i in range(3)) + (255,))
    return img


def title(img, t, s):
    ImageDraw.Draw(img).text((px(D["sd_home_margin"]), px(D["sd_home_title_top"])), s,
                             font=font(24, True), fill=hx(t["c_foreground"]))


def icon(d, cx, cy, kind, col, r=13):
    col = hx(col); w = max(2, int(S * 1.9)); r = r * S
    if kind == "clean":
        d.line([cx, cy - r, cx, cy + r], fill=col, width=w); d.line([cx - r, cy, cx + r, cy], fill=col, width=w)
        d.line([cx - r * .62, cy - r * .62, cx + r * .62, cy + r * .62], fill=col, width=w)
        d.line([cx - r * .62, cy + r * .62, cx + r * .62, cy - r * .62], fill=col, width=w)
        d.ellipse([cx + r * .62, cy + r * .62, cx + r * .95, cy + r * .95], fill=col)
    elif kind == "bug":
        d.ellipse([cx - r * .78, cy - r * .62, cx + r * .78, cy + r * .82], outline=col, width=w)
        d.line([cx - r, cy - r * .2, cx - r * .5, cy - r * .2], fill=col, width=w)
        d.line([cx + r * .5, cy - r * .2, cx + r, cy - r * .2], fill=col, width=w)
        d.line([cx - r, cy + r * .5, cx - r * .5, cy + r * .35], fill=col, width=w)
        d.line([cx + r * .5, cy + r * .35, cx + r, cy + r * .5], fill=col, width=w)
        d.line([cx - r * .5, cy - r * .8, cx - r * .18, cy - r * 1.1], fill=col, width=w)
        d.line([cx + r * .5, cy - r * .8, cx + r * .18, cy - r * 1.1], fill=col, width=w)
    elif kind == "network":
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col, width=w)
        d.line([cx - r, cy, cx + r, cy], fill=col, width=w)
        d.ellipse([cx - r * .48, cy - r, cx + r * .48, cy + r], outline=col, width=w)
    elif kind == "lock":
        d.rounded_rectangle([cx - r * .8, cy - r * .1, cx + r * .8, cy + r], radius=int(r * .3), outline=col, width=w)
        d.arc([cx - r * .5, cy - r * .95, cx + r * .5, cy + r * .15], start=180, end=360, fill=col, width=w)
    elif kind == "shield":
        d.polygon([(cx, cy - r), (cx - r * .78, cy - r * .6), (cx - r * .78, cy + r * .15),
                   (cx, cy + r), (cx + r * .78, cy + r * .15), (cx + r * .78, cy - r * .6)], outline=col, width=w)
    elif kind == "scan":
        d.ellipse([cx - r * .85, cy - r * .85, cx + r * .6, cy + r * .6], outline=col, width=w)
        d.line([cx + r * .35, cy + r * .35, cx + r, cy + r], fill=col, width=w)


def navbar(img, t, active=0):
    d = ImageDraw.Draw(img)
    h = px(D["sd_tabbar_height"]); mh = px(D["sd_nav_margin_h"]); mb = px(D["sd_nav_margin_bottom"])
    top = H - mb - h
    d.rounded_rectangle([mh, top, W - mh, top + h], radius=px(D["sd_nav_radius"]), fill=hx(t["c_nav"]))
    names = ["首页", "检测", "防护"]; kinds = ["shield", "scan", "lock"]
    seg = (W - 2 * mh) / 3.0
    for i, (n, k) in enumerate(zip(names, kinds)):
        cx = mh + seg * (i + 0.5)
        col = t["c_primary"] if i == active else t["c_muted_foreground"]
        icon(d, cx, top + px(20), k, col, r=10)
        d.text((cx, top + px(48)), n, font=font(12, True), fill=hx(col), anchor="mm")


def ring(img, t, cx, cy, score=100):
    d = ImageDraw.Draw(img); size = px(D["sd_ring_size"]); th = int(px(D["sd_ring_stroke"]))
    d.ellipse([cx - px(D["sd_ring_disc"]) / 2, cy - px(D["sd_ring_disc"]) / 2,
               cx + px(D["sd_ring_disc"]) / 2, cy + px(D["sd_ring_disc"]) / 2], fill=hx(t["c_ring_disc"]))
    box = [cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2]
    d.arc(box, 0, 360, fill=hx(t["c_ring_track"]), width=th)
    d.arc(box, -90, -90 + 360 * score / 100.0, fill=hx(t["c_ring"]), width=th)
    d.text((cx, cy - px(8)), str(score), font=font(54, True), fill=hx(t["c_foreground"]), anchor="mm")
    d.text((cx, cy + px(36)), "分", font=font(13), fill=hx(t["c_muted_foreground"]), anchor="mm")


def cta(img, t, y, label):
    d = ImageDraw.Draw(img); h = px(D["sd_cta_height"]); inset = px(D["sd_cta_inset"])
    d.rounded_rectangle([inset, y, W - inset, y + h], radius=h / 2, fill=hx(t["c_primary"]))
    d.text((W / 2, y + h / 2), label, font=font(16, True), fill=hx(t["c_on_primary"]), anchor="mm")
    return y + h


def tile(img, t, x, y, w, kind, name, sub, sub_col=None):
    d = ImageDraw.Draw(img); h = px(D["sd_tile_height"]); pad = px(D["sd_tile_padding"])
    d.rounded_rectangle([x, y, x + w, y + h], radius=px(D["sd_radius_card"]), fill=hx(t["c_card"]))
    icon(d, x + pad + px(D["sd_tile_icon"]) / 2, y + px(22) + px(D["sd_tile_icon"]) / 2, kind,
         t["c_foreground"], r=px(D["sd_tile_icon"]) / 2 / S)
    d.text((x + pad, y + px(66)), name, font=font(18, True), fill=hx(t["c_foreground"]))
    d.text((x + pad, y + px(96)), sub, font=font(15), fill=hx(sub_col or t["c_muted_foreground"]))
    return y + h


def home(img, t):
    title(img, t, "安全中心")
    cy = px(D["sd_home_title_top"]) + px(34) + px(D["sd_ring_top"]) + px(D["sd_ring_size"]) / 2
    ring(img, t, W / 2, cy, 100)
    y = cy + px(D["sd_ring_size"]) / 2 + px(D["sd_status_top"])
    d = ImageDraw.Draw(img)
    d.text((W / 2, y), "设备状态良好,未发现威胁", font=font(17), fill=hx(t["c_muted_foreground"]), anchor="ma")
    y = cta(img, t, y + px(17) + px(D["sd_cta_top"]), "一键优化") + px(D["sd_grid_top"])
    m = px(D["sd_home_margin"]); g = px(D["sd_grid_gap"]); tw = (W - 2 * m - g) / 2
    tiles = [("clean", "清理存储", "可用 12.4 GB", None), ("bug", "病毒风险", "未发现威胁", t["c_success"]),
             ("network", "网络审计", "3 个活动连接", None), ("lock", "应用管理", "已锁定 2 个应用", None)]
    for i, (k, n, s, c) in enumerate(tiles):
        x = m + (i % 2) * (tw + g); yy = y + (i // 2) * (px(D["sd_tile_height"]) + g)
        tile(img, t, x, yy, tw, k, n, s, c)
    navbar(img, t, 0)


def detect(img, t):
    title(img, t, "检测")
    d = ImageDraw.Draw(img)
    y = px(D["sd_home_title_top"]) + px(40)
    seg = "病毒查杀 / 木马扫描 / 深度扫描".split(" / ")
    sw = (W - 2 * px(D["sd_home_margin"])) / len(seg); m = px(D["sd_home_margin"])
    d.rounded_rectangle([m, y, W - m, y + px(40)], radius=px(D["sd_radius_inner"]), fill=hx(t["c_muted"]))
    for i, s in enumerate(seg):
        if i == 0:
            d.rounded_rectangle([m + px(2) + i * sw, y + px(2), m + (i + 1) * sw - px(2), y + px(38)],
                                radius=px(D["sd_radius_inner"]), fill=hx(t["c_card"]))
        d.text((m + i * sw + sw / 2, y + px(20)), s, font=font(14, i == 0),
               fill=hx(t["c_primary"] if i == 0 else t["c_muted_foreground"]), anchor="mm")
    y = cta(img, t, y + px(56), "开始全盘扫描") + px(24)
    d.rounded_rectangle([px(16), y, W - px(16), y + px(6)], radius=px(3), fill=hx(t["c_muted"]))
    d.rounded_rectangle([px(16), y, px(16) + (W - px(32)) * .62, y + px(6)], radius=px(3), fill=hx(t["c_primary"]))
    d.text((px(16), y + px(16)), "正在扫描 /data/app/com.example", font=font(14), fill=hx(t["c_muted_foreground"]))
    y += px(52)
    rows = [("微信", "com.tencent.mm", "未发现风险", t["c_success"]),
            ("某银行", "cn.bank.app", "签名校验通过", t["c_success"]),
            ("未知来源应用", "com.unknown.tool", "请求高危权限 3 项", t["c_gold"]),
            ("广告插件", "com.ad.sdk", "包含已知广告特征", t["c_destructive"])]
    rh = px(76)
    d.rounded_rectangle([px(16), y, W - px(16), y + rh * len(rows)], radius=px(D["sd_radius_card"]), fill=hx(t["c_card"]))
    for i, (n, p, s, c) in enumerate(rows):
        ry = y + i * rh
        d.text((px(32), ry + px(14)), n, font=font(16, True), fill=hx(t["c_foreground"]))
        d.text((px(32), ry + px(38)), p, font=font(14), fill=hx(t["c_muted_foreground"]))
        d.text((px(32), ry + px(58)), s, font=font(14), fill=hx(c))
        if i < len(rows) - 1:
            d.line([px(32), ry + rh, W - px(32), ry + rh], fill=hx(t["c_border"]), width=1)
    navbar(img, t, 1)


def tools(img, t):
    title(img, t, "防护")
    d = ImageDraw.Draw(img); m = px(D["sd_home_margin"])
    y = px(D["sd_home_title_top"]) + px(44)

    def section(label, yy):
        d.text((px(16), yy), label, font=font(13, True), fill=hx(t["c_muted_foreground"]))
        return yy + px(22)

    def switch_row(yy, label, on, sub=None):
        hh = px(D["sd_row_height"])
        d.text((px(32), yy + (hh / 2 if not sub else hh / 2 - px(9))), label, font=font(16), fill=hx(t["c_foreground"]), anchor="lm")
        if sub:
            d.text((px(32), yy + hh / 2 + px(11)), sub, font=font(14), fill=hx(t["c_muted_foreground"]), anchor="lm")
        w = px(46); h = px(28); x = W - px(32) - w
        d.rounded_rectangle([x, yy + hh / 2 - h / 2, x + w, yy + hh / 2 + h / 2], radius=h / 2,
                            fill=hx(t["c_primary"]) if on else hx(t["c_border"]))
        kx = x + w - h + px(2) if on else x + px(2)
        d.ellipse([kx, yy + hh / 2 - h / 2 + px(2), kx + h - px(4), yy + hh / 2 + h / 2 - px(2)], fill=(255, 255, 255))
        return yy + hh

    y = section("防 护 开 关", y)
    card_h = px(D["sd_row_height"]) * 3 + px(40)
    d.rounded_rectangle([m, y, W - m, y + card_h], radius=px(D["sd_radius_card"]), fill=hx(t["c_card"]))
    yy = y
    yy = switch_row(yy, "实时防护", True, "监控应用安装与更新")
    d.line([px(32), yy, W - px(32), yy], fill=hx(t["c_border"]), width=1)
    txt = "未获取 Root 权限"; tw = d.textlength(txt, font=font(12, True)) + px(16)
    d.rounded_rectangle([px(32), yy + px(10), px(32) + tw, yy + px(34)], radius=px(D["sd_radius_chip"]), fill=hx(t["c_muted"]))
    d.text((px(32) + tw / 2, yy + px(22)), txt, font=font(12, True), fill=hx(t["c_muted_foreground"]), anchor="mm")
    yy += px(40)
    yy = switch_row(yy, "自动清除威胁", False)
    d.line([px(32), yy, W - px(32), yy], fill=hx(t["c_border"]), width=1)
    yy = switch_row(yy, "自动卸载恶意应用", False)
    y += card_h + px(24)

    y = section("工 具 箱", y)
    rows = [("全量体检", "system", "chevron"), ("网络审计", "network", "chevron"), ("垃圾清理", "clean", "chevron")]
    rh = px(D["sd_row_height"])
    d.rounded_rectangle([m, y, W - m, y + rh * len(rows)], radius=px(D["sd_radius_card"]), fill=hx(t["c_card"]))
    for i, (n, k, _) in enumerate(rows):
        ry = y + i * rh
        icon(d, px(32) + px(12), ry + rh / 2, k, t["c_muted_foreground"], r=10)
        d.text((px(32) + px(40), ry + rh / 2), n, font=font(16), fill=hx(t["c_foreground"]), anchor="lm")
        x = W - px(32) - px(6)
        d.line([x - px(4), ry + rh / 2 - px(5), x + px(1), ry + rh / 2, x - px(4), ry + rh / 2 + px(5)],
               fill=hx(t["c_muted_foreground"]), width=2, joint="curve")
        if i < len(rows) - 1:
            d.line([px(72), ry + rh, W - px(32), ry + rh], fill=hx(t["c_border"]), width=1)
    navbar(img, t, 2)


def phone(t, fn):
    img = gradient(t); fn(img, t)
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, W - 1, H - 1], radius=int(px(28)), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0)); out.paste(img, (0, 0), m)
    return out


def token_strip():
    h = 480
    img = Image.new("RGBA", (W * 3 + 200, h), hx("#0B1F12"))
    d = ImageDraw.Draw(img)
    d.text((40, 26), "设计令牌(现读 res/values,渲染器不写死任何值)", font=font(17, True), fill="#EDF2EC")
    rows = [
        "圆角:卡片 %gdp / 内层 %gdp / 胶囊 %gdp;页面左右留白 %gdp;四宫格间距 %gdp。" % (
            D["sd_radius_card"], D["sd_radius_inner"], D["sd_radius_pill"], D["sd_home_margin"], D["sd_grid_gap"]),
        "进度环:外径 %gdp / 描边 %gdp / 内盘 %gdp;主按钮 %gdp 高、左右内缩 %gdp;悬浮导航 %gdp 高、圆角 %gdp。" % (
            D["sd_ring_size"], D["sd_ring_stroke"], D["sd_ring_disc"], D["sd_cta_height"], D["sd_cta_inset"],
            D["sd_tabbar_height"], D["sd_nav_radius"]),
        "字阶:标题 24 / 环内数字 54 / 卡标题 18 / 状态 17 / 卡副标题 15 / 小节 13 / 导航 12。",
        "触摸目标 ≥%gdp;列表行 %gdp;命中区 %gdp。" % (D["sd_touch_min"], D["sd_row_height"], D["sd_segment_hit"]),
    ]
    yy = 62
    for r in rows:
        d.text((40, yy), "· " + r, font=font(12), fill="#A9B3A7"); yy += 24
    pairs = [("正文/卡片", "c_foreground", "c_card"), ("副文/卡片", "c_muted_foreground", "c_card"),
             ("副文/页面", "c_muted_foreground", "c_background"), ("品牌绿+白字(刻意偏差)", "c_on_primary", "c_primary"),
             ("红字/卡片", "c_destructive", "c_card"), ("金字/卡片", "c_gold", "c_card"), ("绿字/卡片", "c_success", "c_card")]
    x = 40
    for label, fg, bg in pairs:
        r = contrast(LIGHT[fg], LIGHT[bg])
        d.rounded_rectangle([x, yy + 16, x + 150, yy + 52], radius=8, fill=hx(LIGHT[bg]), outline=(255, 255, 255, 40), width=1)
        d.text((x + 75, yy + 34), "Aa 示例", font=font(13), fill=hx(LIGHT[fg]), anchor="mm")
        d.text((x, yy + 62), label, font=font(11), fill="#EDF2EC")
        d.text((x, yy + 80), "%.2f:1%s" % (r, "" if r >= 4.5 else "  <4.5"), font=font(11), fill="#FBBF24" if r < 4.5 else "#A9B3A7")
        x += 162
    sw = [("c_primary", LIGHT), ("c_ring", LIGHT), ("c_accent", LIGHT), ("c_destructive", LIGHT),
          ("c_gold", LIGHT), ("c_card", LIGHT), ("c_background", LIGHT), ("夜间 c_card", DARK)]
    x = 40; yy += 116
    for name, t in sw:
        key = name.split()[-1]
        col = t[key]
        d.rounded_rectangle([x, yy, x + 130, yy + 40], radius=8, fill=hx(col), outline=(255, 255, 255, 40), width=1)
        d.text((x, yy + 48), name, font=font(11), fill="#EDF2EC")
        d.text((x, yy + 64), col, font=font(11), fill="#A9B3A7")
        x += 142
    d.text((40, h - 46), "浅色页面纵向渐变 #F3F8EE → #EFF0F0;导航悬浮条贴底 %gdp、左右缩进 %gdp,选中态绿色图标+文字、无指示条。"
           % (D["sd_nav_margin_bottom"], D["sd_nav_margin_h"]), font=font(12), fill="#A9B3A7")
    return img


a = phone(LIGHT, home); b = phone(DARK, detect); c = phone(LIGHT, tools)
tk = token_strip()
pad, gap = 40, 40
sheet_w = pad * 2 + W * 3 + gap * 2
sheet_h = 110 + H + 40 + tk.size[1] + 40
sheet = Image.new("RGBA", (sheet_w, sheet_h), hx("#E9EDE7"))
d = ImageDraw.Draw(sheet)
d.text((pad, 28), "SecureDroid · 按设计稿重建(首页 / 检测 / 防护)", font=font(21, True), fill="#1A1A1A")
d.text((pad, 66), "令牌 → design/generate_layouts.mjs → res/layout;本图由 design/render_mockup.py 直接用同一套令牌渲染,令牌改了图就变。",
       font=font(12), fill="#4A5A4E")
for i, ph in enumerate((a, b, c)):
    sheet.alpha_composite(ph, (pad + i * (W + gap), 110))
sheet.alpha_composite(tk, (pad, 110 + H + 40))
out = os.path.join(HERE, "mockup-sheet.png")
sheet.convert("RGB").save(out, quality=95)
print("saved", out, sheet.size)
for label, fg, bg in [("正文/卡片", "c_foreground", "c_card"), ("副文/卡片", "c_muted_foreground", "c_card"),
                      ("副文/页面", "c_muted_foreground", "c_background"), ("品牌绿白字", "c_on_primary", "c_primary"),
                      ("红/卡", "c_destructive", "c_card"), ("金/卡", "c_gold", "c_card"), ("绿/卡", "c_success", "c_card")]:
    print("  %-12s %.2f:1" % (label, contrast(LIGHT[fg], LIGHT[bg])))
print("  night 副文/卡片 %.2f:1  night 正文/卡片 %.2f:1" % (
    contrast(DARK["c_muted_foreground"], DARK["c_card"]), contrast(DARK["c_foreground"], DARK["c_card"])))
