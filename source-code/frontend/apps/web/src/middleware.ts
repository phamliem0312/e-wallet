import { NextRequest, NextResponse } from "next/server";
import { cookieOptions, CSRF_COOKIE, DEVICE_COOKIE, REFRESH_COOKIE } from "@/lib/session";

/** Cấp cookie thiết bị và CSRF khi chưa có; chặn trang tài khoản khi chưa đăng nhập. */
export function middleware(req: NextRequest) {
  const { pathname, search } = req.nextUrl;
  let res: NextResponse;
  if (pathname === "/") {
    res = NextResponse.redirect(new URL("/account", req.url));
  } else if (pathname.startsWith("/account") && !req.cookies.get(REFRESH_COOKIE)) {
    const login = new URL("/login", req.url);
    login.searchParams.set("next", pathname + search);
    res = NextResponse.redirect(login);
  } else {
    res = NextResponse.next();
  }
  if (!req.cookies.get(DEVICE_COOKIE)) {
    res.cookies.set(DEVICE_COOKIE, crypto.randomUUID(), cookieOptions(new Date(Date.now() + 400 * 24 * 3600 * 1000)));
  }
  if (!req.cookies.get(CSRF_COOKIE)) {
    res.cookies.set(CSRF_COOKIE, crypto.randomUUID(), cookieOptions(undefined, false));
  }
  return res;
}

export const config = { matcher: ["/((?!_next/static|_next/image|favicon.ico|api/).*)"] };
