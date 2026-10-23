import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import { Logo } from "@/components/logo";

async function signIn(formData: FormData) {
  "use server";
  const supabase = await createClient();
  const { error } = await supabase.auth.signInWithPassword({
    email: String(formData.get("email")),
    password: String(formData.get("password")),
  });
  if (error) redirect(`/login?error=${encodeURIComponent("이메일 또는 비밀번호가 올바르지 않습니다")}`);
  redirect("/dashboard");
}

async function signUp(formData: FormData) {
  "use server";
  const supabase = await createClient();
  const { data, error } = await supabase.auth.signUp({
    email: String(formData.get("email")),
    password: String(formData.get("password")),
  });
  if (error) redirect(`/login?error=${encodeURIComponent(signUpError(error.message))}`);
  if (!data.session) redirect(`/login?error=${encodeURIComponent("가입 확인 메일을 확인해 주세요")}`);
  redirect("/onboarding");
}

function signUpError(msg: string) {
  if (/already registered|already exists/i.test(msg)) return "이미 가입된 이메일입니다. 로그인해 주세요.";
  if (/password/i.test(msg)) return "비밀번호는 6자 이상이어야 합니다.";
  if (/email/i.test(msg)) return "이메일 주소를 확인해 주세요.";
  return "가입에 실패했습니다. 잠시 후 다시 시도해 주세요.";
}

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const { error } = await searchParams;
  return (
    <main className="min-h-screen grid place-items-center px-6">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <div className="flex items-center gap-2 text-accent font-bold text-xl">
            <Logo /> 오도로그
          </div>
          <h1 className="mt-4 text-2xl font-bold">법인차 운행기록부 관리자</h1>
          <p className="mt-2 text-sm text-gray-500">차량이 계기판 거리로 쓴 운행일지를 확인하고 국세청 서식으로 내려받습니다.</p>
        </div>
        <form className="space-y-3 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-200">
          <label className="block text-sm font-medium">
            이메일
            <input name="email" type="email" required className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            비밀번호
            <input name="password" type="password" required minLength={6} autoComplete="current-password" placeholder="6자 이상" className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2" />
            <span className="mt-1 block text-xs font-normal text-gray-500">회원가입 시 6자 이상으로 정해 주세요.</span>
          </label>
          {error && <p className="text-sm text-red-600">{String(error)}</p>}
          <button formAction={signIn} className="w-full rounded-lg bg-accent py-2.5 font-medium text-white hover:opacity-90">로그인</button>
          <button formAction={signUp} className="w-full rounded-lg border border-gray-300 py-2.5 font-medium hover:bg-gray-50">회원가입</button>
        </form>
      </div>
    </main>
  );
}
