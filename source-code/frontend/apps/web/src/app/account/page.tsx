"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AuthCard } from "@/components/ui";
import { api } from "@/lib/api";

/** UI F-ACC-01.S-09 — Tài khoản (tạm, D4): chưa có SRS trang chủ ví, chưa có GET /me (OQ-048). */
export default function AccountPage() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function logout() {
    setPending(true);
    await api("auth/logout", { method: "POST" });
    router.replace("/login");
  }

  return (
    <AuthCard title="Tài khoản">
      <nav className="flex flex-col divide-y divide-border rounded-md border border-border">
        <Link href="/account/password" className="flex h-11 items-center px-4">Đổi mật khẩu</Link>
        <Link href="/account/security" className="flex h-11 items-center px-4">Bảo mật tài khoản</Link>
      </nav>
      <button type="button" onClick={logout} disabled={pending}
        className="mt-6 h-11 w-full rounded-md border border-border bg-secondary font-medium disabled:opacity-60">
        {pending ? "Đang đăng xuất…" : "Đăng xuất"}
      </button>
    </AuthCard>
  );
}
