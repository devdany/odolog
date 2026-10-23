import type { ReactNode } from "react";
import { PURPOSE_LABEL, type Purpose } from "@/lib/data";

export function PageTitle({ title, desc, right }: { title: string; desc?: string; right?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold">{title}</h1>
        {desc && <p className="mt-1 text-sm text-gray-500">{desc}</p>}
      </div>
      {right}
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-2xl bg-white p-5 shadow-sm ring-1 ring-gray-200 ${className}`}>{children}</section>;
}

export function Stat({ label, value, unit, tone }: { label: string; value: string; unit?: string; tone?: "accent" | "warn" }) {
  const color = tone === "accent" ? "text-accent" : tone === "warn" ? "text-amber-600" : "text-gray-900";
  return (
    <div>
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${color}`}>
        {value}
        {unit && <span className="ml-1 text-sm font-normal text-gray-500">{unit}</span>}
      </p>
    </div>
  );
}

const PURPOSE_STYLE: Record<Purpose, string> = {
  COMMUTE: "bg-blue-50 text-blue-700",
  BUSINESS: "bg-teal-50 text-teal-700",
  PERSONAL: "bg-violet-50 text-violet-700",
  UNCONFIRMED: "bg-amber-50 text-amber-700",
};

export function PurposeBadge({ p }: { p: Purpose }) {
  return <span className={`inline-block rounded-md px-2 py-0.5 text-xs font-medium ${PURPOSE_STYLE[p]}`}>{PURPOSE_LABEL[p]}</span>;
}
