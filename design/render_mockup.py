# -*- coding: utf-8 -*-
"""
SecureDroid 设计稿渲染器 v4 —— Minimalism & Swiss Style(ui-ux-pro-max 设计系统解析结果)

不是真机截图:按 res/values 的令牌 1:1 复刻,用于自检网格、层级、留白与对比度。
Swiss 的语言:几何、留白分区、1dp 边框代替阴影、字号层级代替装饰。
"""
from PIL import Image, ImageDraw, ImageFont
import os

FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_B = "C:/Windows/Fonts/msyhbd.ttc"
def font(s, b=False): return ImageFont.truetype(FONT_B if b else FONT, s)
def hx(s, a=255):
    if isinstance(s, tuple): return s
    s = s.lstrip("#"); return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), a)

LIGHT = dict(bg="#F8FAFC", card="#FFFFFF", fg="#020617", muted="#E8ECF1", mutedfg="#475569",
             border="#E2E8F0", primary="#0F172A", onprimary="#FFFFFF", accent="#0369A1",
             success="#15803D", gold="#A16207", danger="#DC2626")
DARK = dict(bg="#020617", card="#0E1223", fg="#F8FAFC", muted="#1A1E2F", mutedfg="#94A3B8",
            border="#334155", primary="#F8FAFC", onprimary="#0F172A", accent="#38BDF8",
            success="#22C55E", gold="#FBBF24", danger="#EF4444")

W, H, GUT, ROW, R = 412, 915, 16, 56, 12

def base(t): return Image.new("RGBA", (W, H), hx(t["bg"]))

def page_title(img, t, s):
    ImageDraw.Draw(img).text((GUT, 28), s, font=font(26, True), fill=hx(t["fg"]))

def section(img, t, s, y):
    ImageDraw.Draw(img).text((GUT, y), s, font=font(12, True), fill=hx(t["mutedfg"]))
    return y + 20

def card(img, t, y, h, fill=None):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([GUT, y, W-GUT, y+h], radius=R, fill=hx(fill or t["card"]), outline=hx(t["border"]), width=1)
    return y + h

def divider(img, t, y, inset=56):
    ImageDraw.Draw(img).line([GUT+inset, y, W-GUT, y], fill=hx(t["border"]), width=1)

def icon(d, cx, cy, kind, col):
    if kind == "scan":
        d.ellipse([cx-8, cy-8, cx+8, cy+8], outline=col, width=2); d.line([cx+5.5, cy+5.5, cx+9, cy+9], fill=col, width=2)
    elif kind == "grid":
        for dx in (-8, 1):
            for dy in (-8, 1): d.rounded_rectangle([cx+dx, cy+dy, cx+dx+7, cy+dy+7], radius=2, outline=col, width=2)
    elif kind == "lock":
        d.rounded_rectangle([cx-8, cy-2, cx+8, cy+9], radius=3, outline=col, width=2)
        d.arc([cx-5, cy-9, cx+5, cy+1], start=180, end=360, fill=col, width=2)
    elif kind == "shield":
        d.polygon([(cx, cy-9), (cx-7, cy-5.5), (cx-7, cy+1), (cx, cy+7), (cx+7, cy+1), (cx+7, cy-5.5)], outline=col, width=2)

def chevron(d, t, cy):
    x = W - GUT - 20
    d.line([x-4, cy-5, x+1, cy, x-4, cy+5], fill=hx(t["mutedfg"]), width=2, joint="curve")

def switch(d, t, cy, on):
    w, h = 46, 28; x = W - GUT - 16 - w
    d.rounded_rectangle([x, cy-h/2, x+w, cy+h/2], radius=h/2, fill=hx(t["accent"]) if on else hx(t["border"]))
    kx = x + (w-h+4) if on else x + 2
    d.ellipse([kx+2, cy-h/2+2, kx+h-2, cy+h/2-2], fill=(255,255,255))

def row(img, t, y, label, kind=None, right=None, h=ROW, color=None, sub=None):
    d = ImageDraw.Draw(img); cy = y + h/2; tx = GUT + 16
    if kind:
        icon(d, tx + 12, cy, kind, hx(t["mutedfg"])); tx += 40
    d.text((tx, cy - (9 if sub else 0)), label, font=font(16), fill=hx(color or t["fg"]), anchor="lm")
    if sub: d.text((tx, cy + 12), sub, font=font(14), fill=hx(t["mutedfg"]), anchor="lm")
    if right == "chevron": chevron(d, t, cy)
    elif right in ("on", "off"): switch(d, t, cy, right == "on")
    return y + h

def filled(img, t, y, label, h=48, kind="scan"):
    d = ImageDraw.Draw(img)
    # 与 Widget.SecureDroid.Button.Accent 一致:主行动用 accent 填充
    d.rounded_rectangle([GUT, y, W-GUT, y+h], radius=8, fill=hx(t["accent"]))
    d.text((W/2+8, y+h/2), label, font=font(16, True), fill=(255,255,255), anchor="mm")
    return y + h

def progress(img, t, y, ratio=0.62):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([GUT, y, W-GUT, y+4], radius=2, fill=hx(t["muted"]))
    d.rounded_rectangle([GUT, y, GUT+(W-2*GUT)*ratio, y+4], radius=2, fill=hx(t["accent"]))
    return y + 4

def segmented(img, t, y, labels, active=0):
    d = ImageDraw.Draw(img); h = 40
    d.rounded_rectangle([GUT, y, W-GUT, y+h], radius=10, fill=hx(t["muted"]))
    n = len(labels); sw = (W - 2*GUT - 4)/n
    for i, lb in enumerate(labels):
        x = GUT + 2 + i*sw
        if i == active:
            d.rounded_rectangle([x, y+2, x+sw, y+h-2], radius=8, fill=hx(t["card"]), outline=hx(t["border"]), width=1)
        d.text((x+sw/2, y+h/2), lb, font=font(14, i == active), fill=hx(t["fg"] if i == active else t["mutedfg"]), anchor="mm")
    return y + h

def tabbar(img, t, active=0):
    y0 = H - 60
    d = ImageDraw.Draw(img)
    d.rectangle([0, y0, W, H], fill=hx(t["card"]))
    d.line([0, y0, W, y0], fill=hx(t["border"]), width=1)
    names, kinds = ["状态", "检测", "防护"], ["shield", "scan", "lock"]
    for i, (n, k) in enumerate(zip(names, kinds)):
        cx = W/3*(i+0.5)
        col = hx(t["fg"]) if i == active else hx(t["mutedfg"])
        if i == active: d.rectangle([cx-16, y0, cx+16, y0+3], fill=hx(t["accent"]))
        icon(d, cx, y0+22, k, col)
        d.text((cx, y0+44), n, font=font(12, i == active), fill=col, anchor="mm")

def ring(img, t, cy, score=100, color=None):
    d = ImageDraw.Draw(img); size, th = 176, 8
    box = [W/2-size/2, cy-size/2, W/2+size/2, cy+size/2]
    d.arc(box, 0, 360, fill=hx(t["muted"]), width=th)
    d.arc(box, -90, -90+360*score/100.0, fill=hx(color or t["accent"]), width=th)
    d.text((W/2, cy-12), str(score), font=font(34, True), fill=hx(t["fg"]), anchor="mm")
    d.text((W/2, cy+22), "安全评分", font=font(12, True), fill=hx(t["mutedfg"]), anchor="mm")
    return cy + size/2

def status(img, t):
    page_title(img, t, "状态")
    y = 86
    d = ImageDraw.Draw(img); cy = y + 108
    card(img, t, y, 232)
    ring(img, t, cy, 100, t["accent"])
    d.text((W/2, y+196), "设备状态良好,未发现风险项", font=font(16), fill=hx(t["mutedfg"]), anchor="mm")
    txt = "未获取 Root 权限"; tw = d.textlength(txt, font=font(12, True)) + 20
    d.rounded_rectangle([W/2-tw/2, y+208, W/2+tw/2, y+228], radius=6, fill=hx(t["muted"]))
    d.text((W/2, y+218), txt, font=font(12, True), fill=hx(t["mutedfg"]), anchor="mm")
    y += 232 + 26
    y = section(img, t, "快 速 进 入", y)
    card(img, t, y, ROW*3)
    yy = y
    yy = row(img, t, yy, "病毒扫描", "scan", "chevron"); divider(img, t, yy)
    yy = row(img, t, yy, "权限审计", "grid", "chevron"); divider(img, t, yy)
    yy = row(img, t, yy, "应用锁", "lock", "chevron")
    y += ROW*3 + 24
    y = section(img, t, "防 护 开 关", y)
    card(img, t, y, ROW*3)
    yy = y
    yy = row(img, t, yy, "实时防护", "shield", "on"); divider(img, t, yy)
    yy = row(img, t, yy, "自动清除威胁", None, "on"); divider(img, t, yy)
    yy = row(img, t, yy, "自动卸载恶意应用", None, "off")
    tabbar(img, t, 0)

def detect(img, t):
    page_title(img, t, "检测")
    y = segmented(img, t, 86, ["病毒扫描", "木马查杀"], 0) + 20
    y = filled(img, t, y, "开始扫描") + 20
    y = progress(img, t, y, 0.62) + 14
    d = ImageDraw.Draw(img)
    d.text((GUT, y), "正在扫描 /data/app/com.example", font=font(14), fill=hx(t["mutedfg"]))
    y += 32
    rows = [("微信", "com.tencent.mm", "未发现风险", t["success"]),
            ("某银行", "cn.bank.app", "签名校验通过", t["success"]),
            ("未知来源应用", "com.unknown.tool", "请求高危权限 3 项", t["gold"]),
            ("广告插件", "com.ad.sdk", "包含已知广告特征", t["danger"])]
    card(img, t, y, 76*len(rows))
    for i, (name, pkg, st, col) in enumerate(rows):
        ry = y + i*76
        d = ImageDraw.Draw(img)
        d.text((GUT+16, ry+16), name, font=font(16, True), fill=hx(t["fg"]))
        d.text((GUT+16, ry+40), pkg, font=font(14), fill=hx(t["mutedfg"]))
        d.text((GUT+16, ry+58), st, font=font(14), fill=hx(col))
        if i < len(rows)-1: divider(img, t, ry+76, 16)
    tabbar(img, t, 1)

def protect(img, t):
    page_title(img, t, "防护")
    y = segmented(img, t, 86, ["应用锁", "权限审计", "工具箱"], 0) + 20
    card(img, t, y, ROW*3)
    yy = y
    yy = row(img, t, yy, "微信", None, "on", sub="com.tencent.mm"); divider(img, t, yy, 16)
    yy = row(img, t, yy, "某银行", None, "on", sub="cn.bank.app"); divider(img, t, yy, 16)
    yy = row(img, t, yy, "相册", None, "off", sub="com.android.gallery")
    y += ROW*3 + 24
    y = section(img, t, "工 具 箱", y)
    card(img, t, y, ROW*3)
    yy = y
    yy = row(img, t, yy, "文件粉碎", "grid", "chevron"); divider(img, t, yy)
    yy = row(img, t, yy, "加密保险箱", "lock", "chevron"); divider(img, t, yy)
    yy = row(img, t, yy, "紧急求助", "shield", "chevron")
    tabbar(img, t, 2)

def phone(t, fn):
    img = base(t); fn(img, t)
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, W-1, H-1], radius=28, fill=255)
    out = Image.new("RGBA", img.size, (0,0,0,0)); out.paste(img, (0,0), m); return out

def tokens():
    h = 276
    img = Image.new("RGBA", (W*3 + 160, h), hx("#0F172A"))
    d = ImageDraw.Draw(img)
    d.text((40, 26), "设计令牌 · Minimalism & Swiss Style(ui-ux-pro-max 解析结果)", font=font(18, True), fill="#F8FAFC")
    rules = [
        "网格:4/8dp 节奏;分区间距 24/32;页面边距 16;卡片圆角 12、控件 8。",
        "分隔靠 1dp 边框(#E2E8F0/#334155)与留白,不用阴影 - 风格关键词是 sharp shadows if any。",
        "字号层级 6 级:Display 34 / Title 26 / Headline 20 / Body 16(行高 1.5)/ Label 14 / Section 12 大写 0.08 字距。",
        "触摸目标 ≥48dp、相邻 ≥8dp;正文对比度 ≥4.5:1(浅色 #020617 on #FFFFFF,深色 #F8FAFC on #0E1223)。",
        "底部标签 3 项(规则:≤5);多步流程必须给进度条;动效尊重系统动画时长缩放。",
    ]
    yy = 58
    for r in rules:
        d.text((40, yy), "· " + r, font=font(12), fill="#94A3B8"); yy += 22
    sw = [("primary", "#0F172A"), ("accent", "#0369A1"), ("success", "#15803D"), ("gold", "#A16207"),
          ("destructive", "#DC2626"), ("muted", "#E8ECF1"), ("border", "#E2E8F0"), ("bg", "#F8FAFC")]
    x = 40
    for name, col in sw:
        d.rounded_rectangle([x, 196, x+120, 232], radius=8, fill=hx(col), outline=(255,255,255,40), width=1)
        d.text((x, 238), name, font=font(11), fill="#94A3B8"); d.text((x, 252), col, font=font(11), fill="#64748B")
        x += 132
    return img

a = phone(LIGHT, status); b = phone(DARK, detect); c = phone(LIGHT, protect)
tk = tokens()
SH = 100 + H + 30 + 276 + 30
sheet = Image.new("RGBA", (W*3 + 160, SH), hx("#F8FAFC"))
d = ImageDraw.Draw(sheet)
d.text((40, 30), "SecureDroid · Swiss Style(ui-ux-pro-max 设计系统)— 状态 / 检测 / 防护", font=font(22, True), fill="#020617")
d.text((40, 64), "风格:Minimalism & Swiss Style;配色:Trust navy + accent;令牌与 res/values 一一对应", font=font(12), fill="#475569")
sheet.alpha_composite(a, (40, 100)); sheet.alpha_composite(b, (W+80, 100)); sheet.alpha_composite(c, (W*2+120, 100))
sheet.alpha_composite(tk, (40, 100 + H + 30))
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mockup-sheet.png")
sheet.convert("RGB").save(out, quality=95)
print("saved", out, sheet.size)
