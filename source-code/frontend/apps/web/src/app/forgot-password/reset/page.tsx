"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Alert, AuthCard, ChallengeView, Field, OtpField, SubmitButton } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { otpExhausted, otpMessage, PASSWORD_MIN, problemMessage, t } from "@/lib/messages";

type Stored = { phone: string; challenge: ChallengeView };

/** UI F-ACC-01.S-06 — Đặt mật khẩu mới (OTP cùng màn hình, D1). */
export default function ResetPasswordPage() {
  const router = useRouter();
  const [stored, setStored] = useState<Stored>();
  const [otp, setOtp] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [otpLocked, setOtpLocked] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const [pending, setPending] = useState(false);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    const raw = sessionStorage.getItem("forgot");
    if (!raw) router.replace("/forgot-password");
    else setStored(JSON.parse(raw));
  }, [router]);

  if (!stored) return null;
  const { phone, challenge } = stored;

  async function resend() {
    setResending(true);
    const res = await api<ChallengeView>("auth/password/forgot", { body: { phone } });
    setResending(false);
    if (!res.ok) return setProblem(res.problem);
    const next = { phone, challenge: res.data };
    sessionStorage.setItem("forgot", JSON.stringify(next));
    setStored(next);
    setOtp("");
    setOtpLocked(false);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(undefined);
    const local: Record<string, string> = {};
    if (otp.length !== 6) local.otp = t("otp.error.invalid");
    if ([...password].length < PASSWORD_MIN) local.password = t("password.hint", { min: PASSWORD_MIN });
    setErrors(local);
    if (Object.keys(local).length) return;
    setPending(true);
    const res = await api("auth/password/reset", { body: { challengeId: challenge.challengeId, otp, newPassword: password } });
    setPending(false);
    if (res.ok) {
      sessionStorage.removeItem("forgot");
      return router.replace("/login?reset=1");
    }
    const p = res.problem;
    if (p.code === "VALIDATION_ERROR") return setErrors({ password: t("password.hint", { min: PASSWORD_MIN }) });
    if (p.code === "CHALLENGE_INVALID") {
      setOtp("");
      setOtpLocked(otpExhausted(p));
      return setErrors({ otp: otpMessage(p) });
    }
    setProblem(p);
  }

  return (
    <AuthCard title={t("forgot.title")} back="/forgot-password">
      {problem && <Alert>{problemMessage(problem)}</Alert>}
      <p className="mb-4 text-sm">{t("forgot.sentTo", { destination: challenge.destinationMasked ?? "" })}</p>
      <form onSubmit={submit} noValidate>
        <OtpField challenge={challenge} value={otp} onChange={setOtp} error={errors.otp} locked={otpLocked}
          onResend={resend} resending={resending} />
        <Field label={t("password.new")} type="password" autoComplete="new-password" value={password}
          onChange={(e) => setPassword(e.target.value)} error={errors.password}
          hint={t("password.hint", { min: PASSWORD_MIN })} />
        <SubmitButton pending={pending} pendingText="Đang đặt mật khẩu…">Đặt mật khẩu</SubmitButton>
      </form>
    </AuthCard>
  );
}
