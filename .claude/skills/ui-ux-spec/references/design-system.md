# Design system nền (Tailwind + shadcn/ui)

Dùng file này để tạo `docs/ui/design-system.md` **một lần** cho dự án, nếu chưa có. Sau đó mọi đặc tả UI tham chiếu file của dự án chứ không đọc lại file này. Màu thương hiệu chưa chốt thì để giá trị tạm và ghi rõ là tạm, **không tự chọn màu thương hiệu** rồi trình bày như đã quyết.

## 1. Nguyên tắc
1. **Rõ ràng hơn đẹp:** trong ví điện tử, người dùng phải biết chắc mình đang chuyển bao nhiêu, cho ai, và kết quả ra sao.
2. **Một cách cho một việc:** cùng một loại thao tác thì luôn cùng component, cùng vị trí, cùng câu chữ.
3. **Token ngữ nghĩa, không dùng màu thô:** code dùng `bg-primary`, `text-destructive`, không `bg-blue-600`. Đổi thương hiệu chỉ cần đổi token.

## 2. Token

### Màu (CSS variables theo quy ước shadcn/ui)
| Token | Dùng cho | Ghi chú |
|---|---|---|
| `--background` / `--foreground` | Nền, chữ chính | |
| `--primary` / `--primary-foreground` | Nút hành động chính, liên kết | Màu thương hiệu (chưa chốt) |
| `--secondary`, `--muted`, `--accent` | Nút phụ, nền phụ, hover | |
| `--destructive` | Lỗi, hành động không hoàn tác được | |
| `--success` | Giao dịch thành công, tiền vào | Token bổ sung (shadcn không có sẵn) |
| `--warning` | Đang xử lý, cần chú ý, môi trường thử nghiệm | Token bổ sung |
| `--info` | Thông tin trung tính | Token bổ sung |
| `--border`, `--input`, `--ring` | Viền, ô nhập, vòng focus | `--ring` phải nhìn rõ trên mọi nền |

Mọi cặp chữ/nền đạt tương phản ≥ 4.5:1 (chữ thường) và ≥ 3:1 (chữ lớn, viền ô nhập, icon mang nghĩa). Chế độ tối (dark mode): **chưa bắt buộc**; nếu làm thì định nghĩa đủ token cho cả hai chế độ.

### Chữ
- Font hỗ trợ đầy đủ dấu tiếng Việt: **Be Vietnam Pro** (thiết kế cho tiếng Việt) hoặc **Inter**. Tải qua `next/font`.
- Thang chữ: `text-xs` 12 · `text-sm` 14 · `text-base` 16 (mặc định, ô nhập không nhỏ hơn 16 px để Safari iOS không tự phóng to) · `text-lg` 18 · `text-xl` 20 · `text-2xl` 24 · `text-3xl` 30 (số dư nổi bật).
- **Số tiền dùng `tabular-nums`** để các cột số thẳng hàng và con số không "nhảy" khi cập nhật.

### Khoảng cách, bo góc, đổ bóng
- Thang 4 px của Tailwind. Khoảng cách trong form `gap-4`; giữa các nhóm `gap-6`/`gap-8`.
- Bo góc theo `--radius` (mặc định 0.5rem); thẻ `rounded-lg`, nút và ô nhập `rounded-md`.
- Đổ bóng hạn chế: chỉ cho lớp nổi (dialog, dropdown, sheet).

### Breakpoint
Mặc định Tailwind: `sm` 640 · `md` 768 · `lg` 1024 · `xl` 1280. Web khách hàng thiết kế **mobile-first** từ 360 px; CMS thiết kế cho ≥ 1280 px, vẫn dùng được ở 1024 px.

### Kích thước chạm
Phần tử bấm được trên web khách hàng cao ≥ 44 px (`h-11`). CMS có thể dùng `h-9` (36 px) vì dùng chuột, nhưng không nhỏ hơn 24 px.

## 3. Bảng chọn component (shadcn/ui)

| Nhu cầu | Component | Không dùng |
|---|---|---|
| Form và validation | `Form` (react-hook-form + Zod, dùng chung schema với server) | Tự quản lý state lỗi từng ô |
| Ô nhập tiền | `Input` + bộ định dạng tiền dùng chung (`MoneyInput`, component dự án) | `type="number"` (cho phép `e`, dấu trừ, cuộn chuột làm đổi số) |
| OTP | `InputOTP` | 6 ô `Input` rời |
| Xác nhận hành động không hoàn tác được | `AlertDialog` | `Dialog` thường, `window.confirm` |
| Nội dung phụ trên mobile | `Sheet` (từ dưới lên) | Trang mới cho nội dung ngắn |
| Thông báo ngắn sau thao tác | `Sonner` (toast) | Toast cho lỗi cần hành động (dùng `Alert` tại chỗ) |
| Thông báo tại chỗ | `Alert` | |
| Trạng thái | `Badge` + icon + chữ | Chỉ chấm màu |
| Khung chờ | `Skeleton` | Spinner toàn trang |
| Bảng dữ liệu (CMS) | `Table` + TanStack Table (DataTable) | Bảng tự dựng |
| Chọn ngày | `Calendar` + `Popover` (DatePicker) | |
| Tab, menu, tooltip | `Tabs`, `DropdownMenu`, `Tooltip` | Tooltip chứa thông tin bắt buộc phải đọc |
| Điều hướng CMS | `Sidebar`, `Breadcrumb` | |

Component dự án (bọc từ shadcn, dùng lại ở mọi nơi):
- `MoneyInput`, `MoneyText`
- `MaskedValue` (dữ liệu che, có nút hiện trong CMS)
- `StatusBadge` (trạng thái lệnh)
- `CopyableId`
- `EmptyState`, `ErrorState`

## 4. Trạng thái lệnh hiển thị thống nhất

| Trạng thái hệ thống | Nhãn | Màu token | Icon |
|---|---|---|---|
| `SUCCEEDED` | Thành công | `success` | check |
| `FAILED` | Thất bại | `destructive` | x |
| `PROCESSING`, `UNKNOWN` | Đang xử lý | `warning` | clock |

`UNKNOWN` **không bao giờ** hiển thị là "Thất bại" hay "Không xác định": với khách hàng, hai trạng thái này đều là "Đang xử lý".
