"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert, AuthCard, Field, SubmitButton } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { PASSWORD_MIN, problemMessage, t } from "@/lib/messages";

/** UI F-ACC-01.S-07 — Đổi mật khẩu. */
export default function ChangePasswordPage() {
  const router = useRouter();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<Problem>();
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setProblem(undefined);
    if ([...next].length < PASSWORD_MIN) return setErrors({ newPassword: t("password.hint", { min: PASSWORD_MIN }) });
    setErrors({});
    setPending(true);
    const res = await api("me/password", { body: { currentPassword: current, newPassword: next } });
    setPending(false);
    if (res.ok) {
      setCurrent("");
      setNext("");
      return setDone(true);
    }
    const p = res.problem;
    // 401 vì mật khẩu hiện tại sai không phải hết phiên: phân biệt bằng code
    if (p.code === "INVALID_CREDENTIALS") return setErrors({ currentPassword: t("password.currentWrong") });
    if (p.code === "VALIDATION_ERROR") return setErrors({ newPassword: t("password.hint", { min: PASSWORD_MIN }) });
    if (p.code === "UNAUTHENTICATED" || p.code === "TOKEN_EXPIRED") return router.replace("/login?expired=1&next=/account/password");
    setProblem(p);
  }

  return (
    <AuthCard title={t("password.change.title")} back="/account">
      {done && <Alert kind="success">{t("password.changed")}</Alert>}
      {problem && <Alert>{problemMessage(problem)}</Alert>}
      <form onSubmit={submit} noValidate>
        <Field label={t("password.current")} type="password" autoComplete="current-password" value={current}
          onChange={(e) => setCurrent(e.target.value)} error={errors.currentPassword} />
        <Field label={t("password.new")} type="password" autoComplete="new-password" value={next}
          onChange={(e) => setNext(e.target.value)} error={errors.newPassword}
          hint={t("password.hint", { min: PASSWORD_MIN })} />
        <SubmitButton pending={pending} pendingText="Đang đổi mật khẩu…">Đổi mật khẩu</SubmitButton>
      </form>
    </AuthCard>
  );
}
