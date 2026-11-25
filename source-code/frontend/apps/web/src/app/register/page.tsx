"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert, AuthCard, ChallengeView, Field, SubmitButton } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { normalizePhone, problemMessage, t } from "@/lib/messages";

/** UI F-ACC-01.S-01 — Đăng ký: nhập SĐT. */
export default function RegisterPage() {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [phoneError, setPhoneError] = useState<string>();
  const [problem, setProblem] = useState<Problem>();
  const [pending, setPending] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const normalized = normalizePhone(phone);
    setProblem(undefined);
    if (!normalized) return setPhoneError(t("phone.invalid"));
    setPhoneError(undefined);
    setPending(true);
    const res = await api<ChallengeView>("auth/register", { body: { phone: normalized } });
    setPending(false);
    if (!res.ok) {
      if (res.problem.code === "VALIDATION_ERROR") return setPhoneError(t("phone.invalid"));
      return setProblem(res.problem);
    }
    sessionStorage.setItem("register", JSON.stringify({ phone: normalized, challenge: res.data }));
    router.push("/register/complete");
  }

  return (
    <AuthCard title={t("register.title")}>
      {problem && <Alert>{problemMessage(problem)}</Alert>}
      <form onSubmit={submit} noValidate>
        <Field label={t("phone.label")} type="tel" inputMode="tel" autoComplete="tel" value={phone} autoFocus
          onChange={(e) => setPhone(e.target.value)} error={phoneError} />
        <SubmitButton pending={pending} pendingText="Đang gửi mã…">Tiếp tục</SubmitButton>
      </form>
      <p className="mt-6 text-center text-sm">
        Đã có tài khoản? <Link href="/login" className="font-medium text-primary">Đăng nhập</Link>
      </p>
    </AuthCard>
  );
}
