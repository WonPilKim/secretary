// 상단 메뉴: "내 기록 보기"에 로그인한 상태면 [로그인] 버튼 대신 사용자 이름과 로그아웃(문 아이콘)을 보여준다.
// 로그인 정보는 my.html 이 저장한 세션(malbiseo.session)을 읽기만 한다.
(function () {
  'use strict';
  var KEY = 'malbiseo.session';
  // 문 밖으로 나가는 아이콘 (my.html 의 로그아웃 버튼과 같은 모양)
  var ICON = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4"/><path d="M15 16l4-4-4-4"/><path d="M19 12H9"/></svg>';
  var s = null;
  try { s = JSON.parse(localStorage.getItem(KEY) || sessionStorage.getItem(KEY) || 'null'); } catch (e) {}
  if (!s || !s.uid) return;
  var name = s.name || s.loginId || '내 계정';
  document.querySelectorAll('[data-auth=login]').forEach(function (a) {
    a.textContent = name + '님';
    a.title = name + '님의 기록 보기';
    a.href = '/my';
    a.classList.add('user');
    var out = document.createElement('button');
    out.type = 'button';
    out.className = 'icon-btn';
    out.title = '로그아웃';
    out.setAttribute('aria-label', '로그아웃');
    out.innerHTML = ICON;
    out.onclick = function () {
      try { localStorage.removeItem(KEY); sessionStorage.removeItem(KEY); } catch (e) {}
      location.reload();
    };
    a.insertAdjacentElement('afterend', out);
  });
})();
