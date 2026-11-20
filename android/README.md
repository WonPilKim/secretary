# 말비서 Android

기존 Flask 웹앱(`../app.py`)을 Kotlin + Jetpack Compose 네이티브 앱으로 옮긴 버전입니다.
계정과 데이터는 Firebase(Authentication + Firestore)에 저장되고, 회의록 요약은 Claude API를 호출합니다.

## 기능

| 탭 | 내용 | 기존 웹 대비 |
|---|---|---|
| 비서 | 채팅/음성 명령 (`메모 우유 사기`, `회의 시작`, `오늘 일정`, `몇 시야`) | `/command` 확장 |
| 메모 | 목록·검색·작성·수정·삭제, 음성 입력 | `/memos` 포팅 |
| 회의록 | 실시간 받아쓰기 → 저장 → 무료 요약 / AI 요약, 녹취록 편집·공유 | 신규 |
| 일정 | 날짜별 목록, 추가/수정, 완료 체크, 알림(n분 전) | 신규 |
| 설정 | Claude API 키·모델 | 신규 |

## 실행

1. Android Studio(Ladybug 이상)에서 `android` 폴더를 엽니다.
2. Gradle Sync가 끝나면 실기기(권장) 또는 에뮬레이터에서 실행합니다.
   - 에뮬레이터는 마이크/음성 인식이 잘 안 될 수 있어요. Google Play 이미지 + 호스트 마이크 허용이 필요합니다.
3. 회의록 요약은 API 키 없이도 **무료 요약**(기기 안에서 핵심 문장 추출)으로 동작합니다. 설정 탭에서 Claude API 키를 입력하면 Claude AI 요약으로 바뀝니다.

## 로그인 / 데이터 동기화 (Firebase)

이름·이메일·비밀번호로 가입하고, **어느 폰에서 로그인해도 같은 메모·회의록·일정**을 봅니다.

- **비밀번호 찾기**: 로그인 화면 → 비밀번호 찾기 → 가입 이메일로 Firebase 재설정 링크 발송
- **자동 로그인**: 로그인 화면 체크박스(기본 켜짐). 끄면 앱을 새로 켤 때 로그아웃. 마지막 이메일은 입력칸에 미리 채움
- **예전 아이디 계정**(`아이디@malbiseo.app`): 로그인 칸에 아이디를 넣으면 그대로 로그인됨. 설정 → 이메일 등록으로 진짜 이메일로 바꾸면 비밀번호 찾기 가능

| 정보 | 저장 위치 |
|---|---|
| 이메일·비밀번호 | **Firebase Authentication** (비밀번호는 Firebase가 암호화해 관리하며 앱·DB에는 저장 안 함) |
| 이름 | Firebase 사용자 프로필 + Firestore `users/{uid}` |
| 메모 | Firestore `users/{uid}/memos/{id}` |
| 회의록 | Firestore `users/{uid}/meetings/{id}` |
| 일정 | Firestore `users/{uid}/schedules/{id}` |
| Claude API 키 | 이 폰의 DataStore (동기화 안 함) |

- 인터넷이 끊겨도 Firestore 오프라인 캐시로 계속 쓸 수 있고, 연결되면 자동으로 서버와 맞춰집니다.
- 다른 폰에서 바꾼 내용은 실시간으로 반영됩니다.
- `firestore.rules` 보안 규칙으로 **본인 데이터만** 읽고 쓸 수 있습니다.

### Firebase 설정 (처음 한 번)

1. https://console.firebase.google.com 에서 **프로젝트 추가** (예: `malbiseo`). Google 애널리틱스는 꺼도 됩니다.
2. 프로젝트 개요에서 **Android 앱 추가** → 패키지 이름 `com.malbiseo` 입력 → **google-services.json** 다운로드
3. 받은 파일을 `android/app/google-services.json` 위치에 넣기 (이 파일이 없으면 빌드가 실패합니다)
4. **빌드 → Authentication → 시작하기 → 로그인 방법 → 이메일/비밀번호 → 사용 설정**
5. **빌드 → Firestore Database → 데이터베이스 만들기** → 위치 `asia-northeast3 (서울)` → **프로덕션 모드**
6. Firestore의 **규칙** 탭에 `firestore.rules` 내용을 붙여넣고 **게시**
7. Android Studio에서 **Sync Project with Gradle Files** 후 실행

가입한 사용자는 Firebase 콘솔의 **Authentication → 사용자** 탭, 저장된 데이터는 **Firestore Database → 데이터** 탭에서 볼 수 있습니다.

## 구조

```
app/src/main/java/com/malbiseo/
├── MalbiseoApp.kt          앱 전역 (DB, 설정, Claude 클라이언트)
├── data/                   Firebase 로그인·Firestore 저장소 + DataStore 설정
├── ai/ClaudeClient.kt      회의록 요약 (Messages API)
├── speech/                 SpeechRecognizer 래퍼 (단발/연속 모드)
├── reminder/               AlarmManager 일정 알림, 재부팅 시 재예약
└── ui/                     화면 (chat, memo, meeting, schedule, settings)
```

## 알려진 한계 / 다음 단계

- **연속 받아쓰기**: 안드로이드 기본 SpeechRecognizer는 몇 초 침묵하면 끊겨서 자동 재시작으로 이어 붙입니다. 재시작 순간 단어가 일부 빠질 수 있고, 화면이 꺼지면 멈춥니다(녹취 중에는 화면 켜짐 유지). 긴 회의를 정확히 받으려면 녹음 파일(AudioRecord) + 서버 STT(Whisper, Google STT, CLOVA 등) 방식이 다음 단계입니다.
- **API 키 보관**: 키가 기기에 저장되므로 본인용으로는 괜찮지만, 스토어 배포 시에는 키를 서버에 두고 앱은 서버를 호출하도록 바꿔야 합니다.
- **알림 시각**: 정확 알람 권한 없이 `setAndAllowWhileIdle`을 써서 절전 중에는 몇 분 늦을 수 있습니다.
- 회의 요약의 "할 일"을 일정으로 바로 등록하기, 기존 MongoDB 데이터 가져오기는 아직 없습니다.
