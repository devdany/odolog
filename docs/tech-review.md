# 기술 검토 — 법인차 운행기록부 자동 작성 (Claude 참조용)

## ★ 에뮬레이터 실측 결과 (2026-10-22, Pleos Connect Emulator v2.1.0) — 아래 문서 검토보다 우선

검증 앱: `probe-app/` (로그 `adb logcat -s PROBE`). 시나리오는 Virtual Console HTTP API로 자동화.

| # | 항목 | 결과 | 비고 |
|---|---|---|---|
| 1 | Vehicle `getCurrentDistanceDriven` / `registerDistanceDriven` | ✅ 동작 | 값 변경 시 ~0.1s 내 수신. 변경 없어도 ~4-5초 주기로 현재값 재전달 |
| 1 | 트립 세션 거리/시간 (`getTripCurrent*`, `getTripCumulative*`) | ❌ 에뮬레이터 `ERROR_NOT_SUPPORTED_PROPERTY` | 실차 지원 여부 불명 → **총 오도미터 차분만 사용** 설계 확정 |
| 2 | `CarUxRestrictions` | ✅ | 기어 D 진입 즉시 requiresDO=true, active=80(NO_SETUP 64 + NO_VIDEO 16 = 국내 정책과 일치). P 복귀 시 해제. **속도가 아니라 기어 기준으로 전환됨** |
| 3 | `CarPropertyManager` GEAR_SELECTION | ✅ (`CAR_POWERTRAIN` normal 권한, 자동 부여) | P=4, D=8. 트립 경계 1차 신호로 사용 |
| 3 | PARKING_BRAKE_ON | ✅ | |
| 3 | PERF_VEHICLE_SPEED | ✅ (`CAR_SPEED` dangerous → 런타임 권한) | m/s. 60km/h → 16.67. 콜백이 ~100-200ms마다 와서 필터링 필요 |
| 3 | IGNITION_STATE | ❌ 에뮬레이터 미지원 (carPropertyConfig에 없음) | 시동 신호 대신 부팅/기어 사용 |
| 3 | PERF_ODOMETER | ❌ `CAR_MILEAGE` 권한 거부(privileged) | 오도미터는 Pleos Odometer API로만 |
| - | Vehicle SDK `getGear()` (문서 미공개) | ✅ 동작하나 `@Deprecated("use the Google GEAR_SELECTION property")` | 사용하지 말고 CarPropertyManager 사용 |
| 5 | 백그라운드 수신 (FGS specialUse, 홈 화면/내비 전면) | ✅ | 기어·UX·오도미터 모두 수신 |
| 5 | **체인 대사**: 앱 force-stop 상태에서 20→35km 주행 후 재실행 | ✅ gap 15km 검출 | 핵심 설계 검증 완료 |
| 5 | 재부팅 후 `BOOT_COMPLETED` 자동 시작 | ✅ | FGS 시작 허용됨 |
| 5 | 재부팅 시 에뮬레이터 오도미터 0으로 리셋 | ⚠️ 에뮬레이터 특성 | 앱은 오도미터 감소를 이상치로 처리(체인 리셋 기록)해야 함 |
| 6 | NaviHelper `getCurrentLocationInfo` **경로 미설정 상태** | ✅ 주소 반환 (`address='수정구 시흥동'`, lat/lng) | FREE_DRIVE 커버 확인 → 내비 안 켠 출퇴근도 출발/도착지 확보 가능 |
| 6 | 부팅 직후 NaviHelper 조회 | ⚠️ connected 후 응답 없음 | 내비 앱 준비 전 → 재시도 로직 필요 |
| 7 | `getBookmarkInfo` | ✅ HOME/OFFICE 2건 (기본값 빈 주소) | 사용자 설정 후 값 들어옴 |
| 7 | `onDestinationArrived` | 미검증 | 경로 요청(NAVI_ROUTE_SEARCH) + 시뮬레이션 필요 |
| 8 | Seat 위치/점유 get | ⚠️ 모두 0/false | Virtual Console에서 운전석 occupied=true 설정 후에도 false. 매핑 이슈 또는 미반영 → 운전자 추정 기능은 보조로만 |

결정 사항:
- **운행 종료 = 시동 끔** (2026-10-23): Virtual Console Power System(Off/Ready/On/Driving)은 VHAL 내부 전원 상태만 바꾸고, 3rd party가 읽을 수 있는 24개 프로퍼티 중 반응하는 것이 없음(Pleos SDK IgnitionState도 반환 API 없음). 대신 시스템 `ACTION_SCREEN_OFF`/`ACTION_SHUTDOWN`(실차 시동 끔 시 화면 꺼짐·종료)을 P단일 때만 시동 끔으로 해석. 시동 켠 채 P는 30분 유예.
- **트립 경계 = GEAR_SELECTION(P↔D) + CarUxRestrictions**, 거리 = **총 오도미터 차분**, 누락 대비 = 체인 대사.
- 상주 = 포그라운드 서비스(specialUse) + BOOT_COMPLETED. (실차 App Market 심사에서 FGS 허용 여부는 별도 확인 필요)
- 위치(2층) = NaviHelper getCurrentLocationInfo, 트립 시작/종료 시점 1회씩 조회(+재시도).

환경 메모 (재현용):
- 이미지: `system-images/android-34/pleos_car_emulator/arm64-v8a` (XML의 zip 직접 다운로드 후 배치), AVD `Pleos_Connect` (2560x1440, mdpi, RAM 4GB, cold boot)
- Virtual Console: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ . 브라우저 미접속 시 `emulator_inactive`(503) → `POST /browser/session?client=<id>` 를 2초마다 보내야 활성화
- 제어 API (PUT): `/vehicle/gear?gear=Drive|Park|Reverse|Neutral`, `/vehicle/odometer?traveled_distance_km=N`, `/vehicle/controls?speed=KPH&accelerator=%&brake=%&steering=deg`, `/vehicle/seat?kind=occupied&area=driver&value=true`, 전체 목록 `GET /vehicle/catalog`, 상태 `GET /vehicle/state`
- 릴리즈 노트: Emulator v2.1.0에서 **LLM SDK 제공 종료**

---


검토일: 2026-10-22. 근거는 Pleos 공식 문서(document.pleos.ai). 문서 본문 추출은 `python3 tools/pleos_doc.py <경로>`.
상태 표기: ✅ 문서로 확인 / ⚠️ 문서상 불명확 → 에뮬레이터 검증 필요 / ❌ 불가

전제(→ research.md 7-1): 1층 법정 기록 = Vehicle SDK(항상 동작), 2층 목적 보완 = NaviHelper(있으면 활용).

## 결론 요약

| 검토 항목 | 판정 | 핵심 |
|---|---|---|
| 계기판 거리 획득 | ✅ | `Odometer.getCurrentDistanceDriven` (km, Float), 구독 가능, 권한 normal |
| 트립 경계 감지 | ⚠️ 설계로 해결 가능 | 이벤트(CarUxRestrictions/기어)로 감지 + **오도미터 체인 대사**로 누락 방지 |
| 앱 상주·백그라운드 | ⚠️ | 금지 규정 없음, 리소스 기준 있음. suspend/LMK 시 프로세스 생존 검증 필요 |
| 운전자 식별 | ⚠️ | 전용 프로필 API 없음 → 정차 중 1탭 선택 + 보조 추정(시트 위치/블루투스) |
| 위치·목적 보완 | ⚠️ | NaviHelper `getCurrentLocationInfo`(주소 포함). 내비 탑재 차량 한정, FREE_DRIVE 동작 미확인 |
| 법·심사 | ⚠️ | 위치 저장 시 위치정보법 신고 이슈, Vehicle 권한은 App Market 사전 승인 필요 |

**치명적 블로커 없음.** 핵심 법정값(계기판 거리)은 문서로 확정. 나머지는 에뮬레이터 검증 항목.

## 1. 계기판 거리 (Odometer) ✅

- 의존성: `settings.gradle`에 `maven { url "https://nexus-playground.pleos.ai/repository/maven-releases/" }`, `implementation("ai.pleos.playground:Vehicle:2.3.2")`
- 연결: `Vehicle(context).initialize()` → `vehicle.getOdometer()` → 사용 후 `release()`. IPC 비동기(onComplete/onFailed), 콜백 끝나기 전 release 금지.
- 권한: `pleos.car.permission.CAR_DRIVE` (protection level: **normal**). 단 App Market 등록 시 사전 협의·승인된 권한만 선언 가능(review-functionality 가이드).

| API | 단위 | 의미 | 용도 |
|---|---|---|---|
| `getCurrentDistanceDriven` / `registerDistanceDriven` | **km** Float? | 총 주행거리(계기판) | **주행 전/후 계기판 거리 = 법정 필드** |
| `getTripCurrentDrivingDistance` / `register…` | **m** Float? | 현재 주행 세션 거리 | 트립 거리 정밀값(교차검증) |
| `getTripCurrentDrivingTime` | 분 Int? | 현재 주행 세션 시간 | 운행 시간 |
| `getTripCumulativeDrivingDistance` | m | 운영 기간 누적 | 참고 |
| `getTripSinceRefillDrivingDistance` | m | 주유 후 | 유류비 정산 보조 |
| `getTripInstantaneousDrivingDistance` | m | **Deprecated** | 사용 안 함 |

- 리스너 `DistanceDrivenListener.onDistanceDrivenUpdated(value: Float?)` — 단위는 등록 API에 따름(km 또는 m). null 가능 → null 처리 필수.
- ⚠️ 확인: 오도미터 Float 정밀도(0.1km?), 업데이트 주기, "current driving session"이 IGN 기준인지, 에뮬레이터 Virtual Console에서 값 조작 가능 여부(문서상 Motion에 odometer 포함).

## 2. 트립 경계 감지 ⚠️ → 설계로 해결

Vehicle SDK에는 시동·기어·속도가 **없음**(VehicleSpeedListener v2.3.2 삭제). 대신:

| 신호 | 출처 | 근거 | 비고 |
|---|---|---|---|
| 주행 상태(주차/정차/주행) | AAOS `CarUxRestrictionsManager.registerListener` → `onUxRestrictionsChanged` | ✅ 주행 규제 개발 가이드가 사용 지시 | 권한 불필요. 주행 시작/종료의 1차 신호 |
| 기어(P/R/N/D), 주차브레이크, 시동 | AAOS `CarPropertyManager` (`GEAR_SELECTION`, `PARKING_BRAKE_ON`, `IGNITION_STATE`) | ✅ FAQ가 CarPropertyManager 사용(PERF_VEHICLE_SPEED) 공식 안내, api-compatibility 가이드도 언급 | ⚠️ 권한(`android.car.permission.CAR_POWERTRAIN` 추정) 3rd party 허용 여부 검증. 특수 프로퍼티는 기술지원 검토 요청 권고 |
| Suspend 진입/복귀 | 시스템 | ✅ IGN OFF 후 완전 종료 아닌 저전력 상태, 메모리 유지 후 빠른 Resume | 앱은 "언제든 중단·재시작" 가정 필요 |
| 경고등 PARKING_BRAKE / EPB | Vehicle SDK Safety `getTelltalesInfo` | ✅ | 보조 신호 |

### 핵심 설계: 오도미터 체인 대사 (Odometer chain reconciliation)
오도미터는 단조 증가 → **이벤트를 놓쳐도 거리는 절대 잃지 않음.**
1. 모든 트립 레코드는 `(start_odo, end_odo)`. 다음 레코드의 start_odo는 이전 end_odo와 같아야 함.
2. 앱 시작/Resume/주행 시작 시마다 현재 오도미터 조회 → 마지막 저장값과 차이가 있으면 **"미확인 구간"** 레코드 자동 생성 → 다음 정차 때 운전자·용도 지정 요청.
3. 결과: 기록 공백이 구조적으로 불가능 = 페인포인트 #2(공용차 거리 공백)를 기술로 해결. 기획서 차별점.
- 트립 분할 기준(제안): 주행 상태 moving 시작 → start, parked(또는 기어 P) N분 지속 or suspend → end. 짧은 정차(신호대기·편의점)는 같은 트립.
- 놓친 경우 정밀도만 떨어짐(여러 트립이 한 구간으로 합쳐짐). 서식은 일 단위 행도 허용하므로 법적 문제 없음.

## 3. 앱 상주·백그라운드 ⚠️

문서 근거:
- app-structure-screen-model: 화면에서 사라진 앱은 종료되지 않고 백그라운드에 남을 수 있음. 백그라운드 지속 작업은 실행 조건·중단·재개 정책을 명확히 정의, 불필요한 상주·타이머·폴링·네트워크 최소화, 프로세스 재생성 대비 상태 저장.
- performance-resource-mgmt: 비내비 앱 주행 중 CPU **5% 미만**, 정차 중 25% 이하, RAM **400MB 이하**.
- 백그라운드 서비스 금지 규정은 **문서에 없음**.

설계 방침:
- 폴링 금지, 리스너 기반(CarUxRestrictions, Odometer 구독)만 사용 → CPU 부담 극소.
- 자동 시작: `BOOT_COMPLETED` 리시버(콜드부트) + 앱 프로세스 생존 시 Resume 처리. ⚠️ suspend→resume 시 부트 브로드캐스트 없음 → 프로세스가 살아있는지, LMK로 죽으면 언제 재기동되는지 검증.
- 프로세스가 죽어도 2장의 체인 대사로 데이터 손실 없음 → 사용자가 앱을 열거나 다음 기동 시 복구.
- 상태는 Room/파일에 즉시 영속화(심사 기준: 백그라운드 복귀 시 마지막 상태 복원).

## 4. 운전자 식별 ⚠️

- Pleos SDK에 사용자 프로필 API 없음(사이트맵 검색). security-privacy 가이드: "운전자 교체 시 쉽게 계정 전환 가능해야 함".
- 1안(기본): 트립 시작 시점(주차 상태)에 큰 버튼으로 운전자 선택, 기본값 = 직전 운전자. 선택 없이 출발하면 직전 운전자로 가정 후 도착(정차) 시 확인.
- 보조 추정(차별화 아이디어, 검증 필요):
  - 운전석 시트 위치(fore-aft, height, backrest — Vehicle Seat get API, `CAR_SEATS`) 지문으로 운전자 추천. in-car 전용 신호.
  - 연결된 블루투스 휴대폰으로 운전자 매칭 ⚠️ 3rd party BT 권한 허용 여부 미확인.
- 주행 중 선택 UI는 띄우지 않음(주행 규제).

## 5. 위치·목적 보완 (2층, NaviHelper) ⚠️

- 의존성/권한: NaviHelper v3.0.5, `pleos.car.permission.NAVI_ROUTE` (normal).
- `getCurrentLocationInfo()` → 리스너 `onCurrentLocationInfo(CurrentLocationInfo)`: latitude, longitude, heading, **address**, routeId, currentRouteIndex. 문서: "실제 이벤트는 Navi 앱 구현에 따라 다를 수 있음".
- 출퇴근 분류: `getBookmarkInfo` (BookmarkType HOME/OFFICE) ↔ 트립 시작/종료 위치 비교.
- 도착 이벤트: `onDestinationArrived(DestinationArrivedInfo: address, distance, duration, name)` → 비고(방문처) 자동 기입.
- 통행료: `DestinationInfo.tollFare`, `RouteStartInfo.tollFare`.
- 제약: api-compatibility 가이드 — **NaviHelper는 내비 탑재 차량에 한해 호환**, 탑재 여부 확인 방법은 비공개(partnership@pleos.ai). → 2층은 반드시 optional로.
- ⚠️ 확인: 경로 미설정(FREE_DRIVE)에서 getCurrentLocationInfo 응답 여부, 에뮬레이터 내비 앱 동작/경로 시뮬레이션(NaviMode.SIMULATION_GUIDING).
- FusedLocation SDK는 Coming Soon. 표준 Android LocationManager 사용 가능 여부 ⚠️ 미확인.

## 6. 정책·법·심사 ⚠️

- 카테고리: **Business**(업무·생산성) 또는 **Mobility Services**(주행과 밀접한 차량 관리 서비스는 주행 중 제한적 허용). 카테고리 외 기능 포함 시 심사 거부 가능 → 기획 시 확정. 운행일지 특성상 Business가 자연스러움, 주행 중 UI는 거의 없음.
- 주행 중 UX: DO 미선언 시 주행 중 앱 화면 사용 불가 → 우리 앱은 주행 중 화면 불필요(백그라운드 기록), 입력은 정차 시만. 별도 DO 선언 불필요할 가능성 높음 ⚠️ (백그라운드 동작이 DO와 무관한지 확인).
- 개인정보: 수집 항목·목적·보유기간 고지 + 명시적 동의 UI, 동의 철회 시 즉시 삭제, 차량/사용자 ID 가명화, 운전 행동 데이터 마케팅 사용 금지.
- **위치정보**: 위치를 저장·이용하는 서비스는 서비스 국가 법률에 따른 등록/신고 확인 요청 가능(review-user-privacy-security). 한국은 위치정보법상 위치기반서비스사업 신고 대상 → 실서비스 시 필요. 1층은 위치 없음, 2층 위치는 개인 용도 트립 미저장 + 동의 기반 → 기획서에 명시.
- 성능 심사: 실행 10초 이내, 버튼 2초 이내 반응, 터치 응답 0.25초, 백그라운드 복귀 시 상태 복원.

## 7. 에뮬레이터 검증 체크리스트 (우선순위 순)

1. [ ] Vehicle SDK 연결 + `getCurrentDistanceDriven` 값/정밀도, Virtual Console로 오도미터 변경 시 리스너 수신
2. [ ] `CarUxRestrictionsManager` 리스너: Virtual Console 기어 P↔D 변경 시 상태 전환 수신
3. [ ] `CarPropertyManager` GEAR_SELECTION / IGNITION_STATE / PARKING_BRAKE_ON 읽기 권한 허용 여부
4. [ ] `getTripCurrentDrivingDistance` 리셋 시점(IGN 기준인지)
5. [ ] 백그라운드 상태에서 리스너 계속 수신되는지, 앱 kill 후 재기동/체인 대사 동작
6. [ ] NaviHelper `getCurrentLocationInfo` — 경로 미설정/설정 각각 응답, address 값
7. [ ] NaviHelper `getBookmarkInfo` HOME/OFFICE, `onDestinationArrived` 수신 (경로 시뮬레이션 가능 여부)
8. [ ] Seat 위치 get API로 운전석 위치 읽기 (`CAR_SEATS`)
9. [ ] 네트워크(백엔드 업로드) 동작

## 8. 아키텍처 초안

```
[Pleos Connect 차량 앱 (Kotlin)]
  TripRecorder (백그라운드, 리스너 기반)
    ├─ Vehicle SDK: Odometer(km, m), Seat(운전자 추정), Safety(telltale)
    ├─ AAOS: CarUxRestrictions(주행상태), CarPropertyManager(기어/시동, 검증 후)
    └─ NaviHelper(optional): 위치·주소, 북마크, 도착, 통행료
  OdometerChain (Room): 트립/미확인 구간, 체인 무결성
  UI (정차 시만): 운전자 선택, 용도 1탭(출퇴근/업무/개인), 미확인 구간 처리
  Sync → [백엔드] → [관리자 웹: 차량·운전자별 일지, 국세청 서식 엑셀, 업무사용비율·29호 서식 값]
```
