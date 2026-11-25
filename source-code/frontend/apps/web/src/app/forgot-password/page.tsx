"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert, AuthCard, ChallengeView, Field, SubmitButton } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { normalizePhone, problemMessage, t } from "@/lib/messages";

/** UI F-ACC-01.S-05 — Quên mật khẩu: luôn sang bước sau khi 201, không tiết lộ SĐT có tài khoản (D3). */
export default function ForgotPasswordPage() {
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
    const res = await api<ChallengeView>("auth/password/forgot", { body: { phone: normalized } });
    setPending(false);
    if (!res.ok) return res.problem.code === "VALIDATION_ERROR" ? setPhoneError(t("phone.invalid")) : setProblem(res.problem);
    sessionStorage.setItem("forgot", JSON.stringify({ phone: normalized, challenge: res.data }));
    router.push("/forgot-password/reset");
  }

  return (
    <AuthCard title={t("forgot.title")} back="/login">
      {problem && <Alert>{problemMessage(problem)}</Alert>}
      <form onSubmit={submit} noValidate>
        <Field label={t("phone.label")} type="tel" inputMode="tel" autoComplete="tel" value={phone} autoFocus
          onChange={(e) => setPhone(e.target.value)} error={phoneError} />
        <SubmitButton pending={pending} pendingText="Đang gửi mã…">Gửi mã</SubmitButton>
      </form>
    </AuthCard>
  );
}
