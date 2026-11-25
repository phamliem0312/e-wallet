import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import {
  ACCESS_COOKIE,
  AuthSession,
  cookieOptions,
  CSRF_COOKIE,
  CSRF_HEADER,
  DEVICE_COOKIE,
  REFRESH_COOKIE,
} from "@/lib/session";

/**
 * BFF: trình duyệt chỉ gọi /api/* của web; web gọi api-gateway. Chỉ chuyển các endpoint trong danh sách dưới
 * (hợp đồng contracts/openapi/identity/public.yaml). Token không bao giờ đi về trình duyệt.
 */
const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

type Rule = { method: string; auth: boolean; session?: boolean; device?: boolean };

const RULES: Record<string, Rule> = {
  "auth/register": { method: "POST", auth: false },
  "auth/register/verify": { method: "POST", auth: false, session: true, device: true },
  "auth/login": { method: "POST", auth: false, session: true, device: true },
  "auth/login/verify": { method: "POST", auth: false, session: true, device: true },
  "auth/password/forgot": { method: "POST", auth: false },
  "auth/password/reset": { method: "POST", auth: false },
  "auth/logout": { method: "POST", auth: true },
  "me/password": { method: "POST", auth: true },
  "me/devices": { method: "GET", auth: true },
  "me/consents": { method: "GET", auth: true },
};
const DEVICE_PATH = /^me\/devices\/[0-9a-fA-F-]{36}$/;

function ruleFor(path: string, method: string): Rule | undefined {
  const rule = DEVICE_PATH.test(path) ? { method: "DELETE", auth: true } : RULES[path];
  return rule && rule.method === method ? rule : undefined;
}

function problem(status: number, code: string) {
  return NextResponse.json(
    { type: `https://ewallet/errors/${code.toLowerCase().replaceAll("_", "-")}`, title: code, status, code,
      traceId: crypto.randomUUID().replaceAll("-", "") },
    { status, headers: { "content-type": "application/problem+json" } },
  );
}

type Jar = Awaited<ReturnType<typeof cookies>>;

function setSession(jar: Jar, session: AuthSession) {
  const refreshExpiry = new Date(session.refreshTokenExpiresAt);
  // access token sống 5 phút, nhưng cookie giữ tới hạn refresh để BFF còn token cũ mà biết phải làm mới
  jar.set(ACCESS_COOKIE, session.accessToken, cookieOptions(refreshExpiry));
  jar.set(REFRESH_COOKIE, session.refreshToken, cookieOptions(refreshExpiry));
}

function clearSession(jar: Jar) {
  jar.delete(ACCESS_COOKIE);
  jar.delete(REFRESH_COOKIE);
}

async function forward(req: NextRequest, path: string, method: string, body: unknown, token?: string) {
  const headers: Record<string, string> = { accept: "application/json" };
  if (body !== undefined) headers["content-type"] = "application/json";
  if (token) headers.authorization = `Bearer ${token}`;
  // IP của khách do LB đặt; gateway tin đúng một bước proxy (ewallet.gateway.max-trusted-index)
  const forwardedFor = req.headers.get("x-forwarded-for");
  if (forwardedFor) headers["x-forwarded-for"] = forwardedFor;
  const traceparent = req.headers.get("traceparent");
  if (traceparent) headers.traceparent = traceparent;
  const userAgent = req.headers.get("user-agent");
  if (userAgent) headers["user-agent"] = userAgent;
  return fetch(`${GATEWAY}/api/v1/${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: "no-store",
  });
}

/**
 * Làm mới phiên một lần. ponytail: nhiều tab làm mới cùng lúc bằng cùng refresh token thì tab chậm hơn bị coi là
 * dùng lại và mất phiên (OQ-037); cần khoá theo phiên ở BFF khi có nhiều tab thật.
 */
async function refresh(req: NextRequest, jar: Jar): Promise<string | undefined> {
  const refreshToken = jar.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) return undefined;
  const res = await forward(req, "auth/refresh", "POST", { refreshToken });
  if (!res.ok) return undefined;
  const session = (await res.json()) as AuthSession;
  setSession(jar, session);
  return session.accessToken;
}

async function handle(req: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const path = (await context.params).path.join("/");
  const rule = ruleFor(path, req.method);
  if (!rule) return problem(404, "NOT_FOUND");

  const jar = await cookies();
  if (req.method !== "GET") {
    const expected = jar.get(CSRF_COOKIE)?.value;
    if (!expected || req.headers.get(CSRF_HEADER) !== expected) return problem(403, "FORBIDDEN");
  }

  let body: Record<string, unknown> | undefined;
  if (req.method === "POST") {
    const parsed = await req.json().catch(() => undefined);
    if (parsed !== undefined && (typeof parsed !== "object" || parsed === null || Array.isArray(parsed))) {
      return problem(400, "VALIDATION_ERROR");
    }
    body = parsed ?? undefined;
    if (rule.device) {
      const device = jar.get(DEVICE_COOKIE)?.value;
      if (!device) return problem(400, "VALIDATION_ERROR");
      body = { ...body, deviceFingerprint: device }; // client không tự chọn dấu vân tay thiết bị
    }
  }

  let token = rule.auth ? jar.get(ACCESS_COOKIE)?.value : undefined;
  if (rule.auth && !token) token = await refresh(req, jar);
  if (rule.auth && !token) {
    clearSession(jar);
    return problem(401, "UNAUTHENTICATED");
  }

  let res: Response;
  try {
    res = await forward(req, path, req.method, body, token);
    if (rule.auth && res.status === 401) {
      const code = (await res.clone().json().catch(() => ({}))).code;
      // Mật khẩu hiện tại sai cũng là 401 nhưng không phải hết phiên (UI S-07)
      if (code !== "INVALID_CREDENTIALS") {
        token = await refresh(req, jar);
        if (token) res = await forward(req, path, req.method, body, token);
      }
    }
  } catch {
    return problem(502, "INTERNAL_ERROR");
  }

  if (path === "auth/logout") clearSession(jar);
  if (rule.auth && res.status === 401) {
    const code = (await res.clone().json().catch(() => ({}))).code;
    if (code !== "INVALID_CREDENTIALS") clearSession(jar);
  }
  if (rule.session && res.ok) {
    setSession(jar, (await res.json()) as AuthSession);
    return NextResponse.json({ ok: true }, { status: res.status });
  }

  const headers = new Headers();
  const contentType = res.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const retryAfter = res.headers.get("retry-after");
  if (retryAfter) headers.set("retry-after", retryAfter);
  const text = res.status === 204 ? null : await res.text();
  return new NextResponse(text, { status: res.status, headers });
}

export { handle as GET, handle as POST, handle as DELETE };
