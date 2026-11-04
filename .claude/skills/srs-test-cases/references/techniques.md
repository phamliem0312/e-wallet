# Kỹ thuật thiết kế test case

Chọn kỹ thuật theo dạng yêu cầu. Một yêu cầu thường cần kết hợp nhiều kỹ thuật.

| Dạng yêu cầu | Kỹ thuật |
|---|---|
| Có miền giá trị (số tiền, độ dài, định dạng) | Phân vùng tương đương + giá trị biên |
| Nhiều điều kiện kết hợp cho ra kết quả khác nhau | Bảng quyết định |
| Đối tượng có vòng đời (lệnh, ví, hồ sơ KYC) | Chuyển trạng thái |
| Luồng nghiệp vụ nhiều bước | Use case: luồng chính + luồng thay thế + luồng lỗi |
| Tích hợp hệ thống ngoài | Ca lỗi tích hợp: timeout, trùng, đến muộn, sai chữ ký |
| Dữ liệu của người dùng | Phân quyền / ownership |

## 1. Phân vùng tương đương (EP)
Chia đầu vào thành các nhóm mà hệ thống xử lý giống nhau; mỗi nhóm lấy một giá trị đại diện.

Ví dụ: số tiền P2P, với hạn mức mỗi giao dịch = `L` (cấu hình):

| Phân vùng | Đại diện | Kết quả |
|---|---|---|
| ≤ 0 | 0, -1 | `VALIDATION_ERROR` |
| 1 .. L | L/2 | Thành công (nếu đủ số dư) |
| > L | 2L | `LIMIT_EXCEEDED` |
| Không phải số / có phần thập phân | "abc", 1000.5 | `VALIDATION_ERROR` |

## 2. Giá trị biên (BVA)
Với mọi ngưỡng `N`, test `N-1`, `N`, `N+1`. Ngưỡng cũng có thể là thời gian (23:59:59 và 00:00:00 giờ VN), số lần (sai OTP lần 5 và lần 6) hay độ dài chuỗi (140 và 141 ký tự).

Ngưỡng là giá trị cấu hình chưa chốt → viết TC theo tên cấu hình (`L`, `L+1`), không viết con số cụ thể.

## 3. Bảng quyết định
Dùng khi kết quả phụ thuộc tổ hợp điều kiện. Liệt kê điều kiện, các tổ hợp có nghĩa, kết quả; mỗi cột là một TC. Gộp các cột cho cùng kết quả bằng ký hiệu `-` (không quan tâm).

Ví dụ: yêu cầu xác thực tăng cường khi chuyển tiền:

| Điều kiện | C1 | C2 | C3 | C4 |
|---|---|---|---|---|
| Số tiền > ngưỡng OTP | N | Y | Y | - |
| Số tiền / tổng ngày > ngưỡng sinh trắc học | N | N | Y | - |
| Thiết bị mới | N | N | N | Y |
| **Kết quả** | NONE | OTP | FACE | OTP |

## 4. Chuyển trạng thái
1. Vẽ (hoặc lấy từ design doc) sơ đồ trạng thái.
2. Mỗi **chuyển trạng thái hợp lệ** → 1 TC.
3. Mỗi **chuyển trạng thái không hợp lệ quan trọng** → 1 TC (ví dụ `SUCCEEDED → FAILED` phải bị chặn).
4. Ca **sự kiện trùng hoặc đến muộn**, ví dụ callback đến sau khi resolver đã chốt trạng thái.

Phủ tối thiểu: mọi trạng thái được vào ít nhất một lần, mọi chuyển hợp lệ được đi qua ít nhất một lần.

## 5. Use case / luồng
Với mỗi luồng (đăng ký, nạp tiền, rút tiền...):
- Luồng chính: 1 TC, thường ở mức `E2E` hoặc `API`.
- Mỗi luồng thay thế (ví dụ "cần OTP") → 1 TC.
- Mỗi điểm lỗi trên luồng → 1 TC, ở mức thấp nhất có thể.

## 6. Ca lỗi tích hợp
Với mọi lời gọi ra hệ thống ngoài, xét các tình huống:
- Thành công
- Bị từ chối rõ ràng
- Timeout / không biết kết quả
- Phản hồi trùng
- Callback đến trước hoặc sau response
- Sai chữ ký
- Hệ thống ngoài đang bảo trì
- Kết quả đối soát lệch

## 7. Phân quyền & bảo mật
Với mọi chức năng đọc/ghi dữ liệu của một chủ thể:
- Chủ sở hữu → được.
- Người dùng khác → 403/404, không lộ dữ liệu.
- Chưa đăng nhập → 401.
- Back-office: đúng role → được; sai role → 403; maker tự duyệt yêu cầu của chính mình → bị chặn.
- Dữ liệu nhạy cảm không xuất hiện trong response, log hay URL.

## 8. Error guessing (đoán lỗi)
Sau khi áp các kỹ thuật trên, bổ sung các lỗi hay gặp:
- Double-click, gửi lại request.
- Hai thao tác đồng thời lên cùng một đối tượng.
- Chuỗi có dấu tiếng Việt, emoji, khoảng trắng đầu/cuối.
- Số điện thoại có hoặc không có `+84`.
- Đổi múi giờ.
- Phiên đăng nhập hết hạn giữa luồng.
