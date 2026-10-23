# 기능 스펙 & 뼈대 (확정안 v1, 2026-10-22) — Claude 참조용

근거: research.md(도메인·심사기준), tech-review.md(에뮬레이터 실측). 변경 시 이 문서를 먼저 갱신.

## 0. 제품 정의
- 가칭: **오도로그 (OdoLog)** — "계기판이 직접 쓰는 법인차 운행기록부". 이름은 사용자 확정 전 가안.
- 형태: Pleos Connect 차량 앱(Kotlin, AAOS) + 관리자 웹(PT 단계).
- 카테고리: Business (주행 중 화면 불필요, 백그라운드 기록).
- 한 줄: 시동부터 주차까지 차량이 계기판 거리로 운행기록부를 자동 작성하고, 운전자는 도착 후 한 번만 탭한다.
- 3원칙: **무입력**(자동 기록·자동 분류) · **무공백**(오도미터 체인) · **최소 위치**(개인 용도 위치 미저장).

## 1. 사용자
| 역할 | 니즈 | 접점 |
|---|---|---|
| 운전자(임직원) | 기록 귀찮음 제거, 출퇴근/업무 판단 부담 제거 | 차량 앱(정차 시 1탭) |
| 차량 관리자(총무·재무) | 취합·독촉·엑셀 정리 제거, 결산 숫자 즉시 | 관리자 웹 |
| 대표·세무사 | 비용 인정, 세무조사 소명력 | 월/연 리포트, 국세청 양식 |
| (확장) 복식부기 개인사업자 | 1인 운행기록 | 차량 앱 단독 모드 |

## 2. 기능 목록 (우선순위)
P0 = 1차 제출 시연 영상까지(11/15) · P1 = 2차 PT까지(12/3) · P2 = 기획서상 확장 계획

| ID | 기능 | P | 구현 근거(실측) |
|---|---|---|---|
| F1 | **자동 트립 기록**: 기어 P→D/R 시작, **시동 끔(P 상태의 화면 꺼짐·종료) 시 즉시 종료**, 시동을 켠 채 P면 30분 후 종료(짧은 정차는 같은 트립). 시작/종료 시각·계기판 거리 저장 | P0 | GEAR_SELECTION ✅, Odometer ✅, SCREEN_OFF ✅ (차량 시동 신호는 3rd party 접근 불가) |
| F2 | **오도미터 체인**: start_odo = 직전 end_odo 강제. 앱 미동작 중 증가분은 "미확인 구간" 자동 생성. 감소(계기판 교체·에뮬레이터 리셋)는 체인 리셋 이벤트로 기록 | P0 | force-stop 후 15km gap 검출 ✅ |
| F3 | **운전자 지정**: 주차 상태에서 출발 전 운전자 카드(기본=직전 운전자, 1탭 변경). 미선택 출발 시 직전 운전자로 기록 후 도착 시 확인 | P0 | UI only |
| F4 | **용도 자동 제안 + 1탭 확정**: 도착(P) 시 카드 표시. 규칙: 집↔회사 북마크 → 출퇴근 / 학습된 업무 장소 → 일반업무 / 그 외 → 마지막 선택 제안. 버튼 3개(출퇴근·업무·개인) | P0 | NaviHelper bookmark ✅, location ✅ |
| F5 | **출발지·도착지 주소**: 트립 시작·종료 시 getCurrentLocationInfo 1회(+3회 재시도). 경로 안내 중이면 목적지명 사용 | P0 | FREE_DRIVE 주소 ✅ / 부팅 직후 무응답 → 재시도 |
| F6 | **차량 내 운행일지**: 월별 목록, 트립 상세, 이번 달 총주행·업무사용비율·미확인 n건 | P0 | UI |
| F7 | **미확인 구간 처리**: 다음 정차 시 "기록되지 않은 N km" 카드 → 운전자·용도 지정 | P0 | F2 |
| F8 | **국세청 양식 내보내기**: 업무용승용차 운행기록부 서식(사용일자·사용자·주행 전/후 계기판·주행거리·출퇴근/일반업무 거리·비고) XLSX + 연간 합계(총주행·업무거리·업무사용비율 = 29호 서식 값) | P1 | 백엔드/웹 |
| F9 | **관리자 웹**: 차량·운전자별 일지, 미확인/미확정 알림, 월 마감, 다운로드 | P1 | 백엔드 |
| F10 | **프라이버시 모드**: 개인 용도로 확정된 트립은 주소 삭제, 거리만 보존 | P1 | 로컬 처리 |
| F11 | **비용 연결**: 통행료(tollFare), 연비/전비 × 거리 → 유류·충전비 추정 | P1 | NaviHelper, Fuel/EvBattery |
| F12 | 정비 알림: 경고등(telltale)·오일·타이어 → 관리자 알림 | P2 | Safety API |
| F13 | 시트 위치 기반 운전자 추천 | P2 | Seat get 실측 미반영 ⚠️ |
| F14 | 개인사업자 단독 모드, 세무사 공유, 유럽(독일 Fahrtenbuch 등) 확장 | P2 | |

제외(Non-goal): 주행 중 입력 UI, 차량 제어, 음성 입력(STT Coming Soon), 실시간 위치 추적(감시 앱 오해 방지).

## 3. 트립 상태 머신 (F1/F2)
```
PARKED ──gear D/R──▶ DRIVING ──gear P──▶ STOPPING
   ▲                    ▲                    │ gear D/R → DRIVING (같은 트립: 신호 대기·잠깐 정차)
   └──── 트립 종료 ◀──── 시동 끔(P에서 화면 꺼짐·종료) 즉시 / 시동 켠 채 30분 ┘
(주행 중 화면 꺼짐은 무시 — 운전자가 화면만 끄는 경우)
앱 시작·Resume·트립 시작마다: current_odo vs last_odo
   > last+0.05 → GAP 트립 생성(source=GAP, purpose=UNCONFIRMED)
   < last      → CHAIN_RESET 이벤트 기록, 새 체인 시작
```
- 거리 = end_odo − start_odo (총 오도미터 차분만 사용. 트립 세션 API는 에뮬레이터 미지원).
- 주행 상태 보조: CarUxRestrictions(requiresDO) — 기어와 함께 로그만, 판정은 기어 기준.
- 정밀도: 오도미터 Float km(에뮬레이터 1km 단위 설정). 1km 미만 트립은 0으로 기록될 수 있음 → 일 합계로 서식 작성(일 단위 행 허용).

## 4. 화면 (차량 앱, 모두 정차 중에만 표시)
| 화면 | 내용 | 트리거 |
|---|---|---|
| S1 홈 | 이번 달 총주행 / 업무사용비율 / 미확인·미확정 n건 / 최근 트립 3개 | 앱 실행 |
| S2 출발 카드 | "오늘 운전자: 김○○" + 변경 | 승차 후 P 상태(앱 전면 시) / 알림 |
| S3 도착 카드 | 거리·출발→도착 주소·제안 용도, [출퇴근][업무][개인]. 2건 이상 쌓이면 "추천대로 모두 확정" | 트립 종료(시동 끔) 시 알림 |
| S4 운행일지 | 월별 목록 → 상세(시각·계기판 전후·주소·용도·통행료) 편집 | |
| S5 미확인 구간 | "기록되지 않은 15km" 지정 | GAP 존재 시 |
| S6 설정 | 차량 정보(차종·번호), 운전자 목록, 업무 장소, 프라이버시, 연동 코드 | |
- 레이아웃: portrait(1 window) 핵심 / landscape(2 window) 확장. 터치 0.25s, 버튼 2s 내 반응(심사 기준).

## 5. 데이터 모델 (Room)
- Vehicle(id, plateNo, model, ownership[자가/리스/렌트])
- Driver(id, name, dept, isDefault)
- Trip(id, vehicleId, driverId, startAt, endAt, startOdoKm, endOdoKm, distanceKm, purpose[COMMUTE|BUSINESS|PERSONAL|UNCONFIRMED], startAddr?, endAddr?, destName?, tollWon?, note?, source[AUTO|GAP], confirmedAt?)
- Place(id, label, lat, lng, radiusM, purpose) — 학습된 업무 장소
- ChainEvent(id, at, type[RESET|GAP], fromKm, toKm)
- 동기화: Trip 확정 시 서버 업로드(P1). 차량/운전자 ID 가명화.

## 6. 아키텍처 / SDK 매핑
```
[차량 앱]
 TripRecorderService (Foreground, specialUse) ← BOOT_COMPLETED
   ├ CarPropertyManager: GEAR_SELECTION (CAR_POWERTRAIN)      … 트립 경계
   ├ CarUxRestrictionsManager                                  … 주행 상태(보조)
   ├ Pleos Vehicle SDK Odometer (CAR_DRIVE)                    … 계기판 거리(법정 필드)
   ├ Pleos NaviHelper (NAVI_ROUTE)                             … 주소·북마크·도착·통행료 (optional)
   └ (P1) Fuel/EvBattery (CAR_ENERGY), (P2) Safety (CAR_SAFETY)
 TripRepository(Room) / PurposeClassifier / UI(Activity, 정차 시)
 SyncWorker → [백엔드 API] → [관리자 웹: 일지·마감·XLSX]
```
의존성: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`, `useLibrary("android.car")`, minSdk 28 / target 34.

## 7. 시연 시나리오 (영상·PT, Virtual Console 자동화)
1. 아침: 운전석 탑승 → 앱 출발 카드 "운전자: 김대리" 확인
2. 기어 D → (화면 없음, 백그라운드 기록) 60km/h, 오도미터 +18km → 회사 도착 P → 도착 카드 "출퇴근" 자동 제안 → 1탭
3. 오후: 거래처 방문(내비 목적지) → 도착 카드 "업무 · ○○상사" → 1탭, 통행료 표시
4. 앱 강제 종료 상태에서 +12km 주행 → 재실행 시 "기록되지 않은 12km" 카드 → 공백 없는 체인 시연
5. 월 마감 → 국세청 양식 XLSX (업무사용비율 자동 계산) → 관리자 웹(P1)

## 7-1. 구현 아키텍처 v1 (2026-10-23 확정)
저장소: github.com/devdany/odolog (public). 모노레포.
```
android/        Pleos Connect 차량 앱 (Kotlin, Compose, Room) — 패키지 com.hidany.odolog
web/            관리자 웹 (Next.js App Router + @supabase/ssr + Tailwind) → Vercel (root dir = web)
supabase/       migrations/*.sql (스키마·RLS·RPC)
tools/probe-app 에뮬레이터 검증 앱 / tools/pleos_doc.py 문서 추출기
docs/           spec.md, tech-review.md, proposal.md (공개). research.md·submission/은 비공개(.gitignore)
```
- **Supabase 스키마**: organizations, org_members(auth.users↔org), vehicles(페어링 코드·device_token_hash·last_odo), drivers, trips(vehicle_id+local_id 유니크, distance_km 생성 컬럼, purpose/source enum). 모든 테이블 RLS = 조직 멤버만.
- **차량 앱 인증**: 관리자 웹에서 차량 등록 → 6자리 페어링 코드(24h) → 차량 앱 설정에서 입력 → RPC `pair_vehicle(code)`가 device_token 발급(서버엔 sha256만 저장). 이후 RPC `vehicle_sync(token, trips, odo)`로 운행 upsert + 운전자 목록 수신. 차량 앱은 anon key만 보유, 테이블 직접 접근 없음(security definer RPC).
- **개인 용도 위치 삭제**: purpose=PERSONAL이면 서버(트리거)와 앱 양쪽에서 주소 제거.
- **오프라인 우선**: 차량 앱은 페어링 없이도 완전 동작(로컬 Room). 페어링 시에만 동기화.
- **도착 1탭**: 앱 화면 카드 + 시스템 알림 액션 버튼(출퇴근/업무/개인) — 앱을 열지 않고 확정.
- **관리자 웹**: 이메일+비밀번호 로그인(심사용 데모 계정 제공), 조직 생성, 차량/운전자 관리, 월별 운행일지·업무사용비율·미확정, 운행 용도 수정, 국세청 서식 XLSX 다운로드.

## 8. 일정
- ~10/28 설명회: 질문(FGS 상주 심사 허용, 트립 세션 API 실차 지원, Vehicle 권한 승인 절차, 위치정보 신고 요건)
- ~11/12: P0 구현 + 시연 영상 + GitHub(release APK)
- 11/15: 기획서 + 영상 + GitHub 제출
- 11/23~12/3: P1(관리자 웹, XLSX, 비용) + PT 자료
