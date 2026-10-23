#!/usr/bin/env bash
# 시연 영상 차량 장면 녹화 (docs/demo-video-script.md ②~⑥)
# 전제: 에뮬레이터 콜드부트, 앱이 데모 차량에 연결, 정차 유예 15초, 운전자 미확인, Virtual Console 하트비트 동작 중
set -uo pipefail
cd "$(dirname "$0")/../.."
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
UI="python3 tools/demo-video/ui.py"
OUT="${OUT:-/tmp/odolog-video}"; mkdir -p "$OUT"
V=http://127.0.0.1:8088/vehicle
put() { curl -s -o /dev/null -X PUT "$V/$1"; }
odo() { curl -s "$V/state" | python3 -c "import json,sys;print(int(json.load(sys.stdin)['odometer']['traveled_distance_km']))"; }
awake() { [ "$("$ADB" shell dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')" = "mWakefulness=Awake" ]; }
ignition_off() { put "controls?speed=0&accelerator=0&brake=100&steering=0"; put "gear?gear=Park"; sleep 0.5; awake && "$ADB" shell input keyevent KEYCODE_POWER; }
ignition_on() { awake || "$ADB" shell input keyevent KEYCODE_POWER; }

rec_start() { "$ADB" shell rm -f "/sdcard/$1.mp4"; "$ADB" shell screenrecord --size 1920x1080 --bit-rate 12000000 "/sdcard/$1.mp4" & REC=$!; sleep 1.5; }
rec_stop() { sleep "${1:-1.5}"; "$ADB" shell pkill -INT screenrecord; wait $REC 2>/dev/null; sleep 1; "$ADB" pull "/sdcard/$2.mp4" "$OUT/$2.mp4" >/dev/null && echo "saved $2"; }

drive_to() { # $1 = +km
  local now; now=$(odo)
  put "gear?gear=Drive"; sleep 2.5
  put "controls?speed=60&accelerator=30&brake=0&steering=0"; sleep 2.5
  put "odometer?traveled_distance_km=$((now + $1))"; sleep 2.5
  put "controls?speed=0&accelerator=0&brake=100&steering=0"; sleep 1
}

"$ADB" shell am start --user 10 -n com.hidany.odolog/.ui.MainActivity >/dev/null; sleep 3

# ② 시동 → PIN 본인 확인 (김대리)
rec_start s2; sleep 2
$UI tapx "김대리"; sleep 1.5; $UI pin 1234; sleep 3
rec_stop 1 s2

# ③ 주행 (D단: 화면 제한, 계기판 증가)
rec_start s3
drive_to 18
rec_stop 0.5 s3

# ④ 도착 P → 내리기 전 1탭 → 시동 끔
rec_start s4
put "gear?gear=Park"; sleep 4
$UI tapx "출퇴근"; sleep 2.5
ignition_off; sleep 2.5
ignition_on; sleep 3.5
rec_stop 1 s4

# ⑤ 다른 운전자 (이과장): 본인 운행만 → 거래처 운행 → 업무 → 시동 끔
rec_start s5; sleep 1.5
$UI tapx "이과장"; sleep 1.5; $UI pin 2345; sleep 3
drive_to 24
put "gear?gear=Park"; sleep 4
$UI tapx "업무"; sleep 2.5
ignition_off; sleep 2; ignition_on; sleep 3
rec_stop 1 s5

# ⑥ 공백 복구: 앱이 꺼진 동안 12km → 다음 확인 운전자에게 카드
"$ADB" shell am force-stop --user 10 com.hidany.odolog
put "odometer?traveled_distance_km=$(( $(odo) + 12 ))"; sleep 1
rec_start s6
"$ADB" shell am start --user 10 -n com.hidany.odolog/.ui.MainActivity >/dev/null; sleep 7
$UI tapx "김대리"; sleep 1.5; $UI pin 1234; sleep 3.5
$UI tapx "업무"; sleep 3
rec_stop 1 s6
ls -la "$OUT"
