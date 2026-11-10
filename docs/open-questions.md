# Câu hỏi mở — danh sách chung

- Cập nhật: 2026-11-04 (thêm OQ-030..044 từ SRS F-ACC-01, OQ-045..046 từ data model identity, OQ-047..051 từ hợp đồng api F-ACC-01); 2026-11-10 (OQ-052 từ test case F-ACC-01)
- Đây là **nơi duy nhất để xem mọi câu hỏi đang chờ quyết định**. Mỗi tài liệu vẫn giữ bảng câu hỏi của riêng nó (ID cục bộ như `Q4`, `UQ2`) để đọc trong ngữ cảnh. File này gom lại, gán ID chung `OQ-NNN`, ghi ai quyết định và câu hỏi đang chặn việc gì.

**Cách dùng:**
- **Thêm:** skill hoặc người viết tài liệu tạo câu hỏi mới thì thêm một dòng ở đây (ID `OQ-` tiếp theo, không dùng lại ID) và ghi `OQ-NNN` cạnh câu hỏi trong tài liệu nguồn.
- **Trả lời:** ghi câu trả lời và ngày vào đây, chuyển dòng sang mục "Đã trả lời", rồi cập nhật tài liệu nguồn và những gì bị chặn (spec, test case `BLOCKED`).
- Câu hỏi trùng giữa nhiều tài liệu chỉ có **một** dòng, liệt kê mọi nguồn.

Ưu tiên: **P1** = chặn thiết kế, code hoặc test của luồng tiền · **P2** = chặn một phần, có giá trị tạm để làm tiếp · **P3** = cải thiện, không chặn.

## Đang mở

### Pháp chế / Rủi ro
| ID | Câu hỏi | Nguồn | Chặn | Ưu tiên |
|---|---|---|---|---|
| OQ-001 | Con số hạn mức theo từng tầng (trần pháp lý, theo cấp KYC) cho từng loại giao dịch và kỳ ⚖️ | requirements §6 Q4, §7.2 | Giá trị cấu hình trước go-live (code không bị chặn: hạn mức là cấu hình) | P2 |
| OQ-002 | Ngưỡng xác thực tăng cường (OTP, khuôn mặt) cho P2P; có tính theo tổng trong ngày không ⚖️ | SRS F-PAY-03 Q1; requirements §7.2 | F-PAY-03.BR-07; TC-P2P-002 (dòng e–g) | P1 |
| OQ-003 | Cấp định danh tối thiểu để chuyển tiền; người nhận có cần cấp tối thiểu để nhận tiền không | SRS F-PAY-03 Q2, Q6 | F-PAY-03.FR-13; TC-P2P-034 | P1 |
| OQ-004 | Hạn mức P2P tính trên tiền ra, tiền vào hay cả hai; có giới hạn số dư ví tối đa hoặc hạn mức nhận không ⚖️ | SRS F-PAY-03 Q3 | F-PAY-03.BR-06; F-LMT-01 | P1 |
| OQ-005 | Thời hạn xử lý tra soát và thời hạn lưu trữ theo luật (trần cho SLA nội bộ) ⚖️ | requirements §6 Q7, §7.5 | F-DSP-01; chính sách archive | P2 |
| OQ-006 | Dữ liệu có bắt buộc đặt tại Việt Nam không ⚖️ | requirements §6 Q8; design R5 | Chọn cloud và Kafka managed | P1 |
| OQ-007 | "Mỗi người một ví... khi quy định yêu cầu" (FR-ID-03): quy định cụ thể là gì | SRS index Q4 | F-ACC-02 | P2 |
| OQ-008 | Mẫu và tần suất báo cáo định kỳ gửi NHNN | SRS index Q3; FR-BO-11 | F-BO-06 | P2 |
| OQ-030 | ⚖️ Đăng nhập có bắt buộc xác thực hai yếu tố mỗi lần không? FR-ID-05 ghi "mật khẩu + OTP/2FA", design chọn OTP chỉ cho thiết bị mới | SRS F-ACC-01 Q1 | F-ACC-01.FR-19..21; AC-18 | P2 |
| OQ-033 | Khách chưa eKYC (không có ảnh mặt để so) đổi SĐT bằng cách nào? Spec đang chặn (`KYC_REQUIRED`) | SRS F-ACC-01 Q4 | F-ACC-01.FR-37; AC-44 | P2 |
| OQ-034 | Quên mật khẩu chỉ cần OTP tới SĐT (rủi ro SIM swap): khách đã eKYC có phải xác thực khuôn mặt không | SRS F-ACC-01 Q5 | F-ACC-01.FR-33 | P3 |
| OQ-041 | Rút lại đồng ý (`TERMS`, `BIOMETRIC_PROCESSING`) có hệ quả gì (khoá, xoá dữ liệu) ⚖️ | SRS F-ACC-01 Q12 | F-CMP-01; F-ACC-01.FR-40 | P3 |
| OQ-045 | Challenge `CHANGE_PHONE` (cả `FACE` và `OTP`) có gắn `payload_hash` = băm SĐT mới không? SRS F-ACC-01.FR-35 chưa nói; không gắn thì khuôn mặt đã xác thực cho SĐT X dùng được cho SĐT Y. Schema cho phép cả hai | Data model DQ1 (identity) | F-ACC-01.FR-35; `otp_challenge.payload_hash` | P3 |
| OQ-049 | **Hạ cấp yếu tố xác thực:** khi lệnh yêu cầu `requiredFactor = FACE`, client gửi challenge yếu tố `OTP` thì có bị từ chối không? F-PAY-03.FR-16 chỉ nói challenge "hợp lệ". Hợp đồng đã làm `identity` trả `factor` khi xác minh để `payment` so; việc so và quy tắc "FACE thay được OTP, OTP không thay được FACE" cần được ghi vào F-PAY-03 | Hợp đồng api F-ACC-01 | F-PAY-03.FR-16, BR-07; `payment` | P2 |

### Product
| ID | Câu hỏi | Nguồn | Chặn | Ưu tiên |
|---|---|---|---|---|
| OQ-009 | Quy tắc che tên người nhận (`p2p.recipient_mask_rule`) | SRS F-PAY-03 Q4 | F-PAY-03.FR-01; TC-P2P-036 | P2 |
| OQ-010 | Gộp `RECIPIENT_NOT_FOUND` và `RECIPIENT_UNAVAILABLE` thành một lỗi để tránh dò tài khoản? (cần ý kiến Bảo mật) | SRS F-PAY-03 Q5 | F-PAY-03.FR-03; TC-P2P-035 | P2 |
| OQ-011 | Thông báo cho người nhận hiển thị gì về người gửi (cần ý kiến Pháp chế về dữ liệu cá nhân) | SRS F-PAY-03 Q8 | F-PAY-03.FR-29; TC-P2P-042 | P2 |
| OQ-012 | Giá trị `p2p.amount.min` | SRS F-PAY-03 Q10 | F-PAY-03.BR-05 | P3 |
| OQ-013 | Xem trước có trả `WALLET_FROZEN`/`KYC_REQUIRED` không, để khách biết bị chặn ngay ở bước nhập? | UI F-PAY-03 UQ3 | UI S-01 | P3 |
| OQ-014 | "Chuyển tiền khác" sau khi thành công: giữ người nhận cũ hay form trống | UI F-PAY-03 UQ6 | UI S-04 | P3 |
| OQ-015 | Danh sách người nhận gần đây / danh bạ có cần cho giai đoạn 1 không | UI F-PAY-03 UQ7 | UI S-01 | P3 |
| OQ-016 | SĐT có khoảng trắng hoặc dấu chấm: server chấp nhận hay client phải bỏ trước? | Test case F-PAY-03 TQ2 | F-PAY-03.FR-02; TC-P2P-006c | P3 |
| OQ-017 | Xác nhận 7 quyết định gom nhóm tính năng (G1–G7), đặc biệt G1: tách "xác thực tăng cường" thành tính năng dùng chung? | SRS index §6 | Ranh giới F-PAY-03, F-ACC-* | P2 |
| OQ-018 | Yêu cầu phi chức năng không có ưu tiên: coi là `M`? FR-HS-06 (sao kê PDF) để giai đoạn sau? | SRS index Q1, Q2 | Cột ưu tiên trong index | P3 |
| OQ-031 | Có để lộ SĐT đã đăng ký không (đăng ký, quên mật khẩu)? Đề xuất của spec: không lộ, gửi tin nhắn tới SĐT đó (cần ý kiến Bảo mật) | SRS F-ACC-01 Q2 | F-ACC-01.FR-02, FR-32; AC-03, AC-35 | P2 |
| OQ-035 | Chính sách mật khẩu: độ dài tối thiểu, quy tắc thành phần ký tự, danh sách mật khẩu bị cấm | SRS F-ACC-01 Q6 | F-ACC-01.BR-02; AC-39 | P3 |
| OQ-036 | Giá trị tham số `auth.*` (OTP: hạn, số lần, gửi lại, trần gửi; khoá tạm; refresh TTL; idle timeout; rate limit; ngưỡng cảnh báo OTP lỗi) và phạm vi khoá tạm (theo tài khoản có thể bị lợi dụng để khoá nạn nhân) | SRS F-ACC-01 Q7 | F-ACC-01.FR-13, 17, 27, 43; NFR-05 (giá trị trước go-live) | P3 |
| OQ-038 | Họ tên tự khai lúc đăng ký (trước eKYC): có thu không, hiển thị ở đâu | SRS F-ACC-01 Q9 | F-ACC-01.FR-03, BR-03 | P3 |
| OQ-039 | SĐT của tài khoản `CLOSED` có được đăng ký lại không (`customer.phone` duy nhất kể cả `CLOSED`)? Tương tự: SĐT cũ sau khi khách đổi SĐT có được người khác đăng ký ngay không (tiền chuyển theo SĐT sẽ tới chủ mới)? Cách ẩn danh `phone` cũng phụ thuộc câu này | SRS F-ACC-01 Q10; Data model (identity, ẩn danh) | F-ACC-01.BR-10, FR-36; F-ACC-03 | P2 |
| OQ-040 | Kênh gửi cảnh báo bảo mật (thiết bị mới, đổi mật khẩu, đổi SĐT): SMS, trong ứng dụng, hay email | SRS F-ACC-01 Q11 | F-ACC-01.EV-05..07; F-NTF-01 | P3 |
| OQ-044 | Chỉ hỗ trợ SĐT Việt Nam, không kiểm đầu số nhà mạng? (khoảng trắng trong SĐT: OQ-016) | SRS F-ACC-01 Q15 | F-ACC-01.BR-01; AC-02 | P3 |
| OQ-050 | Điều khoản hiện hành (phiên bản và nội dung) lấy từ đâu: endpoint của `identity`, hay nội dung tĩnh trong `web` kèm phiên bản cấu hình? Hợp đồng chỉ cho client biết phiên bản đúng qua `CONSENT_REQUIRED.params.currentTermsVersion` | Hợp đồng api F-ACC-01 | F-ACC-01.FR-04; UI đăng ký | P2 |

### Thiết kế API
*Nhóm này đều là chỗ API chưa khớp với SRS hoặc UI; xem đề xuất skill `api-contract`.*
| ID | Câu hỏi | Nguồn | Chặn | Ưu tiên |
|---|---|---|---|---|
| OQ-019 | `POST /challenges` thiếu `destinationMasked`, `attemptsRemaining`, `resendAfter` cho màn hình OTP. **Đề xuất (chờ duyệt):** thêm cả ba vào `Challenge`; `attemptsRemaining` sau mỗi lần sai trả ở `Problem.params` (`contracts/openapi/identity/public.yaml`) | UI F-PAY-03 UQ2 | UI S-03 | P2 |
| OQ-020 | `order` khi `FAILED LIMIT_EXCEEDED` cần trả `remaining` (data model đã có `failure_detail`). **Đề xuất (chờ duyệt):** `order.failureDetail = {scope, remaining}` (`contracts/openapi/payment/public.yaml`) | UI F-PAY-03 UQ5 | F-PAY-03 §6; TC-P2P-029c | P2 |
| OQ-021 | `GET /orders/{id}` cần trả `counterpartyDisplay` (UI kết quả hiển thị "Người nhận"). **Đề xuất (chờ duyệt):** thêm `order.counterpartyDisplay` (tên đã che hoặc tên ngân hàng), lấy từ `wallet_activity` | Data model DQ1 | UI S-04; TC-P2P-050 | P2 |
| OQ-022 | Có đưa `p2p.amount.min` và `p2p.min_kyc_tier` cho FE (ví dụ qua `GET /wallet/limits`) để báo lỗi sớm không | UI F-PAY-03 UQ4 | UI S-01 | P3 |
| OQ-023 | Người khác gọi `GET /orders/{id}` của lệnh không thuộc mình: trả 403 hay 404. **Đề xuất (chờ duyệt):** 404, không phân biệt với lệnh không tồn tại (không lộ lệnh có tồn tại); khớp UI S-04 và quy ước của skill `api-contract` | Test case F-PAY-03 TQ1 | TC-P2P-040 | P2 |
| OQ-047 | `displayMasked` trong `CustomerRegistered`/`CustomerUpdated` do `identity` tính (tên đầy đủ không được lên Kafka), nên `identity` phải áp quy tắc che tên (OQ-009). Quy tắc nằm ở đâu để `wallet` và `identity` cùng dùng? Có cập nhật lại khi eKYC xác nhận tên thật (`KycTierChanged`) không? | Hợp đồng api F-ACC-01 | events `CustomerRegistered`, `CustomerUpdated`; F-PAY-03.FR-01 | P2 |
| OQ-048 | `GET /me` (hồ sơ, KYC tier, trạng thái) có trong api doc nhưng F-ACC-01 chưa có FR nào: thuộc F-ACC-01 hay F-ACC-02, trả field nào | Hợp đồng api F-ACC-01 | `AuthSession` không mang dữ liệu khách; UI sau đăng nhập | P2 |
| OQ-052 | `AuditRecorded` chưa có JSON Schema; mỗi thao tác bảo mật của F-ACC-01 (đăng ký, đăng nhập đúng/sai, khoá tạm, đổi/đặt lại mật khẩu, đổi SĐT, thu hồi thiết bị, dùng lại refresh token) dùng `action`, `target` nào | Test case F-ACC-01 TQ1 | F-ACC-01.EV-08; TC-ACC-081; F-BO-05 | P2 |
| OQ-051 | Cách `identity` yêu cầu gửi thông báo bảo mật không phải OTP (đăng ký trùng SĐT, thiết bị mới, đổi mật khẩu, đổi SĐT): API nội bộ của `notification`, hay `notification` nghe event của `identity` (chưa có event cho các việc này)? | Hợp đồng api F-ACC-01 | F-ACC-01.EV-04..EV-07; F-NTF-01 | P2 |

### Thiết kế hệ thống / Vận hành / Hạ tầng
| ID | Câu hỏi | Nguồn | Chặn | Ưu tiên |
|---|---|---|---|---|
| OQ-024 | **Cơ chế ép lỗi ở môi trường test** (kill tiến trình giữa saga, làm chậm `ledger`) chưa được thiết kế | Test case F-PAY-03 TQ3; template design §11.1 | TC-P2P-013, 039, 054 | P1 |
| OQ-025 | Thời hạn tối đa một lệnh được ở `PROCESSING` trước khi cảnh báo vận hành; có báo cho khách không | SRS F-PAY-03 Q7 | F-PAY-03.FR-27 | P2 |
| OQ-026 | Có cần trigger ở DB chặn đổi trạng thái từ `SUCCEEDED`/`FAILED`, hay chỉ chặn ở ứng dụng | Data model DQ2 | `payment_order` | P3 |
| OQ-027 | Chuẩn công ty: service mesh (Istio/Linkerd), Kafka managed hay tự dựng, nền tảng CI/CD | design §14 Q1 | Mốc M1 | P1 |
| OQ-032 | Web nhận diện thiết bị bằng vân tay trình duyệt hay mã thiết bị trong cookie; thiết bị tin cậy có hết hạn không | SRS F-ACC-01 Q3 | F-ACC-01.BR-04, FR-19..21, FR-23 | P2 |
| OQ-037 | Nhiều tab làm mới cùng refresh token bị coi là "dùng lại" và mất phiên: BFF làm mới tuần tự hay đặt `auth.refresh.reuse_grace` > 0 | SRS F-ACC-01 Q8 | F-ACC-01.FR-26; AC-29 | P2 |
| OQ-042 | Ví mở bất đồng bộ sau `CustomerRegistered`: khách thấy gì khi chưa có ví; có chờ ví sẵn sàng trước khi trả kết quả đăng ký không | SRS F-ACC-01 Q13 | F-ACC-01.FR-03, FR-05, EV-01; UI | P2 |
| OQ-043 | `customer.status = PENDING` trong data model dùng cho việc gì (đăng ký hiện tạo thẳng `ACTIVE`) | SRS F-ACC-01 Q14 | F-ACC-01.ST-01..05 | P3 |
| OQ-046 | Trần gửi OTP và `resendAfter` đặt ở Redis nên mất khi Redis khởi động lại (kẻ tấn công được thêm lượt gửi). Chấp nhận, hay lưu bền theo `(phone, purpose)` không phụ thuộc tài khoản tồn tại? | Data model DQ2 (identity) | F-ACC-01.FR-12, FR-13 | P3 |

### Thiết kế UI (tính năng khác)
| ID | Câu hỏi | Nguồn | Chặn | Ưu tiên |
|---|---|---|---|---|
| OQ-028 | Luồng xác thực khuôn mặt (`FACE`) thuộc F-ACC-02, chưa thiết kế; cần chốt điểm vào/ra cho luồng P2P | UI F-PAY-03 UQ1 | UI S-02; TC-P2P-059 | P2 |

## Đã trả lời

| ID | Câu hỏi | Trả lời | Ngày | Nguồn |
|---|---|---|---|---|
| OQ-029 | Mã lỗi khi ví bị khoá giữa chừng: `ACCOUNT_FROZEN` hay `ACCOUNT_BLOCKED` | `ACCOUNT_BLOCKED`, khớp api §5 và data model; đã sửa sơ đồ design §6.3 | 2026-11-04 | SRS F-PAY-03 Q9 |

*(Câu hỏi đã trả lời trước khi có danh sách này, ví dụ requirements Q1–Q10 phần đã ✅, không đưa vào đây; xem requirements §6.)*

## Tóm tắt
| Người quyết định | Số câu mở | Trong đó P1 |
|---|---|---|
| Pháp chế / Rủi ro | 14 | 4 (OQ-002, 003, 004, 006) |
| Product | 18 | 0 |
| Thiết kế API | 9 | 0 |
| Thiết kế hệ thống / Vận hành / Hạ tầng | 9 | 2 (OQ-024, 027) |
| Thiết kế UI | 1 | 0 |
| **Cộng** | **51** | **6** |
