# Format `docs/srs/index.md`

```markdown
# SRS — Danh mục tính năng

- Nguồn: docs/requirements.md v0.2 · docs/design/2026-11-04-ewallet-design.md
- Cập nhật: <yyyy-mm-dd> · Phiên bản index: <n>
- Phạm vi: Giai đoạn 1

## 1. Tóm tắt phủ yêu cầu

| Tiền tố | Tổng | Gán tính năng | Cross-cutting | Ngoài phạm vi | Chưa gán |
|---|---|---|---|---|---|
| FR-ID | 8 | 8 | 0 | 0 | 0 |
| ... | | | | | |
| **Cộng** | N | | | | **0** |

Số tính năng: X · Số cross-cutting: Y

## 2. Danh mục tính năng

| ID | Tính năng | Actor | Yêu cầu sở hữu | Liên quan | Ưu tiên | Phụ thuộc | Dịch vụ (thiết kế) | Spec |
|---|---|---|---|---|---|---|---|---|
| F-PAY-02 | Chuyển tiền ví–ví | Khách hàng | FR-P2P-01..04 | FR-LIM-01..05, FR-FEE-01..03; CC-MONEY | M | F-ACC-01, F-LMT-01 | payment, ledger, limit, fee | Chưa viết |

Trạng thái spec: `Chưa viết` · `Draft` · `In review` · `Approved` · `Cần cập nhật`.
Khi spec đã có, ô "Spec" là liên kết tới `features/<ID>-<slug>.md` kèm trạng thái.

## 3. Cross-cutting

| ID | Chủ đề | Yêu cầu | Áp dụng cho |
|---|---|---|---|
| CC-MONEY | Tính đúng của tiền | NFR-C-01..05 | Mọi tính năng ghi tiền |
| CC-SEC | Bảo mật | NFR-S-01..06 | Tất cả |

Mỗi cross-cutting nên có một file `docs/srs/cross-cutting/<ID>-<slug>.md` (viết bằng cùng template, bỏ các mục không áp dụng).

## 4. Ngoài phạm vi / để sau

| Yêu cầu / Tính năng | Lý do | Xem lại khi |
|---|---|---|

## 5. Thứ tự viết đề xuất

| Làn | Tính năng | Lý do |
|---|---|---|
| 1 (nền) | F-ACC-01, F-ACC-02 | Các tính năng sau dùng thuật ngữ và quy tắc ở đây |

## 6. Quyết định gom nhóm cần xác nhận

| # | Quyết định | Phương án khác |
|---|---|---|

## 7. Câu hỏi mở
| # | Câu hỏi | Ảnh hưởng tới |
|---|---|---|

## 8. Lịch sử thay đổi
| Ngày | Phiên bản | Thay đổi |
|---|---|---|
```

Ghi chú:
- Dùng khoảng `FR-P2P-01..04` cho dải liền; dải không liền thì liệt kê từng ID. Khi kiểm tra phủ, khai triển khoảng ra từng ID để đếm.
- Cột "Dịch vụ" lấy từ design doc; nếu chưa có design thì để `—`.

## Ví dụ gom nhóm (minh hoạ, dự án ví điện tử; không phải khuôn bắt buộc)

Từ `docs/requirements.md` v0.2, một cách gom hợp lý:

| Tính năng | Yêu cầu sở hữu (rút gọn) | Vì sao gom như vậy |
|---|---|---|
| Đăng ký & đăng nhập | FR-ID-01, 05, 06 | Cùng actor khách, cùng vòng đời tài khoản đăng nhập |
| eKYC & cấp định danh | FR-ID-02, 03, 04, 07; FR-KYC-01..03 | Cùng luồng xác minh danh tính và phân cấp; FR-ID chứa nhiều hơn một tính năng |
| Đóng ví | FR-ID-08 | Vòng đời riêng, nhiều điều kiện chặn |
| Liên kết ngân hàng | FR-LK-01..05 | Một luồng liên kết, huỷ liên kết |
| Nạp tiền | FR-TU-01..05 | Luồng tiền một chiều có đối tác ngoài |
| Rút tiền | FR-WD-01..03 | Khác nạp ở tạm giữ tiền và phí |
| Chuyển tiền ví–ví | FR-P2P-01..04 | Giao dịch nội bộ, một DB transaction |
| Lịch sử, thông báo, tra soát (khách) | FR-HS-01..06 | Cùng nhóm "xem lại": có thể tách "Tra soát" nếu muốn |
| Hạn mức & phí (cấu hình) | FR-LIM-01..05, FR-FEE-01..04 (phần cấu hình) | Actor là nhân viên, vòng đời cấu hình riêng; khách chỉ xem kết quả |
| Back-office: vận hành tài khoản | FR-BO-02..04, 08 | Actor nhân viên, cùng đối tượng là khách/ví |
| Back-office: phê duyệt, audit | FR-BO-05, 12 | Cơ chế dùng chung (maker–checker) |
| Đối soát & tài khoản đảm bảo | FR-BO-07, 13; IR-09, IR-10 | Cùng mục tiêu: tiền khớp ngân hàng |
| Tích hợp ngân hàng | IR-01..08, IR-11..14 | Năng lực kỹ thuật riêng, nhiều tính năng tiền phụ thuộc |
| Cross-cutting | NFR-C-*, NFR-S-*, NFR-L-*, NFR-O-*, NFR-UX-* | Áp dụng cho nhiều tính năng |

Đây là gợi ý để hình dung độ chi tiết. Khi chạy thật, đọc nội dung từng yêu cầu rồi quyết định.
