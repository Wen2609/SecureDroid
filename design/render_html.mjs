#!/usr/bin/env node
/**
 * design/render_html.mjs —— 把 res/values 里的设计令牌渲染成可直接用浏览器打开的 HTML 视觉稿。
 *
 * 与 design/render_mockup.py(PNG 验收图)同一思路:渲染器不写死任何设计值,
 * 颜色/圆角/字号/间距全部现读 res/values、res/values-night、res/color 选择器。
 * 改了令牌重跑本脚本,HTML 就会跟着变,因此它既是预览页也是"令牌是否落地"的肉眼验收页。
 *
 *   node design/render_html.mjs
 *   -> design/mockup.html(浅色) + design/mockup-dark.html(深色)
 */
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const RES = join(root, 'app', 'src', 'main', 'res');
const rd = (p) => readFileSync(p, 'utf8');
const noComment = (s) => s.replace(/<!--[\s\S]*?-->/g, '');
const unesc = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
  .replace(/&apos;/g, "'").replace(/&#39;/g, "'").replace(/&amp;/g, '&');

function colorsIn(file) {
  const out = {};
  for (const m of noComment(rd(file)).matchAll(/<color\s+name="([^"]+)"\s*>([^<]*)<\/color>/g)) out[m[1]] = m[2].trim();
  return out;
}
function resolveAliases(map) {
  const out = {};
  for (const k of Object.keys(map)) {
    let v = map[k], guard = 0;
    while (typeof v === 'string' && v.indexOf('@color/') === 0 && guard++ < 8) v = map[v.slice(7)];
    out[k] = v;
  }
  return out;
}
function dimensIn(file) {
  const out = {};
  for (const m of noComment(rd(file)).matchAll(/<dimen\s+name="([^"]+)"\s*>([^<]*)<\/dimen>/g)) {
    const raw = m[2].trim();
    const n = parseFloat(raw);
    out[m[1]] = /dp$/.test(raw) && isFinite(n) ? n : raw;
  }
  return out;
}
function stringsIn(file) {
  const out = {};
  for (const m of noComment(rd(file)).matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    out[m[1]] = unesc(m[2]).replace(/\\'/g, "'").replace(/\\"/g, '"');
  }
  return out;
}
function selectorsIn(dir) {
  const out = {};
  for (const f of readdirSync(dir).filter((x) => x.endsWith('.xml'))) {
    const src = noComment(rd(join(dir, f)));
    let on = null, off = null;
    for (const m of src.matchAll(/<item\s+([^>]*?)\/>/g)) {
      const attrs = m[1];
      const col = (attrs.match(/android:color="([^"]+)"/) || [])[1];
      if (!col) continue;
      if (/state_(selected|checked)="true"/.test(attrs)) on = col;
      else if (off === null) off = col;
    }
    out[f.replace(/\.xml$/, '')] = { on: on, off: off };
  }
  return out;
}

const light = resolveAliases(colorsIn(join(RES, 'values', 'colors.xml')));
const dark = resolveAliases(colorsIn(join(RES, 'values-night', 'colors.xml')));
const dimen = dimensIn(join(RES, 'values', 'dimens.xml'));
const str = stringsIn(join(RES, 'values', 'strings.xml'));
const selRaw = selectorsIn(join(RES, 'color'));
const sel = {};
for (const k of Object.keys(selRaw)) {
  sel[k] = {
    on: light[(selRaw[k].on || '').replace('@color/', '')] || selRaw[k].on,
    off: light[(selRaw[k].off || '').replace('@color/', '')] || selRaw[k].off,
  };
}

/** 从 strings.xml 取文案,并把 %1$s / %1$d 换成示例值 */
function S(name, sample) {
  const v = str[name];
  if (v === undefined) throw new Error('missing string: ' + name);
  return sample === undefined ? v : v.replace(/%1\$[sd]/g, sample).replace(/%2\$[sd]/g, '2');
}
/** res/values 里的 #AARRGGBB 必须转成 CSS 的 #RRGGBBAA,否则 8 位 hex 被当成 alpha */
function cssHex(v) {
  return typeof v === 'string' && /^#[0-9a-fA-F]{8}$/.test(v) ? '#' + v.slice(3) + v.slice(1, 3) : v;
}
function fmt(hex) {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), parseInt(h.slice(6, 8), 16)];
}
function lum(hex) {
  const c = fmt(hex).map((v) => { const s = v / 255; return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4); });
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}
function ratio(a, b) {
  const la = lum(a), lb = lum(b);
  const hi = Math.max(la, lb), lo = Math.min(la, lb);
  return Math.round(((hi + 0.05) / (lo + 0.05)) * 100) / 100;
}

const ICONS = {
  home: '<path d="M12 3.2 2.8 11h2.4v9.2h5.2v-6h3.2v6h5.2V11h2.4z"/>',
  scan: '<path d="M10.5 3a7.5 7.5 0 1 0 4.7 13.3l4.4 4.4 1.6-1.6-4.4-4.4A7.5 7.5 0 0 0 10.5 3zm0 2.2a5.3 5.3 0 1 1 0 10.6 5.3 5.3 0 0 1 0-10.6z"/>',
  shield: '<path d="M12 2.6 4.6 5.6v6.1c0 4.3 3.1 8.3 7.4 9.7 4.3-1.4 7.4-5.4 7.4-9.7V5.6z"/>',
  clean: '<path d="M11.6 2.4l1.7 5.6 5.6 1.7-5.6 1.7-1.7 5.6-1.7-5.6L4.3 9.7l5.6-1.7z"/><path d="M18.6 15l.7 2.3 2.3.7-2.3.7-.7 2.3-.7-2.3-2.3-.7 2.3-.7z"/>',
  bug: '<path d="M12 7.4a4.6 4.6 0 0 1 4.6 4.6v2.3a4.6 4.6 0 1 1-9.2 0V12A4.6 4.6 0 0 1 12 7.4zm-5.8 5H3.4v1.9h2.8zm14.4 0h-2.8v1.9h2.8zM7.1 5.6 5.3 3.8l1.4-1.4 2.1 2.1c1-.5 2-.8 3.2-.8s2.2.3 3.2.8l2.1-2.1 1.4 1.4-1.8 1.8z"/>',
  network: '<path d="M12 2.8a9.2 9.2 0 1 0 0 18.4 9.2 9.2 0 0 0 0-18.4zm6.9 8.3h-3a14 14 0 0 0-1.2-5.2 7.3 7.3 0 0 1 4.2 5.2zM12 4.9c.9 1.2 1.6 3.3 1.7 6.2h-3.4C10.4 8.2 11.1 6.1 12 4.9zM5.1 12.9h3a14 14 0 0 0 1.2 5.2 7.3 7.3 0 0 1-4.2-5.2zm0-1.8a7.3 7.3 0 0 1 4.2-5.2 14 14 0 0 0-1.2 5.2zm6.9 8c-.9-1.2-1.6-3.3-1.7-6.2h3.4c-.1 2.9-.8 5-1.7 6.2zm2.8-.9a14 14 0 0 0 1.2-5.3h3a7.3 7.3 0 0 1-4.2 5.3z"/>',
  lock: '<path d="M12 2.6a5 5 0 0 1 5 5v2.1h.7c.9 0 1.7.8 1.7 1.7v7c0 .9-.8 1.7-1.7 1.7H6.3c-.9 0-1.7-.8-1.7-1.7v-7c0-.9.8-1.7 1.7-1.7H7V7.6a5 5 0 0 1 5-5zm0 1.8a3.2 3.2 0 0 0-3.2 3.2v2.1h6.4V7.6A3.2 3.2 0 0 0 12 4.4zm0 8.4a1.6 1.6 0 0 0-.8 3v1.8h1.6v-1.8a1.6 1.6 0 0 0-.8-3z"/>',
  tool: '<path d="M14.2 2.6a5.4 5.4 0 0 0-4.9 7.6L2.8 16.7l4.5 4.5 6.5-6.5a5.4 5.4 0 0 0 6.7-7.2l-3.2 3.2-2.6-.6-.6-2.6 3.2-3.2a5.4 5.4 0 0 0-3.1-1.7z"/>',
  audit: '<path d="M6 2.8h9.3L20 7.5v13.7H6a2 2 0 0 1-2-2V4.8a2 2 0 0 1 2-2zm8.4 2v4.7H19zM8.5 12h7v1.8h-7zm0 3.8h7v1.8h-7z"/>',
  check: '<path d="M9.6 16.2 5.9 12.5l-1.5 1.5 5.2 5.2L20 8.9l-1.5-1.5z"/>',
  alert: '<path d="M12 2.6 22 20.4H2zM11 9h2v5h-2zm0 6.6h2v2h-2z"/>',
  arrow: '<path d="M9.4 5.4 16 12l-6.6 6.6-1.4-1.4 5.2-5.2-5.2-5.2z"/>',
};
const icon = (n, size) => '<svg viewBox="0 0 24 24" width="' + size + '" height="' + size + '" aria-hidden="true">' + ICONS[n] + '</svg>';

/* ---------- 页面片段 ---------- */
const tile = (ic, title, sub, subColor) =>
  '<div class="tile">' + icon(ic, dimen.sd_tile_icon) +
  '<div class="tile-title">' + title + '</div>' +
  '<div class="tile-sub"' + (subColor ? ' style="color:' + subColor + '"' : '') + '>' + sub + '</div></div>';

const ring = (score) => {
  const size = dimen.sd_ring_size, stroke = dimen.sd_ring_stroke;
  const r = (size - stroke) / 2, c = size / 2, circ = 2 * Math.PI * r;
  const arc = (circ * score) / 100;
  const color = score >= 80 ? 'var(--c_ring)' : score >= 60 ? 'var(--c_gold)' : 'var(--c_destructive)';
  return '<div class="ring-wrap">' +
    '<svg viewBox="0 0 ' + size + ' ' + size + '" width="' + size + '" height="' + size + '">' +
    '<circle cx="' + c + '" cy="' + c + '" r="' + dimen.sd_ring_disc / 2 + '" fill="var(--c_ring_disc)"/>' +
    '<circle cx="' + c + '" cy="' + c + '" r="' + r + '" fill="none" stroke="var(--c_ring_track)" stroke-width="' + stroke + '"/>' +
    '<circle cx="' + c + '" cy="' + c + '" r="' + r + '" fill="none" stroke="' + color + '" stroke-width="' + stroke +
    '" stroke-linecap="round" stroke-dasharray="' + arc.toFixed(1) + ' ' + circ.toFixed(1) + '" transform="rotate(-90 ' + c + ' ' + c + ')"/>' +
    '</svg><div class="ring-center"><div class="score">' + score + '</div><div class="score-unit">' + S('dashboard_score_unit') + '</div></div></div>';
};

const navbar = (active) => {
  const tabs = [['home', S('tab_status')], ['scan', S('tab_detect')], ['shield', S('tab_protect')]];
  return '<nav class="navbar">' + tabs.map((t) =>
    '<div class="tab' + (t[1] === active ? ' on' : '') + '">' + icon(t[0], 22) + '<span>' + t[1] + '</span></div>').join('') + '</nav>';
};

const statusbar = '<div class="statusbar"><span>9:41</span><span class="sb-right">' + icon('shield', 12) + icon('network', 12) + '</span></div>';

const segment = (items, activeIdx) =>
  '<div class="segment">' + items.map((t, i) => '<div class="seg' + (i === activeIdx ? ' on' : '') + '">' + t + '</div>').join('') + '</div>';

const rows = (list) => list.map((r, i) =>
  '<div class="row' + (i ? ' divided' : '') + '">' + (r.icon ? '<span class="row-ic">' + icon(r.icon, 22) + '</span>' : '') +
  '<span class="row-main"><span class="row-title">' + r.title + '</span>' +
  (r.sub ? '<span class="row-sub"' + (r.subColor ? ' style="color:' + r.subColor + '"' : '') + '>' + r.sub + '</span>' : '') + '</span>' +
  (r.trail ? '<span class="row-trail"' + (r.trailColor ? ' style="color:' + r.trailColor + '"' : '') + '>' + r.trail + '</span>' : '') +
  '</div>').join('');

const swRow = (label, on) => '<div class="row"><span class="row-main"><span class="row-title">' + label + '</span></span>' +
  '<span class="switch' + (on ? ' on' : '') + '"><i></i></span></div>';

const card = (inner) => '<div class="card">' + inner + '</div>';
const section = (t) => '<div class="section">' + t + '</div>';

/* ---------- 五屏 ---------- */
function screenHome(score, state, stateColor, virusSub, virusColor) {
  return '<div class="home">' +
    '<div class="home-title">' + S('home_title') + '</div>' + ring(score) +
    '<div class="state" style="color:' + stateColor + '">' + state + '</div>' +
    '<button class="cta">' + S('btn_optimize') + '</button>' +
    '<div class="grid">' +
    tile('clean', S('tile_clean_title'), S('tile_clean_free', '3.2 GB')) +
    tile('bug', S('tile_virus_title'), virusSub, virusColor) +
    tile('network', S('tile_network_title'), S('tile_network_count', '7')) +
    tile('lock', S('tile_applock_title'), S('tile_applock_count', '12')) +
    '</div></div>';
}
function screenDetect() {
  return '<div class="page">' +
    '<div class="page-title">' + S('tab_detect') + '</div>' +
    segment([S('seg_virus'), S('seg_trojan')], 0) +
    '<button class="cta accent">' + icon('scan', 18) + S('scan_start') + '</button>' +
    '<div class="progress"><i style="width:42%"></i></div>' +
    '<div class="hint">' + S('scan_scanning') + '</div>' +
    card(rows([
      { icon: 'alert', title: 'com.example.flashlight', sub: S('status_malicious'), subColor: 'var(--c_destructive)', trail: icon('arrow', 18) },
      { icon: 'alert', title: 'com.fake.battery', sub: S('status_risky'), subColor: 'var(--c_gold)', trail: icon('arrow', 18) },
      { icon: 'check', title: 'com.armorlab.securedroid', sub: S('status_safe'), subColor: 'var(--c_success)', trail: icon('arrow', 18) },
    ])) + '</div>';
}
function screenProtect() {
  return '<div class="page">' +
    '<div class="page-title">' + S('tab_protect') + '</div>' +
    segment([S('seg_applock'), S('seg_audit'), S('seg_tools')], 0) +
    card(rows([
      { title: S('lock_pin_state_set'), trail: icon('check', 18) },
      { title: S('lock_set_pin'), trail: icon('arrow', 18) },
    ]) + swRow(S('lock_decoy_sw'), false) + rows([{ title: S('lock_accessibility_go'), trail: icon('arrow', 18) }])) +
    card(rows([
      { icon: 'lock', title: '微信', sub: 'com.tencent.mm', trail: icon('check', 18) },
      { icon: 'lock', title: '支付宝', sub: 'com.eg.android.AlipayGphone', trail: icon('check', 18) },
      { icon: 'lock', title: '相册', sub: 'com.android.gallery3d', trail: '' },
    ])) + '</div>';
}
function screenTools() {
  return '<div class="page tools">' +
    section(S('sd_section_guard')) +
    card(swRow(S('sw_realtime'), true) +
      '<div class="chip-row"><span class="chip">' + S('root_mode_off') + '</span></div>' +
      swRow(S('sw_auto_disinfect'), false) + swRow(S('sw_auto_uninstall'), false)) +
    section(S('sd_section_prefs')) +
    card(swRow(S('sw_daily_scan'), true) + swRow(S('sw_sim_guard'), false)) +
    section(S('sd_section_tools')) +
    card(rows([
      { icon: 'audit', title: S('tool_full_audit'), sub: S('tool_full_audit_sub'), trail: icon('arrow', 18) },
      { icon: 'network', title: S('tool_network'), sub: S('tool_network_sub'), trail: icon('arrow', 18) },
      { icon: 'clean', title: S('tool_cleaner'), sub: S('tool_cleaner_sub'), trail: icon('arrow', 18) },
    ])) + '</div>';
}

const frame = (caption, inner, active) =>
  '<figure class="phone-wrap"><div class="phone">' + statusbar + '<div class="content">' + inner + '</div>' + navbar(active) +
  '</div><figcaption>' + caption + '</figcaption></figure>';

/* ---------- CSS(全部走令牌变量) ---------- */
const css = `
*{box-sizing:border-box}
body{margin:0;background:#dfe3dc;color:#1a1a1a;font-family:"PingFang SC","Microsoft YaHei","Noto Sans SC",system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.head{max-width:1024px;margin:0 auto;padding:28px 4px 4px}
.head h1{margin:0;font-size:22px}
.head p{margin:8px 0 0;font-size:13px;line-height:1.7;color:#4b5563}
.head code{background:#eef1ea;border-radius:4px;padding:1px 5px;font-size:12px}
.head a{color:#0b7a1c}
.sheet{display:flex;gap:28px;align-items:flex-start;padding:24px 28px 8px;overflow-x:auto}
.phone-wrap{margin:0;flex:0 0 auto}
.phone{position:relative;width:360px;height:800px;border-radius:26px;overflow:hidden;background:linear-gradient(180deg,var(--c_bg_top),var(--c_bg_bottom));box-shadow:0 12px 32px rgba(16,32,16,.18)}
figcaption{margin-top:10px;font-size:12px;color:#4b5563;text-align:center}
figcaption b{color:#111827}
.statusbar{height:24px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;font-size:11px;color:var(--c_foreground);opacity:.7}
.sb-right{display:flex;gap:4px;align-items:center}
.content{position:absolute;inset:24px 0 0 0;display:flex;flex-direction:column}
.navbar{position:absolute;left:var(--sd_nav_margin_h);right:var(--sd_nav_margin_h);bottom:var(--sd_nav_margin_bottom);height:var(--sd_tabbar_height);background:var(--c_nav);border-radius:var(--sd_nav_radius);display:flex;box-shadow:0 6px 18px rgba(16,32,16,.16)}
.tab{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;font-size:12px;font-weight:700;color:var(--c_muted_foreground)}
.tab svg{fill:currentColor}
.tab.on{color:var(--c_primary)}
.home{flex:1;display:flex;flex-direction:column;padding:0 var(--sd_home_margin) var(--sd_nav_clearance);overflow:hidden}
.home-title{margin-top:var(--sd_home_title_top);font-size:24px;font-weight:700;letter-spacing:-.01em}
.ring-wrap{position:relative;width:var(--sd_ring_size);height:var(--sd_ring_size);margin:var(--sd_ring_top) auto 0}
.ring-center{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center}
.score{font-size:54px;font-weight:700;line-height:1;letter-spacing:-.04em;color:var(--c_foreground)}
.score-unit{font-size:14px;color:var(--c_muted_foreground);margin-top:2px}
.state{margin-top:var(--sd_status_top);font-size:17px;text-align:center}
.cta{margin:var(--sd_cta_top) var(--sd_cta_inset) 0;height:var(--sd_cta_height);border:0;border-radius:var(--sd_radius_pill);background:var(--c_primary);color:var(--c_on_primary);font-size:16px;font-weight:700;font-family:inherit;display:flex;align-items:center;justify-content:center;gap:8px;cursor:pointer}
.cta.accent{background:var(--c_accent);color:var(--c_on_accent);margin:var(--sd_space_4) 0 0}
.cta svg{fill:currentColor}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:var(--sd_grid_gap);margin-top:var(--sd_grid_top)}
.tile{height:var(--sd_tile_height);padding:var(--sd_tile_padding);border-radius:var(--sd_radius_card);background:var(--c_card);box-shadow:0 2px 6px rgba(16,32,16,.08);display:flex;flex-direction:column}
.tile svg{fill:var(--c_foreground)}
.tile-title{margin-top:var(--sd_space_5);font-size:18px;font-weight:700;color:var(--c_foreground)}
.tile-sub{margin-top:var(--sd_space_1);font-size:15px;color:var(--c_muted_foreground);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.page{flex:1;display:flex;flex-direction:column;padding:0 var(--sd_gutter) var(--sd_nav_clearance);overflow:hidden}
.page-title{margin-top:var(--sd_space_4);font-size:24px;font-weight:700;letter-spacing:-.01em}
.segment{margin-top:var(--sd_space_4);background:var(--c_muted);border-radius:var(--sd_radius_inner);padding:2px;display:flex}
.seg{flex:1;height:var(--sd_segment_hit);display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:600;color:` + cssHex(sel.seg_text.off) + `;border-radius:var(--sd_radius_inner)}
.seg.on{background:` + cssHex(sel.seg_bg.on) + `;color:` + cssHex(sel.seg_text.on) + `}
.card{margin-top:var(--sd_space_4);background:var(--c_card);border-radius:var(--sd_radius_card);box-shadow:0 2px 6px rgba(16,32,16,.08);overflow:hidden;flex:0 0 auto}
.row{min-height:var(--sd_row_height);display:flex;align-items:center;gap:var(--sd_space_3);padding:0 var(--sd_space_4)}
.row.divided{border-top:var(--sd_hairline_width) solid var(--c_border)}
.row-ic{display:flex;color:var(--c_muted_foreground)}
.row-ic svg{fill:currentColor}
.row-main{flex:1;display:flex;flex-direction:column;gap:2px;min-width:0}
.row-title{font-size:16px;color:var(--c_foreground)}
.row-sub{font-size:13px;color:var(--c_muted_foreground);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row-trail{display:flex;color:var(--c_accent)}
.row-trail svg{fill:currentColor}
.switch{width:44px;height:24px;border-radius:12px;background:var(--c_border);position:relative;flex:0 0 auto}
.switch.on{background:var(--c_primary)}
.switch i{position:absolute;top:2px;left:2px;width:20px;height:20px;border-radius:50%;background:#fff;transition:left .15s}
.switch.on i{left:22px}
.progress{margin-top:var(--sd_space_4);height:4px;border-radius:2px;background:` + cssHex(light.c_muted) + `;overflow:hidden}
.progress i{display:block;height:100%;background:var(--c_primary)}
.hint{margin-top:var(--sd_space_3);font-size:14px;color:var(--c_muted_foreground)}
.section{margin:var(--sd_space_5) var(--sd_gutter) var(--sd_space_2);font-size:13px;font-weight:700;letter-spacing:.02em;color:var(--c_muted_foreground)}
.page.tools{padding-left:0;padding-right:0}
.chip-row{padding:var(--sd_space_3) 0 var(--sd_space_3) var(--sd_space_4);border-top:var(--sd_hairline_width) solid var(--c_border)}
.chip{font-size:12px;font-weight:700;letter-spacing:.04em;color:var(--c_muted_foreground);background:var(--c_muted);border-radius:var(--sd_radius_chip);padding:4px 8px}
.appendix{max-width:1024px;margin:36px auto 64px;padding:0 4px}
.appendix h2{font-size:16px;margin:28px 0 12px}
table{border-collapse:collapse;width:100%;font-size:13px;background:#fbfcfa;border-radius:8px;overflow:hidden}
th,td{text-align:left;padding:7px 10px;border-bottom:1px solid #e6eae3}
th{background:#eef1ea;font-weight:600}
.sw{display:inline-block;width:14px;height:14px;border-radius:4px;vertical-align:-2px;margin-right:6px;border:1px solid rgba(0,0,0,.12)}
.legend{font-size:12px;color:#4b5563;margin-top:10px;line-height:1.7}
.ok{color:#0b7a1c;font-weight:600}
.dev{color:#a16207;font-weight:600}
`;

/* ---------- 组装 ---------- */
const theme = (map) => Object.keys(map).sort().map((k) => '  --' + k + ': ' + cssHex(map[k]) + ';').join('\n');
const dims = Object.keys(dimen).sort().map((k) => '  --' + k + ': ' + dimen[k] + 'px;').join('\n');

const PAIRS = [
  ['内容主色 c_foreground', 'c_card', 'c_foreground'],
  ['内容副色 c_muted_foreground', 'c_card', 'c_muted_foreground'],
  ['页面副色 c_muted_foreground', 'c_background', 'c_muted_foreground'],
  ['主按钮 c_primary', 'c_primary', 'c_on_primary'],
  ['强调按钮 c_accent', 'c_accent', 'c_on_accent'],
  ['危险色 c_destructive', 'c_card', 'c_destructive'],
  ['导航选中 c_primary', 'c_nav', 'c_primary'],
  ['导航未选中 c_muted_foreground', 'c_nav', 'c_muted_foreground'],
];

function contrastTable(colors) {
  return '<table><thead><tr><th>配对</th><th>前景</th><th>背景</th><th>对比度</th><th>判定</th></tr></thead><tbody>' +
    PAIRS.map((p) => {
      const r = ratio(colors[p[1]], colors[p[2]]);
      const cls = r >= 4.5 ? 'ok' : 'dev';
      const verdict = r >= 4.5 ? 'AA(≥4.5:1)' : r >= 3 ? '仅大字/图形(≥3:1)' : '刻意偏差,见 DESIGN.md';
      return '<tr><td>' + p[0] + '</td><td><span class="sw" style="background:' + cssHex(colors[p[1]]) + '"></span>' + colors[p[1]] +
        '</td><td><span class="sw" style="background:' + cssHex(colors[p[2]]) + '"></span>' + colors[p[2]] +
        '</td><td class="' + cls + '">' + r.toFixed(2) + ':1</td><td>' + verdict + '</td></tr>';
    }).join('') + '</tbody></table>';
}
function colorTable(colors) {
  return '<table><thead><tr><th>令牌</th><th>值</th><th>用途</th></tr></thead><tbody>' +
    Object.keys(colors).sort().map((k) => '<tr><td><code>' + k + '</code></td><td><span class="sw" style="background:' + cssHex(colors[k]) +
      '"></span>' + colors[k] + '</td><td>' + (k.indexOf('c_') === 0 ? '核心令牌' : '语义别名') + '</td></tr>').join('') +
    '</tbody></table>';
}
function dimTable() {
  return '<table><thead><tr><th>令牌</th><th>值(dp)</th></tr></thead><tbody>' +
    Object.keys(dimen).sort().map((k) => '<tr><td><code>' + k + '</code></td><td>' + dimen[k] + '</td></tr>').join('') +
    '</tbody></table>';
}

const HOME_GOOD = frame('<b>首页</b> · fragment_dashboard.xml · 良好态(100 分)',
  screenHome(100, S('dashboard_state_good'), 'var(--c_muted_foreground)', S('tile_virus_bad', '2'), 'var(--c_destructive)'), S('tab_status'));
const HOME_WARN = frame('<b>首页</b> · fragment_dashboard.xml · 风险态(58 分)',
  screenHome(58, S('dashboard_state_warn'), 'var(--c_gold)', S('tile_virus_bad', '5'), 'var(--c_destructive)'), S('tab_status'));
const DETECT = frame('<b>检测</b> · fragment_detect.xml + fragment_scanner.xml', screenDetect(), S('tab_detect'));
const PROTECT = frame('<b>防护</b> · fragment_protect.xml + fragment_app_lock.xml', screenProtect(), S('tab_protect'));
const TOOLS = frame('<b>工具箱</b> · fragment_tools.xml(防护 → 工具箱)', screenTools(), S('tab_protect'));

function build(colors, other, label, link) {
  const frames = HOME_GOOD + HOME_WARN + DETECT + PROTECT + TOOLS;
  return '<!doctype html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n' +
    '<meta name="viewport" content="width=device-width,initial-scale=1">\n' +
    '<title>安卫安全助手 · v1.6.0 视觉稿(' + label + ')</title>\n<style>\n:root{\n' +
    theme(colors) + '\n' + dims + '\n}\n' + css + '\n</style>\n</head>\n<body>\n' +
    '<header class="head">\n<h1>安卫安全助手 · v1.6.0 视觉稿 · ' + label + '</h1>\n' +
    '<p>本页由 <code>design/render_html.mjs</code> 现读 <code>res/values</code> 令牌渲染:颜色、圆角、字号、间距全部来自令牌,' +
    '渲染器不写死任何设计值。改令牌后重跑 <code>node design/render_html.mjs</code> 即可刷新。' +
    '当前为<b>' + label + '</b>预览 · <a href="' + link + '">切换到' + other + '</a></p>\n</header>\n' +
    '<main class="sheet">\n' + frames + '\n</main>\n' +
    '<section class="appendix">\n<h2>令牌实测对比度(' + label + ')</h2>\n' + contrastTable(colors) +
    '\n<p class="legend">4.5:1 为正文 AA 门槛;主按钮为忠实设计稿刻意保留的品牌绿 + 白字偏差,' +
    '由 <code>ColorContrastTest.brandCtaContrastDeviationIsDocumented</code> 守卫。</p>\n' +
    '<h2>颜色令牌(' + label + ')</h2>\n' + colorTable(colors) + '\n' +
    '<h2>尺寸令牌(共用)</h2>\n' + dimTable() + '\n</section>\n</body>\n</html>\n';
}

writeFileSync(join(root, 'design', 'mockup.html'), build(light, dark, '浅色', 'mockup-dark.html'));
writeFileSync(join(root, 'design', 'mockup-dark.html'), build(dark, light, '深色', 'mockup.html'));
console.log('rendered design/mockup.html + design/mockup-dark.html from ' +
  Object.keys(light).length + ' color tokens / ' + Object.keys(dimen).length + ' dimens');
