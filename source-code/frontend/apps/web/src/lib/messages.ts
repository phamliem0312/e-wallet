import type { Problem } from "./api";

/** Nội dung chữ của UI F-ACC-01 §6. */
export const M = {
  "register.title": "Tạo tài khoản",
  "register.complete.title": "Hoàn tất đăng ký",
  "phone.label": "Số điện thoại",
  "phone.invalid": "Số điện thoại không hợp lệ. Vui lòng nhập số Việt Nam gồm 10 chữ số.",
  "register.sentTo": "Nếu số {destination} hợp lệ, mã 6 chữ số đã được gửi tới đó.",
  "otp.label": "Mã xác thực 6 chữ số",
  "otp.expiresIn": "Mã hết hạn sau {mmss}",
  "otp.expired": "Mã đã hết hạn.",
  "otp.resend": "Gửi lại mã",
  "otp.resendIn": "Gửi lại sau {s} giây",
  "otp.error.invalid": "Mã xác thực không đúng hoặc đã hết hạn.",
  "otp.attemptsLeft": "Bạn còn {n} lần thử.",
  "otp.error.exhausted": "Mã không còn dùng được. Vui lòng gửi lại mã mới.",
  "fullName.label": "Họ và tên",
  "password.label": "Mật khẩu",
  "password.hint": "Ít nhất {min} ký tự và khác số điện thoại của bạn.",
  "consent.label": "Tôi đồng ý với Điều khoản sử dụng",
  "consent.required": "Bạn cần đồng ý với điều khoản sử dụng để tiếp tục.",
  "register.submit": "Tạo tài khoản",
  "register.alreadyRegistered": "Số điện thoại này đã được đăng ký. Vui lòng đăng nhập.",
  "register.networkUncertain": "Mất kết nối. Tài khoản của bạn có thể đã được tạo; hãy thử đăng nhập.",
  "login.title": "Đăng nhập",
  "login.invalid": "Số điện thoại hoặc mật khẩu không đúng.",
  "login.tempLocked": "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau {mmss}.",
  "login.locked": "Tài khoản của bạn đang bị khoá. Vui lòng liên hệ hỗ trợ.",
  "newDevice.title": "Xác minh thiết bị mới",
  "newDevice.sentTo": "Bạn đang đăng nhập trên thiết bị mới. Nhập mã gửi tới {destination}.",
  "forgot.title": "Quên mật khẩu",
  "forgot.sentTo": "Nếu số {destination} có tài khoản, mã 6 chữ số đã được gửi tới đó.",
  "forgot.done": "Đã đặt mật khẩu mới. Các thiết bị khác đã được đăng xuất. Vui lòng đăng nhập lại.",
  "password.change.title": "Đổi mật khẩu",
  "password.current": "Mật khẩu hiện tại",
  "password.new": "Mật khẩu mới",
  "password.currentWrong": "Mật khẩu hiện tại không đúng.",
  "password.changed": "Đã đổi mật khẩu. Các thiết bị khác đã được đăng xuất.",
  "devices.title": "Thiết bị đang đăng nhập",
  "devices.current": "Thiết bị này",
  "devices.unknown": "Thiết bị không rõ tên",
  "devices.remove": "Gỡ bỏ",
  "devices.removeConfirm": "Gỡ thiết bị này? Thiết bị sẽ phải nhập mã xác thực ở lần đăng nhập sau.",
  "devices.alreadyRemoved": "Thiết bị đã được gỡ trước đó.",
  "consents.title": "Điều khoản đã đồng ý",
  "consents.empty": "Chưa có điều khoản nào.",
  "session.expired": "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  "error.rateLimited": "Bạn thao tác quá nhanh. Vui lòng thử lại sau {s} giây.",
  "error.otpSendFailed": "Chưa gửi được mã xác thực. Vui lòng thử lại sau ít phút.",
  "error.network": "Không kết nối được. Vui lòng kiểm tra mạng và thử lại.",
  "error.csrf": "Không thể thực hiện yêu cầu. Vui lòng tải lại trang.",
  "error.validation": "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.",
  "error.generic": "Đã có lỗi xảy ra. Vui lòng thử lại.",
} as const;

export type MessageKey = keyof typeof M;

export function t(key: MessageKey, vars: Record<string, string | number> = {}): string {
  return M[key].replace(/\{(\w+)\}/g, (_, name) => String(vars[name] ?? ""));
}

export function mmss(totalSeconds: number): string {
  const s = Math.max(0, Math.ceil(totalSeconds));
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

/** Thông điệp chung cho mã lỗi không gắn với một ô cụ thể (UI §5). Mã chưa biết → lỗi chung. */
export function problemMessage(problem: Problem): string {
  switch (problem.code) {
    case "NETWORK": return t("error.network");
    case "RATE_LIMITED": return t("error.rateLimited", { s: problem.retryAfter ?? Number(problem.params?.retryAfterSeconds ?? 1) });
    case "OTP_SEND_FAILED": return t("error.otpSendFailed");
    case "INVALID_CREDENTIALS": return t("login.invalid");
    case "ACCOUNT_TEMP_LOCKED": return t("login.tempLocked", { mmss: mmss(Number(problem.params?.retryAfterSeconds ?? 0)) });
    case "ACCOUNT_LOCKED": return t("login.locked");
    case "PHONE_ALREADY_REGISTERED": return t("register.alreadyRegistered");
    case "CONSENT_REQUIRED": return t("consent.required");
    case "UNAUTHENTICATED":
    case "TOKEN_EXPIRED": return t("session.expired");
    case "FORBIDDEN": return t("error.csrf");
    case "VALIDATION_ERROR": return t("error.validation");
    case "CHALLENGE_INVALID": return otpMessage(problem);
    default: return t("error.generic");
  }
}

/** Lỗi của ô mã: còn lượt thì kèm số lần, không còn thì yêu cầu gửi lại. */
export function otpMessage(problem: Problem): string {
  const left = problem.params?.attemptsRemaining;
  if (typeof left === "number" && left > 0) return `${t("otp.error.invalid")} ${t("otp.attemptsLeft", { n: left })}`;
  return t("otp.error.exhausted");
}

export function otpExhausted(problem: Problem): boolean {
  const left = problem.params?.attemptsRemaining;
  return problem.code === "CHALLENGE_INVALID" && !(typeof left === "number" && left > 0);
}

/** BR-01 phía client: bỏ khoảng trắng/dấu chấm (server chưa nhận, OQ-016) rồi kiểm dạng. */
export function normalizePhone(input: string): string | null {
  const phone = input.replace(/[\s.]/g, "");
  return /^(0[0-9]{9}|\+84[0-9]{9})$/.test(phone) ? phone : null;
}

/** Lỗi theo ô từ Problem.errors[].field. */
export function fieldErrors(problem: Problem): Record<string, string> {
  const out: Record<string, string> = {};
  for (const e of problem.errors ?? []) out[e.field] = e.field.toLowerCase().includes("password")
    ? t("password.hint", { min: PASSWORD_MIN }) : t("error.validation");
  return out;
}

/** Cùng giá trị với auth.password.min-length của identity (chưa chốt, OQ-035). Client chỉ gợi ý; server quyết định. */
export const PASSWORD_MIN = Number(process.env.NEXT_PUBLIC_PASSWORD_MIN_LENGTH ?? 8);
/** Phiên bản điều khoản hiện hành (nguồn chưa chốt, OQ-050). */
export const TERMS_VERSION = process.env.NEXT_PUBLIC_TERMS_VERSION ?? "2026-11";
