# -*- coding: utf-8 -*-
"""
SecureDroid 设计稿渲染器 v3 —— Apple 风格(iOS 系统色 + Dynamic Type + inset grouped 列表)

不是真机截图:按 res/values 的令牌 1:1 复刻,用于自检层级/节奏/对比度/圆角。
v3 的关键变化:纯色分组底、10dp 分组卡、0.5dp 内缩分隔线、iOS 分段控件、iOS 绿色开关、
通栏标签栏(实心图标 + 11sp 标签,选中只用 systemBlue 着色、无指示条)。
"""
from PIL import Image, ImageDraw, ImageFont
import os

FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"

def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)

def hexc(s, alpha=255):
    if isinstance(s, tuple):
        return s
    s = s.lstrip("#")
    if len(s) == 6:
        return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), alpha)
    return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), int(s[6:8],16))

# iOS 系统色(Large 档)
LIGHT = dict(bg="#F2F2F7", card="#FFFFFF", label="#000000", label2=(60,60,67,153), label3=(60,60,67,77),
             sep=(60,60,67,73), blue="#007AFF", green="#34C759", orange="#FF9500", red="#FF3B30",
             gray5="#E5E5EA", gray4="#D1D1D6", fill=(118,118,128,31), seg="#FFFFFF",
             tabbar=(249,249,249,242), dark=False)
DARK = dict(bg="#000000", card="#1C1C1E", label="#FFFFFF", label2=(235,235,245,153), label3=(235,235,245,77),
            sep=(84,84,88,166), blue="#0A84FF", green="#30D158", orange="#FF9F0A", red="#FF453A",
            gray5="#38383A", gray4="#3A3A3C", fill=(118,118,128,61), seg="#636366",
            tabbar=(22,22,26,242), dark=True)

W, H = 412, 915
GUT, R_CARD, ROW_H = 16, 10, 48

def base(t):
    return Image.new("RGBA", (W, H), hexc(t["bg"]))

def large_title(img, t, text):
    ImageDraw.Draw(img).text((GUT, 30), text, font=font(34, True), fill=hexc(t["label"]))

def group_header(img, t, text, y):
    ImageDraw.Draw(img).text((GUT + 16, y), text, font=font(13), fill=hexc(t["label2"]))
    return y + 22

def group(img, t, y, rows_h):
    d = ImageDraw.Draw(img)
    box = [GUT, y, W - GUT, y + rows_h]
    d.rounded_rectangle(box, radius=R_CARD, fill=hexc(t["card"]))
    return box

def sep(img, t, y, inset=52):
    d = ImageDraw.Draw(img)
    d.line([GUT + inset, y, W - GUT, y], fill=t["sep"], width=1)

def icon(d, cx, cy, kind, col, filled=True):
    if kind == "scan":
        d.ellipse([cx-8, cy-8, cx+8, cy+8], outline=col, width=2)
        d.line([cx+5.5, cy+5.5, cx+9, cy+9], fill=col, width=2)
    elif kind == "grid":
        for dx in (-8, 1):
            for dy in (-8, 1):
                d.rounded_rectangle([cx+dx, cy+dy, cx+dx+7, cy+dy+7], radius=2, outline=col, width=2)
    elif kind == "lock":
        d.rounded_rectangle([cx-8, cy-2, cx+8, cy+9], radius=3, outline=col, width=2)
        d.arc([cx-5, cy-9, cx+5, cy+1], start=180, end=360, fill=col, width=2)
    elif kind == "shield":
        d.polygon([(cx, cy-9), (cx-7, cy-5.5), (cx-7, cy+1), (cx, cy+7), (cx+7, cy+1), (cx+7, cy-5.5)], outline=col, width=2)
    elif kind == "bug":
        d.ellipse([cx-7, cy-6, cx+7, cy+8], outline=col, width=2)
        d.line([cx, cy-6, cx, cy-10], fill=col, width=2)

def chevron(d, t, cy):
    col = t["label3"] if isinstance(t["label3"], tuple) else hexc(t["label3"])
    x = W - GUT - 14
    d.line([x-4, cy-5.5, x+1, cy, x-4, cy+5.5], fill=col, width=2, joint="curve")

def switch(d, t, cy, on):
    w, h = 51, 31
    x = W - GUT - 16 - w
    d.rounded_rectangle([x, cy-h/2, x+w, cy+h/2], radius=h/2, fill=hexc(t["green"]) if on else hexc(t["gray4"]))
    kx = x + (w - h + 4) if on else x + 2
    d.ellipse([kx+2, cy-h/2+2, kx+h-2, cy+h/2-2], fill=(255,255,255))

def row(img, t, y, label, kind=None, right=None, sub=None):
    d = ImageDraw.Draw(img)
    cy = y + ROW_H/2
    tx = GUT + 16
    if kind:
        icon(d, tx + 12, cy, kind, hexc(t["blue"]))
        tx += 40
    d.text((tx, cy if not sub else cy - 9), label, font=font(17), fill=hexc(t["label"]))
    if sub:
        d.text((tx, cy + 11), sub, font=font(13), fill=hexc(t["label2"]))
    if right == "chevron":
        chevron(d, t, cy)
    elif right in ("on", "off"):
        switch(d, t, cy, right == "on")
    return y + ROW_H

def button_row(img, t, y, label, color=None):
    d = ImageDraw.Draw(img)
    cy = y + ROW_H/2
    d.text((GUT + 16, cy), label, font=font(17), fill=hexc(color if color else t["blue"]), anchor="lm")
    return y + ROW_H

def filled_button(img, t, y, label, h=50):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([GUT, y, W-GUT, y+h], radius=R_CARD, fill=hexc(t["blue"]))
    cx = W/2 - 44
    d.ellipse([cx-8, y+h/2-8, cx+8, y+h/2+8], outline=(255,255,255), width=2)
    d.text((W/2 + 12, y+h/2), label, font=font(17, True), fill=(255,255,255), anchor="mm")
    return y + h

def progress(img, t, y, ratio=0.6):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([GUT, y, W-GUT, y+4], radius=2, fill=hexc(t["gray5"]))
    d.rounded_rectangle([GUT, y, GUT+(W-2*GUT)*ratio, y+4], radius=2, fill=hexc(t["blue"]))
    return y + 4

def segmented(img, t, y, labels, active=0):
    d = ImageDraw.Draw(img)
    h = 32
    d.rounded_rectangle([GUT, y, W-GUT, y+h], radius=9, fill=t["fill"] if isinstance(t["fill"], tuple) else hexc(t["fill"]))
    n = len(labels)
    seg_w = (W - 2*GUT - 4) / n
    for i, lb in enumerate(labels):
        x = GUT + 2 + i * seg_w
        if i == active:
            d.rounded_rectangle([x, y+2, x+seg_w, y+h-2], radius=7, fill=hexc(t["seg"]),
                                outline=t["sep"] if isinstance(t["sep"], tuple) else hexc(t["sep"]), width=1)
        d.text((x + seg_w/2, y+h/2), lb, font=font(13, i == active), fill=hexc(t["label"]), anchor="mm")
    return y + h

def tabbar(img, t, active=0):
    y0 = H - 50
    layer = Image.new("RGBA", img.size, (0,0,0,0))
    ImageDraw.Draw(layer).rectangle([0, y0, W, H], fill=t["tabbar"])
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    d.line([0, y0, W, y0], fill=t["sep"], width=1)
    names = ["状态", "检测", "防护"]
    kinds = ["shield", "scan", "lock"]
    for i, (n, k) in enumerate(zip(names, kinds)):
        cx = W/3*(i+0.5)
        col = hexc(t["blue"]) if i == active else hexc(t["label2"])
        icon(d, cx, y0+17, k, col)
        d.text((cx, y0+38), n, font=font(11), fill=col, anchor="mm")

def ring(img, t, cy, score=100, color=None):
    d = ImageDraw.Draw(img)
    size, thick = 180, 9
    box = [W/2-size/2, cy-size/2, W/2+size/2, cy+size/2]
    d.arc(box, start=0, end=360, fill=hexc(t["gray5"]), width=thick)
    d.arc(box, start=-90, end=-90+360*score/100.0, fill=hexc(color or t["green"]), width=thick)
    d.text((W/2, cy-14), str(score), font=font(40, True), fill=hexc(t["label"]), anchor="mm")
    d.text((W/2, cy+24), "安全评分", font=font(13), fill=hexc(t["label2"]), anchor="mm")
    return cy + size/2

# ---------- 状态 ----------
def status(img, t):
    large_title(img, t, "状态")
    y = 92
    group(img, t, y, 292)
    ring(img, t, y + 130, 100, t["green"])
    d = ImageDraw.Draw(img)
    d.text((W/2, y + 240), "设备状态良好,未发现风险项", font=font(17), fill=hexc(t["label2"]), anchor="mm")
    txt = "未获取 Root 权限"
    tw = d.textlength(txt, font=font(13)) + 28
    d.rounded_rectangle([W/2-tw/2, y+262, W/2+tw/2, y+288], radius=13, fill=t["fill"] if isinstance(t["fill"], tuple) else hexc(t["fill"]))
    d.text((W/2, y+275), txt, font=font(13), fill=hexc(t["label2"]), anchor="mm")
    y += 292 + 26
    y = group_header(img, t, "快 速 进 入", y)
    group(img, t, y, ROW_H*3)
    yy = y
    yy = row(img, t, yy, "病毒扫描", "scan", "chevron"); sep(img, t, yy)
    yy = row(img, t, yy, "权限审计", "grid", "chevron"); sep(img, t, yy)
    yy = row(img, t, yy, "应用锁", "lock", "chevron")
    y += ROW_H*3 + 26
    y = group_header(img, t, "防 护 开 关", y)
    group(img, t, y, ROW_H*3)
    yy = y
    yy = row(img, t, yy, "实时防护", "shield", "on"); sep(img, t, yy)
    yy = row(img, t, yy, "自动清除威胁", None, "on"); sep(img, t, yy)
    yy = row(img, t, yy, "自动卸载恶意应用", None, "off")
    tabbar(img, t, 0)

# ---------- 检测 ----------
def detect(img, t):
    large_title(img, t, "检测")
    y = segmented(img, t, 92, ["病毒扫描", "木马查杀"], 0) + 20
    y = filled_button(img, t, y, "开始扫描") + 20
    y = progress(img, t, y, 0.62) + 14
    d = ImageDraw.Draw(img)
    d.text((GUT, y), "正在扫描 /data/app/com.example", font=font(13), fill=hexc(t["label2"]))
    y += 30
    rows = [("微信", "com.tencent.mm", "未发现风险", t["green"]),
            ("某银行", "cn.bank.app", "签名校验通过", t["green"]),
            ("未知来源应用", "com.unknown.tool", "请求高危权限 3 项", t["orange"]),
            ("广告插件", "com.ad.sdk", "包含已知广告特征", t["red"])]
    group(img, t, y, 72*len(rows))
    for i, (name, pkg, st, col) in enumerate(rows):
        ry = y + i*72
        d = ImageDraw.Draw(img)
        d.text((GUT+16, ry+16), name, font=font(17, True), fill=hexc(t["label"]))
        d.text((GUT+16, ry+40), pkg, font=font(13), fill=hexc(t["label2"]))
        d.text((GUT+16, ry+56), st, font=font(13), fill=hexc(col))
        if i < len(rows)-1:
            sep(img, t, ry+72, 16)
    tabbar(img, t, 1)

# ---------- 防护 ----------
def protect(img, t):
    large_title(img, t, "防护")
    y = segmented(img, t, 92, ["应用锁", "权限审计", "工具箱"], 0) + 20
    group(img, t, y, ROW_H*3)
    yy = y
    yy = row(img, t, yy, "微信", None, "on", sub="com.tencent.mm"); sep(img, t, yy, 16)
    yy = row(img, t, yy, "某银行", None, "on", sub="cn.bank.app"); sep(img, t, yy, 16)
    yy = row(img, t, yy, "相册", None, "off", sub="com.android.gallery")
    y += ROW_H*3 + 26
    y = group_header(img, t, "工 具 箱", y)
    group(img, t, y, ROW_H*3)
    yy = y
    yy = row(img, t, yy, "文件粉碎", "grid", "chevron"); sep(img, t, yy)
    yy = row(img, t, yy, "加密保险箱", "lock", "chevron"); sep(img, t, yy)
    yy = row(img, t, yy, "紧急求助", "shield", "chevron")
    tabbar(img, t, 2)

def phone(t, fn):
    img = base(t)
    fn(img, t)
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, W-1, H-1], radius=34, fill=255)
    out = Image.new("RGBA", img.size, (0,0,0,0))
    out.paste(img, (0,0), m)
    return out

def tokens():
    h = 268
    img = Image.new("RGBA", (W*3 + 160, h), hexc("#1C1C1E"))
    d = ImageDraw.Draw(img)
    d.text((40, 26), "设计令牌 · Apple 系统色与 iOS 度量", font=font(18, True), fill="#FFFFFF")
    rules = [
        "系统色按角色命名:label / secondaryLabel / separator / systemGroupedBackground;同一颜色不表达两种含义。",
        "Dynamic Type:Large Title 34 · Headline 17 semibold · Body 17 · Footnote 13 · Caption 12(单位 sp)。",
        "列表用 inset grouped:10dp 圆角分组卡,行高 48dp(Android 触摸目标下限,严于 iOS 的 44pt),分隔线 0.5dp 且左侧内缩。",
        "标签栏通栏 + 顶部 0.5dp 分隔线,实心图标在上、11sp 标签在下,选中只用 systemBlue 着色、没有指示条。",
        "分段控件:12% 系统填充色轨道 + 选中段浮起的浅色块;开关:打开为 systemGreen。",
    ]
    yy = 58
    for r in rules:
        d.text((40, yy), "· " + r, font=font(12), fill="#AEAEB2")
        yy += 22
    sw = [("systemBlue", "#007AFF"), ("systemGreen", "#34C759"), ("systemOrange", "#FF9500"), ("systemRed", "#FF3B30"),
          ("分组卡(浅)", "#FFFFFF"), ("分组底(浅)", "#F2F2F7"), ("分组卡(深)", "#1C1C1E"), ("分组底(深)", "#000000")]
    x = 40
    for name, col in sw:
        d.rounded_rectangle([x, 186, x+120, 226], radius=10, fill=hexc(col), outline=(255,255,255,40), width=1)
        d.text((x, 232), name, font=font(11), fill="#AEAEB2")
        d.text((x, 246), col, font=font(11), fill="#8E8E93")
        x += 132
    return img

a = phone(LIGHT, status)
b = phone(DARK, detect)
c = phone(LIGHT, protect)
tk = tokens()
SH = 100 + H + 30 + 268 + 30
sheet = Image.new("RGBA", (W*3 + 160, SH), hexc("#F2F2F7"))
d = ImageDraw.Draw(sheet)
d.text((40, 30), "SecureDroid · Apple 风格(iOS HIG)— 状态 / 检测 / 防护", font=font(22, True), fill="#000000")
d.text((40, 64), "系统色 + Dynamic Type + inset grouped 列表 + 分段控件 + 通栏标签栏;令牌与 res/values 一一对应", font=font(12), fill="#3C3C43")
sheet.alpha_composite(a, (40, 100))
sheet.alpha_composite(b, (W + 80, 100))
sheet.alpha_composite(c, (W*2 + 120, 100))
sheet.alpha_composite(tk, (40, 100 + H + 30))
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mockup-sheet.png")
sheet.convert("RGB").save(out, quality=95)
print("saved", out, sheet.size)
