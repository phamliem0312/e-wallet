# Design system EWallet

- Phiên bản: 0.1 (Draft) · Ngày: 2026-11-04
- Nền: Tailwind CSS + shadcn/ui (Radix). Áp dụng cho `apps/web` (khách hàng) và `apps/cms` (back-office).
- **Màu thương hiệu chưa chốt**: các giá trị màu dưới đây là tạm, chỉ để dev dựng được giao diện. Khi có nhận diện thương hiệu, chỉ cần đổi giá trị token.

## 1. Nguyên tắc
1. **Rõ ràng hơn đẹp:** người dùng phải biết chắc mình chuyển bao nhiêu, cho ai, kết quả ra sao.
2. **Một cách cho một việc:** cùng loại thao tác thì cùng component, cùng vị trí, cùng câu chữ.
3. **Chỉ dùng token ngữ nghĩa:** `bg-primary`, `text-destructive`; không dùng `bg-blue-600`.

## 2. Token

### Màu (CSS variables theo quy ước shadcn/ui)
| Token | Dùng cho | Giá trị tạm (light) |
|---|---|---|
| `--background` / `--foreground` | Nền, chữ chính | trắng / slate-900 |
| `--primary` / `--primary-foreground` | Nút chính, liên kết | *tạm* blue-700 / trắng |
| `--secondary`, `--muted`, `--accent` | Nút phụ, nền phụ, hover | slate-100 và tương đương |
| `--destructive` | Lỗi, hành động không hoàn tác được | red-700 |
| `--success` *(bổ sung)* | Thành công, tiền vào | green-700 |
| `--warning` *(bổ sung)* | Đang xử lý, cần chú ý, banner môi trường | amber-700 (chữ) / amber-50 (nền) |
| `--info` *(bổ sung)* | Thông tin trung tính | sky-700 |
| `--border`, `--input`, `--ring` | Viền, ô nhập, vòng focus | slate-300, slate-400, blue-600 |

- Mọi cặp chữ/nền: tương phản ≥ 4.5:1 cho chữ thường; ≥ 3:1 cho chữ lớn, viền ô nhập và icon mang nghĩa. Kiểm lại khi đổi màu thương hiệu.
- Dark mode: chưa làm ở giai đoạn 1.

### Chữ
- Font: **Be Vietnam Pro** (tải qua `next/font`), dự phòng `system-ui`.
- Thang: 12 · 14 · **16 (mặc định; ô nhập không nhỏ hơn 16 px)** · 18 · 20 · 24 · 30 (số dư nổi bật).
- **Số tiền luôn dùng `tabular-nums`.**

### Khoảng cách, bo góc, đổ bóng
- Thang 4 px. Trong form `gap-4`; giữa nhóm `gap-6`.
- `--radius: 0.5rem`; thẻ `rounded-lg`, nút và ô nhập `rounded-md`.
- Đổ bóng chỉ cho dialog, sheet, dropdown.

### Breakpoint & kích thước chạm
- Web khách hàng: mobile-first từ 360 px; nội dung tối đa `max-w-md` (448 px) và căn giữa trên màn hình lớn.
- CMS: ≥ 1280 px, dùng được ở 1024 px.
- Phần tử bấm được trên web khách hàng cao ≥ 44 px (`h-11`); CMS ≥ 36 px (`h-9`).

## 3. Component

### Dùng từ shadcn/ui
| Nhu cầu | Component |
|---|---|
| Form + validation | `Form` (react-hook-form + Zod, dùng chung schema với server action) |
| OTP | `InputOTP` |
| Xác nhận hành động không hoàn tác được | `AlertDialog` |
| Nội dung phụ trên mobile | `Sheet` (side `bottom`) |
| Thông báo ngắn | `Sonner` (toast) — không dùng cho lỗi cần hành động |
| Thông báo tại chỗ | `Alert` |
| Khung chờ | `Skeleton` |
| Bảng dữ liệu CMS | `Table` + TanStack Table |
| Khác | `Button`, `Input`, `Card`, `Badge`, `Tabs`, `Tooltip`, `DropdownMenu`, `Calendar`, `Popover`, `Sidebar`, `Breadcrumb`, `Separator` |

### Component dự án (thư mục `packages/ui` hoặc `components/` dùng chung)
| Component | Chức năng | Quy tắc |
|---|---|---|
| `MoneyInput` | Ô nhập tiền VND | `inputMode="numeric"`, tự thêm dấu chấm nghìn, không thập phân, hậu tố `₫`, dán "1.000.000" hay "1000000" đều đúng; trả về số nguyên. **Không dùng `type="number"`** |
| `MoneyText` | Hiển thị tiền | Theo định dạng ở §5; prop `sign` cho `+`/`−`; `tabular-nums` |
| `StatusBadge` | Trạng thái lệnh | Theo bảng §4 |
| `CopyableId` | Mã giao dịch | Rút gọn + nút sao chép + toast "Đã sao chép" |
| `MaskedValue` | Dữ liệu đã che | Hiển thị nguyên văn từ server; CMS có nút "Hiện" theo quyền |
| `EmptyState`, `ErrorState` | Trạng thái rỗng/lỗi | Icon + tiêu đề + mô tả + hành động |
| `SubmitButton` | Nút gửi lệnh tiền | Khoá ngay khi bấm, hiện spinner + chữ "Đang xử lý…", không nhận click tiếp đến khi có kết quả |

## 4. Trạng thái lệnh
| Trạng thái hệ thống | Nhãn (khách hàng) | Token | Icon (lucide) |
|---|---|---|---|
| `SUCCEEDED` | Thành công | `success` | `CircleCheck` |
| `FAILED` | Thất bại | `destructive` | `CircleX` |
| `PROCESSING`, `UNKNOWN` | Đang xử lý | `warning` | `Clock` |

`UNKNOWN` không bao giờ hiển thị là "Thất bại" hay "Không xác định".

## 5. Định dạng
| Loại | Định dạng | Ví dụ |
|---|---|---|
| Tiền | `Intl.NumberFormat('vi-VN')` + ` ₫` | `1.000.000 ₫` |
| Tiền vào / ra | `+` / `−` (dấu trừ U+2212) trước số | `+100.000 ₫`, `−101.000 ₫` |
| Ngày giờ (khách) | `dd/MM/yyyy HH:mm`, múi giờ `Asia/Ho_Chi_Minh` | `04/11/2026 14:30` |
| Ngày giờ (CMS) | `dd/MM/yyyy HH:mm:ss` | |
| SĐT hiển thị | nhóm 4-3-3 | `0900 000 002` |

Mọi định dạng đi qua hàm dùng chung trong `packages/format`; không tự ghép chuỗi trong component.

## 6. Lịch sử thay đổi
| Phiên bản | Ngày | Thay đổi |
|---|---|---|
| 0.1 | 2026-11-04 | Tạo mới khi thiết kế UI F-PAY-03 |
