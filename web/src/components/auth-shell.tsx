import type { ReactNode } from "react";
import { Logo } from "@/components/logo";

/** 로그인·회원가입 공용 레이아웃 */
export function AuthShell({ title, desc, children, footer }: { title: string; desc: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="min-h-screen grid place-items-center px-6 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <div className="flex items-center gap-2 text-accent font-bold text-xl">
            <Logo /> 오도로그
          </div>
          <h1 className="mt-4 text-2xl font-bold">{title}</h1>
          <p className="mt-2 text-sm text-gray-500">{desc}</p>
        </div>
        <div className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-200">{children}</div>
        <p className="mt-6 text-center text-sm text-gray-600">{footer}</p>
      </div>
    </main>
  );
}

export function Field({ label, hint, ...props }: { label: string; hint?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block text-sm font-medium">
      {label}
      <input {...props} className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 font-normal focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20" />
      {hint && <span className="mt-1 block text-xs font-normal text-gray-500">{hint}</span>}
    </label>
  );
}
