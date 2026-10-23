#!/usr/bin/env python3
"""심사용 데모 계정·데이터 생성 (Supabase service role 필요).

사용: python3 tools/seed_demo.py   (.secrets/supabase.env 의 SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, DEMO_EMAIL, DEMO_PASSWORD 사용)
- 데모 사용자 + 조직 "오도로그 데모(주)" + 차량 2대 + 운전자 3명 + 10월 운행 기록(계기판 체인 연속)
- 다시 실행하면 데모 조직의 차량·운전자·운행을 지우고 새로 만든다.
"""
import json
import os
import random
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

env = dict(l.split("=", 1) for l in (Path(__file__).resolve().parent.parent / ".secrets/supabase.env").read_text().split("\n") if "=" in l)
URL, KEY = env["SUPABASE_URL"], env["SUPABASE_SERVICE_ROLE_KEY"]
EMAIL, PASSWORD = env["DEMO_EMAIL"], env["DEMO_PASSWORD"]
KST = timezone(timedelta(hours=9))
random.seed(7)


def req(method, path, body=None, prefer=None):
    r = urllib.request.Request(URL + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    r.add_header("apikey", KEY)
    r.add_header("Authorization", f"Bearer {KEY}")
    r.add_header("Content-Type", "application/json")
    if prefer:
        r.add_header("Prefer", prefer)
    try:
        with urllib.request.urlopen(r) as res:
            t = res.read().decode()
            return json.loads(t) if t else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {path}: {e.code} {e.read().decode()}")


# ---- 사용자 ----
users = req("GET", "/auth/v1/admin/users?per_page=200")["users"]
user = next((u for u in users if u["email"] == EMAIL), None)
if not user:
    user = req("POST", "/auth/v1/admin/users", {"email": EMAIL, "password": PASSWORD, "email_confirm": True})
else:
    req("PUT", f"/auth/v1/admin/users/{user['id']}", {"password": PASSWORD})
uid = user["id"]

# ---- 조직 ----
mem = req("GET", f"/rest/v1/org_members?user_id=eq.{uid}&select=org_id")
if mem:
    org = mem[0]["org_id"]
    req("DELETE", f"/rest/v1/vehicles?org_id=eq.{org}")
    req("DELETE", f"/rest/v1/drivers?org_id=eq.{org}")
else:
    org = req("POST", "/rest/v1/organizations", {"name": "오도로그 데모(주)"}, "return=representation")[0]["id"]
    req("POST", "/rest/v1/org_members", {"org_id": org, "user_id": uid})

drivers = req("POST", "/rest/v1/drivers", [
    {"org_id": org, "name": "김대리", "dept": "영업팀"},
    {"org_id": org, "name": "이과장", "dept": "영업팀"},
    {"org_id": org, "name": "박팀장", "dept": "기술지원팀"},
], "return=representation")
D = {d["name"]: d for d in drivers}

vehicles = req("POST", "/rest/v1/vehicles", [
    {"org_id": org, "plate_no": "123가 4567", "model": "그랜저", "ownership": "lease"},
    {"org_id": org, "plate_no": "45나 7890", "model": "아이오닉 5", "ownership": "owned"},
], "return=representation")

HOME = "성남시 분당구 정자동"
OFFICE = "성남시 분당구 판교역로"
CLIENTS = [("○○상사 본사", "서울 강남구 테헤란로", 24), ("△△물산 물류센터", "용인시 기흥구 신갈동", 19),
           ("□□전자 연구소", "수원시 영통구 매탄동", 27), ("◇◇건설 현장사무소", "하남시 미사강변대로", 31)]


def trips_for(vehicle, start_odo, main_driver, seed_shift):
    out, odo, n = [], start_odo, 0
    day = datetime(2026, 10, 1, tzinfo=KST)
    today = datetime.now(KST)
    while day.date() < today.date():
        wd = day.weekday()
        plan = []
        if wd < 5:
            plan.append((8, 10, "COMMUTE", HOME, OFFICE, None, 18.2 + random.uniform(-0.3, 0.4), None))
            if (day.day + seed_shift) % 3 != 0:
                name, addr, dist = random.choice(CLIENTS)
                plan.append((14, 0, "BUSINESS", OFFICE, addr, name, dist + random.uniform(-1, 1.5), 2400 if dist > 25 else None))
                plan.append((16, 30, "BUSINESS", addr, OFFICE, None, dist + random.uniform(-1, 1.5), 2400 if dist > 25 else None))
            plan.append((18, 40, "COMMUTE", OFFICE, HOME, None, 18.4 + random.uniform(-0.3, 0.5), None))
        elif wd == 5 and day.day % 2 == 0:
            plan.append((11, 0, "PERSONAL", None, None, None, 42.0 + random.uniform(-5, 8), None))
        for h, m, purpose, a, b, dest, dist, toll in plan:
            n += 1
            st = day.replace(hour=h, minute=m) + timedelta(minutes=random.randint(-10, 10))
            dur = timedelta(minutes=int(dist * 2.2 + random.randint(5, 15)))
            d = round(dist, 1)
            # 최근 며칠은 일부 미확정, 하루는 미확인 구간
            recent = (today - st).days < 3
            p = "UNCONFIRMED" if recent and purpose == "BUSINESS" and n % 2 == 0 else purpose
            out.append({
                "vehicle_id": vehicle, "local_id": f"demo-{vehicle[:8]}-{n}",
                "driver_id": D[main_driver]["id"] if p != "PERSONAL" else D["박팀장"]["id"],
                "driver_name": main_driver if p != "PERSONAL" else "박팀장",
                "start_at": st.isoformat(), "end_at": (st + dur).isoformat(),
                "start_odo_km": round(odo, 1), "end_odo_km": round(odo + d, 1), "purpose": p,
                "start_addr": a, "end_addr": b, "dest_name": dest, "toll_won": toll, "source": "AUTO",
                "confirmed_at": None if p == "UNCONFIRMED" else (st + dur + timedelta(minutes=1)).isoformat(),
            })
            odo += d
        if day.day == 17 and seed_shift == 0:
            # 앱이 기록하지 못한 주행 → 미확인 구간
            gap = 12.0
            st = day.replace(hour=21, minute=0)
            out.append({"vehicle_id": vehicle, "local_id": f"demo-{vehicle[:8]}-gap", "start_at": st.isoformat(),
                        "end_at": (st + timedelta(minutes=30)).isoformat(), "start_odo_km": round(odo, 1),
                        "end_odo_km": round(odo + gap, 1), "purpose": "UNCONFIRMED", "source": "GAP"})
            odo += gap
        day += timedelta(days=1)
    return out, odo


t1, o1 = trips_for(vehicles[0]["id"], 12430.0, "김대리", 0)
t2, o2 = trips_for(vehicles[1]["id"], 3810.0, "이과장", 1)
KEYS = sorted({k for t in t1 + t2 for k in t})
for chunk in (t1, t2):
    chunk = [{k: t.get(k) for k in KEYS} for t in chunk]  # 일괄 입력은 모든 행의 키가 같아야 함
    for i in range(0, len(chunk), 200):
        req("POST", "/rest/v1/trips", chunk[i:i + 200])
now = datetime.now(timezone.utc).isoformat()
req("PATCH", f"/rest/v1/vehicles?id=eq.{vehicles[0]['id']}", {"last_odo_km": round(o1, 1), "last_seen_at": now, "paired_at": now})
req("PATCH", f"/rest/v1/vehicles?id=eq.{vehicles[1]['id']}", {"last_odo_km": round(o2, 1)})
print(f"demo user {EMAIL}, org {org}, trips {len(t1)}+{len(t2)}")
