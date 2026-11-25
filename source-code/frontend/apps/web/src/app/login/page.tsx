"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Alert, AuthCard, ChallengeView, Field, OtpField, SubmitButton, useSecondsUntil } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { normalizePhone, otpExhausted, otpMessage, problemMessage, t } from "@/lib/messages";

/** Chỉ chuyển tiếp tới đường dẫn nội bộ, tránh open redirect qua ?next=. */
function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/account";
}

/**
 * UI F-ACC-01.S-03 (Đăng nhập) và S-04 (Xác minh thiết bị mới) trên cùng một route: mật khẩu chỉ nằm trong state của
 * trang để gửi lại mã (quyết định D2), không lưu ở đâu khác; rời trang là mất.
 */
function LoginFlow() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [phoneError, setPhoneError] = useState<string>();
  const [problem, setProblem] = useState<Problem>();
  const [lockedUntil, setLockedUntil] = useState<string>();
  const [pending, setPending] = useState(false);
  const [challenge, setChallenge] = useState<ChallengeView>();
  const [otp, setOtp] = useState("");
  const [otpError, setOtpError] = useState<string>();
  const [otpLocked, setOtpLocked] = useState(false);
  const lockSeconds = useSecondsUntil(lockedUntil);

  async function login(): Promise<boolean> {
    const normalized = normalizePhone(phone);
    if (!normalized) {
      setPhoneError(t("phone.invalid"));
      return false;
    }
    setPhoneError(undefined);
    setProblem(undefined);
    setPending(true);
    const res = await api("auth/login", { body: { phone: normalized, password } });
    setPending(false);
    if (res.ok) {
      router.replace(next);
      return true;
    }
    const p = res.problem;
    if (p.code === "CHALLENGE_REQUIRED" && p.params) {
      setChallenge(p.params as unknown as ChallengeView);
      setOtp("");
      setOtpError(undefined);
      setOtpLocked(false);
      return true;
    }
    if (p.code === "INVALID_CREDENTIALS") setPassword("");
    if (p.code === "ACCOUNT_TEMP_LOCKED") {
      setLockedUntil(new Date(Date.now() + Number(p.params?.retryAfterSeconds ?? 0) * 1000).toISOString());
    }
    setProblem(p);
    return false;
  }

  async function verify(code: string) {
    if (!challenge || pending) return;
    setPending(true);
    const res = await api("auth/login/verify", { body: { challengeId: challenge.challengeId, otp: code } });
    setPending(false);
    if (res.ok) {
      setPassword("");
      return router.replace(next);
    }
    if (res.problem.code === "CHALLENGE_INVALID") {
      setOtp("");
      setOtpLocked(otpExhausted(res.problem));
      return setOtpError(otpMessage(res.problem));
    }
    setProblem(res.problem);
  }

  // Đủ 6 số thì tự gửi đúng một lần (giống F-PAY-03.S-03)
  useEffect(() => {
    if (challenge && otp.length === 6) void verify(otp);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [otp]);

  if (challenge) {
    return (
      <AuthCard title={t("newDevice.title")}>
        {problem && <Alert>{problemMessage(problem)}</Alert>}
        <p className="mb-4 text-sm">{t("newDevice.sentTo", { destination: challenge.destinationMasked ?? "" })}</p>
        <form onSubmit={(e) => { e.preventDefault(); void verify(otp); }} noValidate>
          <OtpField challenge={challenge} value={otp} onChange={setOtp} error={otpError} locked={otpLocked || pending}
            onResend={() => void login()} resending={pending} />
          <SubmitButton pending={pending} pendingText="Đang xác minh…" disabled={otp.length !== 6}>Xác nhận</SubmitButton>
        </form>
        <button type="button" onClick={() => { setChallenge(undefined); setPassword(""); }}
          className="mt-4 w-full text-center text-sm text-primary">Dùng số điện thoại khác</button>
      </AuthCard>
    );
  }

  const showExpired = params.get("expired") === "1" && !problem;
  return (
    <AuthCard title={t("login.title")}>
      {showExpired && <Alert kind="info">{t("session.expired")}</Alert>}
      {params.get("reset") === "1" && !problem && <Alert kind="success">{t("forgot.done")}</Alert>}
      {problem && (
        <Alert>
          {problem.code === "ACCOUNT_TEMP_LOCKED"
            ? problemMessage({ ...problem, params: { retryAfterSeconds: lockSeconds } })
            : problemMessage(problem)}
        </Alert>
      )}
      <form onSubmit={(e) => { e.preventDefault(); void login(); }} noValidate>
        <Field label={t("phone.label")} type="tel" inputMode="tel" autoComplete="tel" value={phone} autoFocus
          onChange={(e) => setPhone(e.target.value)} error={phoneError} />
        <Field label={t("password.label")} type="password" autoComplete="current-password" value={password}
          onChange={(e) => setPassword(e.target.value)} />
        <p className="-mt-2 mb-4 text-right text-sm">
          <Link href="/forgot-password" className="text-primary">Quên mật khẩu?</Link>
        </p>
        <SubmitButton pending={pending} pendingText="Đang đăng nhập…" disabled={lockSeconds > 0}>Đăng nhập</SubmitButton>
      </form>
      <p className="mt-6 text-center text-sm">
        Chưa có tài khoản? <Link href="/register" className="font-medium text-primary">Đăng ký</Link>
      </p>
    </AuthCard>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginFlow />
    </Suspense>
  );
}
