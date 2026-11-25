import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "EWallet",
  description: "Ví điện tử EWallet",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi">
      <body className="antialiased">{children}</body>
    </html>
  );
}
