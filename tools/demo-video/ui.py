#!/usr/bin/env python3
"""에뮬레이터 화면을 글자 기준으로 조작 (uiautomator dump 이용).
  ui.py tap "운전자"          글자를 포함한 첫 요소의 중심을 탭
  ui.py tapx "김대리" 1       정확히 일치하는 n번째 요소
  ui.py pin 1234              PIN 키패드 입력 후 확인
  ui.py has "본인 확인"        있으면 exit 0
"""
import os, re, subprocess, sys, time
ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")

def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "--user", "10", "/sdcard/ui.xml"], capture_output=True)
    x = subprocess.run([ADB, "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    return [(t, tuple(map(int, b))) for t, *b in re.findall(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]

def find(text, exact=False, idx=1, tries=6):
    for _ in range(tries):
        hits = [b for t, b in dump() if (t == text if exact else text in t)]
        if len(hits) >= idx:
            return hits[idx - 1]
        time.sleep(1)
    raise SystemExit(f"not found: {text}")

def tap_box(b):
    x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    subprocess.run([ADB, "shell", "input", "tap", str(x), str(y)])

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap": tap_box(find(sys.argv[2]))
    elif cmd == "tapx": tap_box(find(sys.argv[2], exact=True, idx=int(sys.argv[3]) if len(sys.argv) > 3 else 1))
    elif cmd == "pin":
        boxes = {t: b for t, b in dump() if t in list("0123456789") + ["확인"]}
        for d in sys.argv[2]:
            tap_box(boxes[d]); time.sleep(0.35)
        time.sleep(0.4); tap_box(boxes["확인"])
    elif cmd == "has": sys.exit(0 if any(sys.argv[2] in t for t, _ in dump()) else 1)
