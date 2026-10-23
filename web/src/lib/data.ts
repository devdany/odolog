import "server-only";
import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";

import { PURPOSE_LABEL, type Purpose } from "@/lib/purpose";
export { PURPOSE_LABEL, type Purpose };

export type Vehicle = {
  id: string;
  plate_no: string;
  model: string | null;
  ownership: "owned" | "lease" | "rent";
  pairing_code: string | null;
  pairing_expires_at: string | null;
  paired_at: string | null;
  last_odo_km: number | null;
  last_seen_at: string | null;
};

export type Driver = { id: string; name: string; dept: string | null; active: boolean };

export type Trip = {
  id: string;
  vehicle_id: string;
  driver_id: string | null;
  driver_name: string | null;
  start_at: string;
  end_at: string | null;
  start_odo_km: number;
  end_odo_km: number | null;
  distance_km: number;
  purpose: Purpose;
  start_addr: string | null;
  end_addr: string | null;
  dest_name: string | null;
  toll_won: number | null;
  note: string | null;
  source: "AUTO" | "GAP";
};

/** 로그인 사용자와 소속 조직. 없으면 로그인/온보딩으로 보낸다 (Data Access Layer). */
export async function getContext() {
  const supabase = await createClient();
  const { data: auth } = await supabase.auth.getUser();
  if (!auth.user) redirect("/login");
  const { data: member } = await supabase
    .from("org_members")
    .select("org_id, organizations(name)")
    .eq("user_id", auth.user.id)
    .limit(1)
    .maybeSingle();
  if (!member) redirect("/onboarding");
  const org = member.organizations as unknown as { name: string } | null;
  return { supabase, user: auth.user, orgId: member.org_id as string, orgName: org?.name ?? "" };
}

/** 'YYYY-MM' → [시작, 다음달 시작) (KST) */
export function monthRange(ym: string) {
  const [y, m] = ym.split("-").map(Number);
  const start = new Date(Date.UTC(y, m - 1, 1, -9));
  const end = new Date(Date.UTC(y, m, 1, -9));
  return { start: start.toISOString(), end: end.toISOString() };
}

export function currentYm() {
  const kst = new Date(Date.now() + 9 * 3600_000);
  return `${kst.getUTCFullYear()}-${String(kst.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** 운행기록부 하단 합계와 같은 정의: 업무용 사용거리 = 출퇴근 + 일반업무 */
export function summarize(trips: Trip[]) {
  const done = trips.filter((t) => t.end_at);
  const sum = (p?: Purpose) => done.filter((t) => !p || t.purpose === p).reduce((a, t) => a + Number(t.distance_km), 0);
  const total = sum();
  const commute = sum("COMMUTE");
  const business = sum("BUSINESS");
  return {
    total,
    commute,
    business,
    work: commute + business,
    ratio: total > 0 ? (commute + business) / total : 0,
    pending: done.filter((t) => t.purpose === "UNCONFIRMED").length,
    count: done.length,
  };
}

export const km = (v: number | null | undefined) =>
  v == null ? "—" : Number(v).toLocaleString("ko-KR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export const fmtDateTime = (iso: string) =>
  new Date(iso).toLocaleString("ko-KR", { timeZone: "Asia/Seoul", month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false });

export const fmtTime = (iso: string) =>
  new Date(iso).toLocaleTimeString("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hour12: false });
