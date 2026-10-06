/* ============================================================
   SecureDroid WebUI — JS 桥接层
   ------------------------------------------------------------
   把上传稿界面上的每个按钮 / 开关 / 列表接到原生功能(AndroidBridge):
     - 首页概览(getDashboard)
     - 病毒扫描 / 木马查杀 / Rootkit / 恶意模块 / 锁机检测(异步事件推送)
     - 应用锁(状态 + 锁定开关 + PIN 弹窗 + 无障碍)
     - 权限审计(getAudit)
     - 工具箱开关(实时防护 / 开机自启 / Root)与检查更新 / 关于 / 工具入口
   没有 AndroidBridge(纯 HTML 预览)时回退到演示数据,界面依然可用。
   ============================================================ */
(function () {
  'use strict';

  var bridge = (typeof AndroidBridge !== 'undefined') ? AndroidBridge : null;
  var demo = !bridge;
  var started = false;

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
    if (bridge) { try { bridge.haptic(); } catch (e) { } }
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

  /* ---------------- 原生事件通道 ----------------
     AndroidBridge 以 JS 字面量调用: window.__sdEvent('virusDone', {...})
     演示模式以 JSON 字符串调用;这里两种都兼容。 */

  var handlers = {};
  window.__sdHandlers = handlers;
  function on(type, fn) { handlers[type] = fn; }

  window.__sdEvent = function (type, payload) {
    var d = payload;
    if (typeof payload === 'string') {
      try { d = JSON.parse(payload); } catch (e) { d = {}; }
    }
    var h = handlers[type];
    if (h) { try { h(d); } catch (e) { } }
  };

  /* 页面就绪(原生 onPageFinished / onResume 时调用,刷新当前面板数据) */
  window.__sdReady = function () { scheduleRefresh(); };

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
    if (demo) { renderDashboard(demoDashboard()); return; }
    try { renderDashboard(JSON.parse(bridge.getDashboard())); } catch (e) { }
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
    var lib = qs('#statLib');
    if (lib) lib.textContent = d.libOk ? '已更新' : '待更新';
    var sc = qs('#statScanned');
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
    if (bridge) { try { bridge.cancelScan(); } catch (e) { } }
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

  /* 结果行点击 → 打开该应用的应用详情页(可改权限 / 卸载) */
  function openApp(pkg) {
    if (!pkg) return;
    if (bridge) { try { bridge.openAppSettings(pkg); } catch (e) { } }
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
    if (demo) { demoVirus(); return; }
    try { bridge.startVirusScan(); } catch (e) { setScanBusy('#btnVirusScan', false); }
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
    if (!hits.length) { box.textContent = d.summary || '未发现威胁，设备安全。'; loadDashboard(); return; }
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
    if (demo) { demoTrojan(); return; }
    try { bridge.startTrojanScan(); } catch (e) { setScanBusy('#btnTrojanScan', false); }
  }

  function runToolScan(label, nativeCall) {
    setScanUi(0, 0, label);
    var box = qs('#trojanResults');
    if (box) box.textContent = '';
    if (demo) { demoTrojan(); return; }
    try { nativeCall(); } catch (e) { }
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
    if (!items.length) { box.textContent = d.summary || '未发现木马。'; loadDashboard(); return; }
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
    if (demo) { renderLock(demoLock()); return; }
    try { renderLock(JSON.parse(bridge.getLockState())); } catch (e) { }
  }

  function renderLock(d) {
    var pin = qs('#swPin');
    if (pin) pin.classList.toggle('is-on', !!d.hasPin);
    var st = qs('#pinState');
    if (st) st.textContent = d.hasPin ? '已设置，点击下方可修改' : '未设置，点击下方设置';
    var decoy = qs('#swDecoy');
    if (decoy) decoy.classList.toggle('is-on', !!d.decoy);
    var acc = qs('#accessibilitySub');
    if (acc) acc.textContent = d.accessibility ? '已开启，应用锁可自动解锁' : '未开启，用于应用锁的自动解锁';

    var card = qs('#lockAppsCard');
    if (!card) return;
    card.innerHTML = '';
    var apps = d.apps || [];
    if (!apps.length) {
      card.appendChild(el('div', 'empty-hint', '未找到可锁定的应用'));
      return;
    }
    // DocumentFragment 批量插入:数百行逐行 appendChild 每行都触发一次重排
    var frag = document.createDocumentFragment();
    apps.forEach(function (a) {
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
        if (bridge) { try { bridge.setLocked(a.pkg, target); } catch (err) { } }
      });
      row.appendChild(body);
      row.appendChild(sw);
      frag.appendChild(row);
    });
    card.appendChild(frag);
  }

  /* ---------------- 权限审计 ---------------- */

  function loadAudit() {
    if (demo) { renderAudit(demoAudit()); return; }
    try { renderAudit(JSON.parse(bridge.getAudit())); } catch (e) { }
  }

  function renderAudit(d) {
    var s = qs('#auditSummary');
    if (s) s.textContent = d.summary || '未发现高风险权限应用。';
    var list = qs('#auditList');
    if (!list) return;
    list.innerHTML = '';
    var items = d.items || [];
    if (!items.length) {
      list.appendChild(el('div', 'empty-hint', '未发现高风险权限应用'));
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

  /* ---------------- 工具箱开关 ---------------- */

  function loadToggles() {
    if (demo) { renderToggles(demoToggles()); return; }
    try { renderToggles(JSON.parse(bridge.getToggles())); } catch (e) { }
  }

  function setSwitch(id, on) {
    var s = qs('#' + id);
    if (s) s.classList.toggle('is-on', !!on);
  }

  function renderToggles(d) {
    setSwitch('swRealtime', d.realtime);
    setSwitch('swBoot', d.boot);
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
      if (!bridge) return;
      hapticTap();
      try { call(target); } catch (err) { sw.classList.toggle('is-on', !target); }
    });
    // 整行可点:开关本体只有 46×28px,达不到 48px 触摸目标;
    // 行内点击(非开关本体)转发给开关。开关自己的 stopPropagation 防止双触发。
    var row = sw.closest('.protection-item');
    if (row) {
      row.style.cursor = 'pointer';
      row.addEventListener('click', function () { sw.click(); });
    }
  }

  bindNativeSwitch('swRealtime', function (t) { bridge.toggleRealtime(t); });
  bindNativeSwitch('swBoot', function (t) { bridge.toggleBoot(t); });
  bindNativeSwitch('swRoot', function (t) { bridge.toggleRoot(t); });
  bindNativeSwitch('swDecoy', function (t) { bridge.toggleDecoy(t); });

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
  bind('btnRootkit', function () { runToolScan('正在检测 Rootkit / 提权后门…', function () { bridge.startRootkit(); }); });
  bind('btnModules', function () { runToolScan('正在检测恶意模块 / SU 脚本…', function () { bridge.startModuleScan(); }); });
  bind('btnLocker', function () { runToolScan('正在检测锁机软件…', function () { bridge.startLockerScan(); }); });
  bind('btnDeepScan', function () { if (bridge) { try { bridge.openDeepScan(); } catch (e) { } } });
  bind('btnVirusCenter', function () { if (bridge) { try { bridge.openVirusCenter(); } catch (e) { } } });

  bind('btnSetPin', function () { if (bridge) { try { bridge.showPinDialog(); } catch (e) { } } });
  bind('pinRow', function () { if (bridge) { try { bridge.showPinDialog(); } catch (e) { } } });
  bind('btnAccessibility', function () { if (bridge) { try { bridge.openAccessibility(); } catch (e) { } } });

  bind('btnUpdate', function () { if (bridge) { try { bridge.checkUpdate(); } catch (e) { } } });
  bind('btnAbout', function () { if (bridge) { try { bridge.showAbout(); } catch (e) { } } });
  /* 顶部头像 = 关于 */
  bind('btnAvatar', function () { if (bridge) { try { bridge.showAbout(); } catch (e) { } } });
  bind('btnNetwork', function () { if (bridge) { try { bridge.openNetworkAudit(); } catch (e) { } } });
  bind('btnPrivacy', function () { if (bridge) { try { bridge.openPrivacy(); } catch (e) { } } });
  bind('btnVuln', function () { if (bridge) { try { bridge.openVulnerability(); } catch (e) { } } });

  /* ---------------- 问候语 ---------------- */

  function setGreeting() {
    var g = qs('.greeting');
    if (!g) return;
    var now = new Date();
    var week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()];
    g.textContent = week + ' · ' + (now.getMonth() + 1) + '月' + now.getDate() + '日';
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

  /* ---------------- 初始化 ---------------- */

  function init() {
    if (started) return;
    started = true;
    // 有原生桥时首屏显示骨架加载态,数据就绪后在 renderDashboard 中移除
    if (bridge) {
      var home = qs('.panel[data-panel="home"]');
      if (home) home.classList.add('is-loading');
    }
    setGreeting();
    hookNavRefresh();
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
      hasPin: true, decoy: false, accessibility: false,
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
    return { realtime: true, boot: false, rootMode: false, rootSub: '未启用，开启将请求 su 授权' };
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

})();
