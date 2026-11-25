"use client";

import Link from "next/link";
import { useEffect, useId, useState } from "react";
import { mmss, t } from "@/lib/messages";

/*
 * Component tối thiểu theo design system (Button, Input, Alert, Card, OTP...). Registry của shadcn/ui bị chặn
 * trên mạng công ty nên chưa sinh được component gốc; tên và hành vi giữ giống để thay thế về sau.
 */

export function AuthCard({ title, children, back }: { title: string; children: React.ReactNode; back?: string }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-4 py-8">
      <p className="mb-8 text-center text-xl font-semibold text-primary">EWallet</p>
      <section className="rounded-lg border border-border bg-background p-6">
        <h1 className="mb-6 flex items-center gap-2 text-2xl font-semibold">
          {back && (
            <Link href={back} aria-label="Quay lại" className="rounded-md px-1 text-muted-foreground">←</Link>
          )}
          {title}
        </h1>
        {children}
      </section>
    </main>
  );
}

export function Alert({ kind = "error", children }: { kind?: "error" | "info" | "success"; children: React.ReactNode }) {
  const style = {
    error: "border-destructive text-destructive",
    info: "border-info text-info",
    success: "border-success text-success",
  }[kind];
  return (
    <div role={kind === "error" ? "alert" : "status"} className={`mb-4 rounded-md border bg-background p-3 text-sm ${style}`}>
      {children}
    </div>
  );
}

type FieldProps = {
  label: string;
  error?: string;
  hint?: string;
} & React.InputHTMLAttributes<HTMLInputElement>;

export function Field({ label, error, hint, type, ...input }: FieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  const isPassword = type === "password";
  return (
    <div className="mb-4">
      <label htmlFor={id} className="mb-1 block text-sm font-medium">{label}</label>
      <div className="relative">
        <input
          id={id}
          type={isPassword && visible ? "text" : type}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className="h-11 w-full rounded-md border border-input bg-background px-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[invalid=true]:border-destructive"
          {...input}
        />
        {isPassword && (
          <button type="button" onClick={() => setVisible(!visible)} aria-pressed={visible}
            aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
            className="absolute inset-y-0 right-0 px-3 text-sm text-muted-foreground">
            {visible ? "Ẩn" : "Hiện"}
          </button>
        )}
      </div>
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-sm text-destructive">{error}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1 text-sm text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

/** Nút gửi: khoá ngay khi đang gửi, không nhận bấm tiếp tới khi có kết quả (design system SubmitButton). */
export function SubmitButton({ pending, pendingText, children, variant = "primary", ...rest }:
  { pending: boolean; pendingText: string; variant?: "primary" | "secondary" } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const style = variant === "primary" ? "bg-primary text-primary-foreground" : "border border-border bg-secondary";
  return (
    <button type="submit" disabled={pending || rest.disabled} aria-busy={pending}
      className={`h-11 w-full rounded-md px-4 font-medium disabled:opacity-60 ${style}`} {...rest}>
      {pending ? pendingText : children}
    </button>
  );
}

/** Đếm ngược tới một thời điểm (giây còn lại, cập nhật mỗi giây). */
export function useSecondsUntil(iso?: string): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  return iso ? Math.max(0, (Date.parse(iso) - now) / 1000) : 0;
}

export type ChallengeView = {
  challengeId: string;
  factor: string;
  expiresAt: string;
  destinationMasked?: string;
  resendAfter?: string;
  attemptsRemaining?: number;
};

/**
 * Ô mã OTP + đếm ngược + gửi lại (UI S-02, S-04, S-06). Một ô 6 chữ số thay cho InputOTP 6 ô của shadcn:
 * vẫn hỗ trợ tự điền mã từ tin nhắn (autocomplete one-time-code).
 */
export function OtpField({ challenge, value, onChange, error, locked, onResend, resending }: {
  challenge: ChallengeView;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  locked?: boolean;
  onResend: () => void;
  resending: boolean;
}) {
  const id = useId();
  const expiresIn = useSecondsUntil(challenge.expiresAt);
  const resendIn = useSecondsUntil(challenge.resendAfter);
  const expired = expiresIn <= 0;
  return (
    <div className="mb-4">
      <label htmlFor={id} className="mb-1 block text-sm font-medium">{t("otp.label")}</label>
      <input
        id={id}
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        value={value}
        disabled={expired || locked}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${id}-status`}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, "").slice(0, 6))}
        className="h-11 w-full rounded-md border border-input bg-background px-3 text-center text-xl tracking-[0.5em] tabular-nums outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[invalid=true]:border-destructive"
      />
      <div id={`${id}-status`}>
        {error && <p className="mt-1 text-sm text-destructive">{error}</p>}
        <p className="mt-1 text-sm text-muted-foreground" aria-live="polite">
          {expired ? t("otp.expired") : expiresIn <= 30 ? t("otp.expiresIn", { mmss: mmss(expiresIn) }) : null}
        </p>
        {!expired && expiresIn > 30 && (
          <p className="mt-1 text-sm text-muted-foreground" aria-hidden>{t("otp.expiresIn", { mmss: mmss(expiresIn) })}</p>
        )}
      </div>
      <button type="button" onClick={onResend} disabled={resendIn > 0 || resending}
        className="mt-2 text-sm font-medium text-primary disabled:text-muted-foreground">
        {resendIn > 0 ? t("otp.resendIn", { s: Math.ceil(resendIn) }) : t("otp.resend")}
      </button>
    </div>
  );
}
