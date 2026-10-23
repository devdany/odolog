import Link from "next/link";
import { Card, PageTitle, PurposeBadge, Stat } from "@/components/ui";
import { currentYm, fmtDateTime, getContext, km, monthRange, summarize, type Trip, type Vehicle } from "@/lib/data";

export default async function DashboardPage() {
  const { supabase } = await getContext();
  const ym = currentYm();
  const { start, end } = monthRange(ym);
  const [{ data: vehicles }, { data: trips }] = await Promise.all([
    supabase.from("vehicles").select("*").order("created_at"),
    supabase.from("trips").select("*").gte("start_at", start).lt("start_at", end).order("start_at", { ascending: false }),
  ]);
  const vs = (vehicles ?? []) as Vehicle[];
  const ts = (trips ?? []) as Trip[];
  const all = summarize(ts);
  const pending = ts.filter((t) => t.end_at && t.purpose === "UNCONFIRMED");

  return (
    <>
      <PageTitle title={`${Number(ym.slice(5))}월 운행 현황`} desc="차량이 계기판 거리로 자동 기록한 운행을 기준으로 합니다." />
      <Card className="mb-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
          <Stat label="총 주행거리" value={km(all.total)} unit="km" />
          <Stat label="업무용 사용거리" value={km(all.work)} unit="km" />
          <Stat label="업무사용비율" value={(all.ratio * 100).toFixed(1)} unit="%" tone="accent" />
          <Stat label="미확정 운행" value={String(all.pending)} unit="건" tone={all.pending ? "warn" : undefined} />
        </div>
      </Card>

      {vs.length === 0 ? (
        <Card>
          <p className="text-gray-600">아직 등록된 차량이 없습니다.</p>
          <Link href="/vehicles" className="mt-3 inline-block rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white">차량 등록하기</Link>
        </Card>
      ) : (
        <div className="grid md:grid-cols-2 gap-4 mb-6">
          {vs.map((v) => {
            const s = summarize(ts.filter((t) => t.vehicle_id === v.id));
            return (
              <Card key={v.id}>
                <div className="flex items-start justify-between">
                  <div>
                    <p className="font-bold text-lg">{v.plate_no}</p>
                    <p className="text-sm text-gray-500">{v.model ?? "차종 미입력"}</p>
                  </div>
                  <span className={`text-xs rounded-md px-2 py-0.5 ${v.paired_at ? "bg-teal-50 text-teal-700" : "bg-gray-100 text-gray-600"}`}>
                    {v.paired_at ? `연결됨${v.last_seen_at ? ` · ${fmtDateTime(v.last_seen_at)}` : ""}` : "미연결"}
                  </span>
                </div>
                <div className="mt-4 grid grid-cols-3 gap-4">
                  <Stat label="주행" value={km(s.total)} unit="km" />
                  <Stat label="업무사용비율" value={(s.ratio * 100).toFixed(1)} unit="%" tone="accent" />
                  <Stat label="미확정" value={String(s.pending)} unit="건" tone={s.pending ? "warn" : undefined} />
                </div>
                <div className="mt-4 flex justify-between text-sm">
                  <span className="text-gray-500">계기판 {km(v.last_odo_km)} km</span>
                  <Link href={`/logbook?vehicle=${v.id}&month=${ym}`} className="text-accent font-medium">운행일지 →</Link>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {pending.length > 0 && (
        <Card>
          <h2 className="font-bold mb-3">용도 미확정 운행 <span className="text-amber-600">{pending.length}</span></h2>
          <ul className="divide-y divide-gray-100">
            {pending.slice(0, 8).map((t) => (
              <li key={t.id} className="py-2.5 flex items-center gap-3 text-sm">
                <span className="w-28 text-gray-500">{fmtDateTime(t.start_at)}</span>
                <span className="flex-1 truncate">{t.source === "GAP" ? "미확인 구간 (앱이 기록하지 못한 주행)" : [t.start_addr, t.dest_name ?? t.end_addr].filter(Boolean).join(" → ") || "위치 정보 없음"}</span>
                <span className="text-gray-500">{t.driver_name ?? "운전자 미지정"}</span>
                <span className="w-20 text-right font-medium">{km(t.distance_km)} km</span>
                <PurposeBadge p={t.purpose} />
              </li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}
