import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { Card, PageTitle } from "@/components/ui";
import { getContext } from "@/lib/data";

type DriverRow = { id: string; name: string; dept: string | null; active: boolean; has_pin: boolean };

async function addDriver(formData: FormData) {
  "use server";
  const { supabase, orgId } = await getContext();
  const name = String(formData.get("name") ?? "").trim();
  const pin = String(formData.get("pin") ?? "").trim();
  if (!name) return;
  if (!/^\d{4,6}$/.test(pin)) redirect("/drivers?error=pin");
  const { data } = await supabase.from("drivers").insert({ org_id: orgId, name, dept: String(formData.get("dept") ?? "").trim() || null }).select("id").single();
  if (data) await supabase.rpc("set_driver_pin", { p_driver: data.id, p_pin: pin });
  revalidatePath("/drivers");
  redirect("/drivers");
}

async function setPin(formData: FormData) {
  "use server";
  const { supabase } = await getContext();
  const pin = String(formData.get("pin") ?? "").trim();
  if (!/^\d{4,6}$/.test(pin)) redirect("/drivers?error=pin");
  await supabase.rpc("set_driver_pin", { p_driver: String(formData.get("id")), p_pin: pin });
  revalidatePath("/drivers");
  redirect("/drivers?ok=pin");
}

async function toggleDriver(formData: FormData) {
  "use server";
  const { supabase } = await getContext();
  await supabase.from("drivers").update({ active: formData.get("active") === "true" }).eq("id", String(formData.get("id")));
  revalidatePath("/drivers");
}

const pinInput = "w-24 rounded-lg border border-gray-300 px-3 py-2 font-mono tracking-widest";

export default async function DriversPage({ searchParams }: PageProps<"/drivers">) {
  const sp = await searchParams;
  const { supabase } = await getContext();
  const { data } = await supabase.rpc("drivers_with_pin_status");
  const drivers = (data ?? []) as DriverRow[];
  return (
    <>
      <PageTitle
        title="운전자"
        desc="운전자는 차량에서 이름을 고른 뒤 PIN을 입력해 본인임을 확인합니다. PIN으로 확인한 운행에는 '본인 확인' 기록이 남습니다."
      />
      <Card className="mb-6">
        <form action={addDriver} className="flex flex-wrap gap-3 items-end">
          <label className="text-sm font-medium">
            이름
            <input name="name" required className="mt-1 block w-40 rounded-lg border border-gray-300 px-3 py-2" />
          </label>
          <label className="text-sm font-medium">
            부서
            <input name="dept" placeholder="영업팀" className="mt-1 block w-40 rounded-lg border border-gray-300 px-3 py-2" />
          </label>
          <label className="text-sm font-medium">
            PIN
            <input name="pin" required inputMode="numeric" pattern="\d{4,6}" maxLength={6} placeholder="4~6자리" className={`mt-1 block ${pinInput}`} />
          </label>
          <button className="rounded-lg bg-accent px-4 py-2 font-medium text-white">추가</button>
        </form>
        <p className="mt-3 text-xs text-gray-500">PIN은 암호화되어 저장되며 관리자도 다시 볼 수 없습니다. 운전자에게 직접 알려주세요.</p>
        {sp.error === "pin" && <p className="mt-2 text-sm text-red-600">PIN은 4~6자리 숫자여야 합니다.</p>}
        {sp.ok === "pin" && <p className="mt-2 text-sm text-teal-700">PIN을 변경했습니다.</p>}
      </Card>
      <Card>
        <ul className="divide-y divide-gray-100">
          {drivers.length === 0 && <li className="py-3 text-gray-500">등록된 운전자가 없습니다.</li>}
          {drivers.map((d) => (
            <li key={d.id} className="py-3 flex flex-wrap items-center gap-3">
              <span className={`flex-1 min-w-40 ${d.active ? "" : "text-gray-400 line-through"}`}>
                {d.name} <span className="text-gray-500 text-sm">{d.dept}</span>
              </span>
              <span className={`text-xs rounded-md px-2 py-0.5 ${d.has_pin ? "bg-teal-50 text-teal-700" : "bg-amber-50 text-amber-700"}`}>
                {d.has_pin ? "PIN 설정됨" : "PIN 없음"}
              </span>
              <form action={setPin} className="flex items-center gap-2">
                <input type="hidden" name="id" value={d.id} />
                <input name="pin" required inputMode="numeric" pattern="\d{4,6}" maxLength={6} placeholder="새 PIN" className={pinInput} />
                <button className="text-sm font-medium text-accent">{d.has_pin ? "재설정" : "설정"}</button>
              </form>
              <form action={toggleDriver}>
                <input type="hidden" name="id" value={d.id} />
                <input type="hidden" name="active" value={String(!d.active)} />
                <button className="text-sm text-gray-500 hover:text-gray-900">{d.active ? "비활성화" : "다시 사용"}</button>
              </form>
            </li>
          ))}
        </ul>
      </Card>
    </>
  );
}
