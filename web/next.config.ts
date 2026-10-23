import type { NextConfig } from "next";

// 관리자 웹은 모든 화면이 로그인 사용자별 동적 데이터라 Cache Components를 쓰지 않는다.
const nextConfig: NextConfig = {
  cacheComponents: false,
  serverExternalPackages: ["exceljs"],
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
