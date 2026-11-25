import { CSRF_COOKIE, CSRF_HEADER } from "./session";

/** Problem (RFC 9457) như common.yaml; `status` = 0 khi mất mạng. */
export type Problem = {
  status: number;
  code: string;
  params?: Record<string, unknown>;
  errors?: { field: string; code: string; message?: string }[];
  retryAfter?: number;
};

export type Result<T> = { ok: true; status: number; data: T } | { ok: false; problem: Problem };

function csrfToken(): string {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : "";
}

/** Gọi BFF của chính web (/api/*), không bao giờ gọi thẳng gateway từ trình duyệt. */
export async function api<T = unknown>(path: string, init: { method?: string; body?: unknown } = {}): Promise<Result<T>> {
  const method = init.method ?? (init.body === undefined ? "GET" : "POST");
  let res: Response;
  try {
    res = await fetch(`/api/${path}`, {
      method,
      headers: { "content-type": "application/json", [CSRF_HEADER]: csrfToken() },
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      cache: "no-store",
    });
  } catch {
    return { ok: false, problem: { status: 0, code: "NETWORK" } };
  }
  const data = res.status === 204 ? undefined : await res.json().catch(() => undefined);
  if (res.ok) return { ok: true, status: res.status, data: data as T };
  const retryAfter = Number(res.headers.get("retry-after")) || undefined;
  return { ok: false, problem: { status: res.status, code: data?.code ?? "INTERNAL_ERROR", params: data?.params,
    errors: data?.errors, retryAfter } };
}
