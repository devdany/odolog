#!/usr/bin/env python3
"""시연 영상 편집: 카드·자막 이미지(HTML→PNG) 생성 → 장면별 자막 합성 → 이어 붙이기. 무음.

사용: VIDEO_DIR=<녹화 폴더> python3 tools/demo-video/build.py
입력: s2~s6.mp4 (record_car.sh), web/web.webm + web/logbook.xlsx.png (웹 녹화)
출력: $VIDEO_DIR/odolog_demo.mp4 (1920x1080, 30fps, H.264, 무음)
"""
import json, os, subprocess, textwrap
from pathlib import Path

D = Path(os.environ["VIDEO_DIR"])
ASSET = D / "assets"; ASSET.mkdir(exist_ok=True)
PW = Path(os.environ.get("PW_DIR", D.parent / "pw"))  # playwright 설치 폴더
FONT = "'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif"
TEAL = "#14B8A6"

CSS = f"""
* {{ margin:0; padding:0; box-sizing:border-box; }}
body {{ width:1920px; height:1080px; font-family:{FONT}; }}
.card {{ width:1920px; height:1080px; background:#0E1116; color:#E8ECF1; display:flex; flex-direction:column; justify-content:center; padding:0 180px; }}
.kicker {{ color:{TEAL}; font-size:34px; font-weight:700; letter-spacing:2px; margin-bottom:28px; }}
h1 {{ font-size:92px; font-weight:800; line-height:1.15; }}
h2 {{ font-size:60px; font-weight:700; line-height:1.3; }}
p.sub {{ font-size:38px; color:#9AA4B2; margin-top:30px; line-height:1.5; }}
.chips {{ display:flex; gap:18px; flex-wrap:wrap; margin-top:46px; }}
.chip {{ border:2px solid #2A313C; border-radius:999px; padding:14px 28px; font-size:30px; color:#C9D1DB; }}
.overlay {{ width:1920px; height:1080px; position:relative; }}
.bar {{ position:absolute; left:0; right:0; bottom:0; padding:30px 80px 36px; background:linear-gradient(transparent, rgba(8,10,14,.92) 30%); color:#fff; font-size:44px; font-weight:700; text-align:center; line-height:1.35; text-shadow:0 2px 6px rgba(0,0,0,.6); }}
.tag {{ position:absolute; left:40px; top:34px; background:{TEAL}; color:#04211d; font-size:30px; font-weight:800; padding:10px 24px; border-radius:14px; }}
.note {{ position:absolute; right:36px; top:40px; font-size:22px; color:rgba(255,255,255,.85); background:rgba(0,0,0,.55); padding:8px 16px; border-radius:10px; }}
"""

def html(body: str) -> str:
    return f"<!doctype html><html><head><meta charset='utf-8'><style>{CSS}</style></head><body>{body}</body></html>"

# ---------- 카드·자막 정의 ----------
CARDS = {
    "c0": """<div class="card"><div class="kicker">현대자동차그룹 차량용 앱 개발 공모전 · 팀 하이대니</div>
             <h1>오도로그 <span style="color:#14B8A6">OdoLog</span></h1>
             <p class="sub">계기판이 직접 쓰는 법인차 운행기록부<br>Pleos Connect 차량 앱 + 관리자 웹</p></div>""",
    "c1": """<div class="card"><div class="kicker">문제</div>
             <h2>법인차 운행기록부,<br>10대 중 4대는 작성하지 못합니다.</h2>
             <p class="sub">매 운행마다 계기판 숫자를 적어야 하고, 연말에 기억으로 몰아 씁니다.<br>공용차는 누가 탔는지 알 수 없어 세무조사 때 소명이 어렵습니다.</p>
             <p class="sub" style="font-size:26px;margin-top:40px">2017~2021 업무용승용차 419.8만 대 중 39.6% 운행일지 미작성 (국세청 자료)</p></div>""",
    "c9": """<div class="card"><div class="kicker">오도로그</div>
             <h2>계기판 실측 · 공백 없는 기록<br>PIN 본인 확인 · 국세청 서식</h2>
             <div class="chips"><span class="chip">Pleos Vehicle SDK · Odometer</span><span class="chip">Pleos NaviHelper</span>
             <span class="chip">AAOS GEAR_SELECTION</span><span class="chip">CarUxRestrictions</span><span class="chip">Supabase · Next.js</span></div>
             <p class="sub">github.com/devdany/odolog · odolog-woad.vercel.app</p></div>""",
}
EMU_NOTE = "Pleos Connect Emulator 화면 · 에뮬레이터는 위치가 고정되어 주소가 같게 표시됩니다"

# 장면: 파일, 자르기(시작초), 속도, 자막 [(시작, 끝, 문구)], 태그
SCENES = [
    ("s2", 0.0, 1.0, "① 시동 → 본인 확인", [
        (0, 6.5, "시동을 걸면 운전자 확인부터. 이름을 고르고 PIN을 입력합니다."),
        (6.5, 99, "PIN은 서버에서 검증되어 체크인이 남고, 운행에 '본인 확인'이 표시됩니다.")]),
    ("s3", 0.0, 1.0, "② 주행", [
        (0, 4.5, "기어 D — 운행 기록 시작. 주행 전 계기판 거리를 Vehicle SDK Odometer에서 읽습니다."),
        (4.5, 99, "주행 중에는 앱 화면을 쓰지 않습니다(Pleos 주행 규제). 기록은 백그라운드에서.")]),
    ("s4", 0.0, 1.0, "③ 도착 → 내리기 전 1탭", [
        (0, 6.5, "회사 도착, P단 — 시동을 끄기 전에 이번 운행 용도를 묻습니다."),
        (6.5, 11.5, "[출퇴근] 1탭. 시동을 끄면 운행이 끝납니다."),
        (11.5, 99, "시동을 끄면 운전자 확인도 해제됩니다. 다음 운전자는 다시 PIN.")]),
    ("s5", 0.0, 1.3, "④ 다른 운전자", [
        (0, 7.5, "같은 차를 이과장이 씁니다. PIN으로 확인하면 본인 운행만 보입니다."),
        (7.5, 15, "김대리의 기록은 보이지 않습니다. 거래처까지 24km 주행."),
        (15, 99, "도착해 P단 → [업무] 1탭 → 시동 끔.")]),
    ("s6", 3.0, 1.0, "⑤ 공백 없는 기록", [
        (0, 7.5, "앱이 꺼져 있던 동안의 주행도 놓치지 않습니다. 계기판은 줄지 않으니까요."),
        (7.5, 15.5, "다음에 PIN으로 확인하면 '기록되지 않은 12km' 카드가 뜹니다."),
        (15.5, 99, "본인이 운전했다면 용도를 골라 가져가고, 아니면 관리자에게 넘깁니다.")]),
    ("web", 1.0, 1.0, "⑥ 관리자 웹", [
        (0, 14, "관리자 웹 — 차량별 업무사용비율과 미확정 운행을 한눈에."),
        (14, 26, "운행일지는 국세청 서식 그대로. 방금 기록한 운행이 'PIN 본인확인'과 함께 이어집니다."),
        (26, 99, "버튼 하나로 업무용승용차 운행기록부 엑셀을 내려받습니다.")]),
]

def overlay_html(tag, text, note):
    n = f'<div class="note">{note}</div>' if note else ""
    return html(f'<div class="overlay"><div class="tag">{tag}</div>{n}<div class="bar">{text}</div></div>')

# ---------- HTML → PNG (Playwright) ----------
jobs = [{"html": html(v), "out": str(ASSET / f"{k}.png"), "transparent": False} for k, v in CARDS.items()]
for name, _, _, tag, caps in SCENES:
    for i, (_, _, text) in enumerate(caps):
        jobs.append({"html": overlay_html(tag, text, EMU_NOTE if name != "web" else ""), "out": str(ASSET / f"{name}_{i}.png"), "transparent": True})
jobs.append({"html": overlay_html("⑥ 관리자 웹", "내려받은 엑셀 — 주행 전·후 계기판, 출·퇴근/일반업무 거리, PIN 본인확인, 업무사용비율.", ""), "out": str(ASSET / "xlsx_cap.png"), "transparent": True})
(ASSET / "jobs.json").write_text(json.dumps(jobs, ensure_ascii=False))
render = textwrap.dedent(f"""
import {{ chromium }} from 'playwright'; import fs from 'fs';
const jobs = JSON.parse(fs.readFileSync('{ASSET}/jobs.json','utf8'));
const b = await chromium.launch({{ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' }});
const p = await b.newPage({{ viewport: {{ width:1920, height:1080 }} }});
for (const j of jobs) {{ await p.setContent(j.html); await p.waitForTimeout(80); await p.screenshot({{ path:j.out, omitBackground:j.transparent }}); }}
await b.close();
""")
(PW / "render_cards.mjs").write_text(render)
subprocess.run(["node", "render_cards.mjs"], cwd=PW, check=True)

def ff(*args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)

NORM = "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0x0E1116,fps=30,format=yuv420p"
parts = []

def still(png, sec, out):
    ff("-loop", "1", "-t", str(sec), "-i", str(png), "-vf", f"{NORM},fade=t=in:st=0:d=0.4,fade=t=out:st={sec-0.4}:d=0.4", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-an", str(out))
    parts.append(out)

still(ASSET / "c0.png", 5, D / "p00.mp4")
still(ASSET / "c1.png", 9, D / "p01.mp4")

for name, trim, speed, tag, caps in SCENES:
    src = D / ("web/web.webm" if name == "web" else f"{name}.mp4")
    dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(src)], capture_output=True, text=True).stdout)
    out_len = (dur - trim) / speed
    inputs = ["-ss", str(trim), "-i", str(src)]
    chain = f"[0:v]setpts=PTS/{speed},{NORM}[v0]"
    last = "v0"
    for i, (a, b, _) in enumerate(caps):
        inputs += ["-i", str(ASSET / f"{name}_{i}.png")]
        nxt = f"v{i+1}"
        chain += f";[{last}][{i+1}:v]overlay=0:0:enable='between(t,{a},{min(b, out_len)})'[{nxt}]"
        last = nxt
    chain += f";[{last}]fade=t=in:st=0:d=0.3,fade=t=out:st={max(0, out_len-0.3)}:d=0.3[out]"
    out = D / f"p_{name}.mp4"
    ff(*inputs, "-filter_complex", chain, "-map", "[out]", "-t", str(out_len), "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-an", str(out))
    parts.append(out)
    if name == "web":
        # 엑셀 미리보기: 머리글(위) → 이번 운행·합계(아래)
        x = D / "web/logbook.xlsx.png"
        for k, (crop, sec) in enumerate([("iw:iw*0.5:0:0", 4), ("iw:iw*0.5:0:ih-iw*0.5", 4)]):
            o = D / f"p_xlsx{k}.mp4"
            ff("-loop", "1", "-t", str(sec), "-i", str(x), "-i", str(ASSET / "xlsx_cap.png"), "-filter_complex",
               f"[0:v]crop={crop},{NORM}[b];[b][1:v]overlay=0:0,fade=t=in:st=0:d=0.3,fade=t=out:st={sec-0.3}:d=0.3[out]",
               "-map", "[out]", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-an", str(o))
            parts.append(o)

still(ASSET / "c9.png", 7, D / "p99.mp4")

lst = D / "concat.txt"
lst.write_text("".join(f"file '{p}'\n" for p in parts))
final = D / "odolog_demo.mp4"
ff("-f", "concat", "-safe", "0", "-i", str(lst), "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", str(final))
d = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(final)], capture_output=True, text=True).stdout.strip()
print(f"done {final} {float(d):.1f}s {final.stat().st_size/1e6:.1f}MB")
