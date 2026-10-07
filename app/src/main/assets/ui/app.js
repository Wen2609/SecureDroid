/* ============================================================
   SecureDroid WebUI — JS 数据层与渲染
   ------------------------------------------------------------
   架构(v1.9.15 微内核):
     通道  window.__sdChannel(msg)   — 原生→页面的唯一入口(reply + event 信封)
     API   api.request(action)/send  — 页面→原生;request 走 promise 应答,
                                       无 AndroidBridge(纯 HTML 预览)时由
                                       demoRequest 数据源适配器接住
     Store store.set/subscribe       — 面板状态单一来源,渲染函数只吃数据
   覆盖功能:首页概览 / 病毒扫描 / 木马查杀 / Rootkit / 恶意模块 / 锁机检测 /
   应用锁(搜索过滤 + PIN 弹窗 + 无障碍)/ 权限审计 / 工具箱开关 / 扫描进度重放
   ============================================================ */
(function () {
  'use strict';

  var bridge = (typeof AndroidBridge !== 'undefined') ? AndroidBridge : null;
  var demo = !bridge;
  var started = false;

  /* ---------------- 通道:原生 → 页面 ---------------- */

  var handlers = {};
  window.__sdHandlers = handlers;
  function on(type, fn) { handlers[type] = fn; }

  /* 所有跨桥消息的信封:
     {kind:"reply", id, ok, data|error} — api.request 的应答
     {kind:"event", type, data}         — 推送事件(进度/结果/状态变化) */
  window.__sdChannel = function (msg) {
    if (typeof msg === 'string') {
      try { msg = JSON.parse(msg); } catch (e) { return; }
    }
    if (!msg || typeof msg !== 'object') return;
    if (msg.kind === 'reply') {
      var p = pendingReplies[msg.id];
      if (p) { delete pendingReplies[msg.id]; p(msg); }
      return;
    }
    var h = handlers[msg.type];
    if (h) { try { h(msg.data); } catch (e) { } }
  };
  /* 兼容入口:demo 数据源仍以事件形式注入(原生不再注入 __sdEvent) */
  window.__sdEvent = function (type, payload) {
    var d = payload;
    if (typeof payload === 'string') {
      try { d = JSON.parse(payload); } catch (e) { d = {}; }
    }
    window.__sdChannel({ kind: 'event', type: type, data: d });
  };

  /* ---------------- API:页面 → 原生 ---------------- */

  var pendingReplies = {};
  var reqSeq = 0;
  var activeReq = {};

  function nativeRequest(action, payload) {
    // 无参数的数据请求按 action 合并:同屏多路触发只发一次
    if (!payload && activeReq[action]) return activeReq[action];
    var req = new Promise(function (resolve) {
      var id = ++reqSeq;
      pendingReplies[id] = resolve;
      var msg = { id: id, action: action };
      if (payload) { for (var k in payload) msg[k] = payload[k]; }
      try {
        bridge.post(action, JSON.stringify(msg));
      } catch (e) {
        delete pendingReplies[id];
        resolve({ ok: false, error: '桥调用失败' });
      }
    });
    if (!payload) {
      activeReq[action] = req;
      req.then(function () { delete activeReq[action]; }, function () { delete activeReq[action]; });
    }
    return req;
  }

  /* demo 数据源:与桥同接口,纯 HTML 预览(无 AndroidBridge)时界面依然可用 */
  var demoMode = 'standard';
  var demoTheme = 'system';
  function demoRequest(action, payload) {
    switch (action) {
      case 'getMode':
        return { selected: false, mode: 'standard', rootMode: false };
      case 'setMode':
        demoMode = (payload && payload.mode) || 'standard';
        // 演示模式视为权限已全部授予,便于纯 HTML 预览完整流程
        return { ok: true, mode: demoMode, permissions: [{ key: 'notification', label: '通知权限', granted: true }] };
      case 'getTheme':
        return { theme: demoTheme };
      case 'setTheme':
        demoTheme = (payload && payload.theme) || 'system';
        return { ok: true, theme: demoTheme };
      case 'getDashboard': return demoDashboard();
      case 'checkUrl':
        return { level: 'warn', score: 45, findings: ['域名伪装知名品牌', '高风险顶级域'] };
      case 'getLockState': return demoLock();
      case 'getAudit': return demoAudit();
      case 'getToggles': return demoToggles();
      case 'startVirusScan': demoVirus(); return {};
      case 'startTrojanScan': case 'startRootkit':
      case 'startModuleScan': case 'startLockerScan': demoTrojan(); return {};
      default: return {};
    }
  }

  var api = {
    /** 请求→应答(promise);demo 模式由本地数据源实现 */
    request: function (action, payload) {
      if (demo) {
        return Promise.resolve({ ok: true, data: demoRequest(action, payload) });
      }
      return nativeRequest(action, payload);
    },
    /** 即发即忘(开关/跳转/触感等 UI 动作) */
    send: function (action, payload) {
      if (demo) { demoRequest(action, payload); return; }
      try { bridge.post(action, JSON.stringify(payload || {})); } catch (e) { }
    }
  };

  /* ---------------- Store:面板状态单一来源 ---------------- */

  var store = {
    state: {},
    listeners: {},
    set: function (key, data) {
      this.state[key] = data;
      var ls = this.listeners[key];
      if (ls) for (var i = 0; i < ls.length; i++) { try { ls[i](data); } catch (e) { } }
    },
    subscribe: function (key, fn) { (this.listeners[key] = this.listeners[key] || []).push(fn); }
  };

  /* ---------------- 基础工具 ---------------- */

  function qs(s) { return document.querySelector(s); }
  function qsa(s) { return Array.prototype.slice.call(document.querySelectorAll(s)); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function fmtNum(n) { return Number(n || 0).toLocaleString('zh-CN'); }

  /* 应用图标:优先加载 https://appicon.local/... 渲染的真实应用图标,失败回退首字字母瓦片。
     图标请求走 IntersectionObserver 懒加载:应用锁列表可达数百行,视口外的行
     不发请求,避免首屏一次性触发几百次原生图标渲染(每次都要解码/缩放位图) */
  var iconObserver = (typeof IntersectionObserver !== 'undefined')
    ? new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        iconObserver.unobserve(en.target);
        var img = en.target;
        if (img.dataset && img.dataset.src) {
          img.src = img.dataset.src;
          delete img.dataset.src;
        }
      });
    }, { rootMargin: '240px' })
    : null;

  function firstLetter(name) {
    var s = String(name || '?').trim();
    if (!s) return '?';
    var ch = s.charAt(0);
    if (/[a-zA-Z]/.test(ch)) return ch.toUpperCase();
    return ch;
  }
  function letterTint(name) {
    var s = String(name || '');
    var h = 0;
    for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) & 0x7fffffff;
    return 'tint-letter-' + (h % 4);
  }
  function iconFor(pkg, name) {
    var wrap = el('span', 'row-icon');
    if (!pkg || demo) {
      wrap.classList.add(letterTint(name));
      wrap.textContent = firstLetter(name);
      return wrap;
    }
    var img = document.createElement('img');
    img.alt = '';
    img.dataset.src = 'https://appicon.local/' + encodeURIComponent(pkg);
    img.onerror = function () {
      this.remove();
      wrap.classList.add(letterTint(name));
      wrap.textContent = firstLetter(name);
    };
    if (iconObserver) {
      iconObserver.observe(img);
    } else {
      img.src = img.dataset.src;
    }
    wrap.appendChild(img);
    return wrap;
  }

  /* 触感轻反馈 */
  function hapticTap() {
    api.send('haptic');
  }

  /* 更新卡头计数(扫描结果/木马检测结果) */
  function setCardHeadCount(id, label, count) {
    var h = qs(id);
    if (!h) return;
    if (count == null || count < 0) { h.textContent = label; return; }
    h.textContent = label + ' · ' + count + ' 项';
  }

  function fmtTime(ms) {
    if (!ms) return '从未扫描';
    var d = new Date(ms), now = new Date();
    var pad = function (n) { return (n < 10 ? '0' : '') + n; };
    var hm = pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()) {
      return '今天 ' + hm;
    }
    if (d.getFullYear() === now.getFullYear()) {
      return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + hm;
    }
    return d.getFullYear() + '/' + (d.getMonth() + 1) + '/' + d.getDate() + ' ' + hm;
  }

  /* 页面就绪(原生 onPageFinished / onResume 时调用,刷新当前面板数据);
     同时重放进行中的扫描(旋转/重建后页面无缝续显进度) */
  window.__sdReady = function () {
    scheduleRefresh();
    api.request('getScanState').then(function (r) {
      if (!r.ok || !r.data || !r.data.active) return;
      r.data.active.forEach(function (s) { applyScanState(s); });
    });
  };

  /* 扫描状态重放:恢复"正在扫描"UI(按钮禁用 + 进度条 + 取消按钮) */
  function applyScanState(s) {
    if (!s || !s.action) return;
    if (s.action === 'virus') {
      setScanUi(s.done, s.total, '全盘扫描中 ' + s.done + ' / ' + s.total + '（' + scanPct(s.done, s.total) + '%）');
      setScanBusy('#btnVirusScan', true);
      showCancel('#btnVirusCancel', s.total > 0 && s.done < s.total);
    } else if (s.action === 'trojan') {
      setScanUi(s.done, s.total, '木马查杀中 ' + s.done + ' / ' + s.total + '（' + scanPct(s.done, s.total) + '%）');
      setScanBusy('#btnTrojanScan', true);
      showCancel('#btnTrojanCancel', s.total > 0 && s.done < s.total);
    }
  }

  /* 原生直达导航(小部件 / 快捷磁贴): 切板块 + 二级分段 */
  window.__sdGoto = function (panel, seg) {
    var t = qs('.dock-item[data-tab="' + panel + '"]');
    if (t) t.click();
    if (seg) {
      var b = qs('.panel[data-panel="' + panel + '"] .seg[data-seg="' + seg + '"]');
      if (b) b.click();
    }
    scheduleRefresh();
  };

  /* ---------------- 面板 / 分段感知 ---------------- */

  function activePanel() {
    var p = qs('.panel.is-active');
    return p ? p.dataset.panel : 'home';
  }
  function activeBody() {
    var p = qs('.panel.is-active .seg-body.is-active');
    return p ? p.dataset.body : null;
  }
  function refreshVisible() {
    var now = Date.now();
    if (now - lastRefreshAt < 300) return;
    lastRefreshAt = now;
    var panel = activePanel();
    if (panel === 'home' || panel === 'security') { loadDashboard(); return; }
    if (panel === 'app') {
      var body = activeBody();
      if (body === 'lock') { loadLock(); loadDashboard(); }
      else if (body === 'audit') { loadAudit(); }
      return;
    }
    if (panel === 'ext') { loadToggles(); loadDashboard(); }
  }

  /* 刷新收敛:一次交互常会触发多条监听(内联切换 + 桥接刷新 + __sdGoto),
     尾沿节流把同一交互内合并成一次;时间窗去重挡住紧邻的重复刷新
     (如启动时 init 与 onPageFinished __sdReady 相隔不到几百毫秒)。 */
  var refreshTimer = 0;
  var lastRefreshAt = 0;
  function scheduleRefresh() {
    if (refreshTimer) return;
    refreshTimer = setTimeout(function () {
      refreshTimer = 0;
      refreshVisible();
    }, 120);
  }

  /* 原生导航(内联脚本先执行,这里在其后刷新数据) */
  function hookNavRefresh() {
    qsa('.dock-item').forEach(function (t) {
      t.addEventListener('click', function () { hapticTap(); scheduleRefresh(); });
    });
    qsa('.seg').forEach(function (s) {
      s.addEventListener('click', function () { hapticTap(); scheduleRefresh(); });
    });
    qsa('[data-goto]').forEach(function (g) {
      g.addEventListener('click', function () { hapticTap(); scheduleRefresh(); });
    });
  }

  /* ---------------- 首页概览 ---------------- */

  function loadDashboard() {
    api.request('getDashboard').then(function (r) {
      if (r.ok) { store.set('dashboard', r.data); return; }
      // 失败也要解除骨架,否则首屏骨架永不消失;提示用户下拉重试
      var home = qs('.panel[data-panel="home"]');
      if (home) home.classList.remove('is-loading');
      toast('数据加载失败，下拉可重试');
    });
  }

  function setAct(id, title, time) {
    var item = qs(id);
    if (!item) return;
    var t = item.querySelector('.activity-title');
    var tm = item.querySelector('.activity-time');
    if (t) t.textContent = title;
    if (tm) tm.textContent = time;
  }

  function renderDashboard(d) {
    // 数据就绪:移除骨架加载态
    var home = qs('.panel[data-panel="home"]');
    if (home) home.classList.remove('is-loading');
    var hero = qs('#heroMeta');
    if (hero) hero.textContent = '上次扫描 · ' + fmtTime(d.lastScanAt || 0);
    // 威胁感知三态:有威胁=危险(红),有风险权限或病毒库待更新=注意(琥珀),否则安全(绿)
    var threats = d.threats || 0, risky = d.risky || 0;
    var state = threats > 0 ? 'danger' : ((risky > 0 || !d.libOk) ? 'warn' : 'safe');
    var heroCard = qs('.hero-card');
    if (heroCard) heroCard.dataset.state = state;
    var title = qs('#heroTitle');
    if (title) {
      title.textContent = threats > 0
        ? '发现 ' + threats + ' 项威胁'
        : (risky > 0 ? '注意 · ' + risky + ' 个应用有风险权限' : '设备安全');
    }
    var lib = qs('#statLib');
    if (lib) lib.textContent = d.libOk ? '已更新' : '待更新';
    var sc = qs('#statScannedValue');
    if (sc) sc.textContent = fmtNum(d.scanned);
    var pr = qs('#statProtected');
    if (pr) pr.textContent = (d.protected || 0) + ' 项';

    setAct('#actScan', '病毒扫描完成',
      d.lastScanAt ? fmtTime(d.lastScanAt) + ((d.threats || 0) > 0 ? ' · 发现 ' + d.threats + ' 个威胁' : ' · 未发现威胁') : '点击「立即扫描」开始全盘扫描');
    setAct('#actClean', '隐私权限审计',
      (d.risky || 0) > 0 ? '高风险应用 ' + d.risky + ' 个，建议前往权限审计' : '未发现高风险权限应用');
    setAct('#actRisk', '应用锁',
      (d.locked || 0) > 0 ? '已锁定 ' + d.locked + ' 个应用' : '尚未锁定应用，前往应用锁开启');
  }

  /* ---------------- 扫描渲染 ---------------- */

  function setScanUi(done, total, hint) {
    var body = qs('.panel.is-active .seg-body.is-active');
    if (!body) return;
    var bar = body.querySelector('.progress > i');
    var prog = body.querySelector('.progress');
    var pct = total > 0 ? Math.min(100, Math.round(done / total * 100)) : (done > 0 ? 100 : 0);
    if (bar) bar.style.width = pct + '%';
    if (prog) prog.setAttribute('aria-valuenow', String(pct));
    var h = body.querySelector('.scan-hint');
    if (h) h.textContent = hint;
    var circ = body.querySelector('.scan-circle');
    if (circ) circ.classList.toggle('is-scanning', total > 0 && done < total);
  }

  function scanPct(done, total) {
    return total > 0 ? Math.min(100, Math.round(done / total * 100)) : 0;
  }

  /* 取消按钮显隐;恢复时还原文案与可点状态 */
  function showCancel(id, visible) {
    var b = qs(id);
    if (!b) return;
    if (visible) {
      b.disabled = false;
      b.textContent = b.dataset.label || b.textContent;
      b.classList.remove('is-hidden');
    } else {
      b.classList.add('is-hidden');
      b.disabled = false;
      b.textContent = b.dataset.label || b.textContent;
    }
  }

  function requestCancelScan(cancelBtnId) {
    hapticTap();
    var b = qs(cancelBtnId);
    if (b) {
      b.disabled = true;
      b.textContent = '正在取消…';
    }
    api.send('cancelScan');
  }

  function levelCls(level) {
    if (level === 'CRITICAL' || level === 'HIGH') return 'danger';
    if (level === 'MEDIUM') return 'risky';
    return 'safe';
  }
  function levelText(level) {
    return { CRITICAL: '严重', HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[level] || '安全';
  }

  function resultRow(title, sub, badge, badgeCls, onClick, pkg, iconName) {
    var row = el('div', 'result-item row' + (onClick ? ' tappable' : ''));
    if (pkg || iconName) row.insertBefore(iconFor(pkg, iconName || title), row.firstChild);
    var body = el('span', 'row-body');
    body.appendChild(el('span', 'row-title', title));
    body.appendChild(el('span', 'row-sub', sub));
    row.appendChild(body);
    if (badge != null) row.appendChild(el('span', 'badge ' + (badgeCls || 'safe'), badge));
    if (onClick) row.addEventListener('click', onClick);
    return row;
  }

  /* 空态:圆形图标 + 文案(替代纯文本空提示) */
  function emptyHint(text, tone) {
    var wrap = el('div', 'empty-hint');
    var icon = el('span', 'empty-icon tint-' + (tone || 'green'));
    icon.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"' +
      ' stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M12 3.2 5 6v5.4c0 4.2 2.9 8 7 9.4 4.1-1.4 7-5.2 7-9.4V6z"/>' +
      '<path d="m9 12 2.2 2.2L15.4 10"/></svg>';
    wrap.appendChild(icon);
    wrap.appendChild(el('span', null, text));
    return wrap;
  }

  /* 错误态:警示图标 + 文案 + 重试按钮。数据加载失败时列表区的统一降级,
     与 emptyHint 同视觉体系;有 onRetry 才渲染按钮。 */
  function errorHint(message, onRetry) {
    var wrap = el('div', 'empty-hint error-hint');
    var icon = el('span', 'empty-icon tint-amber');
    icon.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"' +
      ' stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<circle cx="12" cy="12" r="8.6"/><path d="M12 8v4.5M12 16v.01"/></svg>';
    wrap.appendChild(icon);
    wrap.appendChild(el('span', null, message));
    if (onRetry) {
      var btn = el('button', 'retry-btn');
      btn.type = 'button';
      btn.textContent = '重试';
      btn.addEventListener('click', onRetry);
      wrap.appendChild(btn);
    }
    return wrap;
  }

  /* 结果行点击 → 打开该应用的应用详情页(可改权限 / 卸载) */
  function openApp(pkg) {
    if (!pkg) return;
    api.send('openAppSettings', { pkg: pkg });
  }

  /* ---------------- 病毒扫描 ---------------- */

  function setScanBusy(id, busy) {
    var btn = qs(id);
    if (btn) btn.disabled = !!busy;
  }

  function startVirusScan() {
    setScanUi(0, 0, '正在准备扫描引擎…');
    var box = qs('#virusResults');
    if (box) box.textContent = '';
    setScanBusy('#btnVirusScan', true);
    showCancel('#btnVirusCancel', false);
    hapticTap();
    api.send('startVirusScan');
  }

  on('virusProgress', function (d) {
    setScanUi(d.done, d.total, '全盘扫描中 ' + d.done + ' / ' + d.total + '（' + scanPct(d.done, d.total) + '%）');
    showCancel('#btnVirusCancel', true);
  });

  on('virusDone', function (d) {
    setScanBusy('#btnVirusScan', false);
    showCancel('#btnVirusCancel', false);
    setScanUi(d.total || 0, d.total || 0, d.summary || '扫描完成');
    var box = qs('#virusResults');
    if (!box) return;
    box.textContent = '';
    var rows = d.results || [];
    setCardHeadCount('#virusHead', '扫描结果', rows.length);
    // 只渲染感染/可疑项;全部干净时不再铺几百行"安全",直接给摘要
    var hits = rows.filter(function (r) { return r.infected; });
    if (!hits.length) { box.textContent = ''; box.appendChild(emptyHint(d.summary || '未发现威胁，设备安全。', 'green')); loadDashboard(); return; }
    var frag = document.createDocumentFragment();
    hits.forEach(function (r) {
      var detail = (r.detections || []).map(function (x) { return '[' + x.engine + '] ' + x.name; }).join('；');
      frag.appendChild(resultRow(r.name || r.pkg, r.pkg + (detail ? ' · ' + detail : ''), levelText(r.worst), levelCls(r.worst),
        function () { openApp(r.pkg); }, r.pkg, r.name));
    });
    // 干净应用默认折叠,点击展开(避免一次插入数百行 DOM)
    var rest = rows.length - hits.length;
    if (rest > 0) {
      var cleanRows = rows.filter(function (r) { return !r.infected; });
      var more = el('button', 'row more-toggle', '展开其余 ' + rest + ' 个安全应用');
      more.type = 'button';
      more.addEventListener('click', function () {
        var f2 = document.createDocumentFragment();
        cleanRows.forEach(function (r) {
          f2.appendChild(resultRow(r.name || r.pkg, r.pkg, levelText(r.worst), levelCls(r.worst),
            function () { openApp(r.pkg); }, r.pkg, r.name));
        });
        more.remove();
        box.appendChild(f2);
      });
      frag.appendChild(more);
    }
    box.appendChild(frag);
    loadDashboard();
  });

  /* ---------------- 木马查杀 / 专项检测 ---------------- */

  function startTrojanScan() {
    setScanUi(0, 0, '正在加载签名库…');
    var box = qs('#trojanResults');
    if (box) box.textContent = '';
    setScanBusy('#btnTrojanScan', true);
    showCancel('#btnTrojanCancel', false);
    hapticTap();
    api.send('startTrojanScan');
  }

  function runToolScan(label, action) {
    setScanUi(0, 0, label);
    var box = qs('#trojanResults');
    if (box) box.textContent = '';
    api.send(action);
  }

  function renderTrojan(d) {
    setScanBusy('#btnTrojanScan', false);
    showCancel('#btnTrojanCancel', false);
    var total = d.total || (d.items || []).length;
    setScanUi(total, total, d.summary || '检测完成');
    var box = qs('#trojanResults');
    if (!box) return;
    box.textContent = '';
    var items = d.items || [];
    setCardHeadCount('#trojanHead', '木马检测结果', items.length);
    if (!items.length) { box.textContent = ''; box.appendChild(emptyHint(d.summary || '未发现木马。', 'green')); loadDashboard(); return; }
    var frag = document.createDocumentFragment();
    items.forEach(function (it) {
      frag.appendChild(resultRow(it.title, it.sub + (it.detail ? ' · ' + it.detail : ''), levelText(it.level), levelCls(it.level),
        function () { openApp(it.pkg); }, it.pkg, it.title));
    });
    box.appendChild(frag);
    loadDashboard();
  }

  on('trojanProgress', function (d) {
    setScanUi(d.done, d.total, '木马查杀中 ' + d.done + ' / ' + d.total + '（' + scanPct(d.done, d.total) + '%）');
    showCancel('#btnTrojanCancel', true);
  });
  on('trojanDone', renderTrojan);
  on('rootkitDone', renderTrojan);
  on('modulesDone', renderTrojan);
  on('lockerDone', renderTrojan);

  /* ---------------- 应用锁 ---------------- */

  function loadLock() {
    api.request('getLockState').then(function (r) {
      if (r.ok) { store.set('lock', r.data); return; }
      var card = qs('#lockAppsCard');
      if (card) {
        card.innerHTML = '';
        card.appendChild(errorHint('应用锁数据加载失败', loadLock));
      }
    });
  }

  /* 应用锁列表:数据缓存 + 搜索过滤。
     renderLock 存下完整列表,renderLockRows 按当前关键字渲染,
     输入过滤时不重新拉桥数据,刷新(loadLock)后过滤状态也保持。
     分页渲染:每页 LOCK_PAGE 行,列表尾「加载更多」按需追加,
     数百行一次铺满 DOM 会卡滚动(性能硬规则)。 */
  var LOCK_PAGE = 200;
  var lockApps = [];
  var lockQuery = '';
  var lockShown = LOCK_PAGE;

  function renderLock(d) {
    lockShown = LOCK_PAGE;
    lockApps = d.apps || [];
    var pin = qs('#swPin');
    if (pin) pin.classList.toggle('is-on', !!d.hasPin);
    var st = qs('#pinState');
    if (st) st.textContent = d.hasPin ? '已设置，点击下方可修改' : '未设置，点击下方设置';
    var decoy = qs('#swDecoy');
    if (decoy) decoy.classList.toggle('is-on', !!d.decoy);
    var bio = qs('#swBio');
    if (bio) bio.classList.toggle('is-on', !!d.biometric);
    var bioSub = qs('#bioSub');
    if (bioSub) {
      bioSub.textContent = d.canBiometric
        ? 'PIN 之外可用指纹 / 面容解锁'
        : '设备不支持生物识别';
    }
    var acc = qs('#accessibilitySub');
    if (acc) acc.textContent = d.accessibility ? '已开启，应用锁可自动解锁' : '未开启，用于应用锁的自动解锁';

    var searchCard = qs('#lockSearchCard');
    if (searchCard) searchCard.classList.toggle('is-hidden', !lockApps.length);
    renderLockRows();
  }

  /* 列表尾「加载更多」行:点击追加下一页 */
  function moreRow(remaining, onClick) {
    var btn = el('button', 'row row-more');
    btn.type = 'button';
    btn.appendChild(el('span', 'row-body', '加载更多（还剩 ' + remaining + ' 个）'));
    btn.addEventListener('click', onClick);
    return btn;
  }

  function renderLockRows() {
    var card = qs('#lockAppsCard');
    if (!card) return;
    card.innerHTML = '';
    if (!lockApps.length) {
      card.appendChild(emptyHint('未找到可锁定的应用', 'purple'));
      return;
    }
    var apps = lockApps.filter(function (a) {
      if (!lockQuery) return true;
      return (a.name + ' ' + a.pkg).toLowerCase().indexOf(lockQuery) >= 0;
    });
    if (!apps.length) {
      card.appendChild(emptyHint('没有匹配「' + lockQuery + '」的应用', 'purple'));
      return;
    }
    var shown = apps.slice(0, lockShown);
    // DocumentFragment 批量插入:数百行逐行 appendChild 每行都触发一次重排
    var frag = document.createDocumentFragment();
    shown.forEach(function (a) {
      var row = el('div', 'row');
      row.appendChild(iconFor(a.pkg, a.name));
      var body = el('span', 'row-body');
      body.appendChild(el('span', 'row-title', a.name));
      body.appendChild(el('span', 'row-sub', a.pkg));
      var sw = el('span', 'switch' + (a.locked ? ' is-on' : ''));
      sw.addEventListener('click', function (e) {
        e.stopPropagation();
        var target = !a.locked;
        sw.classList.toggle('is-on', target);
        a.locked = target;
        api.send('setLocked', { pkg: a.pkg, locked: target });
      });
      row.appendChild(body);
      row.appendChild(sw);
      frag.appendChild(row);
    });
    if (apps.length > shown.length) {
      frag.appendChild(moreRow(apps.length - shown.length, function () {
        lockShown += LOCK_PAGE;
        renderLockRows();
      }));
    }
    card.appendChild(frag);
  }

  /* ---------------- 权限审计 ---------------- */

  function loadAudit() {
    api.request('getAudit').then(function (r) {
      if (r.ok) { store.set('audit', r.data); return; }
      var list = qs('#auditList');
      if (list) {
        list.innerHTML = '';
        list.appendChild(errorHint('权限审计数据加载失败', loadAudit));
      }
    });
  }

  function renderAudit(d) {
    var s = qs('#auditSummary');
    if (s) s.textContent = d.summary || '未发现高风险权限应用。';
    var list = qs('#auditList');
    if (!list) return;
    list.innerHTML = '';
    var items = d.items || [];
    if (!items.length) {
      list.appendChild(emptyHint('未发现高风险权限应用', 'green'));
      return;
    }
    var frag = document.createDocumentFragment();
    items.forEach(function (it) {
      var perms = (it.perms || []).join('、');
      frag.appendChild(resultRow(
        it.name, it.pkg + (perms ? ' · ' + perms : ''),
        String(it.score), (it.level === 'HIGH' || it.level === 'CRITICAL') ? 'danger' : 'risky',
        function () { openApp(it.pkg); },
        it.pkg, it.name
      ));
    });
    list.appendChild(frag);
  }

  /* ---------------- 外观主题:跟随系统 / 浅色 / 深色 ---------------- */

  var themePref = 'system';
  var themeMedia = (typeof window.matchMedia === 'function')
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null;

  function resolveTheme(pref) {
    if (pref === 'dark' || pref === 'light') return pref;
    return (themeMedia && themeMedia.matches) ? 'dark' : 'light';
  }

  function applyTheme() {
    document.documentElement.setAttribute('data-theme', resolveTheme(themePref));
    try { localStorage.setItem('sd_theme', themePref); } catch (e) { }
    qsa('.theme-seg').forEach(function (btn) {
      btn.classList.toggle('is-active', btn.dataset.themeOpt === themePref);
    });
  }

  function setThemePref(pref) {
    themePref = (pref === 'dark' || pref === 'light' || pref === 'system') ? pref : 'system';
    applyTheme();
  }

  qsa('.theme-seg').forEach(function (btn) {
    btn.addEventListener('click', function () {
      hapticTap();
      setThemePref(btn.dataset.themeOpt || 'system');
      api.send('setTheme', { theme: themePref });
    });
  });

  if (themeMedia && typeof themeMedia.addEventListener === 'function') {
    themeMedia.addEventListener('change', function () {
      if (themePref === 'system') applyTheme();
    });
  } else if (themeMedia && typeof themeMedia.addListener === 'function') {
    themeMedia.addListener(function () {
      if (themePref === 'system') applyTheme();
    });
  }

  function loadTheme() {
    api.request('getTheme').then(function (r) {
      if (r.ok && r.data && r.data.theme) setThemePref(r.data.theme);
    });
  }

  loadTheme();

  /* ---------------- 工具箱开关 ---------------- */

  function loadToggles() {
    api.request('getToggles').then(function (r) { if (r.ok) store.set('toggles', r.data); });
  }

  function setSwitch(id, on) {
    var s = qs('#' + id);
    if (s) s.classList.toggle('is-on', !!on);
  }

  function renderToggles(d) {
    if (d.mode) applyMode(d.mode);
    setSwitch('swRealtime', d.realtime);
    setSwitch('swBoot', d.boot);
    setSwitch('swAutoUpdate', d.autoUpdate);
    setSwitch('swRoot', d.rootMode);
    var sub = qs('#rootSub');
    if (sub) sub.textContent = d.rootSub || (d.rootMode ? '已启用' : '未启用，开启将请求 su 授权');
  }

  function bindNativeSwitch(id, call) {
    var sw = qs('#' + id);
    if (!sw) return;
    sw.addEventListener('click', function (e) {
      e.stopPropagation();
      var target = !sw.classList.contains('is-on');
      sw.classList.toggle('is-on', target);
      hapticTap();
      call(target); // api.send 内部兜底;Root 开关失败由 rootState 事件回推纠正
    });
    // 整行可点:开关本体只有 46×28px,达不到 48px 触摸目标;
    // 行内点击(非开关本体)转发给开关。开关自己的 stopPropagation 防止双触发。
    var row = sw.closest('.protection-item');
    if (row) {
      row.style.cursor = 'pointer';
      row.addEventListener('click', function () { sw.click(); });
    }
  }

  bindNativeSwitch('swRealtime', function (t) { api.send('toggleRealtime', { on: t }); });
  bindNativeSwitch('swBoot', function (t) { api.send('toggleBoot', { on: t }); });
  bindNativeSwitch('swAutoUpdate', function (t) { api.send('toggleAutoUpdate', { on: t }); });
  bindNativeSwitch('swRoot', function (t) { api.send('toggleRoot', { on: t }); });
  bindNativeSwitch('swDecoy', function (t) { api.send('toggleDecoy', { on: t }); });
  bindNativeSwitch('swBio', function (t) { api.send('toggleBiometric', { on: t }); });

  on('rootState', function (d) {
    setSwitch('swRoot', d.rootMode);
    var sub = qs('#rootSub');
    if (sub) sub.textContent = d.rootSub || (d.rootMode ? '已启用' : '未启用');
  });

  /* 原生侧状态变化后回刷当前面板(PIN 保存成功等) */
  on('lockStateChanged', function () { loadLock(); });

  /* ---------------- 按钮绑定 ---------------- */

  function bind(id, fn) {
    var e = qs('#' + id);
    if (e) e.addEventListener('click', fn);
  }

  bind('btnHeroScan', function () { hapticTap(); window.__sdGoto('security', 'scan'); });
  bind('btnVirusScan', startVirusScan);
  bind('btnTrojanScan', startTrojanScan);
  bind('btnVirusCancel', function () { requestCancelScan('#btnVirusCancel'); });
  bind('btnTrojanCancel', function () { requestCancelScan('#btnTrojanCancel'); });
  bind('btnRootkit', function () { runToolScan('正在检测 Rootkit / 提权后门…', 'startRootkit'); });
  bind('btnModules', function () { runToolScan('正在检测恶意模块 / SU 脚本…', 'startModuleScan'); });
  bind('btnLocker', function () { runToolScan('正在检测锁机软件…', 'startLockerScan'); });
  bind('btnDeepScan', function () { api.send('openDeepScan'); });
  bind('btnVirusCenter', function () { api.send('openVirusCenter'); });

  bind('btnSetPin', function () { api.send('showPinDialog'); });
  bind('pinRow', function () { api.send('showPinDialog'); });
  bind('btnAccessibility', function () { api.send('openAccessibility'); });

  bind('btnUpdate', function () { api.send('checkUpdate'); });
  bind('btnAbout', function () { api.send('showAbout'); });
  bind('btnNetwork', function () { api.send('openNetworkAudit'); });
  bind('btnPrivacy', function () { api.send('openPrivacy'); });
  bind('btnVuln', function () { api.send('openVulnerability'); });

  /* 统计格「已扫描」→ 病毒查杀中心(原生页);病毒库 / 防护中两格走 data-goto */
  bind('statScanned', function () {
    hapticTap();
    api.send('openVirusCenter');
  });

  /* 应用锁搜索过滤:输入即过滤缓存数据,不重新拉桥 */
  var lockSearch = qs('#lockSearch');
  if (lockSearch) {
    lockSearch.addEventListener('input', function () {
      lockQuery = lockSearch.value.trim().toLowerCase();
      lockShown = LOCK_PAGE;
      renderLockRows();
    });
  }

  /* ---------------- 页面可见性 / 键盘可达性 ---------------- */

  /* 转后台时暂停装饰动画(光晕漂移等),由 index.html 的 .page-hidden CSS 生效;
     原生侧 onPause/onResume 也会直接切换该类,这里兜底 WebView 内部的可见性变化 */
  document.addEventListener('visibilitychange', function () {
    document.documentElement.classList.toggle('page-hidden', document.hidden);
  });

  /* 键盘可达性:role="button" 的活动行支持 Enter / 空格触发 */
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    var t = e.target;
    if (t && t.getAttribute && t.getAttribute('role') === 'button') {
      e.preventDefault();
      t.click();
    }
  });

  /* ---------------- 初始化模式选择(强制) ---------------- */

  var currentMode = 'standard';
  var selectedMode = null;

  function modeTitle(id) {
    var t = {
      standard: '标准模式',
      wireless: '无线调试模式',
      superuser: '超级用户模式'
    };
    return t[id] || '标准模式';
  }

  function applyMode(mode) {
    currentMode = mode || 'standard';
    var badge = qs('#modeBadge');
    if (badge) badge.textContent = modeTitle(currentMode);
    // Root 开关仅超级用户模式可见(标准/无线调试模式不开放 Root 功能)
    var rootSw = qs('#swRoot');
    if (rootSw) {
      var row = rootSw.closest('.protection-item');
      if (row) row.style.display = (currentMode === 'superuser') ? '' : 'none';
    }
  }

  /* 权限引导:渲染当前模式所需权限,全部已授予返回 true */
  function renderModePerms(perms) {
    var list = qs('#modePermList');
    if (!list) return true;
    list.innerHTML = '';
    var allOk = true;
    (perms || []).forEach(function (p) {
      allOk = allOk && !!p.granted;
      var item = document.createElement('div');
      item.className = 'mode-perm-item';

      var info = document.createElement('div');
      info.className = 'mode-perm-info';

      var label = document.createElement('span');
      label.className = 'mode-perm-label';
      label.textContent = p.label || p.key;

      var desc = document.createElement('span');
      desc.className = 'mode-perm-desc';
      desc.textContent = p.desc || '';

      info.appendChild(label);
      info.appendChild(desc);
      item.appendChild(info);

      var side = document.createElement('div');
      side.className = 'mode-perm-side';

      var status = document.createElement('span');
      status.className = 'mode-perm-status ' + (p.granted ? 'ok' : 'missing');
      status.textContent = p.granted ? '已授予' : '未授予';
      side.appendChild(status);

      if (!p.granted) {
        var btn = document.createElement('button');
        btn.className = 'mode-perm-btn';
        btn.textContent = '去授权';
        btn.addEventListener('click', function () {
          hapticTap();
          api.send('openPermissionSettings', { key: p.key });
        });
        side.appendChild(btn);
      }
      item.appendChild(side);
      list.appendChild(item);
    });

    var permsWrap = qs('#modePerms');
    if (permsWrap) permsWrap.hidden = allOk;
    if (!allOk) {
      var opts = qs('#modeOptions');
      if (opts) opts.hidden = true;
      var confirmBtn = qs('#btnModeConfirm');
      if (confirmBtn) confirmBtn.hidden = true;
      var title = qs('#modeTitle');
      if (title) title.textContent = '授予所需权限';
      var sub = qs('.mode-subtitle');
      if (sub) sub.textContent = '当前模式需要以下权限才能正常工作';
    }
    return allOk;
  }

  function finishModeSetup(mode) {
    var ov = qs('#modeOverlay');
    if (ov) ov.hidden = true;
    applyMode(mode);
    refreshVisible();
  }

  function bindModeOverlay() {
    var cards = document.querySelectorAll('.mode-card');
    var confirmBtn = qs('#btnModeConfirm');
    var recheckBtn = qs('#btnModeRecheck');
    if (!cards.length) return;

    cards.forEach(function (card) {
      card.addEventListener('click', function () {
        cards.forEach(function (c) { c.classList.remove('is-selected'); });
        card.classList.add('is-selected');
        selectedMode = card.dataset.mode;
        if (confirmBtn) confirmBtn.disabled = false;
        hapticTap();
      });
    });

    if (confirmBtn) {
      confirmBtn.addEventListener('click', function () {
        if (!selectedMode) return;
        api.request('setMode', { mode: selectedMode }).then(function (r) {
          if (r.ok) {
            var perms = r.data && r.data.permissions;
            if (perms && !renderModePerms(perms)) return; // 权限不足,留在引导页
            finishModeSetup(selectedMode);
          }
        });
      });
    }

    if (recheckBtn) {
      recheckBtn.addEventListener('click', function () {
        api.request('getMode').then(function (r) {
          if (r.ok && r.data) {
            var perms = r.data.permissions;
            if (perms && !renderModePerms(perms)) return;
            finishModeSetup(r.data.mode || 'standard');
          }
        });
      });
    }
  }

  /* Root 授权结果回推后自动重新检查权限 */
  on('modePermissions', function () {
    api.request('getMode').then(function (r) {
      if (r.ok && r.data && r.data.permissions && !renderModePerms(r.data.permissions)) return;
      if (r.ok && r.data) finishModeSetup(r.data.mode || 'standard');
    });
  });

  /* ---------------- 初始化 ---------------- */

  function init() {
    if (started) return;
    started = true;
    // 有原生桥时首屏显示骨架加载态,数据就绪后在 renderDashboard 中移除
    if (bridge) {
      var home = qs('.panel[data-panel="home"]');
      if (home) home.classList.add('is-loading');
    }
    hookNavRefresh();
    bindModeOverlay();
    // 强制初始化模式选择:未选择前不进入主界面
    api.request('getMode').then(function (r) {
      if (r.ok && r.data) {
        if (!r.data.selected) {
          var ov = qs('#modeOverlay');
          if (ov) ov.hidden = false;
        } else {
          applyMode(r.data.mode);
        }
      } else {
        applyMode('standard');
      }
    });
    // 渲染订阅:数据到 → 对应面板重绘(渲染与取数解耦)
    store.subscribe('dashboard', renderDashboard);
    store.subscribe('lock', renderLock);
    store.subscribe('audit', renderAudit);
    store.subscribe('toggles', renderToggles);
    // 默认面板就是首页,refreshVisible 即完成首屏加载(不再额外 loadDashboard 重复取数)
    refreshVisible();
  }

  document.addEventListener('DOMContentLoaded', function () { init(); });
  if (document.readyState !== 'loading') init();

  /* ============================================================
     演示数据(无 AndroidBridge 时,纯 HTML 预览可用)
     ============================================================ */

  function demoDashboard() {
    return { libOk: true, scanned: 1234, threats: 0, protected: 3, locked: 2, risky: 3, lastScanAt: Date.now() - 3600e3 };
  }
  function demoLock() {
    return {
      hasPin: true, decoy: false, biometric: true, canBiometric: true, accessibility: false,
      apps: [
        { name: '微信', pkg: 'com.tencent.mm', locked: true },
        { name: '支付宝', pkg: 'com.eg.android.AlipayGphone', locked: false },
        { name: '相册', pkg: 'com.android.gallery3d', locked: true }
      ]
    };
  }
  function demoAudit() {
    return {
      summary: '发现 3 个应用存在高风险权限组合，建议逐项审查。',
      items: [
        { name: '某清理大师', pkg: 'com.demo.cleaner', score: 92, level: 'CRITICAL', perms: ['读取通讯录', '定位', '读取短信'] },
        { name: '某天气', pkg: 'com.demo.weather', score: 68, level: 'HIGH', perms: ['后台定位', '读取设备信息'] },
        { name: '某手电筒', pkg: 'com.demo.torch', score: 54, level: 'HIGH', perms: ['读取通讯录'] }
      ]
    };
  }
  function demoToggles() {
    return { realtime: true, boot: false, autoUpdate: true, rootMode: false, rootSub: '未启用，开启将请求 su 授权' };
  }

  function demoVirus() {
    var n = 0, total = 84;
    var t = setInterval(function () {
      n += 3; if (n > total) n = total;
      window.__sdEvent('virusProgress', JSON.stringify({ done: n, total: total }));
      if (n >= total) {
        clearInterval(t);
        window.__sdEvent('virusDone', JSON.stringify({
          results: [], total: total, infected: 0, summary: '扫描完成，共 ' + total + ' 项，未发现威胁'
        }));
      }
    }, 60);
  }
  function demoTrojan() {
    window.__sdEvent('trojanDone', JSON.stringify({
      total: 1, items: [], summary: '木马查杀完成，未发现木马。'
    }));
  }

  /* ============================================================
     子页面路由(深层工具全HTML化)
     ============================================================ */

  var subStack = [];

  function openSubPage(name) {
    var sub = qs('#subpage');
    if (!sub) return;
    var body = qs('.subpage-body[data-subpage="' + name + '"]');
    if (!body) return;

    subStack.push(name);
    sub.classList.add('is-open');
    qsa('.subpage-body').forEach(function (b) { b.classList.remove('is-active'); });
    body.classList.add('is-active');
    sub.scrollTop = 0;
    hapticTap();

    // 页面载入时触发对应加载
    if (name === 'netaudit') loadNetAudit();
    else if (name === 'privacy') loadPrivacyAudit();
    else if (name === 'vuln') loadVulnScan();
    else if (name === 'deepscan') loadDeepScan();
    else if (name === 'viruscenter') loadVirusCenter();
  }

  function closeSubPage() {
    if (subStack.length === 0) return false;
    subStack.pop();
    if (subStack.length > 0) {
      var prev = subStack[subStack.length - 1];
      var body = qs('.subpage-body[data-subpage="' + prev + '"]');
      qsa('.subpage-body').forEach(function (b) { b.classList.remove('is-active'); });
      if (body) body.classList.add('is-active');
      return true;
    }
    var sub = qs('#subpage');
    if (sub) sub.classList.remove('is-open');
    hapticTap();
    return true;
  }

  /** 全局返回:子页面 → 非首页板块 → 首页 → false(交给原生双击退出) */
  window.__sdBack = function () {
    if (subStack.length > 0) return closeSubPage();
    var p = qs('.panel.is-active');
    var panel = p ? p.dataset.panel : 'home';
    if (panel !== 'home') {
      window.__sdGoto('home', null);
      return true;
    }
    return false;
  };

  // 子页面返回按钮
  function hookSubpageBack() {
    qsa('.subpage-back').forEach(function (btn) {
      btn.addEventListener('click', function () { closeSubPage(); });
    });
  }

  // 原生桥打开子页面
  on('openSubPage', function (data) {
    if (data && data.page) openSubPage(data.page);
  });

  /* 底部导航在所有页面可用:子页面打开时点击 dock 先返回根页面,再切换面板 */
  var dockNav = qs('.dock');
  if (dockNav) {
    dockNav.addEventListener('click', function () {
      if (subStack.length > 0) closeSubPage();
    }, true);
  }

  // 原生触发的对话框事件
  on('showAbout', function () { showAboutDialog(); });
  on('showUpdateDialog', function () { showUpdateDialog(); });

  /* ============================================================
     HTML 模态框(替代原生 AlertDialog)
     ============================================================ */

  var modalStack = [];

  function closeModal() {
    var overlay = qs('#modalOverlay');
    if (!overlay) return;
    if (modalStack.length > 0) {
      var top = modalStack.pop();
      if (top.onClose) { try { top.onClose(); } catch (e) { } }
    }
    if (modalStack.length === 0) {
      overlay.classList.remove('is-open');
    } else {
      renderModal(modalStack[modalStack.length - 1]);
    }
  }

  function renderModal(cfg) {
    qs('#modalTitle').textContent = cfg.title || '';
    var body = qs('#modalBody');
    body.innerHTML = '';
    if (typeof cfg.body === 'string') {
      body.innerHTML = cfg.body;
    } else if (cfg.body instanceof Node) {
      body.appendChild(cfg.body);
    }
    var actions = qs('#modalActions');
    actions.innerHTML = '';
    if (cfg.buttons && cfg.buttons.length) {
      cfg.buttons.forEach(function (btn, i) {
        var el = document.createElement('button');
        el.className = 'btn ' + (btn.primary ? 'btn-primary' : 'btn-ghost');
        el.textContent = btn.label;
        el.addEventListener('click', function () {
          if (btn.onClick) {
            var keep = false;
            try { keep = btn.onClick() === true; } catch (e) { }
            if (!keep) closeModal();
          } else {
            closeModal();
          }
        });
        actions.appendChild(el);
      });
    }
    var overlay = qs('#modalOverlay');
    if (overlay) overlay.classList.add('is-open');
  }

  function showModal(cfg) {
    modalStack.push(cfg);
    renderModal(cfg);
  }

  function uiAlert(title, msg, onOk) {
    showModal({
      title: title,
      body: '<p style="margin:0">' + esc(msg) + '</p>',
      buttons: [
        { label: '确定', primary: true, onClick: onOk }
      ]
    });
  }

  function uiConfirm(title, msg, onOk, onCancel) {
    showModal({
      title: title,
      body: '<p style="margin:0">' + esc(msg) + '</p>',
      buttons: [
        { label: '取消', onClick: onCancel },
        { label: '确定', primary: true, onClick: onOk }
      ]
    });
  }

  function uiPrompt(title, fields, onSubmit, submitLabel) {
    // fields: [{name, label, type, value}]
    var wrap = document.createElement('div');
    if (typeof fields === 'string') {
      var p = document.createElement('p');
      p.style.margin = '0';
      p.textContent = fields;
      wrap.appendChild(p);
      fields = [{ name: 'input', label: '', type: 'text', value: '' }];
    }
    var inputs = [];
    fields.forEach(function (f) {
      var inp = document.createElement('input');
      inp.type = f.type || 'text';
      inp.placeholder = f.label || '';
      inp.value = f.value || '';
      inp.dataset.name = f.name || 'value';
      wrap.appendChild(inp);
      inputs.push(inp);
    });
    showModal({
      title: title,
      body: wrap,
      buttons: [
        { label: '取消' },
        {
          label: submitLabel || '确定', primary: true,
          onClick: function () {
            var result = {};
            inputs.forEach(function (inp) { result[inp.dataset.name] = inp.value; });
            if (onSubmit) { var keep = onSubmit(result); return keep === true; }
          }
        }
      ]
    });
  }

  function uiListDialog(title, items, onSelect) {
    // items: [{label, value}]
    var list = document.createElement('div');
    list.className = 'modal-list';
    items.forEach(function (it) {
      var btn = document.createElement('button');
      btn.className = 'modal-list-item';
      btn.textContent = it.label;
      btn.addEventListener('click', function () {
        if (onSelect) onSelect(it.value, it);
        closeModal();
      });
      list.appendChild(btn);
    });
    showModal({
      title: title,
      body: list,
      buttons: [{ label: '取消' }]
    });
  }

  /* 点击遮罩关闭最上层模态框 */
  document.addEventListener('click', function (e) {
    if (e.target && e.target.id === 'modalOverlay') closeModal();
  });

  /* ============================================================
     Toast 提示条(替代原生 Toast)
     ============================================================ */

  var toastTimer = 0;
  function toast(msg, duration) {
    var bar = qs('#toastBar');
    if (!bar) return;
    bar.textContent = msg;
    bar.classList.add('is-show');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      bar.classList.remove('is-show');
      toastTimer = 0;
    }, duration || 1800);
  }

  /* ============================================================
     统一列表渲染(与原生 TrojanAdapter.UiItem 数据模型一致)
     ============================================================ */

  function renderResultList(containerId, items) {
    var wrap = qs('#' + containerId);
    if (!wrap) return;
    wrap.innerHTML = '';
    if (!items || items.length === 0) return;
    for (var i = 0; i < items.length; i++) {
      wrap.appendChild(resultItemEl(items[i]));
    }
  }

  function resultItemEl(item) {
    var level = item.level || 'low';
    var card = el('div', 'card result-item');
    var bar = el('div', 'risk-bar level-' + level);
    card.appendChild(bar);

    var title = el('p', 'result-title level-' + level, item.title || '');
    card.appendChild(title);

    if (item.sub) {
      var sub = el('p', 'result-sub', item.sub);
      card.appendChild(sub);
    }
    if (item.detail) {
      var detail = el('p', 'result-detail', item.detail);
      card.appendChild(detail);
    }
    if (item.suggestion) {
      var sug = el('p', 'result-suggest', item.suggestion);
      card.appendChild(sug);
    }

    var hasAction = item.uninstallPkg || item.fixCommand;
    if (hasAction) {
      var actions = el('div', 'result-actions');
      if (item.fixCommand) {
        var fixBtn = el('button', 'result-btn', item.fixLabel || '修复');
        (function (cmd) {
          fixBtn.addEventListener('click', function () {
            uiConfirm('确认执行', '是否执行以下命令?\n\n' + cmd, function () {
              api.request('runFixCommand', { cmd: cmd }).then(function (r) {
                toast(r && r.ok ? '执行成功' : '执行失败');
              });
            });
          });
        })(item.fixCommand);
        actions.appendChild(fixBtn);
      }
      if (item.uninstallPkg) {
        var uninstBtn = el('button', 'result-btn danger', '卸载');
        (function (pkg) {
          uninstBtn.addEventListener('click', function () {
            api.send('openAppSettings', { pkg: pkg });
          });
        })(item.uninstallPkg);
        actions.appendChild(uninstBtn);
      }
      card.appendChild(actions);
    }

    return card;
  }

  /* ============================================================
     列表型工具页(网络审计 / 隐私检测 / 漏洞扫描)
     ============================================================ */

  function setListStatus(pageId, text, loading) {
    var statusEl = qs('#' + pageId + 'Status');
    var spin = qs('#' + pageId + ' .progress-ring');
    if (statusEl) statusEl.textContent = text;
    if (spin) spin.classList.toggle('is-hidden', !loading);
  }

  /* 工具页失败降级:状态文案 + 列表区错误卡(带重试) */
  function listLoadError(pageId, listId, message, retry) {
    setListStatus(pageId, '检测失败', false);
    var list = qs('#' + listId);
    if (list) {
      list.innerHTML = '';
      list.appendChild(errorHint(message, retry));
    }
  }

  function loadNetAudit() {
    setListStatus('net', '正在检测网络安全…', true);
    api.request('getNetAudit').then(function (r) {
      if (r && r.ok && r.data) {
        setListStatus('net', '检测完成 · 共 ' + r.data.count + ' 项', false);
        renderResultList('netList', r.data.items);
      } else {
        listLoadError('net', 'netList', '网络安全检测失败', loadNetAudit);
      }
    });
  }

  function loadPrivacyAudit() {
    setListStatus('priv', '正在扫描隐私权限…', true);
    api.request('getPrivacyAudit').then(function (r) {
      if (r && r.ok && r.data) {
        setListStatus('priv', '扫描完成 · 共 ' + r.data.count + ' 项', false);
        renderResultList('privList', r.data.items);
      } else {
        listLoadError('priv', 'privList', '隐私权限扫描失败', loadPrivacyAudit);
      }
    });
  }

  function loadVulnScan() {
    setListStatus('vuln', '正在检测系统漏洞…', true);
    api.request('getVulnScan').then(function (r) {
      if (r && r.ok && r.data) {
        setListStatus('vuln', '检测完成 · 共 ' + r.data.count + ' 项', false);
        renderResultList('vulnList', r.data.items);
      } else {
        listLoadError('vuln', 'vulnList', '系统漏洞检测失败', loadVulnScan);
      }
    });
  }

  /* ============================================================
     深度查杀页
     ============================================================ */

  var deepRunning = false;

  function loadDeepScan() {
    var btn = qs('#btnDeepStart');
    if (!btn) return;
    btn.disabled = deepRunning;
    btn.textContent = deepRunning ? '扫描中…' : '开始';
    qs('#deepPhase').textContent = deepRunning ? '深度扫描进行中…' : '点击开始极致扫描';
    if (!btn.dataset.hooked) {
      btn.dataset.hooked = '1';
      btn.addEventListener('click', function () {
        if (deepRunning) return;
        startDeepScan();
      });
    }
  }

  function startDeepScan() {
    deepRunning = true;
    var btn = qs('#btnDeepStart');
    if (btn) { btn.disabled = true; btn.textContent = '扫描中…'; }
    qs('#deepPhase').textContent = '深度扫描启动中…';
    qs('#deepList').innerHTML = '';
    api.send('startDeepScan');
  }

  on('deepscan.progress', function (data) {
    if (data && data.phase) {
      var el_ = qs('#deepPhase');
      if (el_) el_.textContent = data.phase;
    }
  });

  on('deepscan.result', function (data) {
    deepRunning = false;
    var btn = qs('#btnDeepStart');
    if (btn) { btn.disabled = false; btn.textContent = '重新扫描'; }
    var phase = qs('#deepPhase');
    if (phase && data) phase.textContent = '扫描完成 · 共 ' + (data.count || 0) + ' 项';
    if (data && data.items) renderResultList('deepList', data.items);
  });

  /* ============================================================
     病毒中心页
     ============================================================ */

  var vcMenuLoaded = false;
  var vcRunning = false;

  function loadVirusCenter() {
    if (vcMenuLoaded) return;
    api.request('getVirusCenterMenu').then(function (r) {
      if (r && r.ok && r.data && r.data.menu) {
        renderVCMenu(r.data.menu);
        vcMenuLoaded = true;
      }
    });
  }

  function renderVCMenu(menu) {
    var list = qs('#vcMenuList');
    if (!list) return;
    list.innerHTML = '';
    for (var i = 0; i < menu.length; i++) {
      var item = menu[i];
      var row = document.createElement('button');
      row.className = 'row';
      row.innerHTML =
        '<span class="row-body">' +
          '<span class="row-title">' + esc(item.title) + '</span>' +
          '<span class="row-sub">' + esc(item.sub) + '</span>' +
        '</span>' +
        '<svg class="row-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 5 7 7-7 7"/></svg>';
      (function (actionId, title) {
        row.addEventListener('click', function () {
          if (vcRunning) { toast('正在运行，请稍候'); return; }
          runVirusTool(actionId, title);
        });
      })(item.id, item.title);
      list.appendChild(row);
    }
  }

  function runVirusTool(actionId, title) {
    if (actionId === 'phishing') { showPhishingDialog(); return; }
    vcRunning = true;
    qs('#vcMenu').classList.add('is-hidden');
    var run = qs('#vcRun');
    run.classList.remove('is-hidden');
    qs('#vcPhase').textContent = title + '…';
    qs('#vcResultList').innerHTML = '';
    qs('#vcCancel').onclick = function () {
      api.send('cancelVirusTool');
      toast('已请求取消');
    };
    api.send('runVirusTool', { action: actionId });
  }

  /* 恶意链接检测:输入网址 → 本地启发式判定(纯字符串分析,不访问目标地址) */
  function showPhishingDialog() {
    uiPrompt(
      '恶意链接检测',
      [{ name: 'url', label: '网址 URL', type: 'text', value: '' }],
      function (vals) {
        if (!vals.url) { toast('请输入网址'); return true; }
        api.request('checkUrl', { url: vals.url }).then(function (res) {
          var d = (res && res.data) || {};
          var titleMap = { danger: '危险', warn: '可疑', clean: '未发现异常', invalid: '网址不是有效格式' };
          var lines = ['风险评分: ' + (d.score || 0) + ' · 判定: ' + (titleMap[d.level] || d.level)];
          var f = d.findings || [];
          for (var i = 0; i < f.length; i++) lines.push('· ' + f[i]);
          uiAlert('检测结论', lines.join('\n'));
        });
      },
      '检测'
    );
  }

  function showVCMenu() {
    vcRunning = false;
    qs('#vcRun').classList.add('is-hidden');
    qs('#vcMenu').classList.remove('is-hidden');
  }

  on('virustool.progress', function (data) {
    if (data && data.phase) {
      var el_ = qs('#vcPhase');
      if (el_) el_.textContent = data.phase;
    }
  });

  on('virustool.result', function (data) {
    vcRunning = false;
    if (data && data.items) {
      renderResultList('vcResultList', data.items);
      var ph = qs('#vcPhase');
      if (ph) ph.textContent = '完成 · 共 ' + (data.count || 0) + ' 项';
    }
    // 添加返回菜单按钮
    var backBtn = document.createElement('button');
    backBtn.className = 'btn btn-ghost btn-block';
    backBtn.textContent = '返回工具列表';
    backBtn.style.marginTop = '10px';
    backBtn.addEventListener('click', showVCMenu);
    var list = qs('#vcResultList');
    if (list && list.parentNode) {
      var existing = list.parentNode.querySelector('.vc-back-btn');
      if (existing) existing.remove();
      backBtn.classList.add('vc-back-btn');
      list.parentNode.insertBefore(backBtn, list.nextSibling);
    }
  });

  /* ============================================================
     关于 / 更新对话框(HTML化,替代原生 AlertDialog)
     ============================================================ */

  function showAboutDialog() {
    api.request('getAbout').then(function (r) {
      var d = r && r.data ? r.data : {};
      uiAlert('关于 SecureDroid',
        '版本 ' + (d.version || '?') + ' (' + (d.versionCode || 0) + ')\n\n' +
        (d.desc || '') + '\n' + (d.license || ''));
    });
  }

  function showUpdateDialog() {
    api.request('getUpdateInfo').then(function (r) {
      var d = r && r.data ? r.data : {};
      uiPrompt(
        '检查更新',
        [
          { name: 'status', label: '', type: 'text', value: '签名库 ' + (d.signatureCount || 0) + ' · hash ' + (d.hashCount || 0) },
          { name: 'url', label: '更新地址 URL', type: 'text', value: d.url || '' },
          { name: 'sha', label: 'SHA-256 校验(可选)', type: 'text', value: d.sha || '' }
        ],
        function (vals) {
          if (!vals.url) { toast('请输入更新地址'); return true; }
          api.request('runUpdate', { url: vals.url, sha: vals.sha }).then(function (res) {
            uiAlert('更新结果', (res && res.data && res.data.message) || '更新失败');
          });
        },
        '更新'
      );
      // 第一个字段是只读状态显示,追加官方病毒库接入引导
      var first = qs('#modalBody input');
      if (first) {
        first.readOnly = true;
        first.style.opacity = '.6';
        first.value += ' · ' + (d.lastUpdateAt ? '上次更新 ' + fmtTime(d.lastUpdateAt) : '暂无更新历史') +
          ' · 官方库接入:tools/cvd2clamav.js 解包 daily.cvd 后填 URL+SHA-256';
      }
    });
  }

  /* ============================================================
     PIN 对话框(HTML化)
     ============================================================ */

  on('showPinDialog', function () {
    showPinDialog();
  });

  function showPinDialog() {
    var wrap = document.createElement('div');
    wrap.innerHTML = '<p style="margin:0 0 8px">设置 4 位 PIN 码</p>';
    var p1 = document.createElement('input');
    p1.type = 'password'; p1.placeholder = '新 PIN (4 位)'; p1.maxLength = 4;
    p1.style.webkitTextSecurity = 'disc';
    var p2 = document.createElement('input');
    p2.type = 'password'; p2.placeholder = '确认 PIN'; p2.maxLength = 4;
    p2.style.webkitTextSecurity = 'disc';
    wrap.appendChild(p1); wrap.appendChild(p2);

    showModal({
      title: '设置应用锁 PIN',
      body: wrap,
      buttons: [
        { label: '取消' },
        {
          label: '保存', primary: true,
          onClick: function () {
            var pin = p1.value, confirm = p2.value;
            if (pin.length !== 4) { toast('PIN 必须为 4 位'); return true; }
            if (pin !== confirm) { toast('两次输入不一致'); return true; }
            api.request('savePin', { pin: pin }).then(function (r) {
              if (r && r.ok && r.data && r.data.ok) {
                toast('PIN 已保存');
                store.set('lock', null);
                api.request('getLockState').then(function (res) {
                  if (res.ok) store.set('lock', res.data);
                });
              } else {
                toast('保存失败');
              }
            });
          }
        }
      ]
    });
  }

  /* ============================================================
     Demo 数据扩展(子页面预览)
     ============================================================ */

  function demoResultItems() {
    return [
      { title: 'Demo.Item · 示例检测项 1', sub: 'com.example.app1', detail: '这是 demo 模式下的示例结果项，用于纯 HTML 预览', level: 'high', suggestion: '建议卸载此应用', uninstallPkg: 'com.example.app1' },
      { title: 'Demo.Item · 示例检测项 2', sub: '系统组件', detail: '低风险项，无需处理', level: 'low' },
      { title: 'Demo.Item · 示例检测项 3', sub: 'com.example.app2', detail: '中等风险，请关注', level: 'medium', fixCommand: 'pm disable com.example.app2', fixLabel: '禁用' }
    ];
  }

  var _origDemoRequest = demoRequest;
  demoRequest = function (action, payload) {
    switch (action) {
      case 'getNetAudit':
      case 'getPrivacyAudit':
      case 'getVulnScan':
        return { items: demoResultItems(), count: 3 };
      case 'getVirusCenterMenu':
        return { menu: [
          { id: 'parallel', title: '并行多引擎扫描', sub: '4 引擎并行查杀' },
          { id: 'diff', title: '差异扫描', sub: '仅检测变更应用' },
          { id: 'recent', title: '最近安装检测', sub: '近 7 天新安装应用' },
          { id: 'quarantine', title: '隔离区管理', sub: '查看和管理隔离文件' }
        ]};
      case 'getAbout':
        return { version: '1.9.15', versionCode: 19150, desc: 'SecureDroid 全方位移动安全防护', license: '基于开源安全引擎构建' };
      case 'getUpdateInfo':
        return { signatureCount: 256, hashCount: 1024000, byteCount: 45000000, url: '', sha: '' };
      case 'savePin':
        return { ok: true };
      case 'runUpdate':
        return { ok: true, message: '更新完成，已加载 256 条新签名' };
      case 'runFixCommand':
        return { ok: true };
      case 'runVirusTool':
        // 模拟异步运行
        setTimeout(function () {
          for (var i = 0; i < 3; i++) {
            (function (i) {
              setTimeout(function () {
                window.__sdEvent('virustool.progress', JSON.stringify({ phase: '扫描中… ' + (i + 1) + '/3', running: true, action: payload && payload.action }));
              }, i * 400);
            })(i);
          }
          setTimeout(function () {
            window.__sdEvent('virustool.result', JSON.stringify({
              items: demoResultItems(), count: 3, running: false, action: payload && payload.action
            }));
          }, 1500);
        }, 50);
        return {};
      case 'startDeepScan':
        setTimeout(function () {
          var phases = ['正在扫描运行进程…', '正在扫描系统分区…', '正在扫描用户目录…', '正在分析结果…'];
          for (var i = 0; i < phases.length; i++) {
            (function (p, d) {
              setTimeout(function () {
                window.__sdEvent('deepscan.progress', JSON.stringify({ phase: p, running: true }));
              }, d);
            })(phases[i], i * 500);
          }
          setTimeout(function () {
            window.__sdEvent('deepscan.result', JSON.stringify({
              items: demoResultItems(), count: 3, running: false
            }));
          }, phases.length * 500 + 200);
        }, 50);
        return {};
      default:
        return _origDemoRequest(action, payload);
    }
  };

  /* ============================================================
     初始化扩展
     ============================================================ */

  var _origInit = init;
  init = function () {
    _origInit();
    hookSubpageBack();
  };
  // 重新触发一次(如果 _origInit 已被调用)
  if (started) {
    hookSubpageBack();
  }

})();
