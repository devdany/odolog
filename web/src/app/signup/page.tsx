import Link from "next/link";
import { redirect } from "next/navigation";
import { AuthShell, Field } from "@/components/auth-shell";
import { createClient } from "@/lib/supabase/server";

const ERRORS: Record<string, string> = {
  mismatch: "비밀번호가 서로 다릅니다.",
  short: "비밀번호는 6자 이상이어야 합니다.",
  exists: "이미 가입된 이메일입니다. 로그인해 주세요.",
  email: "이메일 주소를 확인해 주세요.",
  confirm: "가입 확인 메일을 확인한 뒤 로그인해 주세요.",
  unknown: "가입에 실패했습니다. 잠시 후 다시 시도해 주세요.",
};

/** 가입 + 회사 등록을 한 번에. 가입 직후 차량 등록 화면으로 보낸다. */
async function signUp(formData: FormData) {
  "use server";
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const company = String(formData.get("company") ?? "").trim();
  const back = (code: string) => redirect(`/signup?error=${code}&email=${encodeURIComponent(email)}&company=${encodeURIComponent(company)}`);
  if (password.length < 6) back("short");
  if (password !== String(formData.get("password2") ?? "")) back("mismatch");

  const supabase = await createClient();
  const { data, error } = await supabase.auth.signUp({ email, password });
  if (error) back(/already/i.test(error.message) ? "exists" : /email/i.test(error.message) ? "email" : /password/i.test(error.message) ? "short" : "unknown");
  if (!data.session) back("confirm");
  if (company) await supabase.rpc("create_organization", { p_name: company });
  redirect(company ? "/vehicles" : "/onboarding");
}

export default async function SignupPage({ searchParams }: PageProps<"/signup">) {
  const sp = await searchParams;
  const error = typeof sp.error === "string" ? ERRORS[sp.error] ?? ERRORS.unknown : null;
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  return (
    <AuthShell
      title="회원가입"
      desc="회사 계정을 만들고 차량을 등록하면, 차량 앱이 기록한 운행이 이곳에 모입니다."
      footer={
        <>
          이미 계정이 있으신가요?{" "}
          <Link href="/login" className="font-medium text-accent">로그인</Link>
        </>
      }
    >
      <form action={signUp} className="space-y-4">
        <Field label="회사명" name="company" required placeholder="예: 하이대니 주식회사" defaultValue={str(sp.company)} autoComplete="organization" />
        <Field label="이메일" name="email" type="email" required autoComplete="email" defaultValue={str(sp.email)} />
        <Field label="비밀번호" name="password" type="password" required minLength={6} autoComplete="new-password" hint="6자 이상" />
        <Field label="비밀번호 확인" name="password2" type="password" required minLength={6} autoComplete="new-password" />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button className="w-full rounded-lg bg-accent py-2.5 font-medium text-white hover:opacity-90">가입하고 시작하기</button>
      </form>
    </AuthShell>
  );
}
