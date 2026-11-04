# Frontend — Next.js

## Mặc định
- Next.js bản stable mới nhất, **App Router**, TypeScript strict. Kiểm tra repo trước; không tự nâng phiên bản.
- Package manager theo repo (pnpm nếu mới).

## Cấu trúc

```
frontend/
├── app/
│   ├── (auth)/login/
│   ├── (app)/wallet/        # trang cần đăng nhập
│   ├── api/                 # Route Handlers nếu cần BFF endpoint
│   └── layout.tsx
├── features/<feature>/      # component + hook + server action theo tính năng
├── lib/api/                 # client sinh từ OpenAPI của backend
└── middleware.ts            # chặn route chưa đăng nhập
```

## Vai trò: BFF mỏng
- Next.js server (Server Components, Server Actions, Route Handlers) gọi `api-gateway` (web khách) hoặc `admin-gateway` (CMS), không gọi thẳng từng service; trình duyệt không gọi thẳng backend, nhờ vậy ẩn được URL nội bộ và token.
- Màn hình cần dữ liệu từ nhiều service (ví dụ "Khách hàng 360" trong CMS) thì BFF gọi song song qua gateway rồi ghép lại; không tạo service mới chỉ để ghép dữ liệu.
- **Không** đặt rule tiền ở frontend (tính phí, kiểm số dư). Frontend chỉ hiển thị và validate UX; backend quyết định.
- Sinh type/client từ OpenAPI (`openapi-typescript` hoặc `orval`) để FE/BE không lệch hợp đồng.

## Rendering
- Server Components mặc định cho trang đọc dữ liệu (số dư, lịch sử) — dữ liệu tài chính cá nhân: `cache: 'no-store'` / dynamic, **không** cache tĩnh hay ISR.
- Client Components chỉ cho phần tương tác (form chuyển tiền, OTP).
- Dữ liệu client-side cần refetch/polling (trạng thái giao dịch PENDING): TanStack Query hoặc SWR.

## Auth
- OIDC qua Auth.js (NextAuth) hoặc thư viện của IdP; token lưu ở **cookie httpOnly, Secure, SameSite=Lax/Strict**, không `localStorage`.
- Server gắn access token khi gọi backend; refresh ở server.
- CSRF: Server Actions có kiểm tra origin; Route Handler tự viết cần kiểm tra origin/CSRF token.

## Form giao dịch tiền
- Sinh `Idempotency-Key` (UUID) **một lần khi mở form/bước xác nhận**, giữ nguyên khi user bấm lại hoặc retry; chỉ tạo key mới cho giao dịch mới.
- Disable nút submit khi đang gửi; hiển thị trạng thái PENDING thay vì báo lỗi khi timeout (giao dịch có thể đã thành công).
- Validate bằng Zod (dùng chung schema giữa client và server action).

## Bảo mật & vận hành
- Security headers (CSP, HSTS, X-Frame-Options) qua `next.config` / middleware.
- Không log dữ liệu nhạy cảm phía client; mask số tài khoản khi hiển thị.
- Truyền `traceparent`/correlation ID từ Next.js server sang backend.
- Test: Vitest + Testing Library cho component, Playwright cho luồng chính (đăng nhập → nạp tiền → chuyển tiền).
