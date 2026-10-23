import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { PurposeSelect } from "@/components/purpose-select";
import { Card, PageTitle, Stat } from "@/components/ui";
import { currentYm, fmtTime, getContext, km, monthRange, summarize, type Purpose, type Trip, type Vehicle } from "@/lib/data";

async function setPurpose(formData: FormData) {
  "use server";
  const { supabase } = await getContext();
  await supabase.from("trips").update({ purpose: String(formData.get("purpose")) as Purpose, confirmed_at: new Date().toISOString() }).eq("id", String(formData.get("id")));
  revalidatePath("/logbook");
}

async function pick(formData: FormData) {
  "use server";
  redirect(`/logbook?vehicle=${formData.get("vehicle")}&month=${formData.get("month")}`);
}

/** 국세청 업무용승용차 운행기록부 서식의 열 구성을 그대로 보여준다. */
export default async function LogbookPage({ searchParams }: PageProps<"/logbook">) {
  const sp = await searchParams;
  const { supabase } = await getContext();
  const { data: vdata } = await supabase.from("vehicles").select("*").order("created_at");
  const vehicles = (vdata ?? []) as Vehicle[];
  const vehicleId = (typeof sp.vehicle === "string" && sp.vehicle) || vehicles[0]?.id;
  const ym = (typeof sp.month === "string" && /^\d{4}-\d{2}$/.test(sp.month) && sp.month) || currentYm();
  const vehicle = vehicles.find((v) => v.id === vehicleId);

  let trips: Trip[] = [];
  if (vehicle) {
    const { start, end } = monthRange(ym);
    const { data } = await supabase.from("trips").select("*").eq("vehicle_id", vehicle.id).gte("start_at", start).lt("start_at", end).order("start_at");
    trips = (data ?? []) as Trip[];
  }
  const { data: ddata } = await supabase.from("drivers").select("id, name, dept");
  const deptOf = new Map((ddata ?? []).map((d: { id: string; dept: string | null }) => [d.id, d.dept]));
  const s = summarize(trips);
  const year = ym.slice(0, 4);

  return (
    <>
      <PageTitle
        title="운행일지"
        desc="업무용승용차 운행기록부(국세청 서식) 기준. 업무용 사용거리 = 출퇴근용 + 일반업무용. '본인확인'은 운전자가 차량에서 PIN으로 확인한 운행입니다."
        right={
          vehicle && (
            <div className="flex gap-2">
              <a href={`/api/export?vehicle=${vehicle.id}&month=${ym}`} className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white">월 엑셀</a>
              <a href={`/api/export?vehicle=${vehicle.id}&year=${year}`} className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium">{year}년 전체 엑셀</a>
            </div>
          )
        }
      />
      <Card className="mb-6">
        <form action={pick} className="flex flex-wrap gap-3 items-end">
          <label className="text-sm font-medium">
            차량
            <select name="vehicle" defaultValue={vehicleId} className="mt-1 block w-56 rounded-lg border border-gray-300 px-3 py-2">
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.plate_no} {v.model ? `· ${v.model}` : ""}
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm font-medium">
            월
            <input type="month" name="month" defaultValue={ym} className="mt-1 block rounded-lg border border-gray-300 px-3 py-2" />
          </label>
          <button className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium">조회</button>
        </form>
        <div className="mt-5 grid grid-cols-2 md:grid-cols-5 gap-6">
          <Stat label="총 주행거리" value={km(s.total)} unit="km" />
          <Stat label="출퇴근용" value={km(s.commute)} unit="km" />
          <Stat label="일반업무용" value={km(s.business)} unit="km" />
          <Stat label="업무사용비율" value={(s.ratio * 100).toFixed(1)} unit="%" tone="accent" />
          <Stat label="미확정" value={String(s.pending)} unit="건" tone={s.pending ? "warn" : undefined} />
        </div>
      </Card>

      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-600">
            <tr>
              <th className="px-3 py-2 text-left" rowSpan={2}>사용일자</th>
              <th className="px-3 py-2 text-left" colSpan={2}>사용자</th>
              <th className="px-3 py-2 text-center" colSpan={5}>운행내역 (km)</th>
              <th className="px-3 py-2 text-left" rowSpan={2}>비고</th>
              <th className="px-3 py-2 text-left" rowSpan={2}>용도</th>
            </tr>
            <tr>
              <th className="px-3 py-2 text-left">부서</th>
              <th className="px-3 py-2 text-left">성명</th>
              <th className="px-3 py-2 text-right">주행 전 계기판</th>
              <th className="px-3 py-2 text-right">주행 후 계기판</th>
              <th className="px-3 py-2 text-right">주행거리</th>
              <th className="px-3 py-2 text-right">출퇴근용</th>
              <th className="px-3 py-2 text-right">일반업무용</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {trips.length === 0 && (
              <tr>
                <td colSpan={10} className="px-3 py-10 text-center text-gray-500">{vehicle ? "이 달의 운행 기록이 없습니다." : "차량을 먼저 등록하세요."}</td>
              </tr>
            )}
            {trips.map((t) => {
              const d = Number(t.distance_km);
              const note =
                t.source === "GAP"
                  ? "미확인 구간"
                  : [t.dest_name ?? t.end_addr, t.note].filter(Boolean).join(" · ") || (t.purpose === "PERSONAL" ? "개인 (위치 미저장)" : "");
              return (
                <tr key={t.id} className={t.purpose === "UNCONFIRMED" ? "bg-amber-50/40" : ""}>
                  <td className="px-3 py-2 whitespace-nowrap">
                    {new Date(t.start_at).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul", month: "numeric", day: "numeric", weekday: "short" })}
                    <span className="block text-xs text-gray-400">{fmtTime(t.start_at)}{t.end_at ? `–${fmtTime(t.end_at)}` : " 주행 중"}</span>
                  </td>
                  <td className="px-3 py-2">{(t.driver_id && deptOf.get(t.driver_id)) || ""}</td>
                  <td className="px-3 py-2 whitespace-nowrap">
                    {t.driver_name ?? <span className="text-gray-400">미지정</span>}
                    {t.driver_verified && <span title={t.driver_verified_at ? `PIN 확인 ${fmtTime(t.driver_verified_at)}` : "PIN 확인"} className="ml-1 rounded bg-teal-50 px-1 text-[10px] font-medium text-teal-700">본인확인</span>}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{km(t.start_odo_km)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{km(t.end_odo_km)}</td>
                  <td className="px-3 py-2 text-right tabular-nums font-medium">{km(d)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.purpose === "COMMUTE" ? km(d) : ""}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{t.purpose === "BUSINESS" ? km(d) : ""}</td>
                  <td className="px-3 py-2 max-w-64 truncate text-gray-600">{note}</td>
                  <td className="px-3 py-2"><PurposeSelect tripId={t.id} value={t.purpose} action={setPurpose} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>
    </>
  );
}
