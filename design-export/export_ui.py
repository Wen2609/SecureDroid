#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SecureDroid 界面结构导出(一次性脚本,不是构建步骤,CI 不跑它)

用途:把 app/src/main/res 里的真实界面结构导出成第三方设计软件可以直接吃的格式。
  screens/{light,dark}/*.svg   六个成品画板(activity_main 外壳 + 板块 Fragment 组合)
  parts/{light,dark}/*.svg     单个 Fragment / Activity / 列表项 / 对话框 / 小组件
  icons/*.svg                  12 个矢量图标(从 drawable/*.xml 的 vector 转出)
  tokens/design-tokens.json    令牌(W3C DTCG 格式,含 light/night 两套)
  tokens/tokens.csv            同一批令牌的表格版
  ui-structure.json            机器可读:信息架构 + 每个屏幕的控件树(含 dp 坐标/尺寸/令牌)
  ui-structure.md              人读版结构说明(本脚本生成)
  index.html                   画板预览页(本地打开)
  manifest.json                导出清单(数量/时间/源文件)

唯一事实源是 res/layout 与 res/values*;这个脚本只读不写,重跑会覆盖 design-export/ 下的产物。
布局手改后重跑即可同步,不存在"生成器覆盖布局"的问题。
"""
from __future__ import annotations

import csv
import datetime
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, 'app', 'src', 'main', 'res')
LAYOUT_DIR = os.path.join(RES, 'layout')
DRAWABLE_DIR = os.path.join(RES, 'drawable')
COLOR_DIR = os.path.join(RES, 'color')

NS_ANDROID = 'http://schemas.android.com/apk/res/android'
NS_APP = 'http://schemas.android.com/apk/res-auto'
NS_TOOLS = 'http://schemas.android.com/tools'

CANVAS_W = 360.0          # dp;1dp = 1 SVG 用户单位,输出时整体放大 3 倍(1080 宽)
SCALE = 3.0
MIN_H = 800.0
FONT = 'Noto Sans CJK SC, Source Han Sans SC, PingFang SC, Microsoft YaHei, Roboto, sans-serif'


def local(tag):
    return tag.rsplit('}', 1)[-1]


def attrs_of(el):
    out = {}
    for k, v in el.attrib.items():
        if k.startswith('{'):
            uri, name = k[1:].split('}', 1)
            if uri == NS_ANDROID:
                out['a:' + name] = v
            elif uri == NS_APP:
                out['app:' + name] = v
            elif uri == NS_TOOLS:
                continue
            else:
                out[name] = v
        else:
            out[k] = v
    return out


def esc(s):
    return (str(s).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            .replace('"', '&quot;'))


def num(v, default=0.0):
    try:
        return float(v)
    except Exception:
        return default


def hex_to_svg(v):
    """#AARRGGBB / #RRGGBB / #RGB -> (fill, opacity)"""
    if not v:
        return None, 1.0
    h = v.lstrip('#').strip()
    if len(h) == 8:
        a = int(h[0:2], 16) / 255.0
        return '#' + h[2:].upper(), round(a, 3)
    if len(h) == 6:
        return '#' + h.upper(), 1.0
    if len(h) == 3:
        return '#' + ''.join(c * 2 for c in h).upper(), 1.0
    return None, 1.0


def svg_fill(v, fallback=None):
    """颜色值 -> 可用的 SVG paint;非法或全透明时返回 fallback(默认不画)。
    直接字符串化 hex_to_svg() 的 None 会得到 fill="None",Chrome 会把它画成黑色。"""
    f, o = hex_to_svg(v)
    return f if (f and o > 0.01) else fallback


class Tables:
    """res/values* 的解析结果与 @ref 解析器"""

    def __init__(self):
        self.light = parse_simple(os.path.join(RES, 'values', 'colors.xml'), 'color')
        self.dark = dict(self.light)
        self.dark.update(parse_simple(os.path.join(RES, 'values-night', 'colors.xml'), 'color'))
        self.dimens = parse_simple(os.path.join(RES, 'values', 'dimens.xml'), 'dimen')
        self.strings = parse_simple(os.path.join(RES, 'values', 'strings.xml'), 'string')
        self.arrays = parse_arrays(os.path.join(RES, 'values', 'strings.xml'))
        self.styles = parse_styles(os.path.join(RES, 'values', 'styles.xml'))
        self.selectors = {}
        if os.path.isdir(COLOR_DIR):
            for f in sorted(os.listdir(COLOR_DIR)):
                if f.endswith('.xml'):
                    self.selectors[f[:-4]] = parse_selector(os.path.join(COLOR_DIR, f))

    def color(self, v, night=False, _depth=0):
        if v is None or _depth > 12:
            return None
        v = v.strip()
        if v.startswith('@color/'):
            name = v.split('/', 1)[1]
            table = self.dark if night else self.light
            return self.color(table.get(name), night, _depth + 1)
        if v.startswith('@android:color/'):
            return {'transparent': '#00000000', 'white': '#FFFFFFFF', 'black': '#FF000000'}.get(
                v.split('/', 1)[1])
        if v.startswith('#'):
            return v
        return None

    def selector_color(self, name, state='checked', night=False):
        sel = self.selectors.get(name)
        if not sel:
            return None
        for st, val in sel:
            if st == state:
                return self.color(val, night)
        return self.color(sel[-1][1], night) if sel else None

    def dimen(self, v, _depth=0):
        if v is None or _depth > 8:
            return None
        v = v.strip()
        if v.startswith('@dimen/'):
            return self.dimen(self.dimens.get(v.split('/', 1)[1]), _depth + 1)
        m = re.match(r'^(-?[\d.]+)(dp|dip|sp|px)$', v)
        if m:
            return float(m.group(1))
        if re.match(r'^-?[\d.]+$', v):
            return float(v)
        return None

    def string(self, v):
        if not v:
            return None
        v = v.strip()
        if v.startswith('@string/'):
            name = v.split('/', 1)[1]
            if name in self.strings:
                return clean_text(self.strings[name])
            if name in self.arrays:
                return clean_text(self.arrays[name])
            return '{' + name + '}'
        if v.startswith('@plurals/'):
            return '{' + v.split('/', 1)[1] + '}'
        return clean_text(v)

    def style_items(self, name, _depth=0):
        if not name or _depth > 8:
            return {}
        st = self.styles.get(name)
        if not st:
            return {}
        merged = dict(self.style_items(st['parent'], _depth + 1))
        merged.update(st['items'])
        return merged


def parse_simple(path, tag):
    d = {}
    if not os.path.exists(path):
        return d
    for el in ET.parse(path).getroot():
        if local(el.tag) == tag:
            d[el.get('name')] = (el.text or '').strip()
    return d


def parse_arrays(path):
    d = {}
    if not os.path.exists(path):
        return d
    for el in ET.parse(path).getroot():
        if local(el.tag) in ('string-array', 'array'):
            for item in el:
                if local(item.tag) == 'item':
                    d[el.get('name')] = (item.text or '').strip()
                    break
        elif local(el.tag) == 'plurals':
            for item in el:
                if local(item.tag) == 'item':
                    d[el.get('name')] = (item.text or '').strip()
                    break
    return d


def parse_styles(path):
    out = {}
    if not os.path.exists(path):
        return out
    for el in ET.parse(path).getroot():
        if local(el.tag) != 'style':
            continue
        items = {}
        for it in el:
            if local(it.tag) == 'item':
                items[it.get('name')] = (it.text or '').strip()
        out[el.get('name')] = {'parent': el.get('parent'), 'items': items}
    return out


def parse_selector(path):
    out = []
    for el in ET.parse(path).getroot():
        if local(el.tag) != 'item':
            continue
        state = []
        color = None
        for k, v in el.attrib.items():
            name = local(k)
            if name == 'color':
                color = v
                continue
            if name.startswith('state_'):
                name = name[len('state_'):]
            if v == 'true':
                state.append(name)
        key = ','.join(sorted(state)) if state else 'default'
        if 'checked' in state:
            key = 'checked'
        out.append((key, (color if color is not None else (el.text or '')).strip()))
    return out


def clean_text(s):
    if s is None:
        return None
    s = s.replace('\\n', '\n').replace("\\'", "'").replace('\\"', '"')
    s = re.sub(r'%(?:\d+\$)?[sd]', '·', s)
    s = s.replace('\\@', '@').replace('\\?', '?')
    return s


def parse_shapes():
    """drawable/*.xml 里 <shape> 的填充/圆角/渐变;vector 交给 icons 导出。"""
    out = {}
    if not os.path.isdir(DRAWABLE_DIR):
        return out
    for f in sorted(os.listdir(DRAWABLE_DIR)):
        if not f.endswith('.xml'):
            continue
        name = f[:-4]
        try:
            root = ET.parse(os.path.join(DRAWABLE_DIR, f)).getroot()
        except Exception:
            continue
        if local(root.tag) == 'shape':
            info = {'kind': 'shape'}
            if (root.get('{%s}shape' % NS_ANDROID) or 'rectangle') == 'oval':
                info['oval'] = True
            for el in root:
                t = local(el.tag)
                if t == 'solid':
                    info['solid'] = el.get('{%s}color' % NS_ANDROID)
                elif t == 'gradient':
                    info['gradient'] = {
                        'start': el.get('{%s}startColor' % NS_ANDROID),
                        'end': el.get('{%s}endColor' % NS_ANDROID),
                        'angle': el.get('{%s}angle' % NS_ANDROID) or '270',
                        'type': el.get('{%s}type' % NS_ANDROID) or 'linear',
                    }
                elif t == 'corners':
                    info['radius'] = el.get('{%s}radius' % NS_ANDROID)
                    info['radiusTop'] = el.get('{%s}topLeftRadius' % NS_ANDROID)
                elif t == 'stroke':
                    info['stroke'] = (el.get('{%s}color' % NS_ANDROID),
                                      el.get('{%s}width' % NS_ANDROID))
            out[name] = info
        elif local(root.tag) == 'layer-list':
            out[name] = {'kind': 'layer-list'}
    return out


class Node:
    __slots__ = ('cls', 'attrs', 'children', 'style', 'checked', 'hidden')

    def __init__(self, cls, attrs):
        self.cls = cls
        self.attrs = attrs
        self.children = []
        self.style = {}
        self.checked = None
        self.hidden = False

    @property
    def short(self):
        return self.cls.rsplit('.', 1)[-1]


def build(el):
    n = Node(el.tag, attrs_of(el))
    if (n.attrs.get('a:visibility') or '') == 'gone':
        n.hidden = True
    for c in el:
        if local(c.tag) in ('item', 'style'):
            continue
        n.children.append(build(c))
    return n


def parse_layout(path):
    return build(ET.parse(path).getroot())


def find_by_id(node, view_id):
    if node.attrs.get('a:id', '').endswith('/' + view_id):
        return node
    for c in node.children:
        r = find_by_id(c, view_id)
        if r is not None:
            return r
    return None


# ---------------------------------------------------------------- 组合成品画板

def compose(chrome, chain):
    """chain: [(layout_file, container_id, segment_index_or_None), ...]"""
    root = parse_layout(os.path.join(LAYOUT_DIR, chrome))
    host = root
    for i, (lp, cid, seg) in enumerate(chain):
        frag = parse_layout(os.path.join(LAYOUT_DIR, lp))
        target = find_by_id(host, cid)
        if target is None:
            return root
        target.children = [frag]
        if seg is not None:
            mark_segment(frag, seg)
        host = frag
    return root


def mark_segment(frag, index):
    def apply(c):
        c.checked = index
        for i, ch in enumerate(c.children):
            ch.checked = (i == index)
    for n in frag.children:
        if n.short == 'LinearLayout':
            for c in n.children:
                if c.short == 'MaterialButtonToggleGroup':
                    apply(c)
                    return
    for c in frag.children:
        if c.short == 'MaterialButtonToggleGroup':
            apply(c)
            return


class Renderer:
    def __init__(self, tables, shapes, night, meta):
        self.T = tables
        self.shapes = shapes
        self.night = night
        self.meta = meta
        self.body = []
        self.defs = []
        self.flat = []
        self.anon = {}
        self._style_cache = {}
        self.max_bottom = 0.0   # 渲染中实际用到的最低边界(用于 part 画板自适应高度)

    # ------------------------------------------------------------ 属性/令牌
    def style(self, node):
        key = id(node)
        if key in self._style_cache:
            return self._style_cache[key]
        items = {}
        name = node.attrs.get('a:style') or node.attrs.get('style')
        if name and name.startswith('@style/'):
            name = name.split('/', 1)[1]
        if name:
            items = dict(self.T.style_items(name))
        merged = items
        self._style_cache[key] = merged
        return merged

    def A(self, node, name):
        for k in ('a:' + name, 'app:' + name):
            if k in node.attrs:
                return node.attrs[k]
        st = self.style(node)
        for k in ('android:' + name, 'app:' + name, name):
            if k in st:
                return st[k]
        return None

    def text_appearance(self, node):
        name = self.A(node, 'textAppearance')
        if name and name.startswith('@style/'):
            return self.T.style_items(name.split('/', 1)[1])
        return {}

    def color_token(self, node, names):
        for nm in names:
            v = self.A(node, nm)
            if v:
                c = self.T.color(v, self.night)
                if c:
                    return v, c
        return None, None

    def fill_of(self, node, default=None):
        """背景填充:(fill, opacity, radius, token)"""
        bg = self.A(node, 'background')
        radius = self.radius_of(node)
        if bg and bg.startswith('@drawable/'):
            sh = self.shapes.get(bg.split('/', 1)[1])
            if sh and sh.get('kind') == 'shape':
                if sh.get('solid'):
                    c = self.T.color(sh['solid'], self.night)
                    f, o = hex_to_svg(c)
                    return f, o, radius, sh['solid']
                if sh.get('gradient'):
                    return 'URLGRAD:' + bg.split('/', 1)[1], 1.0, radius, None
        if bg and (bg.startswith('@color/') or bg.startswith('#')):
            c = self.T.color(bg, self.night)
            f, o = hex_to_svg(c)
            return f, o, radius, bg
        if default:
            c = self.T.color(default, self.night)
            f, o = hex_to_svg(c)
            return f, o, radius, default
        return None, 1.0, radius, None

    def bg_oval(self, node):
        bg = self.A(node, 'background')
        if bg and bg.startswith('@drawable/'):
            sh = self.shapes.get(bg.split('/', 1)[1]) or {}
            return bool(sh.get('oval'))
        return False

    def radius_of(self, node):
        for nm in ('cardCornerRadius', 'cornerRadius'):
            v = self.A(node, nm)
            if v:
                d = self.T.dimen(v)
                if d is not None:
                    return d
        bg = self.A(node, 'background')
        if bg and bg.startswith('@drawable/'):
            sh = self.shapes.get(bg.split('/', 1)[1]) or {}
            if sh.get('radius'):
                d = self.T.dimen(sh['radius'])
                if d is not None:
                    return d
            if sh.get('radiusTop'):
                d = self.T.dimen(sh['radiusTop'])
                if d is not None:
                    return d
        return None

    def _dim_attr(self, node, name):
        v = self.A(node, name)
        if not v:
            return None
        return self.T.dimen(v)

    def pads(self, node):
        all_ = self._dim_attr(node, 'padding')
        ph = self._dim_attr(node, 'paddingHorizontal')
        pv = self._dim_attr(node, 'paddingVertical')

        def pick(*vals):
            for v in vals:
                if v is not None:
                    return v
            return 0.0

        return (pick(self._dim_attr(node, 'paddingStart'), self._dim_attr(node, 'paddingLeft'), ph, all_),
                pick(self._dim_attr(node, 'paddingTop'), pv, all_),
                pick(self._dim_attr(node, 'paddingEnd'), self._dim_attr(node, 'paddingRight'), ph, all_),
                pick(self._dim_attr(node, 'paddingBottom'), pv, all_))

    def margins(self, node):
        all_ = self._dim_attr(node, 'layout_margin')
        mh = self._dim_attr(node, 'layout_marginHorizontal')
        mv = self._dim_attr(node, 'layout_marginVertical')

        def pick(*vals):
            for v in vals:
                if v is not None:
                    return v
            return 0.0

        return (pick(self._dim_attr(node, 'layout_marginStart'), self._dim_attr(node, 'layout_marginLeft'), mh, all_),
                pick(self._dim_attr(node, 'layout_marginTop'), mv, all_),
                pick(self._dim_attr(node, 'layout_marginEnd'), self._dim_attr(node, 'layout_marginRight'), mh, all_),
                pick(self._dim_attr(node, 'layout_marginBottom'), mv, all_))

    def text_of(self, node):
        return self.T.string(self.A(node, 'text'))

    def text_size(self, node, default=14.0):
        v = self.A(node, 'textSize')
        d = self.T.dimen(v) if v else None
        if d:
            return d
        ta = self.text_appearance(node)
        for k in ('android:textSize', 'textSize'):
            if k in ta:
                d = self.T.dimen(ta[k])
                if d:
                    return d
        return default

    def text_style(self, node):
        st = self.A(node, 'textStyle') or ''
        ta = self.text_appearance(node)
        st += ' ' + (ta.get('android:textStyle') or ta.get('textStyle') or '')
        return 'bold' if 'bold' in st else 'normal'

    def letter_spacing(self, node):
        v = self.A(node, 'letterSpacing')
        if v is None:
            ta = self.text_appearance(node)
            v = ta.get('android:letterSpacing') or ta.get('letterSpacing')
        try:
            return float(v)
        except Exception:
            return 0.0

    def text_color(self, node):
        _, c = self.color_token(node, ['textColor'])
        if c:
            f, o = hex_to_svg(c)
            if f:
                return f, o
        tv = self.A(node, 'textColor')
        if tv and tv.startswith('@color/'):
            sc = self.T.selector_color(tv.split('/', 1)[1],
                                       'checked' if node.checked else 'default', self.night)
            if sc:
                f, o = hex_to_svg(sc)
                if f:
                    return f, o
        ta = self.text_appearance(node)
        for k in ('android:textColor', 'textColor'):
            if k in ta:
                c = self.T.color(ta[k], self.night)
                f, o = hex_to_svg(c)
                if f:
                    return f, o
        f, o = hex_to_svg(self.T.color('@color/c_foreground', self.night))
        return f, o

    # ------------------------------------------------------------ 测量
    def size_of(self, v, avail):
        if v is None:
            return ('wrap', None)
        v = v.strip()
        if v in ('match_parent', 'fill_parent'):
            return ('match', avail)
        if v == 'wrap_content':
            return ('wrap', None)
        if v in ('0dp', '0px', '0dip'):
            return ('zero', 0.0)
        d = self.T.dimen(v)
        if d is not None:
            return ('fixed', d)
        return ('wrap', None)

    def orient_of(self, node):
        v = self.A(node, 'orientation')
        if v:
            return v
        # MaterialButtonToggleGroup 默认横向(Material 规范);线性布局默认纵向
        return 'horizontal' if node.short == 'MaterialButtonToggleGroup' else 'vertical'

    def is_weighted(self, node):
        w = self.A(node, 'layout_weight')
        try:
            return float(w) > 0
        except Exception:
            return False

    def weight(self, node):
        try:
            return float(self.A(node, 'layout_weight'))
        except Exception:
            return 0.0

    def text_width(self, s, size):
        return sum(size * (1.0 if ord(ch) > 0x2E80 else 0.55) for ch in s)

    def wrap_lines(self, text, size, max_w, lsx=0.0):
        """按可用宽度折行;lsx 为字距(每字之间的额外间距,SVG 与 Android 一致)
        容差 0.5dp:刚好排满的行不要因为浮点误差被折成两行。"""
        if not text:
            return []
        lines = []
        for para in str(text).split('\n'):
            cur, curw = '', 0.0
            for ch in para:
                w = size * (1.0 if ord(ch) > 0x2E80 else 0.55) + (lsx if cur else 0.0)
                if cur and curw + w > max_w + 0.5:
                    lines.append(cur)
                    cur, curw = ch, w
                else:
                    cur += ch
                    curw += w
            lines.append(cur)
        return lines

    def measure(self, node, aw, ah, depth=0):
        """返回 (w, h) 的 wrap 尺寸估计"""
        if depth > 24:
            return (0.0, 0.0)
        pl, pt, pr, pb = self.pads(node)
        t = node.short
        if t == 'TextView':
            size = self.text_size(node)
            text = self.text_of(node) or ''
            maxw = max(4.0, aw - pl - pr)
            lsx = self.letter_spacing(node) * size
            lines = self.wrap_lines(text, size, maxw, lsx)
            h = sum(size * 1.35 for _ in lines) + pt + pb
            w = max([self.text_width(x, size) + lsx * max(0, len(x) - 1)
                     for x in lines] or [0]) + pl + pr
            return (min(w, aw) if w > 0 else aw, h)
        if t in ('ImageView', 'ImageButton'):
            return (24.0 + pl + pr, 24.0 + pt + pb)
        if t in ('MaterialSwitch', 'SwitchMaterial', 'SwitchCompat', 'Switch'):
            return (52.0 + pl + pr, 32.0 + pt + pb)
        if t == 'CircularProgressIndicator':
            d = self.T.dimen(self.A(node, 'indicatorSize')) or 48.0
            return (d + pl + pr, d + pt + pb)
        if t in ('ProgressBar',):
            return (aw - pl - pr, 4.0 + pt + pb)
        if t == 'RecyclerView':
            return (aw - pl - pr, 3 * 56.0 + pt + pb)
        if t == 'TabLayout':
            d = self.T.dimen(self.A(node, 'layout_height'))
            return (aw - pl - pr, (d or 60.0) + pt + pb)
        if t in ('TextInputLayout', 'TextInputEditText'):
            return (aw - pl - pr, 56.0 + pt + pb)
        if t == 'MaterialButton':
            d = self.T.dimen(self.A(node, 'layout_height'))
            if d:
                return (aw - pl - pr, d + pt + pb)
            text = self.text_of(node) or ''
            size = self.text_size(node, 16.0)
            return (self.text_width(text, size) + pl + pr, max(48.0, size * 1.35 + pt + pb))
        if t in ('Space',):
            d = self.T.dimen(self.A(node, 'layout_height'))
            return (aw - pl - pr, (d or 0.0) + pt + pb)
        if t == 'View':
            d = self.T.dimen(self.A(node, 'layout_height'))
            return (aw - pl - pr, (d or 1.0) + pt + pb)
        # 容器
        inner_w = max(4.0, aw - pl - pr)
        inner_h = max(0.0, ah - pt - pb)
        orient = self.orient_of(node)
        boxes = self.place_children(node, 0.0, 0.0, inner_w, inner_h, depth + 1)
        if not boxes:
            return (aw - pl - pr, pt + pb + 8.0)
        if t in ('LinearLayout', 'RadioGroup', 'MaterialButtonToggleGroup') and orient == 'horizontal':
            w = sum(b[3] + self.margins(b[0])[0] + self.margins(b[0])[2] for b in boxes)
            h = max(b[4] + self.margins(b[0])[1] + self.margins(b[0])[3] for b in boxes)
        elif t in ('LinearLayout', 'RadioGroup', 'MaterialButtonToggleGroup'):
            w = max(b[3] + self.margins(b[0])[0] + self.margins(b[0])[2] for b in boxes)
            h = sum(b[4] + self.margins(b[0])[1] + self.margins(b[0])[3] for b in boxes)
        else:
            w = max(b[3] for b in boxes)
            h = max(b[4] for b in boxes)
        return (min(w, aw) + pl + pr, h + pt + pb)

    def place_children(self, node, cx, cy, cw, ch, depth=0):
        """返回 [(child, x, y, w, h)]"""
        kids = [k for k in node.children if not k.hidden]
        out = []
        if not kids:
            return out
        t = node.short
        orient = self.orient_of(node)
        if t in ('LinearLayout', 'RadioGroup', 'MaterialButtonToggleGroup'):
            if orient == 'horizontal':
                fixed = 0.0
                wmargin = 0.0
                for k in kids:
                    ml, mt, mr, mb = self.margins(k)
                    if self.is_weighted(k):
                        wmargin += ml + mr
                        continue
                    fixed += self.child_w(k, cw, ch, depth) + ml + mr
                # weight 子项自身 margin 也要从剩余空间里扣掉(Android 语义),否则右侧会溢出
                free = max(0.0, cw - fixed - wmargin)
                sw = sum(self.weight(k) for k in kids if self.is_weighted(k)) or 1.0
                pg = (self.A(node, 'gravity') or '').replace('|', ' ').split()
                x = cx
                for k in kids:
                    ml, mt, mr, mb = self.margins(k)
                    if self.is_weighted(k):
                        w = free * self.weight(k) / sw
                        h = self.child_h(k, cw, ch, depth, is_weighted=True)
                    else:
                        w, h = self.child_w(k, cw, ch, depth), self.child_h(k, cw, ch, depth)
                    toks = ((self.A(k, 'layout_gravity') or '') + ' ' + ' '.join(pg)).replace('|', ' ').split()
                    y = cy + mt
                    if 'bottom' in toks:
                        y = cy + max(0.0, ch - h - mb)
                    elif 'center' in toks or 'center_vertical' in toks:
                        y = cy + max(0.0, (ch - h) / 2.0 + (mt - mb) / 2.0)
                    out.append((k, x + ml, y, max(0.0, w), h))
                    x += ml + w + mr
            else:
                fixed = 0.0
                hmargin = 0.0
                for k in kids:
                    ml, mt, mr, mb = self.margins(k)
                    if self.is_weighted(k):
                        hmargin += mt + mb
                        continue
                    fixed += self.child_h(k, cw, ch, depth) + mt + mb
                free = max(0.0, ch - fixed - hmargin)
                sw = sum(self.weight(k) for k in kids if self.is_weighted(k)) or 1.0
                pg = (self.A(node, 'gravity') or '').replace('|', ' ').split()
                y = cy
                for k in kids:
                    ml, mt, mr, mb = self.margins(k)
                    if self.is_weighted(k):
                        h = free * self.weight(k) / sw
                        w = self.child_w(k, cw, ch, depth)
                    else:
                        w, h = self.child_w(k, cw, ch, depth), self.child_h(k, cw, ch, depth)
                    if self.size_of(self.A(k, 'layout_width'), cw)[0] == 'match':
                        w = max(0.0, w - ml - mr)
                    toks = ((self.A(k, 'layout_gravity') or '') + ' ' + ' '.join(pg)).replace('|', ' ').split()
                    x = cx + ml
                    if 'end' in toks or 'right' in toks:
                        x = cx + max(0.0, cw - w - mr)
                    elif 'center' in toks or 'center_horizontal' in toks:
                        x = cx + max(0.0, (cw - w) / 2.0 + (ml - mr) / 2.0)
                    out.append((k, x, y + mt, max(0.0, w), h))
                    y += mt + h + mb
            return out
        # FrameLayout / ScrollView / Card / 其它:叠放 + gravity
        for k in kids:
            ml, mt, mr, mb = self.margins(k)
            w = self.child_w(k, cw, ch, depth)
            if isinstance(w, tuple):
                w = w[0]
            h = self.child_h(k, cw, ch, depth)
            if self.size_of(self.A(k, 'layout_width'), cw)[0] == 'match':
                w = max(0.0, w - ml - mr)
            if self.size_of(self.A(k, 'layout_height'), ch)[0] == 'match':
                h = max(0.0, h - mt - mb)
            if t == 'ScrollView':
                h = max(h, ch)
            # 只看子项自己的 layout_gravity:子项的 android:gravity 只管它内部内容,不该挪动它本身
            toks = (self.A(k, 'layout_gravity') or '').replace('|', ' ').split()
            x, y = cx + ml, cy + mt
            if 'center' in toks or 'center_horizontal' in toks:
                x = cx + (cw - w) / 2.0
            elif 'end' in toks or 'right' in toks:
                x = cx + max(0.0, cw - w - mr)
            if 'bottom' in toks:
                y = cy + ch - h - mb
            elif 'center' in toks or 'center_vertical' in toks:
                y = cy + (ch - h) / 2.0
            out.append((k, x, y, w, h))
        return out

    def child_w(self, node, aw, ah, depth=0):
        mode, v = self.size_of(self.A(node, 'layout_width'), aw)
        if mode == 'fixed':
            return v
        if mode == 'match':
            return aw
        if mode == 'zero':
            return 0.0
        return self.measure(node, aw, ah, depth + 1)[0]

    def child_h(self, node, aw, ah, depth=0, is_weighted=False):
        mode, v = self.size_of(self.A(node, 'layout_height'), ah)
        if mode == 'fixed':
            return v
        if mode == 'match':
            return ah
        if mode == 'zero':
            return 0.0
        return self.measure(node, aw, ah, depth + 1)[1]

    # ------------------------------------------------------------ 绘制
    def grad_def(self, drawable_name, gid):
        sh = self.shapes.get(drawable_name) or {}
        g = sh.get('gradient') or {}
        c1 = hex_to_svg(self.T.color(g.get('start'), self.night))[0] or '#FFFFFF'
        c2 = hex_to_svg(self.T.color(g.get('end'), self.night))[0] or '#FFFFFF'
        angle = g.get('angle') or '270'
        vertical = angle in ('270', '90')
        coords = 'x1="0" y1="0" x2="0" y2="1"' if vertical else 'x1="0" y1="0" x2="1" y2="0"'
        self.defs.append(
            '  <linearGradient id="%s" %s>\n'
            '    <stop offset="0" stop-color="%s"/>\n'
            '    <stop offset="1" stop-color="%s"/>\n'
            '  </linearGradient>' % (esc(gid), coords, c1, c2))
        return 'url(#' + gid + ')'

    def grad_url(self, drawable_name):
        sh = self.shapes.get(drawable_name) or {}
        g = sh.get('gradient') or {}
        c1 = self.T.color(g.get('start'), self.night) or '#FFFFFF'
        c2 = self.T.color(g.get('end'), self.night) or '#FFFFFF'
        gid = 'grad_' + drawable_name + ('_night' if self.night else '_light')
        c1s = hex_to_svg(c1)[0]
        c2s = hex_to_svg(c2)[0]
        angle = g.get('angle') or '270'
        vertical = angle in ('270', '90')
        coords = 'x1="0" y1="0" x2="0" y2="1"' if vertical else 'x1="0" y1="0" x2="1" y2="0"'
        self.defs.append('  <linearGradient id="%s" %s gradientUnits="objectBoundingBox">\n'
                         '    <stop offset="0" stop-color="%s"/>\n'
                         '    <stop offset="1" stop-color="%s"/>\n'
                         '  </linearGradient>' % (esc(gid), coords, c1s, c2s))
        return 'url(#' + gid + ')'

    def view_id(self, node):
        v = node.attrs.get('a:id')
        if v:
            return v.split('/', 1)[-1]
        base = node.short
        self.anon[base] = self.anon.get(base, 0) + 1
        return base + str(self.anon[base])

    def layer_name(self, node, label):
        vid = node.attrs.get('a:id')
        if vid:
            return vid.split('/', 1)[-1]
        return label

    def open_group(self, node, label, x, y, w, h):
        vid = self.layer_name(node, label)
        self.body.append(
            '    <g id="%s" data-name="%s" data-type="%s" data-x="%g" data-y="%g" '
            'data-w="%g" data-h="%g">' % (esc(vid), esc(label), esc(node.short), x, y, w, h))

    def rect(self, x, y, w, h, fill, op=1.0, r=None, stroke=None, sw=1.0, dash=None):
        if w <= 0 or h <= 0 or (not fill and not stroke):
            return
        # 只描边不填充的框(fill=None)以前被整体丢弃 —— 例如 TextInputLayout 的输入框
        a = ' x="%g" y="%g" width="%g" height="%g" fill="%s"' % (x, y, w, h, fill or 'none')
        if op < 1.0:
            a += ' fill-opacity="%g"' % op
        if r:
            a += ' rx="%g" ry="%g"' % (r, r)
        if stroke:
            a += ' stroke="%s" stroke-width="%g"' % (stroke, sw)
        if dash:
            a += ' stroke-dasharray="%s"' % dash
        self.body.append('      <rect%s/>' % a)

    def text_block(self, node, text, x, y, w, h, default_size=14.0, color=None, align=None,
                   vcenter=False, nowrap=False):
        if not text:
            return
        size = self.text_size(node, default_size)
        weight = self.text_style(node)
        fill, op = color or self.text_color(node)
        pl, pt, pr, pb = self.pads(node)
        maxw = max(4.0, w - pl - pr)
        lines = ([text] if nowrap
                 else self.wrap_lines(text, size, maxw, self.letter_spacing(node) * size))
        g = (self.A(node, 'gravity') or '')
        if align is None:
            if 'center' in g:
                align = 'middle'
            elif 'end' in g or 'right' in g:
                align = 'end'
            else:
                align = 'start'
        tx = x + pl
        anchor = 'start'
        if align == 'middle':
            tx = x + w / 2.0
            anchor = 'middle'
        elif align == 'end':
            tx = x + w - pr
            anchor = 'end'
        ls = self.letter_spacing(node) * size
        self.body.append('      <text font-family="%s" font-size="%g" font-weight="%s" '
                         'fill="%s" text-anchor="%s" letter-spacing="%g" data-text="%s">'
                         % (FONT, size, weight, fill, anchor, ls, esc(text)))
        if op < 1.0:
            self.body[-1] = self.body[-1][:-1] + ' fill-opacity="%g">' % op
        if vcenter:
            total = len(lines) * size * 1.35
            y0 = y + (h - total) / 2.0 + size * 0.8
        else:
            y0 = y + pt + size * 0.85
        for i, line in enumerate(lines):
            ly = y0 + i * size * 1.35
            self.body.append('        <tspan x="%g" y="%g">%s</tspan>' % (tx, ly, esc(line)))
            self.max_bottom = max(self.max_bottom, ly + size * 0.35)
        self.body.append('      </text>')

    def draw_icon(self, name, x, y, size, tint):
        info = VECTORS.get(name)
        if not info:
            self.rect(x, y, size, size, None, stroke='#B9C4B7', sw=1.0, dash='3 3')
            return
        vw, vh = info['w'], info['h']
        s = size / max(vw, vh)
        tx = x + (size - vw * s) / 2.0
        ty = y + (size - vh * s) / 2.0
        self.body.append('      <g data-name="icon:%s" transform="translate(%g,%g) scale(%g)">'
                         % (esc(name), tx, ty, s))
        for p in info['paths']:
            # tint 同时作用于填充与描边,但不把"无填充"的线性图标变成实心块
            bf = svg_fill(self.T.color(p.get('fill'), self.night)) if p.get('fill') else None
            bs = svg_fill(self.T.color(p['stroke'], self.night)) if p.get('stroke') else None
            f = (tint or bf) if bf else None
            attrs_ = ' d="%s"' % esc(p['d'])
            attrs_ += (' fill="%s"' % f) if f else ' fill="none"'
            if p.get('stroke'):
                st = tint or bs or '#000000'
                attrs_ += ' stroke="%s" stroke-width="%s"' % (st, p.get('strokeWidth') or '1')
                if p.get('cap') and p['cap'] != 'butt':
                    attrs_ += ' stroke-linecap="%s"' % p['cap']
                if p.get('join') and p['join'] != 'miter':
                    attrs_ += ' stroke-linejoin="%s"' % p['join']
            self.body.append('        <path%s/>' % attrs_)
        self.body.append('      </g>')

    def render(self, node, x, y, w, h, depth=0):
        if node.hidden or depth > 30:
            return
        t = node.short
        label = node.attrs.get('a:id', '').split('/')[-1] or (
            (t + ': ' + self.text_of(node)) if self.text_of(node) else t)
        self.open_group(node, label, x, y, w, h)
        fill, op, radius, token = self.fill_of(node)
        if fill and fill.startswith('URLGRAD:'):
            fill = self.grad_url(fill.split(':', 1)[1])
        stroke = None
        if t in ('MaterialCardView', 'CardView') and not fill:
            fill = hex_to_svg(self.T.color('@color/c_card', self.night))[0]
        if t in ('View',) and not fill:
            fill = hex_to_svg(self.T.color('@color/c_border', self.night))[0]
        if t == 'TextInputLayout':
            # Material 描边输入框:圆角描边 + hint(此前整块不画,设置 PIN 对话框看起来是空白)
            self.rect(x, y, w, h, None,
                      stroke=svg_fill(self.T.color('@color/c_border', self.night), '#B9C4B7'),
                      sw=1.0, r=4.0)
            hint = self.T.string(self.A(node, 'hint'))
            if hint:
                size = 16.0
                self.body.append('      <text x="%g" y="%g" font-family="%s" font-size="%g" '
                                 'fill="%s">%s</text>'
                                 % (x + 16.0, y + h / 2.0 + size * 0.35, FONT, size,
                                    svg_fill(self.T.color('@color/c_muted_foreground', self.night),
                                             '#6B6B6B'), esc(hint)))
        if t in ('MaterialButton', 'Button'):
            bt = self.A(node, 'backgroundTint')
            state = 'checked' if node.checked else 'default'
            if bt and bt.startswith('@color/'):
                nm = bt.split('/', 1)[1]
                c = self.T.selector_color(nm, state, self.night) or self.T.color(bt, self.night)
                if c:
                    f2, o2 = hex_to_svg(c)
                    fill = f2 if o2 > 0.01 else None
            elif node.checked:
                fill = hex_to_svg(self.T.color('@color/c_primary', self.night))[0]
            # OutlinedButton:无 backgroundTint,只有 strokeColor + cornerRadius 描边
            sc = self.A(node, 'strokeColor')
            if sc and not fill and str(sc).startswith(('@color/', '#')):
                sfill = svg_fill(self.T.color(sc, self.night))
                if sfill:
                    cr = self._dim_attr(node, 'cornerRadius')
                    self.rect(x, y, w, h, None, stroke=sfill, sw=1.0,
                              r=cr if cr is not None else h / 2.0)
        if t in ('MaterialSwitch', 'SwitchMaterial', 'SwitchCompat'):
            # 轨道与滑块都按实际 checked 取色(布局未写 checked 即为关闭);
            # 有文本时文本在左、开关贴右,与 Material 一致。
            checked = bool(node.checked)
            state = 'checked' if checked else 'default'

            def tint(attr, fb):
                v = self.A(node, attr)
                c = None
                if v and str(v).startswith('@color/'):
                    nm = str(v).split('/', 1)[1]
                    c = self.T.selector_color(nm, state, self.night) or self.T.color(v, self.night)
                return svg_fill(c, fb)

            track = tint('trackTint', svg_fill(self.T.color(
                '@color/c_primary' if checked else '@color/c_border', self.night), '#D3D9D0'))
            thumb = tint('thumbTint', '#FFFFFF')
            pl, pt, pr, pb = self.pads(node)
            tw, thh = 52.0, 32.0
            label = self.text_of(node) or ''
            if label:
                self.text_block(node, label, x, y, max(4.0, w - tw - 12.0), h)
                tx = x + max(0.0, w - pr - tw)
            else:
                tx = x + pl
            ty = y + (h - thh) / 2.0
            self.rect(tx, ty, tw, thh, track, r=thh / 2.0)
            rad = 11.0
            cx = tx + (tw - rad - 5.0 if checked else rad + 5.0)
            self.body.append('      <circle cx="%g" cy="%g" r="%g" fill="%s" stroke="#D3D9D0" '
                             'stroke-width="1"/>' % (cx, ty + thh / 2.0, rad, thumb))
            self.body.append('    </g>')
            return
        if t in ('ProgressBar',):
            track = self.T.color('@color/c_muted', self.night)
            self.rect(x, y, w, min(8.0, h), hex_to_svg(track)[0], r=min(4.0, h / 2))
            self.rect(x, y, w * 0.4, min(8.0, h), hex_to_svg(self.T.color('@color/c_primary', self.night))[0],
                      r=min(4.0, h / 2))
            self.body.append('    </g>')
            return
        if t == 'CircularProgressIndicator':
            d = min(w, h)
            sw = self.T.dimen(self.A(node, 'trackThickness')) or 25.0
            cx, cy = x + w / 2.0, y + h / 2.0
            r = (d - sw) / 2.0
            tc = self.T.color(self.A(node, 'trackColor') or '@color/c_ring_track', self.night)
            ic = self.T.color(self.A(node, 'indicatorColor') or '@color/c_ring', self.night)
            self.body.append('      <circle cx="%g" cy="%g" r="%g" fill="none" stroke="%s" stroke-width="%g"/>'
                             % (cx, cy, r, hex_to_svg(tc)[0], sw))
            import math
            # 进度读 android:progress / android:max(与真机一致);读不到才用静态示意值
            pv = self.A(node, 'progress')
            mv = self.A(node, 'max') or '100'
            try:
                frac = float(str(pv).strip()) / max(1e-6, float(str(mv).strip()))
            except Exception:
                frac = 0.72
            frac = min(1.0, max(0.0, frac))
            if frac >= 0.999:
                self.body.append('      <circle cx="%g" cy="%g" r="%g" fill="none" stroke="%s" '
                                 'stroke-width="%g"/>' % (cx, cy, r, hex_to_svg(ic)[0], sw))
                self.body.append('    </g>')
                return
            a0 = -90.0
            a1 = -90.0 + 360.0 * frac
            p0 = (cx + r * math.cos(math.radians(a0)), cy + r * math.sin(math.radians(a0)))
            p1 = (cx + r * math.cos(math.radians(a1)), cy + r * math.sin(math.radians(a1)))
            self.body.append('      <path d="M %g %g A %g %g 0 %d 1 %g %g" fill="none" stroke="%s" '
                             'stroke-width="%g" stroke-linecap="round"/>'
                             % (p0[0], p0[1], r, r, 1 if frac > 0.5 else 0, p1[0], p1[1],
                                hex_to_svg(ic)[0], sw))
            self.body.append('    </g>')
            return
        if t == 'RecyclerView':
            self.rect(x, y, w, h, None, stroke='#9FB79C', sw=1.0, dash='4 4', r=8.0)
            hint = ITEM_HINTS.get(self.meta.get('key'), '列表项 item_*.xml')
            rows = min(3, max(1, int(h // 56)))
            for i in range(rows):
                self.rect(x + 8.0, y + 8.0 + i * 56.0, w - 16.0, 48.0,
                          hex_to_svg(self.T.color('@color/c_muted', self.night))[0], r=8.0)
            self.body.append(
                '      <text font-family="%s" font-size="10" fill="#7A8A79" x="%g" y="%g">RecyclerView · %s</text>'
                % (FONT, x + 8.0, y + h - 8.0, esc(hint)))
        elif t == 'TabLayout':
            self.rect(x, y, w, h, fill or hex_to_svg(self.T.color('@color/c_nav', self.night))[0],
                      r=radius if radius else 24.0)
            tabs = NAV_TABS
            tw = w / max(1, len(tabs))
            sel = self.meta.get('tab', self.meta.get('navSelected', 0)) or 0
            for i, (icon, title) in enumerate(tabs):
                cx = x + tw * i + tw / 2.0
                col = '#04BD19' if (not self.night and i == sel) else (
                    '#31D027' if self.night and i == sel else (
                        '#6B6B6B' if not self.night else '#A9B3A7'))
                self.draw_icon(icon, cx - 12.0, y + h / 2.0 - 20.0, 24.0, col)
                self.body.append('      <text font-family="%s" font-size="12" font-weight="bold" '
                                 'fill="%s" text-anchor="middle" x="%g" y="%g">%s</text>'
                                 % (FONT, col, cx, y + h / 2.0 + 18.0, esc(title)))
        elif t in ('ImageView', 'ImageButton'):
            src = self.A(node, 'src') or self.A(node, 'app:srcCompat')
            size = min(w, h) if min(w, h) > 0 else 24.0
            tint = self.A(node, 'tint') or self.A(node, 'imageTint')
            tintc = hex_to_svg(self.T.color(tint, self.night))[0] if tint else None
            if src and src.startswith('@drawable/'):
                nm = src.split('/', 1)[1]
                if nm in VECTORS:
                    self.draw_icon(nm, x + (w - size) / 2.0, y + (h - size) / 2.0, size, tintc)
                else:
                    self.rect(x, y, w, h, fill, op, radius)
                    self.body.append('      <text font-family="%s" font-size="9" fill="#7A8A79" '
                                     'text-anchor="middle" x="%g" y="%g">%s</text>'
                                     % (FONT, x + w / 2.0, y + h / 2.0, esc(nm)))
                    self.body.append('    </g>')
                    return
            elif fill:
                self.rect(x, y, w, h, fill, op, radius)
            else:
                self.rect(x, y, w, h, None, stroke='#B9C4B7', sw=1.0, dash='3 3', r=6.0)
        else:
            if fill and self.bg_oval(node):
                self.body.append('      <ellipse cx="%g" cy="%g" rx="%g" ry="%g" fill="%s"%s/>'
                                 % (x + w / 2.0, y + h / 2.0, w / 2.0, h / 2.0, fill,
                                    '' if op >= 1.0 else ' fill-opacity="%g"' % op))
            elif fill:
                self.rect(x, y, w, h, fill, op, radius)
            elif t in ('MaterialCardView', 'CardView'):
                self.rect(x, y, w, h, hex_to_svg(self.T.color('@color/c_card', self.night))[0],
                          r=radius if radius else 24.0)
        # 文本
        if t in ('TextView',):
            self.text_block(node, self.text_of(node) or '', x, y, w, h)
        elif t == 'MaterialButton':
            txt = self.text_of(node) or ''
            icv = self.A(node, 'icon')
            iname = icv.split('/', 1)[1] if (icv and str(icv).startswith('@drawable/')) else None
            if iname and iname in VECTORS:
                isize = self._dim_attr(node, 'iconSize') or 24.0
                ipad = self._dim_attr(node, 'iconPadding') or 8.0
                itint = self.A(node, 'iconTint')
                icol = svg_fill(self.T.color(itint, self.night)) if itint else None
                if not icol:
                    icol = svg_fill(self.text_color(node)[0], '#6B6B6B')
                pl2, _, pr2, _ = self.pads(node)
                grav = (self.A(node, 'gravity') or '').replace('|', ' ')
                lead = ('start' in grav or 'left' in grav
                        or 'textstart' in str(self.A(node, 'iconGravity') or '').lower())
                if txt:
                    tsz = self.text_size(node, 16.0)
                    tw_ = self.text_width(txt, tsz) + self.letter_spacing(node) * tsz * len(txt)
                    if lead:
                        # iconGravity=textStart / android:gravity=start:图标在左、文字左对齐
                        self.draw_icon(iname, x + pl2, y + (h - isize) / 2.0, isize, icol)
                        self.text_block(node, txt, x + isize + ipad, y,
                                        max(4.0, w - isize - ipad), h,
                                        default_size=16.0, align='start', vcenter=True, nowrap=True)
                    else:
                        left = x + (w - (isize + ipad + tw_)) / 2.0
                        self.draw_icon(iname, left, y + (h - isize) / 2.0, isize, icol)
                        self.text_block(node, txt, left + isize + ipad, y,
                                        max(4.0, tw_), h,
                                        default_size=16.0, align='middle', vcenter=True, nowrap=True)
                else:
                    self.draw_icon(iname, x + (w - isize) / 2.0, y + (h - isize) / 2.0, isize, icol)
            else:
                grav0 = (self.A(node, 'gravity') or '').replace('|', ' ')
                al = 'start' if ('start' in grav0 or 'left' in grav0) else 'middle'
                self.text_block(node, txt, x, y, w, h, default_size=16.0,
                                align=al, vcenter=True, nowrap=True)
        elif t in ('TextInputEditText', 'EditText'):
            self.text_block(node, self.text_of(node) or self.A(node, 'hint') or '', x, y, w, h)
        # 子节点
        pl, pt, pr, pb = self.pads(node)
        inner = (x + pl, y + pt, max(0.0, w - pl - pr), max(0.0, h - pt - pb))
        for child, cx, cy, cw_, ch_ in self.place_children(node, *inner, depth + 1):
            self.render(child, cx, cy, cw_, ch_, depth + 1)
        self.max_bottom = max(self.max_bottom, y + h)
        self.flat.append({'id': self.layer_name(node, label), 'type': node.short, 'text': self.text_of(node),
                          'x': round(x, 1), 'y': round(y, 1), 'w': round(w, 1), 'h': round(h, 1)})
        self.body.append('    </g>')

    def svg(self, root, w, h):
        self.render(root, 0.0, 0.0, w, h)
        head = ['<?xml version="1.0" encoding="UTF-8"?>',
                '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"'
                ' width="%g" height="%g" viewBox="0 0 %g %g"'
                % (w * SCALE, h * SCALE, w, h),
                '     data-export="design-export/export_ui.py" data-mode="%s"'
                % ('night' if self.night else 'light'),
                '     data-source="%s">' % esc(self.meta.get('source', '')),
                '  <title>%s</title>' % esc(self.meta.get('title', '')),
                '  <desc>%s</desc>' % esc(self.meta.get('desc', ''))]
        defs = ['  <defs>'] + self.defs + ['  </defs>'] if self.defs else []
        return '\n'.join(head + defs + self.body + ['</svg>', ''])

# ---------------------------------------------------------------- 图标(vector -> SVG)

def load_vectors():
    out = {}
    if not os.path.isdir(DRAWABLE_DIR):
        return out
    for f in sorted(os.listdir(DRAWABLE_DIR)):
        if not f.endswith('.xml'):
            continue
        try:
            root = ET.parse(os.path.join(DRAWABLE_DIR, f)).getroot()
        except Exception:
            continue
        if local(root.tag) != 'vector':
            continue
        w = num(root.get('{%s}viewportWidth' % NS_ANDROID), 24.0)
        h = num(root.get('{%s}viewportHeight' % NS_ANDROID), 24.0)
        paths = []
        for el in root.iter():
            if local(el.tag) != 'path':
                continue
            paths.append({
                'd': el.get('{%s}pathData' % NS_ANDROID) or '',
                'fill': el.get('{%s}fillColor' % NS_ANDROID),
                'stroke': el.get('{%s}strokeColor' % NS_ANDROID),
                'strokeWidth': el.get('{%s}strokeWidth' % NS_ANDROID),
                'cap': el.get('{%s}strokeLineCap' % NS_ANDROID),
                'join': el.get('{%s}strokeLineJoin' % NS_ANDROID),
            })
        out[f[:-4]] = {'w': w, 'h': h, 'paths': paths}
    return out


def load_nav(tables):
    tabs = []
    path = os.path.join(RES, 'menu', 'bottom_nav.xml')
    if not os.path.exists(path):
        return tabs
    for el in ET.parse(path).getroot():
        if local(el.tag) != 'item':
            continue
        a = attrs_of(el)
        tabs.append(((a.get('a:icon') or '').split('/')[-1], tables.string(a.get('a:title')) or ''))
    return tabs


ITEM_HINTS = {
    '02-detect-virus': 'item_scan_result.xml',
    '03-detect-trojan': 'item_trojan.xml',
    '04-protect-applock': 'item_lock_app.xml',
    '05-protect-audit': 'item_audit.xml',
    '06-protect-tools': 'item_tool.xml',
    'act-virus-center': 'item_tool.xml',
    'act-result-list': 'item_scan_result.xml',
    'frag-tools': 'item_tool.xml',
}

SCREENS = [
    {'key': '01-home', 'title': '首页 · 设备状态', 'tab': 0, 'chrome': 'activity_main.xml',
     'chain': [('fragment_dashboard.xml', 'container', None)],
     'desc': '默认板块:分数环 + 一键优化 + 四宫格入口'},
    {'key': '02-detect-virus', 'title': '检测 · 病毒扫描', 'tab': 1, 'chrome': 'activity_main.xml',
     'chain': [('fragment_detect.xml', 'container', 0), ('fragment_scanner.xml', 'detectContainer', None)],
     'desc': '检测板块第 1 段:扫描入口与结果'},
    {'key': '03-detect-trojan', 'title': '检测 · 木马查杀', 'tab': 1, 'chrome': 'activity_main.xml',
     'chain': [('fragment_detect.xml', 'container', 1), ('fragment_trojan.xml', 'detectContainer', None)],
     'desc': '检测板块第 2 段:木马查杀与感染列表'},
    {'key': '04-protect-applock', 'title': '防护 · 应用锁', 'tab': 2, 'chrome': 'activity_main.xml',
     'chain': [('fragment_protect.xml', 'container', 0), ('fragment_app_lock.xml', 'protectContainer', None)],
     'desc': '防护板块第 1 段:应用锁列表与 PIN 设置入口'},
    {'key': '05-protect-audit', 'title': '防护 · 权限审计', 'tab': 2, 'chrome': 'activity_main.xml',
     'chain': [('fragment_protect.xml', 'container', 1), ('fragment_permission_audit.xml', 'protectContainer', None)],
     'desc': '防护板块第 2 段:权限风险清单'},
    {'key': '06-protect-tools', 'title': '防护 · 工具箱', 'tab': 2, 'chrome': 'activity_main.xml',
     'chain': [('fragment_protect.xml', 'container', 2), ('fragment_tools.xml', 'protectContainer', None)],
     'desc': '防护板块第 3 段:工具箱'},
]

PARTS = [
    {'key': 'act-deep-scan', 'title': '独立页 · 深度扫描', 'file': 'activity_deep_scan.xml'},
    {'key': 'act-lock', 'title': '独立页 · 解锁(PIN 键盘)', 'file': 'activity_lock.xml'},
    {'key': 'act-result-list', 'title': '独立页 · 结果列表', 'file': 'activity_result_list.xml'},
    {'key': 'act-virus-center', 'title': '独立页 · 病毒风险中心', 'file': 'activity_virus_center.xml'},
    {'key': 'frag-dashboard', 'title': 'Fragment · 首页', 'file': 'fragment_dashboard.xml'},
    {'key': 'frag-detect', 'title': 'Fragment · 检测(分段外壳)', 'file': 'fragment_detect.xml'},
    {'key': 'frag-protect', 'title': 'Fragment · 防护(分段外壳)', 'file': 'fragment_protect.xml'},
    {'key': 'frag-scanner', 'title': 'Fragment · 病毒扫描', 'file': 'fragment_scanner.xml'},
    {'key': 'frag-trojan', 'title': 'Fragment · 木马查杀', 'file': 'fragment_trojan.xml'},
    {'key': 'frag-app-lock', 'title': 'Fragment · 应用锁', 'file': 'fragment_app_lock.xml'},
    {'key': 'frag-audit', 'title': 'Fragment · 权限审计', 'file': 'fragment_permission_audit.xml'},
    {'key': 'frag-tools', 'title': 'Fragment · 工具箱', 'file': 'fragment_tools.xml'},
    {'key': 'dialog-set-pin', 'title': '对话框 · 设置 PIN', 'file': 'dialog_set_pin.xml'},
    {'key': 'widget-security', 'title': '桌面小组件 · 安全状态', 'file': 'widget_security.xml'},
    {'key': 'item-audit', 'title': '列表项 · 权限审计行', 'file': 'item_audit.xml'},
    {'key': 'item-lock-app', 'title': '列表项 · 应用锁行', 'file': 'item_lock_app.xml'},
    {'key': 'item-scan-result', 'title': '列表项 · 扫描结果行', 'file': 'item_scan_result.xml'},
    {'key': 'item-tool', 'title': '列表项 · 工具/动作行', 'file': 'item_tool.xml'},
    {'key': 'item-trojan', 'title': '列表项 · 木马检测行', 'file': 'item_trojan.xml'},
]


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(text)


def has_type(node, kind, depth=0):
    if node.short == kind:
        return True
    if depth > 12:
        return False
    return any(has_type(c, kind, depth + 1) for c in node.children)


def weight_min_height(r, node, depth=0):
    """纵向容器里 0dp+weight 占位子项至少应占的高度之和。
    组件板没有父约束,这些子项会被压成几 dp 的细条(列表卡看起来像一条白线)。"""
    if depth > 12:
        return 0.0
    total = 0.0
    vertical = r.orient_of(node) == 'vertical'
    for c in node.children:
        lh = str(r.A(c, 'layout_height') or '').strip()
        wgt = str(r.A(c, 'layout_weight') or '').strip()
        if vertical and wgt not in ('', '0', '0.0') and (lh.startswith('0d') or lh in ('0', '0.0', '0px')):
            total += 120.0 if has_type(c, 'RecyclerView') else 56.0
        total += weight_min_height(r, c, depth + 1)
    return total


def frame_height(r, root, kind):
    mode, v = r.size_of(root.attrs.get('a:layout_height'), MIN_H)
    if mode == 'fixed':
        return max(120.0, v)
    if mode == 'match' and kind == 'screen':
        return MIN_H
    natural = r.measure(root, CANVAS_W, 0.0 if kind == 'part' else MIN_H)[1]
    return max(120.0, min(MIN_H, natural))


def render_frame(entry, night):
    import math
    T = Tables()

    def build():
        r = Renderer(T, SHAPES, night, {'key': entry['key'], 'title': entry['title'],
                                        'source': entry.get('source', ''), 'desc': entry.get('desc', ''),
                                        'tab': entry.get('tab', 0)})
        if entry['kind'] == 'screen':
            root = compose(entry['chrome'], entry['chain'])
        else:
            root = parse_layout(os.path.join(LAYOUT_DIR, entry['file']))
        return r, root

    r, root = build()
    h = frame_height(r, root, entry['kind'])
    if entry['kind'] == 'part' and not entry['key'].startswith(('item-', 'widget-')):
        extra = weight_min_height(r, root)
        if extra > 0:
            h = max(h, min(MIN_H, r.measure(root, CANVAS_W, 0.0)[1] + extra))
    svg = r.svg(root, CANVAS_W, h)
    flat = r.flat
    if entry['kind'] == 'part':
        # 组件板按实际内容自适应高度:measure() 的估算偏小时逐步长高,避免内容被裁掉
        for _ in range(4):
            need = r.max_bottom + 1.0
            if need <= h + 0.6:
                break
            h = max(120.0, min(MIN_H, math.ceil(need * 2.0) / 2.0))
            r, root = build()
            svg = r.svg(root, CANVAS_W, h)
            flat = r.flat
    return svg, flat, h


def icon_svgs(T):
    out = {}
    for name, info in VECTORS.items():
        vw, vh = info['w'], info['h']
        paths = []
        for p in info['paths']:
            f = T.color(p.get('fill'), False) if p.get('fill') else None
            attrs_ = ' d="%s"' % esc(p['d'])
            if f:
                attrs_ += ' fill="%s"' % (hex_to_svg(f)[0] if f != '#00000000' else 'none')
            else:
                attrs_ += ' fill="none"'
            if p.get('stroke'):
                attrs_ += ' stroke="%s" stroke-width="%s"' % (
                    hex_to_svg(T.color(p['stroke'], False))[0] or '#000000', p.get('strokeWidth') or '1')
            paths.append('  <path%s/>' % attrs_)
        out[name] = ('<?xml version="1.0" encoding="UTF-8"?>\n'
                     '<svg xmlns="http://www.w3.org/2000/svg" width="%g" height="%g" viewBox="0 0 %g %g"'
                     ' data-export="design-export/export_ui.py" data-source="app/src/main/res/drawable/%s.xml">\n'
                     '  <title>%s</title>\n%s\n</svg>\n'
                     % (vw, vh, vw, vh, name, name, '\n'.join(paths)))
    return out


def dtcg_hex(argb):
    f, o = hex_to_svg(argb)
    if f is None:
        return None
    if o < 1.0:
        return '%s%02X' % (f, int(round(o * 255)))
    return f


def tokens_payload(T):
    core_colors, night_colors = {}, {}
    for name in sorted(T.light.keys()):
        light = T.color('@color/' + name, False)
        dark = T.color('@color/' + name, True)
        if light:
            core_colors[name] = {'$type': 'color', '$value': dtcg_hex(light),
                                 '$extensions': {'android': {'resource': '@color/' + name}}}
        if dark and dark != light:
            night_colors[name] = {'$type': 'color', '$value': dtcg_hex(dark),
                                  '$extensions': {'android': {'resource': '@color/' + name}}}
    dims = {}
    for name, raw in sorted(T.dimens.items()):
        d = T.dimen('@dimen/' + name)
        dims[name] = {'$type': 'dimension', '$value': ('%gdp' % d) if d is not None else raw,
                      '$extensions': {'android': {'resource': '@dimen/' + name, 'unit': 'dp'}}}
    typo = {}
    for name in sorted(T.styles.keys()):
        if not name.startswith('TextAppearance.SecureDroid.'):
            continue
        items = T.style_items(name)
        size = T.dimen(items.get('android:textSize'))
        if size is None:
            continue
        typo[name.split('.')[-1]] = {
            '$type': 'typography',
            '$value': {
                'fontFamily': 'Noto Sans CJK SC',
                'fontSize': '%gdp' % size,
                'fontWeight': 700 if 'bold' in (items.get('android:textStyle') or '') else 400,
                'letterSpacing': items.get('android:letterSpacing') or '0',
                'lineHeight': '%gdp' % (size * 1.35),
            },
            '$extensions': {'android': {'style': name,
                                        'lineSpacingExtra': items.get('android:lineSpacingExtra')}},
        }
    return {
        '$description': 'SecureDroid 界面令牌(W3C DTCG 格式)。core = 浅色基准,night = 深色覆盖;'
                        '来源 app/src/main/res/values 与 values-night。',
        'core': {'color': core_colors, 'dimension': dims, 'typography': typo},
        'night': {'color': night_colors},
        '$themes': [
            {'id': 'light', 'name': 'Light', 'selectedTokenSets': {'core': 'enabled', 'night': 'disabled'}},
            {'id': 'night', 'name': 'Night', 'selectedTokenSets': {'core': 'enabled', 'night': 'enabled'}},
        ],
    }


def tokens_csv(T):
    rows = [['Token', 'Type', 'Light', 'Night', 'Android 资源', '说明']]
    for name in sorted(T.light.keys()):
        light = dtcg_hex(T.color('@color/' + name, False) or '')
        dark = dtcg_hex(T.color('@color/' + name, True) or '')
        raw = T.light.get(name, '')
        rows.append([name, 'color', light or '', dark or '', '@color/' + name,
                     ('别名 → ' + raw) if raw.startswith('@') else ''])
    for name in sorted(T.dimens.keys()):
        d = T.dimen('@dimen/' + name)
        rows.append([name, 'dimension', ('%gdp' % d) if d is not None else '', '', '@dimen/' + name, ''])
    for st_name in sorted(T.styles.keys()):
        if not st_name.startswith('TextAppearance.SecureDroid.'):
            continue
        items = T.style_items(st_name)
        size = T.dimen(items.get('android:textSize'))
        if size is None:
            continue
        rows.append([st_name.split('.')[-1], 'typography', 'textSize %gdp' % size, '',
                     '@style/' + st_name,
                     'bold' if 'bold' in (items.get('android:textStyle') or '') else 'regular'])
    return '\n'.join(','.join('"' + str(c).replace('"', '""') + '"' for c in row) for row in rows) + '\n'

DIM_USAGE = {
    'sd_space_1': '4dp 网格', 'sd_space_2': '8dp', 'sd_space_3': '12dp', 'sd_space_4': '16dp',
    'sd_space_5': '24dp', 'sd_gutter': '页面左右留白', 'sd_touch_min': '最小触摸目标',
    'sd_row_height': '列表行高', 'sd_radius_card': '卡片圆角', 'sd_radius_inner': '内层圆角',
    'sd_radius_chip': 'Chip 圆角', 'sd_radius_pill': '胶囊(按钮/导航)', 'sd_hairline_width': '分隔线粗细',
    'sd_home_margin': '首页外边距', 'sd_home_title_top': '标题上边距', 'sd_ring_size': '分数环直径',
    'sd_ring_stroke': '分数环描边', 'sd_ring_disc': '环内圆盘直径', 'sd_ring_top': '环上边距',
    'sd_status_top': '状态文案上边距', 'sd_cta_height': '主按钮高', 'sd_cta_inset': '主按钮左右内缩',
    'sd_cta_top': '主按钮上边距', 'sd_grid_top': '宫格上边距', 'sd_grid_gap': '宫格间距',
    'sd_tile_height': '宫格卡高', 'sd_tile_icon': '宫格图标', 'sd_tile_padding': '宫格卡内边距',
    'sd_tabbar_height': '悬浮导航高', 'sd_nav_margin_h': '导航左右外边距', 'sd_nav_margin_bottom': '导航贴底边距',
    'sd_nav_radius': '导航圆角', 'sd_nav_clearance': '内容为导航预留的高度', 'sd_btn_height': '通用按钮高',
    'sd_segment_hit': '分段控件触摸高', 'sd_divider_inset': '列表分隔线缩进',
}
COLOR_USAGE = {
    'c_primary': '品牌绿 / 主按钮', 'c_on_primary': '主按钮文字', 'c_primary_container': '容器浅绿',
    'c_on_primary_container': '容器浅绿上的文字', 'c_accent': '强调(深绿)', 'c_on_accent': '强调上的文字',
    'c_background': '窗口底色', 'c_bg_top': '渐变顶', 'c_bg_bottom': '渐变底 / 系统导航栏',
    'c_foreground': '正文', 'c_card': '卡片', 'c_nav': '悬浮导航', 'c_muted': '次级底 / 涟漪',
    'c_muted_foreground': '次级文字 / 图标', 'c_border': '描边 / 分隔线', 'c_destructive': '风险红',
    'c_on_destructive': '风险红上的文字', 'c_gold': '警告金', 'c_success': '安全绿',
    'c_ring': '分数环弧线', 'c_ring_track': '分数环底轨', 'c_ring_disc': '环内圆盘',
    'ic_launcher_background': '应用图标底色',
}
TA_USAGE = {
    'Score': '首页环内分数', 'Title': '页面标题', 'Headline': '卡片标题', 'Status': '状态文案',
    'Body': '正文', 'Tile': '宫格副标题', 'Label': '小标签', 'Section': '小节标签',
    'Caption': '说明文字', 'Nav': '底部导航文字', 'Keypad': 'PIN 键盘数字',
}


def structure_md(T, frames, icons, stamp):
    BT = chr(96)
    FENCE = BT * 3
    L = []
    A = L.append
    A('# SecureDroid 界面结构(自动导出)')
    A('')
    A('> 本文件由 ' + BT + 'design-export/export_ui.py' + BT + ' 从 ' + BT + 'app/src/main/res' + BT +
      ' 生成,生成时间 ' + stamp + '。信息源是布局 XML 与令牌 XML,不是截图。')
    A('> 重新生成:' + BT + 'python design-export/export_ui.py' + BT + '。')
    A('')
    A('## 0. 画布与命名约定')
    A('')
    A('| 项 | 值 |')
    A('| --- | --- |')
    A('| 画布宽度 | 360dp(SVG viewBox 宽 360,width/height 放大 %g 倍 = 1080 宽)|' % SCALE)
    A('| 屏幕高度 | 800dp;内容超出部分按真机滚动裁切 |')
    A('| 单位 | dp / sp 原值;1dp = 1 SVG 用户单位 |')
    A('| 图层命名 | 有 ' + BT + 'android:id' + BT + ' 的取 id;没有的取「类型: 文本」|')
    A('| 进度类 | 分数环 72%、横向进度条 40%,是静态示意值 |')
    A('| 主题 | 同结构两套配色:light / night,见 tokens/design-tokens.json |')
    A('')
    A('## 1. 信息架构')
    A('')
    A(FENCE)
    A('Theme.SecureDroid(activity_main.xml:FrameLayout + bg_page 渐变底)')
    A('├── container(FrameLayout,weight=1,paddingBottom sd_nav_clearance=72dp)')
    A('│   ├── 首页    DashboardFragment          fragment_dashboard.xml')
    A('│   ├── 检测    DetectFragment             fragment_detect.xml')
    A('│   │   ├── seg 0  病毒扫描   ScannerFragment          fragment_scanner.xml')
    A('│   │   └── seg 1  木马查杀   TrojanFragment           fragment_trojan.xml')
    A('│   └── 防护    ProtectFragment            fragment_protect.xml')
    A('│       ├── seg 0  应用锁     AppLockFragment          fragment_app_lock.xml')
    A('│       ├── seg 1  权限审计   PermissionAuditFragment  fragment_permission_audit.xml')
    A('│       └── seg 2  工具箱     ToolsFragment            fragment_tools.xml')
    A('└── bottomNav(TabLayout,贴底 4dp,高 60dp,圆角 24dp,左右外边距 6dp)')
    A('    menu/bottom_nav.xml:nav_status 首页 / nav_detect 检测 / nav_protect 防护')
    A(FENCE)
    A('')
    A('跨板块直达:MainActivity.navigateTo(itemId, segment) —— 首页快捷卡与桌面小组件走这条路。')
    A('')
    A('不在底部三板块内的页面:')
    A('')
    A('| 宿主 | 布局 | 说明 |')
    A('| --- | --- | --- |')
    A('| DeepScanActivity | ' + BT + 'activity_deep_scan.xml' + BT + ' | 深度扫描进度 |')
    A('| LockActivity | ' + BT + 'activity_lock.xml' + BT + ' | PIN 解锁键盘 |')
    A('| BaseListToolActivity | ' + BT + 'activity_result_list.xml' + BT + ' | 通用结果列表 |')
    A('| VirusCenterActivity | ' + BT + 'activity_virus_center.xml' + BT + ' | 病毒风险中心 |')
    A('| AppLockFragment:96 | ' + BT + 'dialog_set_pin.xml' + BT + ' | 设置 PIN 对话框 |')
    A('| SecurityWidgetProvider | ' + BT + 'widget_security.xml' + BT + ' | 桌面小组件(RemoteViews)|')
    A('')
    A('列表项布局(RecyclerView item)与适配器一一对应:')
    A('')
    A('| 适配器 | item 布局 |')
    A('| --- | --- |')
    A('| ScanAdapter | ' + BT + 'item_scan_result.xml' + BT + ' |')
    A('| TrojanAdapter | ' + BT + 'item_trojan.xml' + BT + ' |')
    A('| AppLockAdapter | ' + BT + 'item_lock_app.xml' + BT + ' |')
    A('| PermissionAuditAdapter | ' + BT + 'item_audit.xml' + BT + ' |')
    A('| ToolsAdapter / VirusActionAdapter | ' + BT + 'item_tool.xml' + BT + ' |')
    A('')
    A('## 2. 设计令牌')
    A('')
    A('### 2.1 颜色(浅色 / 深色)')
    A('')
    A('| 令牌 | 浅色 | 深色 | 用途 |')
    A('| --- | --- | --- | --- |')
    for name in sorted(T.light.keys()):
        light = dtcg_hex(T.color('@color/' + name, False) or '') or T.light.get(name, '')
        dark = dtcg_hex(T.color('@color/' + name, True) or '') or light
        raw = T.light.get(name, '')
        note = COLOR_USAGE.get(name) or (('别名 → ' + raw) if raw.startswith('@') else '')
        A('| ' + BT + name + BT + ' | %s | %s | %s |' % (light, dark, note))
    A('')
    A('### 2.2 尺寸与圆角')
    A('')
    A('| 令牌 | 值 | 用途 |')
    A('| --- | --- | --- |')
    for name in sorted(T.dimens.keys()):
        d = T.dimen('@dimen/' + name)
        A('| ' + BT + name + BT + ' | %gdp | %s |' % (d, DIM_USAGE.get(name, '')))
    A('')
    A('### 2.3 字阶(TextAppearance.SecureDroid.*)')
    A('')
    A('| 样式 | 字号 | 粗细 | 字距 | 用在哪 |')
    A('| --- | --- | --- | --- | --- |')
    for name in sorted(T.styles.keys()):
        if not name.startswith('TextAppearance.SecureDroid.'):
            continue
        items = T.style_items(name)
        size = T.dimen(items.get('android:textSize'))
        if size is None:
            continue
        key = name.split('.')[-1]
        A('| ' + BT + key + BT + ' | %gsp | %s | %s | %s |'
          % (size, 'bold' if 'bold' in (items.get('android:textStyle') or '') else 'regular',
             items.get('android:letterSpacing') or '0', TA_USAGE.get(key, '')))
    A('')
    A('### 2.4 组件样式(Widget.SecureDroid.*)')
    A('')
    A('| 样式 | 父样式 | 关键取值 |')
    A('| --- | --- | --- |')
    for name in sorted(T.styles.keys()):
        if not name.startswith('Widget.SecureDroid.'):
            continue
        merged = T.style_items(name)
        vals = [k.split(':')[-1] + '=' + merged[k]
                for k in ('cardBackgroundColor', 'cardCornerRadius', 'cardElevation', 'strokeWidth',
                          'cornerRadius', 'backgroundTint', 'android:minHeight', 'android:textSize',
                          'android:paddingStart', 'android:paddingEnd', 'rippleColor', 'iconTint')
                if k in merged]
        A('| ' + BT + name + BT + ' | ' + BT + (T.styles[name].get('parent') or '—') + BT + ' | %s |'
          % ', '.join(vals))
    A('')
    A('## 3. 画板与结构树')
    A('')
    A('每个画板下面列出全部图层:图层 id、类型、文本、以及它在 360dp 画布里的 x/y/w/h(单位 dp)。')
    A('')
    for fr in frames:
        sub = 'screens' if fr['kind'] == 'screen' else 'parts'
        A('### %s · %s' % (fr['key'], fr['title']))
        A('')
        A('- 源文件:' + BT + fr['source'] + BT)
        A('- 画布:%g × %g dp' % (CANVAS_W, fr['h']))
        A('- SVG:' + BT + '%s/light/%s.svg' % (sub, fr['key']) + BT + ' / ' +
          BT + '%s/night/%s.svg' % (sub, fr['key']) + BT)
        if fr.get('desc'):
            A('- 说明:%s' % fr['desc'])
        A('')
        A('| 图层 id | 类型 | 文本 | x | y | w | h |')
        A('| --- | --- | --- | --- | --- | --- | --- |')
        for el in fr['flat']:
            A('| ' + BT + el['id'] + BT + ' | %s | %s | %g | %g | %g | %g |' % (
                el['type'], (el['text'] or '').replace('|', '/').replace('\n', ' '),
                el['x'], el['y'], el['w'], el['h']))
        A('')
    A('## 4. 图标(drawable/*.xml 里的 vector)')
    A('')
    A('| 名称 | viewport | 路径数 | SVG |')
    A('| --- | --- | --- | --- |')
    for name in sorted(icons.keys()):
        info = VECTORS[name]
        A('| ' + BT + name + BT + ' | %g×%g | %d | ' % (info['w'], info['h'], len(info['paths'])) +
          BT + 'icons/%s.svg' % name + BT + ' |')
    A('')
    A('## 5. 形状类 drawable(非 vector)')
    A('')
    A('| drawable | 类型 | 说明 |')
    A('| --- | --- | --- |')
    for name in sorted(SHAPES.keys()):
        if name in VECTORS:
            continue
        sh = SHAPES[name]
        desc = str(sh.get('kind'))
        if sh.get('solid'):
            desc += ' · solid ' + str(sh['solid'])
        if sh.get('gradient'):
            desc += ' · gradient %s → %s' % (sh['gradient'].get('start'), sh['gradient'].get('end'))
        if sh.get('radius'):
            desc += ' · radius ' + str(sh['radius'])
        A('| ' + BT + name + BT + ' | ' + str(sh.get('kind')) + ' | %s |' % desc)
    A('')
    return '\n'.join(L) + '\n'


def index_html(frames, parts, icons, stamp):
    def cards(items, sub):
        out = []
        for it in items:
            out.append(
                '      <figure>\n'
                '        <img data-light="%s/light/%s.svg" data-dark="%s/dark/%s.svg" '
                'src="%s/light/%s.svg" alt="%s">\n'
                '        <figcaption><b>%s</b><br><code>%s</code></figcaption>\n'
                '      </figure>' % (sub, it['key'], sub, it['key'], sub, it['key'], esc(it['title']),
                                    esc(it['title']), esc(it['source'])))
        return '\n'.join(out)

    html = '''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>SecureDroid 界面结构 · 导出预览</title>
<style>
  body { margin: 0; padding: 24px 32px 80px; background: #EFF2ED; color: #1A1A1A;
         font: 14px/1.6 "Noto Sans CJK SC", "Microsoft YaHei", sans-serif; }
  h1 { font-size: 22px; margin: 0 0 4px; }
  h2 { font-size: 16px; margin: 32px 0 12px; }
  .meta { color: #6B6B6B; font-size: 12px; margin-bottom: 18px; }
  label { display: inline-flex; gap: 6px; align-items: center; font-size: 13px; }
  .grid { display: flex; flex-wrap: wrap; gap: 20px; align-items: flex-start; }
  figure { margin: 0; background: #fff; border-radius: 16px; padding: 12px; width: 268px;
           box-shadow: 0 1px 3px rgba(0,0,0,.08); }
  img { width: 244px; height: auto; display: block; border-radius: 10px;
        outline: 1px solid rgba(0,0,0,.06); }
  figcaption { font-size: 12px; color: #444; margin-top: 8px; }
  code { color: #6B6B6B; font-size: 11px; }
  .icons { display: flex; flex-wrap: wrap; gap: 10px; }
  .icons img { width: 40px; padding: 8px; background: #F5F8F4; border-radius: 8px; }
</style>
</head>
<body>
<h1>SecureDroid 界面结构(从 res/ 导出)</h1>
<div class="meta">生成时间 __STAMP__ · 画布 360×800dp · SVG 输出 1080 宽 · 生成器
<code>design-export/export_ui.py</code></div>
<label><input type="checkbox" id="mode"> 深色模式预览(切换深色 SVG)</label>

<h2>成品画板(6)</h2>
<div class="grid">
__SCREENS__
</div>

<h2>Fragment / Activity / 列表项 / 对话框 / 小组件(19)</h2>
<div class="grid">
__PARTS__
</div>

<h2>图标(__ICONCOUNT__)</h2>
<div class="icons">
__ICONS__
</div>

<script>
var box = document.getElementById('mode');
box.addEventListener('change', function () {
  var dark = box.checked;
  document.querySelectorAll('img[data-light]').forEach(function (img) {
    img.src = dark ? img.dataset.dark : img.dataset.light;
  });
  document.body.style.background = dark ? '#0B0F0C' : '#EFF2ED';
  document.body.style.color = dark ? '#EDF2EC' : '#1A1A1A';
  document.querySelectorAll('figure').forEach(function (f) {
    f.style.background = dark ? '#171B18' : '#fff';
  });
  document.querySelectorAll('figcaption').forEach(function (f) {
    f.style.color = dark ? '#A9B3A7' : '#444';
  });
});
</script>
</body>
</html>
'''
    return (html.replace('__STAMP__', esc(stamp))
            .replace('__SCREENS__', cards(frames, 'screens'))
            .replace('__PARTS__', cards(parts, 'parts'))
            .replace('__ICONCOUNT__', str(len(icons)))
            .replace('__ICONS__', '\n'.join('  <img src="icons/%s.svg" alt="%s" title="%s">'
                                            % (n, esc(n), esc(n)) for n in sorted(icons))))


def main():
    stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    T = Tables()
    frames, parts = [], []
    manifest = {'generatedAt': stamp, 'generator': 'design-export/export_ui.py',
                'canvas': {'widthDp': CANVAS_W, 'heightDp': MIN_H, 'svgScale': SCALE},
                'screens': [], 'parts': [], 'icons': sorted(VECTORS.keys()),
                'shapes': sorted(SHAPES.keys())}

    for mode, night in (('light', False), ('night', True)):
        for entry in SCREENS:
            e = dict(entry)
            e['kind'] = 'screen'
            e['source'] = 'app/src/main/res/layout/' + entry['chrome']
            svg, flat, h = render_frame(e, night)
            write_text(os.path.join(HERE, 'screens', mode, entry['key'] + '.svg'), svg)
            if mode == 'light':
                frames.append({'key': entry['key'], 'title': entry['title'], 'kind': 'screen',
                               'source': e['source'], 'desc': entry['desc'], 'h': h, 'flat': flat})
        for entry in PARTS:
            e = dict(entry)
            e['kind'] = 'part'
            e['source'] = 'app/src/main/res/layout/' + entry['file']
            svg, flat, h = render_frame(e, night)
            write_text(os.path.join(HERE, 'parts', mode, entry['key'] + '.svg'), svg)
            if mode == 'light':
                parts.append({'key': entry['key'], 'title': entry['title'], 'kind': 'part',
                              'source': e['source'], 'desc': '', 'h': h, 'flat': flat})

    icons = icon_svgs(T)
    for name, svg in icons.items():
        write_text(os.path.join(HERE, 'icons', name + '.svg'), svg)

    payload = tokens_payload(T)
    write_text(os.path.join(HERE, 'tokens', 'design-tokens.json'),
               json.dumps(payload, ensure_ascii=False, indent=2) + '\n')
    write_text(os.path.join(HERE, 'tokens', 'tokens.csv'), tokens_csv(T))

    structure = {
        'generatedAt': stamp,
        'generator': 'design-export/export_ui.py',
        'canvas': {'widthDp': CANVAS_W, 'heightDp': MIN_H, 'svgScale': SCALE,
                   'note': '1dp = 1 SVG 单位;SVG width/height = viewBox × 3'},
        'navigation': {
            'bottomNav': [{'id': t[0], 'title': t[1]} for t in NAV_TABS],
            'boards': [
                {'tab': 0, 'id': 'nav_status', 'fragment': 'DashboardFragment',
                 'layout': 'fragment_dashboard.xml'},
                {'tab': 1, 'id': 'nav_detect', 'fragment': 'DetectFragment',
                 'layout': 'fragment_detect.xml',
                 'segments': [{'index': 0, 'title': '病毒扫描', 'fragment': 'ScannerFragment',
                               'layout': 'fragment_scanner.xml'},
                              {'index': 1, 'title': '木马查杀', 'fragment': 'TrojanFragment',
                               'layout': 'fragment_trojan.xml'}]},
                {'tab': 2, 'id': 'nav_protect', 'fragment': 'ProtectFragment',
                 'layout': 'fragment_protect.xml',
                 'segments': [{'index': 0, 'title': '应用锁', 'fragment': 'AppLockFragment',
                               'layout': 'fragment_app_lock.xml'},
                              {'index': 1, 'title': '权限审计', 'fragment': 'PermissionAuditFragment',
                               'layout': 'fragment_permission_audit.xml'},
                              {'index': 2, 'title': '工具箱', 'fragment': 'ToolsFragment',
                               'layout': 'fragment_tools.xml'}]},
            ],
            'standaloneActivities': [
                {'activity': 'DeepScanActivity', 'layout': 'activity_deep_scan.xml'},
                {'activity': 'LockActivity', 'layout': 'activity_lock.xml'},
                {'activity': 'BaseListToolActivity', 'layout': 'activity_result_list.xml'},
                {'activity': 'VirusCenterActivity', 'layout': 'activity_virus_center.xml'},
            ],
            'dialogsAndWidgets': [
                {'host': 'AppLockFragment:96', 'layout': 'dialog_set_pin.xml'},
                {'host': 'SecurityWidgetProvider', 'layout': 'widget_security.xml'},
            ],
            'adapters': [
                {'adapter': 'ScanAdapter', 'item': 'item_scan_result.xml'},
                {'adapter': 'TrojanAdapter', 'item': 'item_trojan.xml'},
                {'adapter': 'AppLockAdapter', 'item': 'item_lock_app.xml'},
                {'adapter': 'PermissionAuditAdapter', 'item': 'item_audit.xml'},
                {'adapter': 'ToolsAdapter', 'item': 'item_tool.xml'},
                {'adapter': 'VirusActionAdapter', 'item': 'item_tool.xml'},
            ],
        },
        'tokens': payload,
        'widgetStyles': {k: v for k, v in T.styles.items() if k.startswith('Widget.SecureDroid.')},
        'shapes': SHAPES,
        'icons': {k: {'viewport': [v['w'], v['h']], 'paths': len(v['paths'])}
                  for k, v in VECTORS.items()},
        'frames': [{'key': f['key'], 'title': f['title'], 'kind': 'screen', 'source': f['source'],
                    'canvas': {'w': CANVAS_W, 'h': f['h']},
                    'svg': {'light': 'screens/light/%s.svg' % f['key'],
                            'night': 'screens/night/%s.svg' % f['key']},
                    'elements': f['flat']} for f in frames] +
                   [{'key': p['key'], 'title': p['title'], 'kind': 'part', 'source': p['source'],
                     'canvas': {'w': CANVAS_W, 'h': p['h']},
                     'svg': {'light': 'parts/light/%s.svg' % p['key'],
                             'night': 'parts/night/%s.svg' % p['key']},
                     'elements': p['flat']} for p in parts],
    }
    write_text(os.path.join(HERE, 'ui-structure.json'), json.dumps(structure, ensure_ascii=False, indent=2) + '\n')
    write_text(os.path.join(HERE, 'ui-structure.md'), structure_md(T, frames + parts, icons, stamp))
    write_text(os.path.join(HERE, 'index.html'), index_html(frames, parts, icons, stamp))

    manifest['screens'] = [{'key': f['key'], 'title': f['title'], 'heightDp': f['h'],
                            'elements': len(f['flat'])} for f in frames]
    manifest['parts'] = [{'key': p['key'], 'title': p['title'], 'heightDp': p['h'],
                          'elements': len(p['flat'])} for p in parts]
    manifest['colors'] = len(T.light)
    manifest['dimens'] = len(T.dimens)
    write_text(os.path.join(HERE, 'manifest.json'), json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')

    # 自检:SVG paint 不允许出现 "None" —— 非法颜色会被浏览器当成黑色
    bad = []
    for sub in ('screens', 'parts', 'icons'):
        for dp, _, fs in os.walk(os.path.join(HERE, sub)):
            for f in fs:
                if not f.endswith('.svg'):
                    continue
                txt = open(os.path.join(dp, f), encoding='utf-8').read()
                if '="None"' in txt:
                    bad.append(os.path.relpath(os.path.join(dp, f), HERE))
    print('paint check: ' + ('!! invalid "None" paint in %d file(s): %s'
                             % (len(bad), ', '.join(sorted(bad)[:6])) if bad
                             else 'ok (no invalid paint)'))

    print('screens=%d parts=%d icons=%d colors=%d dimens=%d' % (
        len(frames), len(parts), len(icons), len(T.light), len(T.dimens)))
    for f in frames + parts:
        print('  %-20s %-30s h=%-6g elements=%d' % (f['key'], f['title'], f['h'], len(f['flat'])))


SHAPES = parse_shapes()
VECTORS = load_vectors()
NAV_TABS = load_nav(Tables())

if __name__ == '__main__':
    main()

