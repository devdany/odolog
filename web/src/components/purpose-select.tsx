"use client";

import { PURPOSE_LABEL, type Purpose } from "@/lib/purpose";

/** 용도 변경 즉시 서버 액션 제출 */
export function PurposeSelect({ tripId, value, action }: { tripId: string; value: Purpose; action: (fd: FormData) => Promise<void> }) {
  return (
    <form action={action}>
      <input type="hidden" name="id" value={tripId} />
      <select
        name="purpose"
        defaultValue={value}
        onChange={(e) => e.currentTarget.form?.requestSubmit()}
        className={`rounded-md border px-2 py-1 text-xs font-medium ${value === "UNCONFIRMED" ? "border-amber-300 bg-amber-50 text-amber-700" : "border-gray-200 bg-white"}`}
      >
        {(Object.keys(PURPOSE_LABEL) as Purpose[]).map((p) => (
          <option key={p} value={p}>
            {PURPOSE_LABEL[p]}
          </option>
        ))}
      </select>
    </form>
  );
}
