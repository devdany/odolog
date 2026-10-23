import Link from "next/link";
import { redirect } from "next/navigation";
import { Logo } from "@/components/logo";
import { getContext } from "@/lib/data";
import { createClient } from "@/lib/supabase/server";

async function signOut() {
  "use server";
  const supabase = await createClient();
  await supabase.auth.signOut();
  redirect("/login");
}

const NAV = [
  { href: "/dashboard", label: "대시보드" },
  { href: "/logbook", label: "운행일지" },
  { href: "/vehicles", label: "차량" },
  { href: "/drivers", label: "운전자" },
] as const;

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const { orgName, user } = await getContext();
  return (
    <div className="min-h-screen md:flex">
      <aside className="md:w-60 md:min-h-screen bg-white border-b md:border-b-0 md:border-r border-gray-200 px-5 py-5 flex md:flex-col gap-4 md:gap-8">
        <Link href="/dashboard" className="flex items-center gap-2 font-bold text-accent text-lg">
          <Logo /> 오도로그
        </Link>
        <nav className="flex md:flex-col gap-1 overflow-x-auto">
          {NAV.map((n) => (
            <Link key={n.href} href={n.href} className="rounded-lg px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 whitespace-nowrap">
              {n.label}
            </Link>
          ))}
        </nav>
        <div className="hidden md:block mt-auto text-sm">
          <p className="font-medium">{orgName}</p>
          <p className="text-gray-500 truncate">{user.email}</p>
          <form action={signOut}>
            <button className="mt-2 text-gray-500 hover:text-gray-900">로그아웃</button>
          </form>
        </div>
      </aside>
      <main className="flex-1 px-5 md:px-10 py-8 max-w-6xl">{children}</main>
    </div>
  );
}
