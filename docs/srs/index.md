# SRS — Danh mục tính năng EWallet

- Nguồn: [requirements.md](../requirements.md) v0.2 · [design](../design/2026-11-04-ewallet-design.md) v0.3
- Cập nhật: 2026-11-04 · Phiên bản index: 1
- Phạm vi: Giai đoạn 1 (B2C + back-office, kênh Web)

## 1. Tóm tắt phủ yêu cầu

| Tiền tố | Tổng | Gán tính năng | Cross-cutting | Ngoài phạm vi | Chưa gán |
|---|---|---|---|---|---|
| FR-ID | 8 | 8 | 0 | 0 | 0 |
| FR-LK | 5 | 5 | 0 | 0 | 0 |
| FR-TU | 5 | 5 | 0 | 0 | 0 |
| FR-WD | 3 | 3 | 0 | 0 | 0 |
| FR-P2P | 4 | 4 | 0 | 0 | 0 |
| FR-HS | 6 | 5 | 0 | 1 | 0 |
| FR-BO | 13 | 13 | 0 | 0 | 0 |
| FR-LIM | 5 | 5 | 0 | 0 | 0 |
| FR-FEE | 4 | 4 | 0 | 0 | 0 |
| FR-KYC | 3 | 3 | 0 | 0 | 0 |
| IR | 14 | 14 | 0 | 0 | 0 |
| PR | 7 | 0 | 7 | 0 | 0 |
| NFR-P | 4 | 2 | 2 | 0 | 0 |
| NFR-A | 4 | 0 | 4 | 0 | 0 |
| NFR-C | 5 | 4 | 1 | 0 | 0 |
| NFR-S | 6 | 0 | 6 | 0 | 0 |
| NFR-L | 7 | 3 | 4 | 0 | 0 |
| NFR-O | 4 | 0 | 4 | 0 | 0 |
| NFR-UX | 3 | 0 | 3 | 0 | 0 |
| **Cộng** | **110** | **78** | **31** | **1** | **0** |

Số tính năng: 24 · Số cross-cutting: 8

## 2. Danh mục tính năng

Ưu tiên: yêu cầu phi chức năng không có cột ưu tiên, coi là `M` (xem Q1).
Trạng thái spec: `Chưa viết` · `Draft` · `In review` · `Approved` · `Cần cập nhật`.

| ID | Tính năng | Actor | Yêu cầu sở hữu | Liên quan | Ưu tiên | Phụ thuộc | Dịch vụ (thiết kế) | Spec |
|---|---|---|---|---|---|---|---|---|
| F-ACC-01 | Đăng ký & đăng nhập | Khách | FR-ID-01, 05, 06 | FR-ID-04; NFR-S-01..04; NFR-L-03 | M | F-NTF-01 (chỉ interface gửi OTP), F-ACC-02 (chỉ UC-06 đổi SĐT) | identity, notification | [Draft](features/F-ACC-01-dang-ky-dang-nhap.md) |
| F-ACC-02 | Định danh eKYC & phân cấp tài khoản | Khách, Nhân viên rủi ro | FR-ID-02, 03, 04, 07; FR-KYC-01..03; FR-BO-08 | NFR-L-02, 03 | M | F-ACC-01, F-AML-01 | kyc, aml, identity | Chưa viết |
| F-ACC-03 | Đóng ví | Khách | FR-ID-08 | FR-WD-01 | M | F-ACC-01, F-FIN-01, F-PAY-04 | wallet, ledger, payment | Chưa viết |
| F-BNK-01 | Liên kết tài khoản ngân hàng | Khách | FR-LK-01..04 | FR-LK-02; IR-06 | M | F-ACC-02, F-BNK-02 | bank-gateway, kyc | Chưa viết |
| F-BNK-02 | Cổng tích hợp ngân hàng (`BankGateway`) | Hệ thống | FR-LK-05; IR-01..08, 11, 12, 13 | CC-PROV | M | — | bank-gateway | Chưa viết |
| F-PAY-01 | Nạp tiền | Khách | FR-TU-01..05 | FR-LIM, FR-FEE; CC-MONEY | M | F-BNK-01, F-BNK-02, F-LMT-01, F-FEE-01, F-FIN-01 | payment, bank-gateway, ledger, limit, fee | Chưa viết |
| F-PAY-02 | Rút tiền | Khách | FR-WD-01..03 | FR-LIM, FR-FEE; CC-MONEY | M | F-BNK-01, F-BNK-02, F-LMT-01, F-FEE-01, F-FIN-01 | payment, bank-gateway, ledger, limit, fee | Chưa viết |
| F-PAY-03 | Chuyển tiền ví–ví | Khách | FR-P2P-01..04 | FR-LIM, FR-FEE-01..03; NFR-L-04; CC-MONEY | M | F-ACC-02, F-LMT-01, F-FEE-01, F-FIN-01 | payment, ledger, limit, fee, identity, wallet | [Draft](features/F-PAY-03-chuyen-tien-vi-vi.md) |
| F-PAY-04 | Số dư & lịch sử giao dịch | Khách | FR-HS-01, 02, 03; NFR-P-03 | NFR-P-02 | M | F-FIN-01 | payment, wallet, ledger | Chưa viết |
| F-NTF-01 | Thông báo biến động | Khách | FR-HS-04 | PR-* (`NotificationSender`) | M | F-ACC-01 | notification | Chưa viết |
| F-DSP-01 | Tra soát & khiếu nại | Khách, CSKH | FR-HS-05; FR-BO-06 | NFR-L-01 | M | F-PAY-04, F-BO-01 | dispute | Chưa viết |
| F-LMT-01 | Hạn mức giao dịch | Hệ thống, Khách (xem) | FR-LIM-01..05 | FR-ID-04; NFR-L-01 | M | F-ACC-02, F-FIN-01 | limit | Chưa viết |
| F-FEE-01 | Biểu phí | Hệ thống, Khách (xem) | FR-FEE-01..04 | NFR-C-05 | M | F-FIN-01 | fee | Chưa viết |
| F-FIN-01 | Sổ cái kép & số dư | Hệ thống | NFR-C-01, 02, 04, 05; NFR-P-01a; IR-14 | CC-MONEY | M | — | ledger | Chưa viết |
| F-FIN-02 | Tài khoản đảm bảo & thanh khoản | Nhân viên tài chính | FR-BO-13; IR-10 | NFR-O-03 | M | F-FIN-01, F-BNK-02 | recon, ledger, bank-gateway | Chưa viết |
| F-REC-01 | Đối soát ngân hàng | Nhân viên đối soát | FR-BO-07; IR-09 | NFR-C-04 | M | F-BNK-02, F-FIN-01, F-BO-03 | recon, bank-gateway, ledger | Chưa viết |
| F-AML-01 | Sàng lọc & giám sát AML | Nhân viên rủi ro | NFR-L-02 | FR-ID-02 | M | F-ACC-02 | aml | Chưa viết |
| F-CMP-01 | Bảo vệ dữ liệu cá nhân | Khách, Hệ thống | NFR-L-03, 07 | NFR-L-05 | M | F-ACC-01 | identity và các service chứa dữ liệu cá nhân | Chưa viết |
| F-BO-01 | Truy cập & phân quyền back-office | Nhân viên | FR-BO-01, 02 | NFR-S-* | M | — | admin-gateway, IdP | Chưa viết |
| F-BO-02 | Tra cứu & vận hành tài khoản khách | CSKH, Vận hành, Rủi ro | FR-BO-03, 04 | FR-BO-05 | M | F-BO-01, F-BO-03, F-FIN-01, F-PAY-04 | wallet, identity, kyc, payment | Chưa viết |
| F-BO-03 | Phê duyệt maker–checker | Nhân viên | FR-BO-05 | — | M | F-BO-01 | approval | Chưa viết |
| F-BO-04 | Cấu hình tham số vận hành | Admin, Rủi ro, Tài chính | FR-BO-09 | FR-LIM, FR-FEE | M | F-BO-03, F-LMT-01, F-FEE-01 | limit, fee, approval | Chưa viết |
| F-BO-05 | Audit log | Nhân viên, Kiểm toán | FR-BO-12 | NFR-S-* | M | F-BO-01 | audit | Chưa viết |
| F-BO-06 | Báo cáo vận hành & báo cáo NHNN | Vận hành, Tài chính | FR-BO-10, 11 | NFR-L-01 | M | F-BO-01, F-PAY-04 | recon | Chưa viết |

## 3. Cross-cutting

Mỗi cross-cutting sẽ có một file `cross-cutting/<ID>-<slug>.md` (viết bằng cùng template, bỏ mục không áp dụng). Chưa viết.

| ID | Chủ đề | Yêu cầu | Áp dụng cho |
|---|---|---|---|
| CC-PERF | Hiệu năng & quy mô | NFR-P-01, 02 | Mọi tính năng có API |
| CC-AVAIL | Sẵn sàng & phục hồi | NFR-A-01..04 | Tất cả |
| CC-MONEY | Xử lý tiền (idempotency) | NFR-C-03 | F-PAY-01..03, F-BO-02, F-BO-03 và mọi API ghi tiền |
| CC-SEC | Bảo mật | NFR-S-01..06 | Tất cả |
| CC-COMP | Tuân thủ (NHNN, xác thực giao dịch, lưu trữ) | NFR-L-01, 04, 05, 06 | Tính năng tiền, KYC, dữ liệu |
| CC-OBS | Quan sát & vận hành | NFR-O-01..04 | Tất cả |
| CC-UX | Trải nghiệm Web | NFR-UX-01..03 | Mọi màn hình |
| CC-PROV | Quy tắc chung cho provider bên thứ 3 | PR-01..07 | F-BNK-02, F-ACC-02, F-NTF-01, F-AML-01 |

## 4. Ngoài phạm vi / để sau

| Yêu cầu | Lý do | Xem lại khi |
|---|---|---|
| FR-HS-06 (xuất sao kê PDF) | Mức S; design ghi nhận để giai đoạn sau (R8) | Sau go-live giai đoạn 1 |

## 5. Thứ tự viết đề xuất

Lý do: spec tính năng sau dùng thuật ngữ và quy tắc đã chốt ở tính năng trước.

| Làn | Tính năng | Lý do |
|---|---|---|
| 1 (nền) | F-FIN-01, F-ACC-01, F-BO-01, F-BO-03, F-BO-05 | Sổ cái, danh tính, phân quyền, phê duyệt và audit: mọi tính năng khác dựa vào |
| 2 | F-ACC-02, F-AML-01, F-LMT-01, F-FEE-01, F-NTF-01, F-CMP-01, F-BNK-02 | Quy tắc dùng chung cho các luồng tiền |
| 3 (luồng tiền) | F-PAY-03, F-PAY-04, F-BNK-01, F-PAY-01, F-PAY-02, F-ACC-03 | Bắt đầu bằng P2P (không có đối tác ngoài) rồi đến nạp/rút |
| 4 (vận hành) | F-FIN-02, F-REC-01, F-DSP-01, F-BO-02, F-BO-04, F-BO-06 | Phụ thuộc dữ liệu và quy tắc của các làn trước |
| Cross-cutting | CC-MONEY, CC-SEC, CC-COMP, CC-PROV trước; số còn lại sau | Các tính năng tham chiếu CC-* |

## 6. Quyết định gom nhóm cần xác nhận

| # | Quyết định | Phương án khác |
|---|---|---|
| G1 | FR-P2P-04 (xác thực tăng cường) thuộc F-PAY-03 vì nằm trong yêu cầu P2P | Tách `F-ACC-04 Xác thực tăng cường` dùng chung cho P2P, rút, đổi SĐT |
| G2 | F-DSP-01 gồm cả phía khách (FR-HS-05) và phía nhân viên (FR-BO-06): cùng một quy trình | Tách hai tính năng |
| G3 | FR-BO-08 (duyệt eKYC thủ công) thuộc F-ACC-02 | Tính năng back-office riêng |
| G4 | FR-ID-03 (chặn trùng CCCD) thuộc F-ACC-02 vì kiểm tra khi eKYC | Thuộc F-ACC-01 (đăng ký) |
| G5 | NFR-C-01, 02, 04, 05 và NFR-P-01a thuộc F-FIN-01 (sổ cái) vì mô tả hành vi của sổ cái; chỉ NFR-C-03 (idempotency) là cross-cutting | Gom hết NFR-C-* vào CC-MONEY |
| G6 | FR-BO-09 thuộc F-BO-04 (quy trình cấu hình trên CMS); ngữ nghĩa hạn mức/phí nằm ở F-LMT-01 và F-FEE-01 | Chia FR-BO-09 vào F-LMT-01 và F-FEE-01 |
| G7 | FR-LK-05 (hỗ trợ nhiều ngân hàng) thuộc F-BNK-02 | Thuộc F-BNK-01 |

## 7. Câu hỏi mở

Danh sách đầy đủ của mọi tài liệu, kèm ID chung, người quyết định và trạng thái: [open-questions.md](../open-questions.md). Bảng dưới chỉ giữ câu hỏi phát sinh khi lập index.

| # | Câu hỏi | Ảnh hưởng tới |
|---|---|---|
| Q1 | Yêu cầu phi chức năng không có ưu tiên: xác nhận coi là `M`? | Cột ưu tiên của F-FIN-01, F-AML-01, F-CMP-01 |
| Q2 | FR-HS-06 (sao kê PDF) chốt để giai đoạn sau? | Mục 4 |
| Q3 | FR-BO-11 (báo cáo NHNN): chưa có mẫu và tần suất (Pháp chế) | F-BO-06 |
| Q4 | `FR-ID-03` có điều kiện "khi quy định yêu cầu" ⚖️: quy định cụ thể là gì? | F-ACC-02 |
| F-PAY-03.Q1..Q10 | Xem [spec P2P mục 9](features/F-PAY-03-chuyen-tien-vi-vi.md): ngưỡng xác thực tăng cường ⚖️, cấp KYC tối thiểu, cách tính hạn mức ra/vào ⚖️, quy tắc che tên, lộ thông tin dò tài khoản, thông báo cho người nhận, mã lỗi khi ví bị khoá... | F-PAY-03; một số câu (cấp KYC, hạn mức ra/vào) ảnh hưởng F-LMT-01 |

## 8. Lịch sử thay đổi

| Ngày | Phiên bản | Thay đổi |
|---|---|---|
| 2026-11-04 | 1 | Tạo mới: 24 tính năng, 8 cross-cutting, 110 yêu cầu (78 gán / 31 cross-cutting / 1 ngoài phạm vi) |
| 2026-11-04 | 1 | F-PAY-03 có spec Draft; thêm câu hỏi mở F-PAY-03.Q1..Q10 |
| 2026-11-04 | 2 | F-ACC-01 có spec Draft (câu hỏi mở F-ACC-01.Q1..Q15 → OQ-030..044). Sửa cột "liên quan" của F-ACC-01: bỏ `FR-LIM` (hạn mức giao dịch, không liên quan đăng nhập), thêm `FR-ID-04`, `NFR-S-01..04`, `NFR-L-03`. Ghi phụ thuộc vòng F-ACC-01 ↔ F-ACC-02 (chỉ ở UC-06) và F-ACC-01 ↔ F-NTF-01 (chỉ interface); cơ chế challenge OTP dùng chung nằm ở F-ACC-01 |
