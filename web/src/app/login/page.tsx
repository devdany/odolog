import Link from "next/link";
import { redirect } from "next/navigation";
import { AuthShell, Field } from "@/components/auth-shell";
import { createClient } from "@/lib/supabase/server";

async function signIn(formData: FormData) {
  "use server";
  const supabase = await createClient();
  const email = String(formData.get("email") ?? "").trim();
  const { error } = await supabase.auth.signInWithPassword({ email, password: String(formData.get("password") ?? "") });
  if (error) redirect(`/login?error=1&email=${encodeURIComponent(email)}`);
  redirect("/dashboard");
}

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const { error, email } = await searchParams;
  return (
    <AuthShell
      title="로그인"
      desc="차량이 계기판 거리로 쓴 운행일지를 확인하고 국세청 서식으로 내려받습니다."
      footer={
        <>
          계정이 없으신가요?{" "}
          <Link href="/signup" className="font-medium text-accent">회원가입</Link>
        </>
      }
    >
      <form action={signIn} className="space-y-4">
        <Field label="이메일" name="email" type="email" required autoComplete="email" defaultValue={typeof email === "string" ? email : ""} />
        <Field label="비밀번호" name="password" type="password" required autoComplete="current-password" />
        {error && <p className="text-sm text-red-600">이메일 또는 비밀번호가 올바르지 않습니다.</p>}
        <button className="w-full rounded-lg bg-accent py-2.5 font-medium text-white hover:opacity-90">로그인</button>
      </form>
    </AuthShell>
  );
}
