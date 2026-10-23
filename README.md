# 오도로그 (OdoLog)

**계기판이 직접 쓰는 법인차 운행기록부** — 현대자동차그룹 차량용 앱 개발 공모전 출품작 (팀 하이대니)

시동부터 주차까지 차량이 계기판 거리로 업무용승용차 운행기록부를 자동 작성하고, 운전자는 도착 후 한 번만 탭하면 되는 Pleos Connect 앱입니다.

| | |
|---|---|
| 차량 앱 | `android/` — Pleos Connect (Android Automotive, Kotlin, Jetpack Compose) |
| 관리자 웹 | `web/` — Next.js + Supabase, Vercel 배포 |
| DB | `supabase/migrations/` — 스키마, RLS, 차량 페어링·동기화 RPC |
| APK | `apk/` (release) |

## 핵심 기능

- **계기판 기반 자동 기록** — 기어 P→D/R로 운행 시작, P 진입 후 유예 시간(기본 5분)이 지나면 종료. 주행 전·후 계기판 거리를 Pleos Vehicle SDK `Odometer`에서 직접 읽습니다. 주행 중에는 화면을 띄우지 않습니다.
- **공백 없는 기록 (오도미터 체인)** — 앱이 기록하지 못한 주행이 있으면 다음 시동 때 계기판 증가분을 "미확인 구간"으로 자동 생성합니다.
- **도착 후 1탭** — 도착 카드와 시스템 알림 버튼(출퇴근/업무/개인)으로 용도 확정. 집·회사·학습된 업무 장소로 용도를 자동 추천합니다.
- **운전자 지정** — 공용차도 운행마다 사용자 기록.
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
앱을 한 번 실행하면 기록 서비스가 시작되고, 이후에는 부팅(시동) 시 자동으로 시작됩니다.

### 시연 순서 (Virtual Console)
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
```

## 문서
- [기능 스펙](docs/spec.md) · [기술 검토와 에뮬레이터 실측](docs/tech-review.md) · [기획서 본문](docs/proposal.md)
- `tools/probe-app` — 기술 검토 단계에서 Pleos 에뮬레이터 신호를 실측한 검증 앱
