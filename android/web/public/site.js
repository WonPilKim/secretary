// 상단 메뉴: "내 기록 보기"에 로그인한 상태면 [로그인] 버튼 대신 사용자 이름을 보여준다.
// 로그인 정보는 my.html 이 저장한 세션(malbiseo.session)을 읽기만 한다.
(function () {
  'use strict';
  var s = null;
  try { s = JSON.parse(localStorage.getItem('malbiseo.session') || sessionStorage.getItem('malbiseo.session') || 'null'); } catch (e) {}
  if (!s || !s.uid) return;
  var name = s.name || s.loginId || '내 계정';
  document.querySelectorAll('[data-auth=login]').forEach(function (a) {
    a.textContent = name + '님';
    a.title = name + '님의 기록 보기';
    a.href = '/my';
    a.classList.add('user');
  });
})();
