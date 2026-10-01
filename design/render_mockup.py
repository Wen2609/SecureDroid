# -*- coding: utf-8 -*-
"""
SecureDroid 设计稿渲染器(设计自检用)
不是真机截图 —— 它按 res/values 中的设计令牌(dimens/colors/styles)1:1 复刻关键界面,
用于在没有人脸设备/模拟器的情况下校验:层级、间距、对比度、圆角与光效是否成立。
令牌改动后重跑本脚本,即可看到界面随之变化。
"""
from PIL import Image, ImageDraw, ImageFont
import math, os

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
    """径向光晕:用一圈圈递减透明度的椭圆近似径向渐变(不引入 numpy 依赖)"""
    layer = Image.new("RGBA", size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    steps = 90
    for i in range(steps, 0, -1):
        t = i / steps
        r = radius * t
        a = int(color[3] * ((1 - t) ** 2.0))
        if a <= 0: continue
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(color[0], color[1], color[2], a))
    return layer

def rr(draw, box, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)

# ---------------- 设计令牌(与 res/values 保持一致) ----------------
DARK = dict(bg="#070B14", bg2="#0C1626", bloom="#2E7BFF", bloom2="#22D3EE",
            surface="#131C2E", glass=(255,255,255,20), hairline=(255,255,255,46),
            text="#EAF0FF", text2="#A3B2CC", text3="#7C8CA6",
            brand="#4C8DFF", brandb="#7FB0FF", glow="#37D6FF", onbrand="#06101F")
LIGHT = dict(bg="#F3F7FD", bg2="#E6EFFB", bloom="#2F7BFF", bloom2="#0FB5D6",
             surface="#FFFFFF", glass=(255,255,255,242), hairline=(11,18,32,31),
             text="#0B1220", text2="#51617C", text3="#7A88A0",
             brand="#0B57D0", brandb="#2F7BFF", glow="#0FB5D6", onbrand="#FFFFFF")

W, H = 412, 915
GUT = 20

def base(t, dark):
    img = Image.new("RGBA", (W, H))
    img.alpha_composite(vgrad((W,H), hexc(t["bg"]), hexc(t["bg2"])))
    a1 = 0x4D if dark else 0x33
    a2 = 0x33 if dark else 0x1F
    img.alpha_composite(bloom((W,H), hexc(t["bloom"], a1), 300, W-40, 10))
    img.alpha_composite(bloom((W,H), hexc(t["bloom2"], a2), 280, 10, H-70))
    return img

def glass_card(img, box, radius, t, alpha=None):
    layer = Image.new("RGBA", img.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    fill = t["glass"] if alpha is None else (t["glass"][0], t["glass"][1], t["glass"][2], alpha)
    rr(d, box, radius, fill=fill, outline=t["hairline"], width=1)
    img.alpha_composite(layer)

def text(d, xy, s, size, color, bold=False, anchor="la"):
    d.text(xy, s, font=font(size, bold), fill=hexc(color), anchor=anchor)

def tab_bar(img, t, active=0):
    y0 = H - 64
    layer = Image.new("RGBA", img.size, (0,0,0,0))
    d = ImageDraw.Draw(layer)
    rr(d, [0, y0, W, H], 28, fill=t["glass"], outline=t["hairline"], width=1)
    d.rectangle([0, y0, W, y0+28], fill=t["glass"])
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    names = ["首页","扫描","审计","应用锁","查杀","工具"]
    slot = W / len(names)
    for i, n in enumerate(names):
        cx = slot * (i + 0.5)
        col = t["brand"] if i == active else t["text3"]
        # 指示器胶囊
        if i == active:
            d.rounded_rectangle([cx-16, y0+2, cx+16, y0+5], radius=2, fill=hexc(t["brand"]))
        d.ellipse([cx-9, y0+16, cx+9, y0+34], outline=hexc(col), width=2)
        text(d, (cx, y0+44), n, 11, col, anchor="ma")

def toolbar(img, t, title):
    d = ImageDraw.Draw(img)
    layer = Image.new("RGBA", img.size, (0,0,0,0))
    dl = ImageDraw.Draw(layer)
    dl.rectangle([0, 0, W, 64], fill=t["glass"])
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    d.line([0, 64, W, 64], fill=t["hairline"], width=1)
    text(d, (GUT, 32), title, 20, t["text"], bold=True, anchor="lm")

def switch(d, x, y, on, t):
    w, h = 44, 26
    rr(d, [x, y, x+w, y+h], h//2, fill=hexc(t["brand"] if on else t["text3"], 120 if on else 60),
       outline=t["hairline"], width=1)
    cx = x + (w - h + 4) if on else x + 2
    d.ellipse([cx+1, y+2, cx+h-1, y+h-2], fill=hexc(t["onbrand"] if on else t["text2"]))

def button(d, box, label, t, filled, radius=18, icon=True):
    if filled:
        rr(d, box, radius, fill=hexc(t["brand"]))
        col = t["onbrand"]
    else:
        rr(d, box, radius, fill=(255,255,255,26) if t["text"]!="#0B1220" else (255,255,255,235),
           outline=t["hairline"], width=1)
        col = t["text"]
    cx = (box[0]+box[2])/2
    cy = (box[1]+box[3])/2
    if icon:
        d.ellipse([box[0]+16, cy-8, box[0]+32, cy+8], outline=hexc(col), width=2)
        text(d, (cx+12, cy), label, 15, col, anchor="mm")
    else:
        text(d, (cx, cy), label, 15, col, bold=True, anchor="mm")

# ---------------- 界面 1:仪表盘(深色) ----------------
def dashboard():
    t = DARK
    img = base(t, True)
    toolbar(img, t, "安卫安全助手")
    d = ImageDraw.Draw(img)
    y = 84
    hero = [GUT, y, W-GUT, y+342]
    glass_card(img, hero, 28, t)
    text(d, (W/2, y+30), "安全评分", 13, t["text3"], bold=True, anchor="mm")
    img.alpha_composite(bloom((W,H), hexc(t["brandb"], 62), 98, W/2, y+146))
    text(d, (W/2, y+146), "100", 64, t["text"], bold=True, anchor="mm")
    text(d, (W/2, y+224), "设备状态良好,未发现风险项", 15, t["text2"], anchor="mm")
    d.rounded_rectangle([W/2-86, y+252, W/2+86, y+286], radius=17, fill=(255,255,255,26), outline=t["hairline"], width=1)
    text(d, (W/2, y+269), "未获取 Root 权限", 13, t["text2"], anchor="mm")

    y += 370
    text(d, (GUT, y), "快速操作", 13, t["text3"], bold=True)
    y += 22
    bw = (W - 2*GUT - 8) / 2
    button(d, [GUT, y, GUT+bw, y+52], "立即扫描", t, True, icon=True)
    button(d, [W-GUT-bw, y, W-GUT, y+52], "权限审计", t, False, icon=True)
    y += 60
    button(d, [GUT, y, GUT+bw, y+52], "应用锁", t, False, icon=True)
    button(d, [W-GUT-bw, y, W-GUT, y+52], "实时防护", t, False, icon=True)

    y += 76
    card = [GUT, y, W-GUT, y+150]
    glass_card(img, card, 28, t)
    d = ImageDraw.Draw(img)
    text(d, (GUT+16, y+26), "安全设置", 13, t["text3"], bold=True, anchor="lm")
    text(d, (GUT+16, y+74), "自动清除威胁", 15, t["text"], anchor="lm")
    switch(d, W-GUT-60, y+61, True, t)
    text(d, (GUT+16, y+122), "自动卸载恶意应用", 15, t["text"], anchor="lm")
    switch(d, W-GUT-60, y+109, False, t)
    tab_bar(img, t, 0)
    return img

# ---------------- 界面 2:扫描页(浅色) ----------------
def scanner():
    t = LIGHT
    img = base(t, False)
    toolbar(img, t, "病毒扫描")
    d = ImageDraw.Draw(img)
    y = 84
    card = [GUT, y, W-GUT, y+190]
    glass_card(img, card, 28, t)
    d = ImageDraw.Draw(img)
    button(d, [GUT+16, y+16, W-GUT-16, y+68], "开始扫描", t, True, radius=26, icon=True)
    rr(d, [GUT+16, y+92, W-GUT-16, y+102], 5, fill=hexc(t["brand"], 40))
    bar = Image.new("RGBA", img.size, (0,0,0,0))
    bd = ImageDraw.Draw(bar)
    rr(bd, [GUT+16, y+92, GUT+16+200, y+102], 5, fill=hexc(t["brand"]))
    img.alpha_composite(bar)
    d = ImageDraw.Draw(img)
    text(d, (GUT+16, y+122), "正在扫描 /data/app/com.example …", 15, t["text2"])
    text(d, (GUT+16, y+150), "已扫描 1 284 / 2 000 项", 13, t["text3"])

    y += 214
    rows = [("微信", "com.tencent.mm", "未发现风险", "#1B7F4B"),
            ("某银行", "cn.bank.app", "签名校验通过", "#1B7F4B"),
            ("未知来源应用", "com.unknown.tool", "请求高危权限 3 项", "#9A5B00"),
            ("广告插件", "com.ad.sdk", "包含已知广告特征", "#C62828")]
    for name, pkg, status, col in rows:
        box = [GUT, y, W-GUT, y+92]
        glass_card(img, box, 18, t)
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([GUT+16, y+24, GUT+60, y+68], radius=14, fill=hexc(t["brand"], 26), outline=t["hairline"], width=1)
        d.ellipse([GUT+28, y+36, GUT+48, y+56], outline=hexc(t["brand"]), width=2)
        text(d, (GUT+72, y+30), name, 15, t["text"], bold=True)
        text(d, (GUT+72, y+52), pkg, 12, t["text3"])
        text(d, (GUT+72, y+74), status, 13, col)
        y += 100
    tab_bar(img, t, 1)
    return img

# ---------------- 设计规范条 ----------------
def round_mask(img, radius=36):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, img.size[0]-1, img.size[1]-1], radius=radius, fill=255)
    out = Image.new("RGBA", img.size, (0,0,0,0))
    out.paste(img, (0,0), m)
    return out

def tokens():
    swatches = [("品牌", "#4C8DFF"), ("凝光青", "#37D6FF"), ("安全", "#34D399"),
                ("警示", "#FBBF24"), ("危险", "#FF7A7A"), ("深空底", "#070B14"), ("表面", "#131C2E")]
    h = 300
    img = Image.new("RGBA", (W*2+120, h), hexc("#0B1220"))
    d = ImageDraw.Draw(img)
    text(d, (40, 30), "设计令牌与规则", 18, "#EAF0FF", bold=True)
    text(d, (40, 60), "4dp 栅格 · 触摸目标 ≥48dp 且相邻间距 ≥8dp · 卡片圆角 28dp · 内圆角 18dp · 胶囊 100dp",
         12, "#A3B2CC")
    text(d, (40, 82), "动效 280–320ms 缓出(fast_out_slow_in)· 按压缩放 0.97 / 110ms,回弹 240ms",
         12, "#A3B2CC")
    x = 40
    for name, col in swatches:
        d.rounded_rectangle([x, 110, x+120, 170], radius=14, fill=hexc(col), outline=(255,255,255,46), width=1)
        text(d, (x, 178), name, 12, "#EAF0FF")
        text(d, (x, 194), col, 11, "#7C8CA6")
        x += 140
    rules = [
        "凝光视效:1dp 亮边 + 极低阴影代替重投影;背景用两处径向光晕,不引入图片资源。",
        "流体动效:列表错峰入场(每项延迟 8%),页面转场交给系统,避免低端机掉帧。",
        "柔性反馈:按压缩放 0.97 后 240ms 回弹;开关与按钮均满足 48dp 触摸目标。",
        "克制用色:玻璃只用于功能层(工具栏 / 底部入口条),内容层保持不透明表面以保证正文对比度 ≥4.5:1。",
        "可访问性:支持系统深色模式;文案不依赖颜色单独表意(状态同时有文字)。",
    ]
    yy = 222
    for r in rules:
        text(d, (40, yy), "· " + r, 12, "#A3B2CC")
        yy += 20
    return img

a = round_mask(dashboard())
b = round_mask(scanner())
tk = tokens()
SHEET_H = 96 + H + 32 + 300 + 40
sheet = Image.new("RGBA", (W*2 + 120, SHEET_H), hexc("#05080F"))
d = ImageDraw.Draw(sheet)
text(d, (40, 30), "SecureDroid · Fluid Design 设计稿(深色 / 浅色双主题)", 22, "#EAF0FF", bold=True)
text(d, (40, 62), "依据 ColorOS 17「流体设计」(凝光视效 / 流体动效 / 柔性反馈)与 HIG 玻璃材质规范重绘;令牌与 res/values 一一对应",
     12, "#A3B2CC")
sheet.alpha_composite(a, (40, 96))
sheet.alpha_composite(b, (W + 80, 96))
sheet.alpha_composite(tk, (40, 96 + H + 32))
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mockup-sheet.png")
sheet.convert("RGB").save(out, quality=95)
print("saved", out, sheet.size)
