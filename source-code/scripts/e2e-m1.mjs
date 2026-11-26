// Kịch bản end-to-end M1: trình duyệt (giả lập) → web BFF :3000 → api-gateway :8080 → identity :8101.
// OTP đọc từ provider giả của identity (/internal/dev/messages, gọi thẳng identity, không qua gateway).
const WEB = "http://localhost:3000";
const GATEWAY = "http://localhost:8080";
const IDENTITY = "http://localhost:8101";
const results = [];
function check(name, cond, detail = "") {
  results.push({ name, ok: !!cond });
  console.log(`${cond ? "PASS" : "FAIL"}  ${name}${cond ? "" : "  -> " + detail}`);
}

class Browser {
  constructor(ip) { this.jar = new Map(); this.rawSetCookies = []; this.ip = ip; }
  cookieHeader() { return [...this.jar].map(([k, v]) => `${k}=${v}`).join("; "); }
  store(res) {
    for (const sc of res.headers.getSetCookie()) {
      this.rawSetCookies.push(sc);
      const [pair] = sc.split(";");
      const i = pair.indexOf("=");
      const name = pair.slice(0, i), value = pair.slice(i + 1);
      if (value === "" || /max-age=0|expires=thu, 01 jan 1970/i.test(sc)) this.jar.delete(name);
      else this.jar.set(name, value);
    }
  }
  async page(path) {
    const res = await fetch(WEB + path, { redirect: "manual", headers: { cookie: this.cookieHeader(), "x-forwarded-for": this.ip } });
    this.store(res);
    return res;
  }
  async api(path, body, { method, csrf = true } = {}) {
    const headers = { cookie: this.cookieHeader(), "content-type": "application/json", "x-forwarded-for": this.ip,
      "user-agent": "e2e-script" };
    if (csrf) headers["x-csrf-token"] = this.jar.get("ewallet_csrf") ?? "";
    const res = await fetch(`${WEB}/api/${path}`, { method: method ?? (body === undefined ? "GET" : "POST"), headers,
      body: body === undefined ? undefined : JSON.stringify(body) });
    this.store(res);
    const text = await res.text();
    let data; try { data = text ? JSON.parse(text) : undefined; } catch { data = text; }
    return { status: res.status, data, text, headers: res.headers };
  }
}

async function otpFor(phone) {
  const e164 = "+84" + phone.slice(1);
  const res = await fetch(`${IDENTITY}/internal/dev/messages?recipient=${encodeURIComponent(e164)}`);
  const messages = await res.json();
  return messages.filter((m) => m.type === "OTP").map((m) => m.code);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rand = () => String(Math.floor(Math.random() * 1e8)).padStart(8, "0");

const phone = "09" + rand();
const password = "matkhau-dai-va-kho-doan";
const ip = `203.0.113.${1 + Math.floor(Math.random() * 250)}`;
const a = new Browser(ip);

// ---- Trang và cookie ban đầu ----
let res = await a.page("/account");
check("Chưa đăng nhập: /account chuyển về /login", res.status === 307 && res.headers.get("location")?.includes("/login?next=%2Faccount"), `${res.status} ${res.headers.get("location")}`);
res = await a.page("/login");
check("Trang đăng nhập 200", res.status === 200, res.status);
check("Có cookie thiết bị và CSRF", a.jar.has("ewallet_device") && a.jar.has("ewallet_csrf"), [...a.jar.keys()]);
const deviceCookie = a.rawSetCookies.find((c) => c.startsWith("ewallet_device="));
check("Cookie thiết bị là HttpOnly, SameSite=Lax", /httponly/i.test(deviceCookie) && /samesite=lax/i.test(deviceCookie), deviceCookie);
const csp = res.headers.get("content-security-policy");
check("Trang HTML có CSP và HSTS (NFR-04)", csp?.includes("frame-ancestors 'none'") && !!res.headers.get("strict-transport-security"), csp);

// ---- Đăng ký ----
let r = await a.api("auth/register", { phone }, { csrf: false });
check("POST thiếu CSRF bị 403 FORBIDDEN (FR-30)", r.status === 403 && r.data?.code === "FORBIDDEN", r.text);
r = await a.api("auth/register", { phone });
check("Bắt đầu đăng ký 201, có challenge", r.status === 201 && r.data?.challengeId && r.data?.destinationMasked, r.text);
const regChallenge = r.data;
let otps = await otpFor(phone);
check("Đúng 1 OTP 6 chữ số được 'gửi'", otps.length === 1 && /^\d{6}$/.test(otps[0]), JSON.stringify(otps));
r = await a.api("auth/register/verify", { challengeId: regChallenge.challengeId, otp: otps[0], password, fullName: "Nguyễn Văn A" });
check("Thiếu đồng ý điều khoản → 422 CONSENT_REQUIRED", r.status === 422 && r.data?.code === "CONSENT_REQUIRED", r.text);
r = await a.api("auth/register/verify", { challengeId: regChallenge.challengeId, otp: otps[0], password, fullName: "Nguyễn Văn A", termsVersion: "2026-11" });
check("Hoàn tất đăng ký 201 {ok:true}", r.status === 201 && r.data?.ok === true, r.text);
check("Body trả trình duyệt không chứa token (FR-29)", !/accessToken|refreshToken|eyJ/.test(r.text), r.text);
const atCookie = a.rawSetCookies.findLast((c) => c.startsWith("ewallet_at="));
const rtCookie = a.rawSetCookies.findLast((c) => c.startsWith("ewallet_rt="));
check("Cookie phiên HttpOnly + SameSite=Lax", [atCookie, rtCookie].every((c) => c && /httponly/i.test(c) && /samesite=lax/i.test(c)), `${atCookie} | ${rtCookie}`);

// ---- Trang tài khoản, thiết bị, đồng ý ----
res = await a.page("/account");
check("Đã đăng nhập: /account 200", res.status === 200, res.status);
r = await a.api("me/devices");
check("Danh sách thiết bị: 1 thiết bị hiện tại, không lộ dấu vân tay", r.status === 200 && r.data.items.length === 1 && r.data.items[0].isCurrent === true && !r.text.includes(a.jar.get("ewallet_device")), r.text);
r = await a.api("me/consents");
check("Đồng ý TERMS 2026-11", r.status === 200 && r.data.items[0]?.purpose === "TERMS" && r.data.items[0]?.termsVersion === "2026-11", r.text);

// ---- Đăng xuất, đăng nhập lại trên cùng thiết bị ----
r = await a.api("auth/logout", undefined, { method: "POST" });
check("Đăng xuất 204, xoá cookie phiên", r.status === 204 && !a.jar.has("ewallet_rt") && !a.jar.has("ewallet_at"), `${r.status} ${[...a.jar.keys()]}`);
res = await a.page("/account");
check("Sau đăng xuất /account chuyển về /login", res.status === 307, res.status);
r = await a.api("auth/login", { phone, password: "sai-mat-khau-roi" });
check("Sai mật khẩu → 401 INVALID_CREDENTIALS", r.status === 401 && r.data?.code === "INVALID_CREDENTIALS", r.text);
r = await a.api("auth/login", { phone, password });
check("Đăng nhập thiết bị đã biết: 200, không cần OTP", r.status === 200 && r.data?.ok === true && (await otpFor(phone)).length === 1, r.text);

// ---- Thiết bị mới ----
const b = new Browser(ip);
await b.page("/login");
r = await b.api("auth/login", { phone, password });
check("Thiết bị mới → 422 CHALLENGE_REQUIRED kèm challenge", r.status === 422 && r.data?.code === "CHALLENGE_REQUIRED" && r.data?.params?.challengeId, r.text);
otps = await otpFor(phone);
r = await b.api("auth/login/verify", { challengeId: r.data.params.challengeId, otp: otps[0] });
check("Xác minh OTP thiết bị mới → 200", r.status === 200 && r.data?.ok === true, r.text);
r = await a.api("me/devices");
check("Trình duyệt A thấy 2 thiết bị", r.status === 200 && r.data.items.length === 2, r.text);

// ---- Làm mới phiên khi access token hết hạn (giả lập bằng xoá cookie access token) ----
b.jar.delete("ewallet_at");
r = await b.api("me/devices");
check("BFF tự làm mới phiên bằng refresh token", r.status === 200 && b.jar.has("ewallet_at"), r.text);

// ---- Đổi mật khẩu trên B: phiên A bị thu hồi ----
const newPassword = "mat-khau-moi-dai-hon";
r = await b.api("me/password", { currentPassword: "sai-mat-khau-roi", newPassword });
check("Mật khẩu hiện tại sai → 401 INVALID_CREDENTIALS, B vẫn còn phiên", r.status === 401 && r.data?.code === "INVALID_CREDENTIALS" && b.jar.has("ewallet_rt"), r.text);
r = await b.api("me/password", { currentPassword: password, newPassword });
check("Đổi mật khẩu 204", r.status === 204, r.text);
a.jar.delete("ewallet_at"); // buộc A phải làm mới
r = await a.api("me/devices");
check("Phiên A bị thu hồi sau khi đổi mật khẩu → 401", r.status === 401 && !a.jar.has("ewallet_rt"), r.text);
r = await b.api("me/devices");
check("Phiên B (hiện tại) vẫn dùng được", r.status === 200, r.text);

// ---- Quên mật khẩu ----
await sleep(31_000); // qua thời gian chờ gửi lại OTP (30 giây)
const c = new Browser(ip);
await c.page("/login");
r = await c.api("auth/password/forgot", { phone });
check("Quên mật khẩu 201", r.status === 201 && r.data?.challengeId, r.text);
otps = await otpFor(phone);
const resetPassword = "mat-khau-thu-ba-dai";
r = await c.api("auth/password/reset", { challengeId: r.data.challengeId, otp: otps[0], newPassword: resetPassword });
check("Đặt lại mật khẩu 204", r.status === 204, r.text);
r = await b.api("me/devices");
// access token JWT còn hạn tối đa 5 phút sau khi thu hồi (FR-31, design §10): vẫn 200 là đúng thiết kế
check("Ngay sau đặt lại: access token cũ của B còn hiệu lực tới hết hạn (≤ 5 phút)", r.status === 200, r.text);
b.jar.delete("ewallet_at"); // buộc B phải làm mới
r = await b.api("me/devices");
check("Đặt lại mật khẩu thu hồi mọi phiên: B không làm mới được → 401", r.status === 401 && !b.jar.has("ewallet_rt"), r.text);
r = await a.api("auth/login", { phone, password: resetPassword });
check("Đăng nhập bằng mật khẩu mới trên thiết bị A → 200", r.status === 200, r.text);

// ---- Biên của gateway ----
let g = await fetch(`${GATEWAY}/internal/dev/messages?recipient=x`);
check("Gateway không định tuyến /internal/** (401/404)", g.status === 401 || g.status === 404, g.status);
g = await fetch(`${GATEWAY}/api/v1/me/devices`);
check("Gateway chặn /me khi không có token (401 Problem)", g.status === 401 && (await g.json()).code === "UNAUTHENTICATED", g.status);
r = await a.api("me/../../internal/dev/messages");
check("BFF không chuyển đường dẫn ngoài danh sách", r.status === 404, r.status);

const failed = results.filter((x) => !x.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed; phone ${phone}`);
process.exit(failed.length ? 1 : 0);
