#!/usr/bin/env bash
# Pleos Virtual Console로 주행을 한 줄로 시뮬레이션한다 (슬라이더 대신).
#
#   tools/drive.sh status          현재 기어·계기판
#   tools/drive.sh drive 18        D → 70km/h → 계기판 +18km → 정차 → P
#   tools/drive.sh gap 12          앱 강제 종료 → 계기판 +12km → 앱 실행  ("기록되지 않은 12km" 카드)
#   tools/drive.sh off | on        시동 끔/켬 (에뮬레이터 화면 끄기/켜기 = 실차의 시동 끔 → 운행 즉시 종료)
#   tools/drive.sh park | d        기어만 변경
#
# 계기판은 줄일 수 없다(콘솔 규칙). 되돌리려면 에뮬레이터 재부팅(0으로 초기화).
set -euo pipefail
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
V=http://127.0.0.1:8088/vehicle
APP=com.hidany.odolog

"$ADB" forward tcp:8088 tcp:8088 >/dev/null

# 콘솔은 브라우저 세션이 있어야 차량에 반영된다 → 스크립트 실행 동안 하트비트 유지
( while true; do curl -s -X POST "http://127.0.0.1:8088/browser/session?client=drive-sh" -o /dev/null; sleep 2; done ) &
HB=$!
trap 'kill $HB 2>/dev/null' EXIT
curl -s -X POST "http://127.0.0.1:8088/browser/session?client=drive-sh" -o /dev/null
sleep 3

put() { local code; code=$(curl -s -o /tmp/drive_sh_resp -w '%{http_code}' -X PUT "$V/$1"); [ "$code" = 200 ] || { echo "실패: $1 → $code $(cat /tmp/drive_sh_resp)"; exit 1; }; }
odo() { curl -s "$V/state" | python3 -c "import json,sys;print(json.load(sys.stdin)['odometer']['traveled_distance_km'])"; }
gear() { curl -s "$V/state" | python3 -c "import json,sys;print((json.load(sys.stdin)['gear'].get('actual') or {}).get('value'))"; }
user() { "$ADB" shell am get-current-user | tr -d '\r'; }

case "${1:-status}" in
  status) echo "기어 $(gear) · 계기판 $(odo) km" ;;
  off) put "controls?speed=0&accelerator=0&brake=100&steering=0"; put "gear?gear=Park"; sleep 1
       [ "$("$ADB" shell dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')" = "mWakefulness=Awake" ] && "$ADB" shell input keyevent KEYCODE_POWER
       echo "시동 끔 (P + 화면 꺼짐) — 열린 운행이 바로 종료됩니다" ;;
  on) [ "$("$ADB" shell dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')" = "mWakefulness=Awake" ] || "$ADB" shell input keyevent KEYCODE_POWER
      echo "시동 켬 (화면 켜짐)" ;;
  park) put "controls?speed=0&accelerator=0&brake=100&steering=0"; put "gear?gear=Park"; echo "P" ;;
  d) put "gear?gear=Drive"; echo "D" ;;
  drive)
    km="${2:?주행거리(km)를 입력하세요. 예: drive 18}"
    now=$(odo); target=$(python3 -c "print(round($now + $km))")
    echo "주행 시작 · 계기판 $now km"
    put "gear?gear=Drive"; sleep 2
    put "controls?speed=70&accelerator=30&brake=0&steering=0"; sleep 2
    put "odometer?traveled_distance_km=$target"; sleep 2
    put "controls?speed=0&accelerator=0&brake=100&steering=0"; sleep 1
    put "gear?gear=Park"
    echo "정차(P) · 계기판 $target km — 이어서 tools/drive.sh off(시동 끔)이면 바로, 시동을 켠 채면 정차 유예 뒤 운행이 끝납니다"
    ;;
  gap)
    km="${2:?주행거리(km)를 입력하세요. 예: gap 12}"
    u=$(user); now=$(odo); target=$(python3 -c "print(round($now + $km))")
    "$ADB" shell am force-stop --user "$u" "$APP"; echo "앱 강제 종료"
    put "odometer?traveled_distance_km=$target"; echo "앱이 꺼진 동안 계기판 $now → $target km"
    sleep 1; "$ADB" shell am start --user "$u" -n "$APP/.ui.MainActivity" >/dev/null
    echo "앱 실행 — 약 5초 뒤 '기록되지 않은 ${km}km' 카드가 뜹니다"
    ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
