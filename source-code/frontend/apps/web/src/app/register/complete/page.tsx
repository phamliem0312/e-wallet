"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Alert, AuthCard, ChallengeView, Field, OtpField, SubmitButton } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { fieldErrors, otpExhausted, otpMessage, PASSWORD_MIN, problemMessage, t, TERMS_VERSION } from "@/lib/messages";

type Stored = { phone: string; challenge: ChallengeView };

/** UI F-ACC-01.S-02 — Hoàn tất đăng ký: OTP cùng màn hình với họ tên, mật khẩu, điều khoản (quyết định D1). */
export default function CompleteRegistrationPage() {
  const router = useRouter();
  const [stored, setStored] = useState<Stored>();
  const [otp, setOtp] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [otpLocked, setOtpLocked] = useState(false);
  const [problem, setProblem] = useState<Problem>();
  const [pending, setPending] = useState(false);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    const raw = sessionStorage.getItem("register");
    if (!raw) router.replace("/register");
    else setStored(JSON.parse(raw));
  }, [router]);

  if (!stored) return null;
  const { phone, challenge } = stored;

  async function resend() {
    setResending(true);
    const res = await api<ChallengeView>("auth/register", { body: { phone } });
    setResending(false);
    if (!res.ok) return setProblem(res.problem);
    const next = { phone, challenge: res.data };
    sessionStorage.setItem("register", JSON.stringify(next));
    setStored(next);
    setOtp("");
    setOtpLocked(false);
    setErrors((e) => ({ ...e, otp: "" }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(undefined);
    const local: Record<string, string> = {};
    if (otp.length !== 6) local.otp = t("otp.error.invalid");
    if (!fullName.trim()) local.fullName = t("error.validation");
    if ([...password].length < PASSWORD_MIN) local.password = t("password.hint", { min: PASSWORD_MIN });
    setErrors(local);
    if (Object.keys(local).length) return;

    setPending(true);
    const res = await api("auth/register/verify", {
      body: { challengeId: challenge.challengeId, otp, password, fullName,
        ...(agreed ? { termsVersion: TERMS_VERSION } : {}) },
    });
    setPending(false);
    if (res.ok) {
      sessionStorage.removeItem("register");
      return router.replace("/account");
    }
    const p = res.problem;
    if (p.code === "VALIDATION_ERROR" && p.errors?.length) return setErrors(fieldErrors(p));
    if (p.code === "CONSENT_REQUIRED") return setErrors({ consent: t("consent.required") });
    if (p.code === "CHALLENGE_INVALID") {
      setOtp("");
      setOtpLocked(otpExhausted(p));
      return setErrors({ otp: otpMessage(p) });
    }
    setProblem(p);
  }

  const networkUncertain = problem?.code === "NETWORK";
  return (
    <AuthCard title={t("register.complete.title")} back="/register">
      {problem && (
        <Alert>
          {networkUncertain ? t("register.networkUncertain") : problemMessage(problem)}{" "}
          {(networkUncertain || problem.code === "PHONE_ALREADY_REGISTERED") && (
            <Link href="/login" className="font-medium underline">Đăng nhập</Link>
          )}
        </Alert>
      )}
      <p className="mb-4 text-sm">{t("register.sentTo", { destination: challenge.destinationMasked ?? "" })}</p>
      <form onSubmit={submit} noValidate>
        <OtpField challenge={challenge} value={otp} onChange={setOtp} error={errors.otp} locked={otpLocked}
          onResend={resend} resending={resending} />
        <Field label={t("fullName.label")} autoComplete="name" maxLength={128} value={fullName}
          onChange={(e) => setFullName(e.target.value)} error={errors.fullName} />
        <Field label={t("password.label")} type="password" autoComplete="new-password" value={password}
          onChange={(e) => setPassword(e.target.value)} error={errors.password}
          hint={t("password.hint", { min: PASSWORD_MIN })} />
        <div className="mb-4">
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" className="mt-1 size-4" checked={agreed} onChange={(e) => setAgreed(e.target.checked)}
              aria-invalid={errors.consent ? true : undefined} aria-describedby="consent-error" />
            <span>
              {t("consent.label")}{" "}
              <a href="/terms" target="_blank" rel="noopener" className="text-primary underline">
                (xem điều khoản, mở tab mới)
              </a>
            </span>
          </label>
          {errors.consent && <p id="consent-error" className="mt-1 text-sm text-destructive">{errors.consent}</p>}
        </div>
        <SubmitButton pending={pending} pendingText="Đang tạo tài khoản…">{t("register.submit")}</SubmitButton>
      </form>
    </AuthCard>
  );
}
