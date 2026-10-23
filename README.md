# 오도로그 (OdoLog)

**계기판이 직접 쓰는 법인차 운행기록부** — 현대자동차그룹 차량용 앱 개발 공모전 출품작 (팀 하이대니)

시동부터 주차까지 차량이 계기판 거리로 업무용승용차 운행기록부를 자동 작성하고, 운전자는 도착 후 한 번만 탭하면 되는 Pleos Connect 앱입니다.

| | |
|---|---|
| 차량 앱 | `android/` — Pleos Connect (Android Automotive, Kotlin, Jetpack Compose) |
| 관리자 웹 | `web/` — Next.js + Supabase, Vercel 배포 |
| DB | `supabase/migrations/` — 스키마, RLS, 차량 페어링·동기화 RPC |
| APK | `apk/` (release) |
| 시연 영상 | [v1.0.0 릴리스](https://github.com/devdany/odolog/releases/tag/v1.0.0) — `odolog-demo-video.mp4` (2분 40초, 무음·자막) |

## 핵심 기능

- **계기판 기반 자동 기록** — 기어 P→D/R로 운행 시작, 시동을 끄면 종료(신호 대기·잠깐 정차는 같은 운행). 주행 전·후 계기판 거리를 Pleos Vehicle SDK `Odometer`에서 직접 읽습니다. 주행 중에는 화면을 띄우지 않습니다.
- **공백 없는 기록 (오도미터 체인)** — 앱이 기록하지 못한 주행이 있으면 다음 시동 때 계기판 증가분을 "미확인 구간"으로 자동 생성합니다.
- **내리기 전 1탭** — P단에 넣으면 카드와 알림 버튼(출퇴근/업무/개인)으로 용도 확정. 집·회사·학습된 업무 장소로 용도를 자동 추천합니다.
- **운전자 본인 확인(PIN)** — 시동 후 PIN으로 체크인, 서버가 체크인 기록으로 운행의 본인 확인을 판정. 시동을 끄면 해제되고, 차량 화면에는 본인의 운행만 보입니다.
- **개인 용도 위치 미저장** — 개인으로 확정한 운행은 주소를 지우고 거리만 남깁니다.
- **국세청 서식** — 운행기록부(사용일자·사용자·주행 전/후 계기판·주행거리·출퇴근/일반업무 거리)와 업무사용비율을 관리자 웹에서 엑셀로 내려받습니다.

## 사용 SDK / API

| 용도 | API | 권한 |
|---|---|---|
| 주행 전·후 계기판 거리 | Pleos Vehicle SDK `Odometer.getCurrentDistanceDriven` / `registerDistanceDriven` | `pleos.car.permission.CAR_DRIVE` |
| 출발·도착 주소, 집·회사 즐겨찾기, 목적지·통행료 | Pleos NaviHelper `getCurrentLocationInfo`, `getBookmarkInfo`, `getDestinationInfo` | `pleos.car.permission.NAVI_ROUTE` |
| 운행 시작·종료 | AAOS `CarPropertyManager` `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` |
| 주행 중 화면 제한 | AAOS `CarUxRestrictionsManager` | — |

## 실행 방법

### 테스트 환경
- Pleos Connect Emulator v2.1.0 (Android 14 / API 34, Automotive, RAM 4GB, Cold boot)
- Pleos Connect Virtual Console (`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088)

### 설치
```bash
adb install -r -g apk/하이대니_오도로그_release.apk
```
- `apk/하이대니_오도로그_release.apk` — v1.0.0, release 서명(APK Signature Scheme v3), 약 4.6MB, 관리자 웹(Supabase) 접속 정보 포함
- 앱을 한 번 실행하면 기록 서비스가 시작되고, 이후에는 부팅(시동) 시 자동으로 시작됩니다.
- 관리자 웹: https://odolog-woad.vercel.app — 심사용 데모 계정과 운전자 PIN은 접수양식(기획서) 7번에 기재했습니다.

### 관리자 웹 연결과 운전자 확인
1. 관리자 웹 **차량**에서 차량 등록 → 6자리 페어링 코드 → 차량 앱 **설정 > 관리자 웹 연결**에 입력
2. 관리자 웹 **운전자**에서 운전자와 PIN 등록 → 차량 앱 홈 **운전자 확인**에서 이름 선택 후 PIN 입력
3. 주행 후 P단에서 용도 선택 → 시동 끔(`tools/drive.sh off`) → 다음 운전자는 다시 PIN 확인

### 시연 순서 (Virtual Console)

슬라이더 대신 한 줄 명령으로도 됩니다: `tools/drive.sh drive 18` (주행 18km 후 P) → `tools/drive.sh off` (시동 끔 → 운행 종료) → `tools/drive.sh on`, `tools/drive.sh gap 12` (앱이 꺼진 동안 12km 주행), `tools/drive.sh status`.

1. 앱 **설정 > 기록**에서 정차 유예를 `15초 (시연)`으로 바꿉니다.
2. Motion > Gear Selection을 **D**로 → 앱이 "주행 중"으로 바뀌고 플랫폼이 앱 화면을 가립니다(주행 중 사용 제한).
3. Motion > Odometer를 늘립니다(예: +18km).
4. Gear를 **P**로 → 15초 후 **도착 카드**와 알림이 뜹니다. [출퇴근]/[업무]/[개인] 중 하나를 누릅니다.
5. 공백 복구: 앱을 강제 종료(`adb shell am force-stop com.hidany.odolog`) → Odometer를 늘림 → 앱 실행 → **"기록되지 않은 N km"** 카드가 뜹니다.

### 직접 빌드
```bash
cd android
# local.properties: sdk.dir, (선택) SUPABASE_URL, SUPABASE_ANON_KEY
./gradlew assembleDebug
# release: keystore.properties(storeFile, storePassword, keyAlias, keyPassword) 필요 — R8 축소, v3 서명
./gradlew assembleRelease
```

## 문서
- [기능 스펙](docs/spec.md) · [기술 검토와 에뮬레이터 실측](docs/tech-review.md) · [기획서 본문](docs/proposal.md)
- `tools/probe-app` — 기술 검토 단계에서 Pleos 에뮬레이터 신호를 실측한 검증 앱
