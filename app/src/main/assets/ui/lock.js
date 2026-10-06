/* SecureDroid PIN 解锁页 JS */
(function () {
  'use strict';

  var bridge = (typeof AndroidBridge !== 'undefined') ? AndroidBridge : null;
  var input = '';
  var dots = document.querySelectorAll('.pin-dot');
  var msgEl = document.getElementById('lockMsg');

  function refresh() {
    for (var i = 0; i < dots.length; i++) {
      dots[i].classList.toggle('is-filled', i < input.length);
      dots[i].classList.remove('is-error');
    }
  }

  function shakeDots() {
    for (var i = 0; i < dots.length; i++) {
      dots[i].classList.add('is-error');
    }
    setTimeout(function () {
      for (var i = 0; i < dots.length; i++) {
        dots[i].classList.remove('is-error');
      }
    }, 400);
  }

  function showMsg(text) {
    if (msgEl) msgEl.textContent = text || '';
  }

  function checkPin() {
    if (!bridge) {
      // demo 模式:任意 4 位通过
      setTimeout(function () {
        showMsg('Demo 模式解锁成功');
        input = '';
        refresh();
      }, 200);
      return;
    }
    try {
      bridge.post('verifyPin', JSON.stringify({ pin: input }));
    } catch (e) {
      showMsg('验证失败');
      shakeDots();
      input = '';
      refresh();
    }
  }

  /* 原生 → 页面:验证结果 */
  window.__sdPinResult = function (ok, message) {
    if (ok) {
      showMsg('');
    } else {
      showMsg(message || 'PIN 错误');
      shakeDots();
      input = '';
      refresh();
    }
  };

  /* 数字按键 */
  document.querySelectorAll('.num-btn[data-num]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (input.length >= 4) return;
      input += btn.dataset.num;
      showMsg('');
      refresh();
      if (input.length === 4) {
        setTimeout(checkPin, 120);
      }
    });
  });

  /* 删除键 */
  document.getElementById('btnDel').addEventListener('click', function () {
    if (input.length > 0) {
      input = input.slice(0, -1);
      showMsg('');
      refresh();
    }
  });

  refresh();
})();
