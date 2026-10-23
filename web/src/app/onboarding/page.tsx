import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";

async function createOrg(formData: FormData) {
  "use server";
  const supabase = await createClient();
  const name = String(formData.get("name") ?? "").trim();
  if (!name) return;
  const { error } = await supabase.rpc("create_organization", { p_name: name });
  if (error) redirect(`/onboarding?error=${encodeURIComponent(error.message)}`);
  redirect("/vehicles");
}

export default async function OnboardingPage({ searchParams }: PageProps<"/onboarding">) {
  const { error } = await searchParams;
  return (
    <main className="min-h-screen grid place-items-center px-6">
      <form action={createOrg} className="w-full max-w-md space-y-4 rounded-2xl bg-white p-8 shadow-sm ring-1 ring-gray-200">
        <h1 className="text-xl font-bold">회사 등록</h1>
        <p className="text-sm text-gray-500">운행기록부를 관리할 법인(또는 사업자) 이름을 입력하세요. 다음 단계에서 차량과 운전자를 등록합니다.</p>
        <input name="name" required placeholder="예: 하이대니 주식회사" className="w-full rounded-lg border border-gray-300 px-3 py-2" />
        {error && <p className="text-sm text-red-600">{String(error)}</p>}
        <button className="w-full rounded-lg bg-accent py-2.5 font-medium text-white">시작하기</button>
      </form>
    </main>
  );
}
