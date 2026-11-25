"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, AuthCard } from "@/components/ui";
import { api, Problem } from "@/lib/api";
import { problemMessage, t } from "@/lib/messages";

type Device = { deviceId: string; label?: string; lastSeenAt: string; isCurrent: boolean };
type Consent = { purpose: string; termsVersion: string; granted: boolean; createdAt: string };

const PURPOSES: Record<string, string> = { TERMS: "Điều khoản sử dụng", BIOMETRIC_PROCESSING: "Xử lý dữ liệu sinh trắc học" };

/** dd/MM/yyyy HH:mm theo giờ Việt Nam (design system §5). */
function formatTime(iso: string): string {
  return new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit",
    year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(iso));
}

/** UI F-ACC-01.S-08 — Thiết bị & đồng ý. */
export default function SecurityPage() {
  const router = useRouter();
  const [devices, setDevices] = useState<Device[]>();
  const [consents, setConsents] = useState<Consent[]>();
  const [problem, setProblem] = useState<Problem>();
  const [notice, setNotice] = useState<string>();

  const load = useCallback(async () => {
    setProblem(undefined);
    const [d, c] = await Promise.all([
      api<{ items: Device[] }>("me/devices"),
      api<{ items: Consent[] }>("me/consents"),
    ]);
    for (const r of [d, c]) {
      if (!r.ok && (r.problem.code === "UNAUTHENTICATED" || r.problem.code === "TOKEN_EXPIRED")) {
        return router.replace("/login?expired=1&next=/account/security");
      }
    }
    if (!d.ok) return setProblem(d.problem);
    if (!c.ok) return setProblem(c.problem);
    setDevices(d.data.items);
    setConsents(c.data.items);
  }, [router]);

  useEffect(() => {
    void load();
  }, [load]);

  async function remove(device: Device) {
    if (!window.confirm(t("devices.removeConfirm"))) return;
    const res = await api(`me/devices/${device.deviceId}`, { method: "DELETE" });
    if (!res.ok && res.problem.code === "NOT_FOUND") setNotice(t("devices.alreadyRemoved"));
    else if (!res.ok) return setProblem(res.problem);
    void load();
  }

  return (
    <AuthCard title="Bảo mật tài khoản" back="/account">
      {problem && (
        <Alert>
          {problemMessage(problem)}{" "}
          <button type="button" className="font-medium underline" onClick={() => void load()}>Thử lại</button>
        </Alert>
      )}
      {notice && <Alert kind="info">{notice}</Alert>}

      <h2 className="mb-2 font-semibold">{t("devices.title")}</h2>
      {!devices ? (
        <div aria-busy className="mb-6 h-24 animate-pulse rounded-md bg-secondary" />
      ) : (
        <ul className="mb-6 divide-y divide-border rounded-md border border-border">
          {devices.map((d) => (
            <li key={d.deviceId} className="flex items-center justify-between gap-2 p-3 text-sm">
              <div>
                <p className="font-medium">{d.label ?? t("devices.unknown")}</p>
                <p className="text-muted-foreground">
                  {d.isCurrent ? `${t("devices.current")} · ` : ""}{formatTime(d.lastSeenAt)}
                </p>
              </div>
              {!d.isCurrent && (
                <button type="button" onClick={() => void remove(d)}
                  className="h-11 rounded-md border border-border px-3 text-destructive">{t("devices.remove")}</button>
              )}
            </li>
          ))}
        </ul>
      )}

      <h2 className="mb-2 font-semibold">{t("consents.title")}</h2>
      {!consents ? (
        <div aria-busy className="h-12 animate-pulse rounded-md bg-secondary" />
      ) : consents.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("consents.empty")}</p>
      ) : (
        <ul className="space-y-2 text-sm">
          {consents.map((c) => (
            <li key={c.purpose}>
              <p className="font-medium">{PURPOSES[c.purpose] ?? c.purpose} · {c.termsVersion}</p>
              <p className="text-muted-foreground">
                {c.granted ? "Đồng ý" : "Không đồng ý"} lúc {formatTime(c.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </AuthCard>
  );
}
