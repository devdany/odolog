import { revalidatePath } from "next/cache";
import { Card, PageTitle } from "@/components/ui";
import { fmtDateTime, getContext, km, type Vehicle } from "@/lib/data";

async function addVehicle(formData: FormData) {
  "use server";
  const { supabase, orgId } = await getContext();
  const plate = String(formData.get("plate_no") ?? "").trim();
  if (!plate) return;
  const { data } = await supabase
    .from("vehicles")
    .insert({ org_id: orgId, plate_no: plate, model: String(formData.get("model") ?? "").trim() || null, ownership: String(formData.get("ownership") ?? "owned") })
    .select("id")
    .single();
  if (data) await supabase.rpc("issue_pairing_code", { p_vehicle: data.id });
  revalidatePath("/vehicles");
}

async function issueCode(formData: FormData) {
  "use server";
  const { supabase } = await getContext();
  await supabase.rpc("issue_pairing_code", { p_vehicle: String(formData.get("id")) });
  revalidatePath("/vehicles");
}

async function removeVehicle(formData: FormData) {
  "use server";
  const { supabase } = await getContext();
  await supabase.from("vehicles").delete().eq("id", String(formData.get("id")));
  revalidatePath("/vehicles");
}

const OWNERSHIP = { owned: "자가", lease: "리스", rent: "렌트" } as const;

export default async function VehiclesPage() {
  const { supabase } = await getContext();
  const { data } = await supabase.from("vehicles").select("*").order("created_at");
  const vehicles = (data ?? []) as Vehicle[];

  return (
    <>
      <PageTitle title="차량" desc="차량을 등록하면 6자리 페어링 코드가 발급됩니다. 차량의 오도로그 앱 > 설정 > 관리자 웹 연결에 입력하세요." />
      <Card className="mb-6">
        <form action={addVehicle} className="flex flex-wrap gap-3 items-end">
          <label className="text-sm font-medium">
            차량번호
            <input name="plate_no" required placeholder="12가 3456" className="mt-1 block w-40 rounded-lg border border-gray-300 px-3 py-2" />
          </label>
          <label className="text-sm font-medium">
            차종
            <input name="model" placeholder="그랜저" className="mt-1 block w-40 rounded-lg border border-gray-300 px-3 py-2" />
          </label>
          <label className="text-sm font-medium">
            소유 형태
            <select name="ownership" className="mt-1 block w-32 rounded-lg border border-gray-300 px-3 py-2">
              <option value="owned">자가</option>
              <option value="lease">리스</option>
              <option value="rent">렌트</option>
            </select>
          </label>
          <button className="rounded-lg bg-accent px-4 py-2 font-medium text-white">차량 등록</button>
        </form>
      </Card>

      <div className="grid md:grid-cols-2 gap-4">
        {vehicles.map((v) => {
          const codeValid = v.pairing_code && v.pairing_expires_at && new Date(v.pairing_expires_at) > new Date();
          return (
            <Card key={v.id}>
              <div className="flex justify-between">
                <div>
                  <p className="text-lg font-bold">{v.plate_no}</p>
                  <p className="text-sm text-gray-500">{[v.model, OWNERSHIP[v.ownership]].filter(Boolean).join(" · ")}</p>
                </div>
                <form action={removeVehicle}>
                  <input type="hidden" name="id" value={v.id} />
                  <button className="text-sm text-gray-400 hover:text-red-600">삭제</button>
                </form>
              </div>
              <div className="mt-4 rounded-xl bg-gray-50 p-4">
                {v.paired_at ? (
                  <div className="text-sm">
                    <p className="font-medium text-teal-700">차량 연결됨</p>
                    <p className="text-gray-500 mt-1">마지막 동기화 {v.last_seen_at ? fmtDateTime(v.last_seen_at) : "—"} · 계기판 {km(v.last_odo_km)} km</p>
                  </div>
                ) : codeValid ? (
                  <div>
                    <p className="text-xs text-gray-500">페어링 코드 (24시간 유효)</p>
                    <p className="mt-1 font-mono text-3xl font-bold tracking-[0.3em]">{v.pairing_code}</p>
                  </div>
                ) : (
                  <p className="text-sm text-gray-500">페어링 코드가 만료되었습니다.</p>
                )}
                <form action={issueCode} className="mt-3">
                  <input type="hidden" name="id" value={v.id} />
                  <button className="text-sm font-medium text-accent">{v.paired_at ? "다른 기기로 다시 연결 (코드 발급)" : "새 코드 발급"}</button>
                </form>
              </div>
            </Card>
          );
        })}
      </div>
    </>
  );
}
