# Quy tắc viết đặc tả

## 1. Viết một yêu cầu

**Cấu trúc:** `[Điều kiện kích hoạt], <chủ thể> phải/không được <hành vi> [trong điều kiện/ngưỡng đo được].`

Dùng các mẫu sau (kiểu EARS). Mẫu giúp câu có đủ ba thứ cần có: khi nào, ai làm gì, đến mức nào.

| Mẫu | Dùng khi | Ví dụ |
|---|---|---|
| Luôn đúng | Hành vi không phụ thuộc điều kiện | Hệ thống phải lưu số tiền dưới dạng số nguyên đơn vị đồng. |
| Theo sự kiện | Phản ứng với một sự kiện | **Khi** người gửi xác nhận chuyển tiền, hệ thống phải tạo đúng một lệnh ứng với `Idempotency-Key`. |
| Theo trạng thái | Chỉ áp dụng trong một trạng thái | **Trong khi** ví ở trạng thái `FROZEN`, hệ thống không được cho phép ghi nợ ví. |
| Xử lý lỗi | Điều kiện bất thường | **Nếu** ngân hàng không trả kết quả trong thời gian chờ, **thì** hệ thống phải đặt lệnh ở trạng thái `UNKNOWN` và giữ nguyên số tiền tạm giữ. |
| Tuỳ chọn | Chỉ khi một khả năng được bật | **Với** kênh SMS được bật, hệ thống phải gửi OTP qua SMS. |

**Từ chỉ mức độ bắt buộc** (dùng nhất quán):
- **phải / không được**: bắt buộc (Must).
- **nên**: khuyến nghị (Should).
- **có thể**: tuỳ chọn (Could).

Mỗi câu một hành vi. Câu có "và" nối hai hành vi độc lập thì tách thành hai yêu cầu, để mỗi yêu cầu truy vết và test riêng được.

## 2. Phép thử cho mọi câu

1. **Kiểm chứng được:** nghĩ ra được một test mà chỉ có kết quả đạt hoặc không đạt?
2. **Đơn nghĩa:** hai người đọc độc lập sẽ hiểu giống nhau?
3. **Cần thiết:** bỏ câu này đi thì có ai làm sai không? Nếu không, bỏ.
4. **Khả thi và nhất quán:** không mâu thuẫn với câu khác hay với spec đã có.
5. **Truy vết:** biết nó đến từ đâu (yêu cầu gốc / thiết kế / giả định)?

## 3. Từ mơ hồ cần tránh

Gặp từ trong cột trái thì thay bằng điều đo được.

| Từ / cụm | Thay bằng |
|---|---|
| nhanh, kịp thời, sớm, ngay lập tức | thời gian cụ thể: "trong vòng 3 giây", "p99 < 300 ms" |
| thân thiện, dễ dùng, trực quan | hành vi cụ thể: "hiển thị lý do từ chối bằng tiếng Việt kèm mã tra soát" |
| phù hợp, thích hợp, hợp lý, cần thiết, nếu cần | điều kiện chính xác: "nếu số tiền > `stepup.otp.threshold`" |
| đầy đủ, toàn bộ, đúng | liệt kê thứ gì |
| một số, nhiều, vài, v.v., vân vân, ... | danh sách đóng, hoặc con số |
| và/hoặc, hoặc tương tự | chọn một, hoặc tách thành hai yêu cầu có điều kiện |
| an toàn, bảo mật (đứng một mình) | cơ chế và tiêu chí: "mã hoá AES-256-GCM", "TLS 1.2+" |
| tối ưu, linh hoạt, mạnh mẽ | bỏ, hoặc tiêu chí đo được |
| thông thường, thường xuyên | tần suất: "mỗi 30 giây" |
| tương tự như, giống như (tham chiếu mơ hồ) | tham chiếu đích danh: "như F-PAY-01.FR-04" |
| ít nhất, tối đa (không có con số) | thêm con số hoặc tham số cấu hình |

## 4. Số, tiền, thời gian, cấu hình

- Số có **đơn vị**: "15 giây", "100.000 đồng", "5 lần".
- Tiền: số nguyên đơn vị đồng (VND). Ghi quy tắc làm tròn khi có phép nhân chia.
- Thời gian: nêu múi giờ khi quan trọng (kỳ ngày/tháng của hạn mức tính theo giờ Việt Nam).
- Ngưỡng chưa chốt hoặc có thể đổi → **tham số cấu hình** đặt tên theo dạng `domain.object.name` (ví dụ `limit.tier1.p2p.per_txn`) và ghi `[Cần xác nhận Q#]` nếu chưa có giá trị. Bảng cấu hình liệt kê tên, kiểu, giá trị mặc định (nếu đã chốt), ai được đổi.
- Con số liên quan pháp lý (⚖️): không bao giờ ghi như sự thật cứng. Ghi tham số cấu hình, kèm "Pháp chế xác nhận theo văn bản hiện hành".

## 5. "Cái gì" và "thế nào"

SRS nói hệ thống **phải làm gì và cư xử ra sao nhìn từ bên ngoài**. Chi tiết xây dựng thuộc design doc.

| Nên có trong SRS | Để ở design doc |
|---|---|
| Lệnh trùng `Idempotency-Key` phải trả lại kết quả của lệnh đầu tiên | Bảng `payment_order` có unique `(customer_id, idempotency_key)` |
| Khi ngân hàng timeout, lệnh ở `UNKNOWN`, tiền tạm giữ giữ nguyên | Resolver chạy mỗi 30 giây, backoff 1-2-5-15 phút |
| Mã lỗi `LIMIT_EXCEEDED` kèm hạn mức còn lại | Thứ tự lock giữa các bucket |

Ngoại lệ: khi chi tiết kỹ thuật **là yêu cầu** (ví dụ "mã hoá AES-256-GCM", "TLS 1.2+", "audit log không sửa xoá được"), thì ghi vào đặc tả vì nó kiểm chứng được và mang tính ràng buộc.

## 6. Nguồn và nhãn

Mỗi FR/BR có cột **Nguồn**:
- `FR-P2P-01`: yêu cầu gốc (hoặc nhiều ID, cách nhau dấu phẩy).
- `[Thiết kế §6.3]`: không có trong requirements nhưng design đã quyết; ghi tài liệu và mục.
- `[Giả định]`: hành vi tiêu chuẩn do người viết suy ra, cần người duyệt thấy.
- `[Cần xác nhận Q3]`: quyết định chưa có; luôn có dòng tương ứng trong mục 9.

Đặc tả mà không có nhãn nào là đáng ngờ: gần như không tài liệu nào hoàn toàn suy ra được từ requirements.

## 7. Nội dung bắt buộc cho tính năng chạm tới tiền

Tính năng nạp, rút, chuyển, hoàn, điều chỉnh, phong toả, phí hoặc hạn mức phải trả lời **tất cả** các câu dưới đây (viết thành FR/BR hoặc ghi "Không áp dụng — lý do"):

1. **Idempotency:** gửi lại cùng yêu cầu thì sao? Cùng khoá nhưng nội dung khác thì sao? Sau khi lệnh thất bại thì khách thử lại thế nào?
2. **Đồng thời:** hai thao tác cùng lúc lên cùng một ví/hạn mức thì kết quả ra sao? Số dư có thể âm không?
3. **Trạng thái & kết quả không chắc chắn:** các trạng thái của lệnh; khi đối tác không trả lời thì lệnh ở trạng thái nào, tiền ở đâu, khách thấy gì, ai chốt kết quả cuối cùng?
4. **Tiền nằm ở đâu ở mỗi bước:** số dư, tạm giữ, hạn mức đã dùng trước, trong và sau khi thành công, thất bại, không chắc chắn.
5. **Phí:** hiển thị trước khi xác nhận; phí đổi giữa lúc xem và lúc xác nhận thì sao; ai chịu; làm tròn.
6. **Hạn mức & xác thực tăng cường:** áp dụng tầng nào; vượt thì báo gì; khi nào cần OTP/sinh trắc học.
7. **Điều kiện của ví / người dùng:** trạng thái ví được phép, cấp KYC tối thiểu, liên kết ngân hàng hợp lệ và thuộc về chính chủ.
8. **Thông báo & lịch sử:** ai nhận thông báo gì khi nào; lệnh hiện trong lịch sử với thông tin nào.
9. **Audit & quyền:** thao tác nào ghi audit; ai được xem dữ liệu nhạy cảm; thao tác của nhân viên có cần maker–checker không.
10. **Đối soát:** lệnh này được đối chiếu với đối tác/sổ cái thế nào; lệch thì ai xử lý.
11. **Thứ tự ưu tiên lỗi:** khi một yêu cầu vi phạm nhiều điều kiện cùng lúc thì trả mã lỗi nào? Viết thành một quy tắc nghiệp vụ có thứ tự rõ ràng (định dạng → tra khoá idempotency → xác thực → trạng thái ví → người nhận → phí...). Không có quy tắc này, hai bản cài đặt đúng yêu cầu vẫn trả hai mã khác nhau và test sẽ chập chờn. Quan trọng nhất: tra khoá idempotency phải đứng trước mọi kiểm tra có thể thay đổi giữa hai lần gửi (OTP đã dùng, phí đổi, ví bị khoá), vì gửi lại sau khi thành công phải nhận lại kết quả cũ chứ không phải một lỗi mới.

## 8. Tiêu chí chấp nhận (AC)

- Dạng **Given / When / Then** với dữ liệu cụ thể và con số: "Ví A số dư 1.000.000, phí 1.000. Khi A chuyển 100.000 → số dư A = 899.000, số dư B tăng 100.000, doanh thu phí tăng 1.000".
- Một AC kiểm một hành vi. Tên AC nói được điều kiện và kết quả.
- Mỗi FR và BR có ít nhất một AC; AC ghi cột "Phủ" trỏ về FR/BR.
- Có đủ: ca thành công, ca biên cho mọi ngưỡng (N-1, N, N+1), ca từ chối cho mỗi mã lỗi, ca trùng/lặp lại, ca thiếu quyền.
- AC không mô tả cách làm, chỉ mô tả kết quả quan sát được.
- **Phi chức năng đo được** (p99, khả dụng, WCAG) không cần ép thành Given/When/Then; ghi mức kiểm chứng (load test, giám sát, kiểm tra truy cập) ở ma trận truy vết. Phi chức năng có hành vi quan sát được (bảo toàn tiền, phân quyền, audit) thì vẫn nên có AC.
- Không được tuyên bố "phủ gián tiếp": nếu một FR/BR chưa có AC thì thêm AC, hoặc ghi lý do không thể có AC. "Gián tiếp" là cách che khoảng trống.
- Số trong AC (ngưỡng, phí, hạn mức) là cấu hình giả định để minh hoạ: nêu rõ ở đầu mục AC để người đọc không nhầm với giá trị đã chốt.

## 9. Danh mục lỗi

Mỗi lỗi một dòng: mã ổn định (`INSUFFICIENT_BALANCE`), điều kiện kích hoạt, HTTP status (hoặc "trong order.failureCode" nếu lệnh đã được tạo), thông điệp cho người dùng bằng tiếng Việt, có thể thử lại hay không. Thông điệp không lộ thông tin nhạy cảm hay cho phép dò tìm (ví dụ không phân biệt "SĐT không tồn tại" và "SĐT không dùng được" nếu điều đó cho phép dò người dùng, trừ khi yêu cầu cho phép).

## 10. Checklist trước khi giao

- [ ] Mọi yêu cầu sở hữu có mặt trong ma trận truy vết (hoặc có lý do)
- [ ] Mỗi FR/BR/ST/EV có ≥ 1 AC; mỗi AC trỏ về FR/BR/ST/EV
- [ ] Không còn từ trong danh sách mơ hồ; mọi con số có đơn vị hoặc là tham số cấu hình
- [ ] Mỗi quyết định nghiệp vụ không có trong requirements đều có nhãn và (nếu có nhiều lựa chọn) nằm trong câu hỏi mở
- [ ] Mỗi use case có luồng chính, thay thế và lỗi; hậu điều kiện thất bại nêu rõ tiền và hạn mức
- [ ] Đã trả lời 11 câu mục 7 (nếu chạm tới tiền)
- [ ] Đối chiếu danh mục lỗi với API doc và design; nếu hai tài liệu mâu thuẫn nhau thì nêu rõ ở câu hỏi mở (hoặc sửa tài liệu sai khi đã rõ bên nào đúng) thay vì chọn một bên ngầm
- [ ] Thuật ngữ khớp với glossary và các spec đã có
- [ ] ID đặt đúng quy ước; không dùng lại ID cũ
- [ ] Danh mục lỗi khớp với API doc và design
- [ ] Không có chi tiết cài đặt thừa (bảng, service nội bộ) trừ khi là ràng buộc
