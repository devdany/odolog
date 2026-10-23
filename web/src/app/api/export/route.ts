import ExcelJS from "exceljs";
import { NextResponse, type NextRequest } from "next/server";
import { createClient } from "@/lib/supabase/server";
import type { Trip, Vehicle } from "@/lib/data";

/**
 * 업무용승용차 운행기록부 (국세청고시 서식 항목) XLSX.
 * 항목: 사용일자 / 사용자(부서·성명) / 주행 전·후 계기판 거리 / 주행거리 / 업무용 사용거리(출퇴근용·일반업무용) / 비고
 * 하단: 사업연도(기간) 총주행거리, 업무용 사용거리, 업무사용비율 (법인세 신고서 별지 제29호 기재값)
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const { data: auth } = await supabase.auth.getUser();
  if (!auth.user) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const vehicleId = req.nextUrl.searchParams.get("vehicle");
  const month = req.nextUrl.searchParams.get("month");
  const year = req.nextUrl.searchParams.get("year") ?? month?.slice(0, 4);
  if (!vehicleId || !year) return NextResponse.json({ error: "vehicle and month|year required" }, { status: 400 });

  // RLS가 조직 소속 여부를 보장한다
  const { data: v } = await supabase.from("vehicles").select("*, organizations(name)").eq("id", vehicleId).maybeSingle();
  if (!v) return NextResponse.json({ error: "not found" }, { status: 404 });
  const vehicle = v as Vehicle & { organizations: { name: string } | null };

  const [y, m] = [Number(year), month ? Number(month.slice(5)) : null];
  const start = new Date(Date.UTC(y, (m ?? 1) - 1, 1, -9)).toISOString();
  const end = new Date(Date.UTC(m ? y : y + 1, m ? m : 0, 1, -9)).toISOString();
  const { data: tdata } = await supabase.from("trips").select("*").eq("vehicle_id", vehicleId).gte("start_at", start).lt("start_at", end).not("end_at", "is", null).order("start_at");
  const trips = (tdata ?? []) as Trip[];
  const { data: ddata } = await supabase.from("drivers").select("id, dept");
  const deptOf = new Map((ddata ?? []).map((d: { id: string; dept: string | null }) => [d.id, d.dept ?? ""]));

  const wb = new ExcelJS.Workbook();
  const ws = wb.addWorksheet("운행기록부", { pageSetup: { orientation: "landscape", paperSize: 9, fitToPage: true, fitToWidth: 1 } });
  ws.columns = [{ width: 14 }, { width: 12 }, { width: 10 }, { width: 14 }, { width: 14 }, { width: 11 }, { width: 11 }, { width: 11 }, { width: 36 }];
  const border = { top: { style: "thin" }, left: { style: "thin" }, bottom: { style: "thin" }, right: { style: "thin" } } as const;
  const period = m ? `${y}년 ${m}월` : `${y}년`;

  ws.mergeCells("A1:I1");
  ws.getCell("A1").value = "업무용승용차 운행기록부";
  ws.getCell("A1").font = { size: 16, bold: true };
  ws.getCell("A1").alignment = { horizontal: "center" };
  ws.addRow([]);
  ws.addRow(["기간", period, "", "법인명", vehicle.organizations?.name ?? ""]);
  ws.addRow(["차종", vehicle.model ?? "", "", "자동차등록번호", vehicle.plate_no]);
  ws.addRow([]);

  const h1 = ws.addRow(["사용일자", "사용자", "", "운행내역", "", "", "", "", "비고"]);
  const h2 = ws.addRow(["", "부서", "성명", "주행 전 계기판의 거리(㎞)", "주행 후 계기판의 거리(㎞)", "주행거리(㎞)", "업무용 사용거리(㎞)", "", ""]);
  const h3 = ws.addRow(["", "", "", "", "", "", "출·퇴근용(㎞)", "일반 업무용(㎞)", ""]);
  const r1 = h1.number, r3 = h3.number;
  ws.mergeCells(`A${r1}:A${r3}`); ws.mergeCells(`B${r1}:C${r1}`); ws.mergeCells(`B${r1 + 1}:B${r3}`); ws.mergeCells(`C${r1 + 1}:C${r3}`);
  ws.mergeCells(`D${r1}:H${r1}`); ws.mergeCells(`D${r1 + 1}:D${r3}`); ws.mergeCells(`E${r1 + 1}:E${r3}`); ws.mergeCells(`F${r1 + 1}:F${r3}`);
  ws.mergeCells(`G${r1 + 1}:H${r1 + 1}`); ws.mergeCells(`I${r1}:I${r3}`);
  [h1, h2, h3].forEach((r) => r.eachCell({ includeEmpty: true }, (c, col) => {
    if (col > 9) return;
    c.font = { bold: true }; c.alignment = { horizontal: "center", vertical: "middle", wrapText: true }; c.border = border;
    c.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFF1F5F9" } };
  }));

  let total = 0, commute = 0, business = 0;
  for (const t of trips) {
    const d = Number(t.distance_km);
    total += d;
    if (t.purpose === "COMMUTE") commute += d;
    if (t.purpose === "BUSINESS") business += d;
    const note = [t.source === "GAP" ? "미확인 구간" : null, t.dest_name ?? t.end_addr, t.note, t.purpose === "PERSONAL" ? "개인" : null, t.purpose === "UNCONFIRMED" ? "용도 미확정" : null]
      .filter(Boolean).join(" · ");
    const row = ws.addRow([
      new Date(t.start_at).toLocaleDateString("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }),
      (t.driver_id && deptOf.get(t.driver_id)) || "",
      t.driver_name ?? "",
      Number(t.start_odo_km),
      t.end_odo_km == null ? "" : Number(t.end_odo_km),
      d,
      t.purpose === "COMMUTE" ? d : "",
      t.purpose === "BUSINESS" ? d : "",
      note,
    ]);
    row.eachCell({ includeEmpty: true }, (c, col) => {
      if (col > 9) return;
      c.border = border;
      if (col >= 4 && col <= 8) { c.numFmt = "#,##0.0"; c.alignment = { horizontal: "right" }; }
    });
  }

  ws.addRow([]);
  const work = commute + business;
  const sumRows = [
    ["기간 총주행거리(㎞)", total],
    ["업무용 사용거리(㎞)", work],
    ["업무사용비율(%)", total > 0 ? Math.round((work / total) * 1000) / 10 : 0],
  ] as const;
  for (const [label, value] of sumRows) {
    const r = ws.addRow(["", "", "", "", "", label, "", Math.round(value * 10) / 10]);
    ws.mergeCells(`F${r.number}:G${r.number}`); // 병합 후에는 G(=F 프록시)를 건드리지 않는다
    r.getCell(6).font = { bold: true };
    r.getCell(6).border = border;
    r.getCell(8).numFmt = "#,##0.0";
    r.getCell(8).border = border;
    r.getCell(8).font = { bold: true };
  }
  const foot = ws.addRow(["※ 주행 전·후 거리는 차량 계기판(Pleos Vehicle SDK Odometer) 실측값으로 자동 기록되었습니다. — 오도로그"]);
  foot.getCell(1).font = { size: 9, color: { argb: "FF6B7280" } };

  const buf = await wb.xlsx.writeBuffer();
  const filename = `운행기록부_${vehicle.plate_no}_${m ? `${y}-${String(m).padStart(2, "0")}` : y}.xlsx`;
  return new NextResponse(buf as ArrayBuffer, {
    headers: {
      "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
    },
  });
}
