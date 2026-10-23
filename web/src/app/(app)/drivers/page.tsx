import { revalidatePath } from "next/cache";
import { Card, PageTitle } from "@/components/ui";
import { getContext, type Driver } from "@/lib/data";

async function addDriver(formData: FormData) {
  "use server";
  const { supabase, orgId } = await getContext();
  const name = String(formData.get("name") ?? "").trim();
  if (!name) return;
  await supabase.from("drivers").insert({ org_id: orgId, name, dept: String(formData.get("dept") ?? "").trim() || null });
  revalidatePath("/drivers");
}

async function toggleDriver(formData: FormData) {
  "use server";
  const { supabase } = await getContext();
  await supabase.from("drivers").update({ active: formData.get("active") === "true" }).eq("id", String(formData.get("id")));
  revalidatePath("/drivers");
}

export default async function DriversPage() {
  const { supabase } = await getContext();
  const { data } = await supabase.from("drivers").select("*").order("name");
  const drivers = (data ?? []) as Driver[];
  return (
    <>
      <PageTitle title="운전자" desc="등록한 운전자는 연결된 차량 앱에 자동으로 동기화되어, 출발 전 '오늘 운전자'로 선택할 수 있습니다." />
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
          <button className="rounded-lg bg-accent px-4 py-2 font-medium text-white">추가</button>
        </form>
      </Card>
      <Card>
        <ul className="divide-y divide-gray-100">
          {drivers.length === 0 && <li className="py-3 text-gray-500">등록된 운전자가 없습니다.</li>}
          {drivers.map((d) => (
            <li key={d.id} className="py-3 flex items-center justify-between">
              <span className={d.active ? "" : "text-gray-400 line-through"}>
                {d.name} <span className="text-gray-500 text-sm">{d.dept}</span>
              </span>
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
