# Template đặc tả tính năng (IEEE 29148 rút gọn)

Dùng đúng thứ tự mục. Mục không áp dụng: ghi "Không áp dụng — <lý do>". Bảng và ID giữ nguyên định dạng để các skill khác (srs-test-cases) đọc được.

**Quy ước ID** (tiền tố là ID tính năng, ví dụ `F-PAY-03`):
- `F-PAY-03.UC-01` use case · `.FR-01` yêu cầu chức năng · `.BR-01` quy tắc nghiệp vụ · `.ST-01` chuyển trạng thái · `.EV-01` đầu ra phụ (thông báo, event, audit) · `.NFR-01` phi chức năng riêng · `.AC-01` tiêu chí chấp nhận
- Mọi mục có thể test đều cần ID, kể cả dòng của bảng trạng thái và bảng đầu ra phụ; nếu không, test case không có gì để truy vết về ngoài số mục.
- Số tăng dần, **không bao giờ đổi hay dùng lại**.

````markdown
# F-PAY-03 — <Tên tính năng>

| | |
|---|---|
| Trạng thái | Draft \| In review \| Approved |
| Phiên bản | 0.1 |
| Ngày | <yyyy-mm-dd> |
| Yêu cầu nguồn | FR-P2P-01..04 (docs/requirements.md) |
| Cross-cutting áp dụng | CC-MONEY, CC-SEC, CC-AUDIT |
| Tài liệu thiết kế | docs/design/... §6.3; docs/design/...-api.md §2 |
| Phụ thuộc | F-ACC-01, F-LMT-01 |

## 1. Giới thiệu
### 1.1 Mục đích
Một đến ba câu: tính năng giúp ai làm được gì.
### 1.2 Phạm vi
- Trong phạm vi: ...
- Ngoài phạm vi: ... (và tính năng nào đảm nhiệm, nếu có)
### 1.3 Định nghĩa & viết tắt
| Thuật ngữ | Định nghĩa |
### 1.4 Tài liệu tham chiếu
Danh sách tài liệu, kèm mục.

## 2. Mô tả tổng quan
### 2.1 Bối cảnh & giá trị
### 2.2 Actor & quyền
| Actor | Mô tả | Được phép |
### 2.3 Phụ thuộc
| Loại | Tên | Vai trò |
| Tính năng / Hệ thống ngoài / Cấu hình | | |
### 2.4 Giả định & ràng buộc
Mỗi dòng gắn nhãn `[Giả định]` / `[Ràng buộc]` / `[Cần xác nhận Q#]`.

## 3. Yêu cầu chức năng
### 3.1 Use case
Bảng tóm tắt:
| ID | Tên | Actor | Mục tiêu |

Với mỗi use case:
#### F-PAY-03.UC-01 — <Tên>
- **Tiền điều kiện:** ...
- **Hậu điều kiện (thành công):** ...
- **Hậu điều kiện (thất bại):** ... (trạng thái hệ thống sau khi lỗi: tiền, hạn mức, bản ghi)
- **Luồng chính:**
  1. Actor ...
  2. Hệ thống ...
- **Luồng thay thế:** `A1` (tại bước 3) Nếu ... thì ...
- **Luồng lỗi:** `E1` (tại bước 5) Nếu ... thì hệ thống ... (trỏ tới mã lỗi mục 6)

### 3.2 Yêu cầu chức năng
| ID | Yêu cầu | Ưu tiên | Nguồn |
|---|---|---|---|
| F-PAY-03.FR-01 | Hệ thống phải ... | M | FR-P2P-01 |
| F-PAY-03.FR-02 | Khi ..., hệ thống phải ... | M | FR-P2P-02; [Thiết kế §6.3] |

### 3.3 Quy tắc nghiệp vụ
| ID | Quy tắc | Tham số cấu hình | Nguồn |
|---|---|---|---|
| F-PAY-03.BR-01 | Phí = ... (công thức, làm tròn) | `fee.p2p.*` | FR-FEE-01 |

Công thức ghi tường minh, kèm ví dụ số:
> Ví dụ: amount = 1.000.001, percent = 0,1%, min = 1.000 → phí = max(1.000, ⌈1.000,001⌉) = 1.001.

### 3.4 Trạng thái & chuyển trạng thái
(Nếu đối tượng có vòng đời.) Bảng (có thể kèm sơ đồ Mermaid); liệt kê chuyển hợp lệ **và** một dòng cho các chuyển bị cấm.
| ID | Từ | Sự kiện / Điều kiện | Sang | Ghi chú |
|---|---|---|---|---|
| F-PAY-03.ST-01 | (chưa có) | ... | `PROCESSING` | FR-19 |
| F-PAY-03.ST-0n | `SUCCEEDED`, `FAILED` | bất kỳ | (bị cấm) | Trạng thái cuối |

### 3.5 Dữ liệu
| Trường | Kiểu | Bắt buộc | Ràng buộc / Validation | Ví dụ |
|---|---|---|---|---|
| amount | số nguyên (VND) | Có | ≥ 1; ≤ hạn mức còn lại | 100000 |

Ghi rõ: định dạng, độ dài, miền giá trị, xử lý khoảng trắng/Unicode, dữ liệu nhạy cảm (che/mã hoá/không log).

### 3.6 Đầu ra phụ: thông báo, sự kiện, audit
| ID | Sự kiện | Kênh / Đích | Nội dung / Trường | Điều kiện |
|---|---|---|---|---|
| F-PAY-03.EV-01 | Chuyển tiền thành công | Thông báo người gửi | ... | |

## 4. Giao diện ngoài
### 4.1 Giao diện người dùng
Mỗi màn hình: mục đích, thành phần, trạng thái (đang tải, rỗng, lỗi, thành công, đang xử lý), thông điệp, hành vi khi thao tác lặp (bấm hai lần), khả năng truy cập.
### 4.2 API
| Method | Path | Idempotency | Auth | Request chính | Response / Mã lỗi |
Liên kết tới tài liệu API; không sao chép toàn bộ schema, chỉ ghi các điểm có hành vi nghiệp vụ.
### 4.3 Hệ thống ngoài / provider
| Hệ thống | Thao tác | Kết quả có thể | Xử lý từng kết quả |

## 5. Yêu cầu phi chức năng riêng của tính năng
| ID | Loại | Yêu cầu (đo được) | Nguồn |
|---|---|---|---|
| F-PAY-03.NFR-01 | Hiệu năng | p99 của POST /transfers < 1 s ở 150 TPS | NFR-P-02 |
Các NFR chung tham chiếu cross-cutting, không sao chép.

## 6. Danh mục lỗi & thông điệp
| Mã lỗi | Điều kiện | HTTP | Thông điệp cho người dùng (vi) | Thử lại được? |

## 7. Tiêu chí chấp nhận
| ID | Phủ | Given | When | Then |
|---|---|---|---|---|
| F-PAY-03.AC-01 | FR-01, BR-01 | Ví A ACTIVE, số dư 1.000.000, phí P2P = 0 | A chuyển 100.000 cho B | Lệnh SUCCEEDED; số dư A 900.000; số dư B tăng 100.000 |

## 8. Ma trận truy vết
| Yêu cầu nguồn | FR | BR | AC | Ghi chú |
|---|---|---|---|---|
| FR-P2P-01 | FR-01, FR-02 | BR-01 | AC-01, AC-02 | |
| FR-P2P-03 | — | — | — | Hoãn (mức S): lý do |

Mọi yêu cầu sở hữu phải có mặt; dòng không phủ phải có lý do.

## 9. Câu hỏi mở & giả định cần xác nhận
| # | Câu hỏi / Giả định | Ảnh hưởng (ID) | Ai quyết định |

## 10. Lịch sử thay đổi
| Phiên bản | Ngày | Thay đổi (ID) | Lý do |
````
