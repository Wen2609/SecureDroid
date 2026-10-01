# -*- coding: utf-8 -*-
"""
SecureDroid 设计稿渲染器 v2(三大板块:状态 / 检测 / 防护)

不是真机截图:它按 res/values 中的设计令牌 1:1 复刻界面,用于在没有模拟器/真机的环境下
自检层级、节奏、对比度与圆角。v2 的关键变化:
  - 顶层只有三个板块,二级功能收进分段控件;
  - 内容卡片靠色调分层(深色纯色调 / 浅色 1dp 阴影),不再每张卡描边;
  - 评分环是有意义的(环 = 分值),不是装饰。
"""
from PIL import Image, ImageDraw, ImageFont
import os

FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"

def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)

def hexc(s, alpha=255):
    s = s.lstrip("#")
    if len(s) == 6:
        return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), alpha)
    return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), int(s[6:8],16))

def vgrad(size, c1, c2):
    w, h = size
    img = Image.new("RGBA", size)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(c1[i] + (c2[i]-c1[i])*t) for i in range(4)))
    return img

def bloom(size, color, radius, cx, cy):
    layer = Image.new("RGBA", size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    for i in range(90, 0, -1):
        t = i / 90
        a = int(color[3] * ((1 - t) ** 2.0))
        if a <= 0: continue
        r = radius * t
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(color[0], color[1], color[2], a))
    return layer

DARK = dict(bg="#080C15", bg2="#0B1220", bloom="#2E7BFF", bloomA=0x33,
            surface="#121A2A", surface2="#182234", glass=(255,255,255,20),
            hairline=(255,255,255,38), track=(255,255,255,38), shadow=False,
            text="#EDF2FF", text2="#A9B7CE", text3="#8290A8",
            brand="#5B93FF", glow="#3FD8F5", onbrand="#06101F",
            safe="#3FD98F", warn="#FFC24B", danger="#FF8A8A")
LIGHT = dict(bg="#F4F7FC", bg2="#E9F0FA", bloom="#2F7BFF", bloomA=0x1F,
             surface="#FFFFFF", surface2="#F3F6FC", glass=(255,255,255,242),
             hairline=(11,18,32,20), track=(11,18,32,20), shadow=True,
             text="#0B1220", text2="#4C5C77", text3="#78879E",
             brand="#0B57D0", glow="#0FB5D6", onbrand="#FFFFFF",
             safe="#12734A", warn="#8A5200", danger="#B4232A")

W, H = 412, 915
GUT = 20
R_CARD, R_INNER, ROW_H = 26, 16, 56

def base(t):
    img = Image.new("RGBA", (W, H))
    img.alpha_composite(vgrad((W,H), hexc(t["bg"]), hexc(t["bg2"])))
    img.alpha_composite(bloom((W,H), hexc(t["bloom"], t["bloomA"]), 320, W-30, -40))
    return img

def title(img, t, text):
    d = ImageDraw.Draw(img)
    d.text((GUT, 34), text, font=font(22, True), fill=hexc(t["text"]))

def section(img, t, text, y):
    d = ImageDraw.Draw(img)
    d.text((GUT, y), text, font=font(12, True), fill=hexc(t["text3"]))
    return y + 20

def card(img, t, box, radius=R_CARD, fill=None):
    d = ImageDraw.Draw(img)
    color = fill if fill else t["surface"]
    if t["shadow"]:
        sh = Image.new("RGBA", img.size, (0,0,0,0))
        ImageDraw.Draw(sh).rounded_rectangle([box[0], box[1]+2, box[2], box[3]+3], radius=radius, fill=(11,18,32,18))
        img.alpha_composite(sh)
    d.rounded_rectangle(box, radius=radius, fill=hexc(color) if isinstance(color, str) else color)

def row(img, t, y, label, icon="shield", right=None, sub=None, h=ROW_H):
    """卡内一行:图标 + 文本(+ 右侧控件)"""
    d = ImageDraw.Draw(img)
    x = GUT + 16
    cy = y + h / 2
    cx = x + 12
    col = hexc(t["text2"])
    if icon == "shield":
        d.polygon([(cx, cy-9), (cx-7, cy-5.5), (cx-7, cy+1), (cx, cy+7), (cx+7, cy+1), (cx+7, cy-5.5)], outline=col, width=2)
    elif icon == "scan":
        d.ellipse([cx-8, cy-8, cx+8, cy+8], outline=col, width=2)
        d.line([cx+5, cy+5, cx+9, cy+9], fill=col, width=2)
    elif icon == "lock":
        d.rounded_rectangle([cx-8, cy-2, cx+8, cy+9], radius=3, outline=col, width=2)
        d.arc([cx-5, cy-9, cx+5, cy+1], start=180, end=360, fill=col, width=2)
    elif icon == "grid":
        for dx in (-8, 1):
            for dy in (-8, 1):
                d.rounded_rectangle([cx+dx, cy+dy, cx+dx+7, cy+dy+7], radius=2, outline=col, width=2)
    elif icon == "bug":
        d.ellipse([cx-7, cy-6, cx+7, cy+8], outline=col, width=2)
        d.line([cx, cy-6, cx, cy-10], fill=col, width=2)
    d.text((x + 32, cy), label, font=font(16), fill=hexc(t["text"]), anchor="lm")
    if sub:
        d.text((x + 32, cy + 20), sub, font=font(12), fill=hexc(t["text3"]), anchor="lm")
    if right == "chevron":
        d.line([W-GUT-28, cy-5, W-GUT-32, cy, W-GUT-28, cy+5], fill=hexc(t["text3"]), width=2, joint="curve")
    elif right == "switch_on" or right == "switch_off":
        on = right == "switch_on"
        sw, sh_ = 46, 28
        sx = W - GUT - 16 - sw
        d.rounded_rectangle([sx, cy-sh_/2, sx+sw, cy+sh_/2], radius=sh_/2,
                            fill=hexc(t["brand"]) if on else hexc(t["text3"], 70))
        knob = sx + (sw - sh_ + 2) if on else sx + 2
        d.ellipse([knob+1, cy-sh_/2+2, knob+sh_-1, cy+sh_/2-2], fill=hexc(t["onbrand"]) if on else hexc(t["surface"]))
    return y + h

def segment(img, t, y, labels, active=0):
    d = ImageDraw.Draw(img)
    h = 48
    n = len(labels)
    w = (W - 2*GUT - (n-1)*8) / n
    for i, lb in enumerate(labels):
        x = GUT + i * (w + 8)
        on = i == active
        d.rounded_rectangle([x, y, x+w, y+h], radius=R_INNER,
                            fill=hexc(t["brand"], 38) if on else None,
                            outline=hexc(t["brand"]) if on else t["hairline"], width=2 if on else 1)
        d.text((x+w/2, y+h/2), lb, font=font(14, on), fill=hexc(t["brand"]) if on else hexc(t["text2"]), anchor="mm")
    return y + h

def pill_button(img, t, y, label, icon="scan", h=52):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([GUT, y, W-GUT, y+h], radius=h/2, fill=hexc(t["brand"]))
    cx = W/2 - 42
    d.ellipse([cx-8, y+h/2-8, cx+8, y+h/2+8], outline=hexc(t["onbrand"]), width=2)
    d.text((W/2 + 14, y+h/2), label, font=font(16), fill=hexc(t["onbrand"]), anchor="mm")
    return y + h

def progress(img, t, y, ratio=0.62):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([GUT, y, W-GUT, y+10], radius=5, fill=t["track"] if isinstance(t["track"], tuple) else hexc(t["track"]))
    d.rounded_rectangle([GUT, y, GUT+(W-2*GUT)*ratio, y+10], radius=5, fill=hexc(t["brand"]))
    return y + 10

def ring(img, t, cy, score=100, color=None):
    d = ImageDraw.Draw(img)
    size, thick = 188, 8
    box = [W/2-size/2, cy-size/2, W/2+size/2, cy+size/2]
    d.arc(box, start=0, end=360, fill=t["track"] if isinstance(t["track"], tuple) else hexc(t["track"]), width=thick)
    d.arc(box, start=-90, end=-90+360*score/100.0, fill=hexc(color or t["safe"]), width=thick)
    d.text((W/2, cy-14), str(score), font=font(60, True), fill=hexc(t["text"]), anchor="mm")
    d.text((W/2, cy+34), "安全评分", font=font(12, True), fill=hexc(t["text3"]), anchor="mm")
    return cy + size/2

def chip(img, t, cy, text):
    d = ImageDraw.Draw(img)
    w = d.textlength(text, font=font(13)) + 28
    d.rounded_rectangle([W/2-w/2, cy-15, W/2+w/2, cy+15], radius=15,
                        fill=t["glass"] if isinstance(t["glass"], tuple) else hexc(t["glass"]),
                        outline=t["hairline"] if isinstance(t["hairline"], tuple) else hexc(t["hairline"]), width=1)
    d.text((W/2, cy), text, font=font(13), fill=hexc(t["text2"]), anchor="mm")
    return cy + 15

def tabbar(img, t, active=0):
    y0 = H - 68
    layer = Image.new("RGBA", img.size, (0,0,0,0))
    dl = ImageDraw.Draw(layer)
    dl.rounded_rectangle([0, y0, W, H+40], radius=26, fill=t["glass"], outline=t["hairline"], width=1)
    dl.rectangle([0, y0+26, W, H], fill=t["glass"])
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    names = ["状态", "检测", "防护"]
    for i, n in enumerate(names):
        cx = W/3*(i+0.5)
        col = t["brand"] if i == active else t["text3"]
        if i == active:
            d.rounded_rectangle([cx-16, y0+2, cx+16, y0+5], radius=2, fill=hexc(t["brand"]))
        if i == 0:
            d.polygon([(cx, y0+18), (cx-9, y0+23), (cx-9, y0+30), (cx, y0+36), (cx+9, y0+30), (cx+9, y0+23)], outline=hexc(col), width=2)
        elif i == 1:
            d.ellipse([cx-9, y0+19, cx+9, y0+37], outline=hexc(col), width=2)
            d.line([cx+5, y0+33, cx+9, y0+37], fill=hexc(col), width=2)
        else:
            d.rounded_rectangle([cx-9, y0+24, cx+9, y0+36], radius=3, outline=hexc(col), width=2)
            d.arc([cx-6, y0+18, cx+6, y0+30], start=180, end=360, fill=hexc(col), width=2)
        d.text((cx, y0+48), n, font=font(12, i == active), fill=hexc(col), anchor="mm")

def phone(draw_fn):
    img = base(draw_fn["t"])
    draw_fn["fn"](img, draw_fn["t"])
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, W-1, H-1], radius=34, fill=255)
    out = Image.new("RGBA", img.size, (0,0,0,0))
    out.paste(img, (0,0), m)
    return out

# ---------- 状态 ----------
def status(img, t):
    title(img, t, "状态")
    y = ring(img, t, 175, 100, t["safe"]) + 22
    d = ImageDraw.Draw(img)
    d.text((W/2, y), "设备状态良好,未发现风险项", font=font(16), fill=hexc(t["text2"]), anchor="mm")
    y = chip(img, t, y + 34, "未获取 Root 权限") + 34
    y = section(img, t, "快 速 进 入", y)
    box = [GUT, y, W-GUT, y + 8 + ROW_H*3]
    card(img, t, box)
    yy = y + 4
    yy = row(img, t, yy, "病毒扫描", "scan", "chevron")
    yy = row(img, t, yy, "权限审计", "grid", "chevron")
    yy = row(img, t, yy, "应用锁", "lock", "chevron")
    y = box[3] + 26
    y = section(img, t, "防 护 开 关", y)
    box = [GUT, y, W-GUT, y + 8 + ROW_H*3]
    card(img, t, box)
    yy = y + 4
    yy = row(img, t, yy, "实时防护", "shield", "switch_on")
    yy = row(img, t, yy, "自动清除威胁", "shield", "switch_on")
    yy = row(img, t, yy, "自动卸载恶意应用", "shield", "switch_off")
    tabbar(img, t, 0)

# ---------- 检测 ----------
def detect(img, t):
    title(img, t, "检测")
    y = segment(img, t, 60, ["病毒扫描", "木马查杀"], 0) + 20
    y = pill_button(img, t, y, "开始扫描") + 20
    y = progress(img, t, y, 0.62) + 16
    d = ImageDraw.Draw(img)
    d.text((GUT, y), "正在扫描 /data/app/com.example", font=font(13), fill=hexc(t["text2"]))
    y += 34
    rows = [("微信", "com.tencent.mm", "未发现风险", t["safe"]),
            ("某银行", "cn.bank.app", "签名校验通过", t["safe"]),
            ("未知来源应用", "com.unknown.tool", "请求高危权限 3 项", t["warn"]),
            ("广告插件", "com.ad.sdk", "包含已知广告特征", t["danger"])]
    for name, pkg, st, col in rows:
        box = [GUT, y, W-GUT, y+84]
        card(img, t, box, R_INNER)
        d = ImageDraw.Draw(img)
        d.text((GUT+16, y+24), name, font=font(16, True), fill=hexc(t["text"]))
        d.text((GUT+16, y+48), pkg, font=font(12), fill=hexc(t["text3"]))
        d.text((GUT+16, y+66), st, font=font(13), fill=hexc(col))
        y += 92
    tabbar(img, t, 1)

# ---------- 防护 ----------
def protect(img, t):
    title(img, t, "防护")
    y = segment(img, t, 60, ["应用锁", "权限审计", "工具箱"], 0) + 20
    box = [GUT, y, W-GUT, y + 8 + ROW_H*3]
    card(img, t, box)
    yy = y + 4
    yy = row(img, t, yy, "微信", "lock", "switch_on", sub="com.tencent.mm")
    yy = row(img, t, yy, "某银行", "lock", "switch_on", sub="cn.bank.app")
    yy = row(img, t, yy, "相册", "lock", "switch_off", sub="com.android.gallery")
    y = box[3] + 24
    y = section(img, t, "工 具 箱", y)
    box = [GUT, y, W-GUT, y + 8 + ROW_H*3]
    card(img, t, box)
    yy = y + 4
    yy = row(img, t, yy, "文件粉碎", "grid", "chevron")
    yy = row(img, t, yy, "加密保险箱", "lock", "chevron")
    yy = row(img, t, yy, "紧急求助", "shield", "chevron")
    tabbar(img, t, 2)

a = phone(dict(t=DARK, fn=status))
b = phone(dict(t=DARK, fn=detect))
c = phone(dict(t=LIGHT, fn=protect))

def tokens():
    h = 250
    w = W*3 + 160
    img = Image.new("RGBA", (w, h), hexc("#0B1220"))
    d = ImageDraw.Draw(img)
    d.text((40, 28), "设计令牌与规则(三大板块 · 流体设计 v2)", font=font(18, True), fill="#EDF2FF")
    rules = [
        "顶层 3 个入口(状态/检测/防护),二级功能收进分段控件 —— 决策成本从 6 选 1 降到 3 选 1。",
        "内容卡片靠色调分层:深色纯色块、浅色 1dp 阴影;描边只留给功能层(工具栏/入口条/分段)。",
        "正文 16sp / 行高 1.5;分节标签 12sp 加宽字距;触摸目标 ≥48dp 且相邻间距 ≥8dp。",
        "评分环 = 分值本身(不是装饰),颜色随状态切换;状态同时有文字,不靠颜色单独表意。",
        "动效:列表错峰入场(每项 8%)、按压缩放 0.97/110ms 回弹 240ms,遵循系统动画时长缩放。",
    ]
    yy = 62
    for r in rules:
        d.text((40, yy), "· " + r, font=font(12), fill="#A9B7CE")
        yy += 22
    sw = [("品牌", "#5B93FF"), ("凝光青", "#3FD8F5"), ("安全", "#3FD98F"), ("警示", "#FFC24B"),
          ("危险", "#FF8A8A"), ("表面(深)", "#121A2A"), ("底色(深)", "#080C15"), ("表面(浅)", "#FFFFFF")]
    x = 40
    for name, col in sw:
        d.rounded_rectangle([x, 186, x+120, 224], radius=12, fill=hexc(col), outline=(255,255,255,40), width=1)
        d.text((x, 230), name + " " + col, font=font(11), fill="#8290A8")
        x += 132
    return img

tk = tokens()
SH = 96 + H + 30 + 250 + 30
sheet = Image.new("RGBA", (W*3 + 160, SH), hexc("#05080F"))
d = ImageDraw.Draw(sheet)
d.text((40, 30), "SecureDroid · 流体设计 v2 — 三大板块(状态 / 检测 / 防护)", font=font(22, True), fill="#EDF2FF")
d.text((40, 62), "依据 ColorOS 17「流体设计」+ Apple HIG 材质与动效规范 + UI/UX Pro Max 规则库;令牌与 res/values 一一对应", font=font(12), fill="#A9B7CE")
sheet.alpha_composite(a, (40, 96))
sheet.alpha_composite(b, (W + 80, 96))
sheet.alpha_composite(c, (W*2 + 120, 96))
sheet.alpha_composite(tk, (40, 96 + H + 30))
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mockup-sheet.png")
sheet.convert("RGB").save(out, quality=95)
print("saved", out, sheet.size)
