// 클라이언트·서버 공용 (server-only 아님)
export type Purpose = "COMMUTE" | "BUSINESS" | "PERSONAL" | "UNCONFIRMED";
export const PURPOSE_LABEL: Record<Purpose, string> = {
  COMMUTE: "출퇴근",
  BUSINESS: "업무",
  PERSONAL: "개인",
  UNCONFIRMED: "미확정",
};
