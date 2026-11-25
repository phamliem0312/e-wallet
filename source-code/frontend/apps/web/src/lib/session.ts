// Tên và thuộc tính cookie của BFF. Token chỉ nằm trong cookie httpOnly, trình duyệt không đọc được (F-ACC-01.FR-29).
export const ACCESS_COOKIE = "ewallet_at";
export const REFRESH_COOKIE = "ewallet_rt";
/** UUID ngẫu nhiên làm dấu vân tay thiết bị ([Giả định] UI §7, chờ OQ-032). */
export const DEVICE_COOKIE = "ewallet_device";
/** Double-submit: cookie đọc được bằng JS, client gửi lại trong header (F-ACC-01.FR-30). */
export const CSRF_COOKIE = "ewallet_csrf";
export const CSRF_HEADER = "x-csrf-token";

const secure = process.env.COOKIE_SECURE ? process.env.COOKIE_SECURE === "true" : process.env.NODE_ENV === "production";

export function cookieOptions(expires?: Date, httpOnly = true) {
  return { httpOnly, secure, sameSite: "lax" as const, path: "/", ...(expires ? { expires } : {}) };
}

/** Khớp schema AuthSession của hợp đồng identity. */
export type AuthSession = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
};
