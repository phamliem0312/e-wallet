# Gán mức test & chuyển sang auto test

## Chọn mức thấp nhất mà vẫn chứng minh được

Hỏi lần lượt, gặp "có" đầu tiên thì dừng:
1. Hành vi có kiểm được **chỉ bằng code**, không cần DB, mạng hay UI không? → `UNIT`
2. Hành vi nằm ở **DB/broker**, như lock, unique constraint, transaction, consumer? → `INTEGRATION`
3. Hành vi là **hợp đồng HTTP**: mã lỗi, header, phân quyền, idempotency? → `API`
4. Hành vi chỉ thấy được qua **UI** hoặc cần **luồng xuyên nhiều màn hình**? → `E2E`
5. Cần con người phán đoán, hoặc cần hệ thống thật chưa có (eKYC thật, ngân hàng thật)? → `MANUAL` (ghi lý do)
6. Là yêu cầu về tải, độ bền, bảo mật hạ tầng? → `NFR`

Lý do: test ở mức thấp chạy nhanh, ổn định và chỉ ra lỗi chính xác. `E2E` chậm và dễ chập chờn, chỉ dùng cho luồng chính.

## Phân bổ gợi ý cho một nhóm chức năng
- `UNIT` khoảng 50–60%: rule phí, hạn mức, validate, state machine, format.
- `INTEGRATION` + `API` khoảng 30–40%: mã lỗi, phân quyền, idempotency, đồng thời.
- `E2E` khoảng 5–10%: mỗi luồng chính một ca thành công, cộng một hai ca lỗi quan trọng nhất với người dùng.

## Framework theo mức (mặc định của dự án EWallet; repo đã có framework khác thì theo repo)

| Mức | Backend | Frontend |
|---|---|---|
| `UNIT` | JUnit 5 + AssertJ (skill `unit-test-java-nextjs`) | Vitest + Testing Library |
| `INTEGRATION` | Testcontainers (skill `unit-test-java-nextjs`) | — |
| `API` | Playwright `APIRequestContext` (dùng chung tooling với E2E) hoặc REST Assured nếu team backend muốn viết bằng Java | — |
| `E2E` | — | Playwright |
| `NFR` | k6 (load), chaos test trên staging, OWASP ZAP (DAST) | Lighthouse/axe (accessibility) |

## Quy ước để auto test truy vết được về TC
- Tên test hoặc tag chứa ID TC:
  - Playwright: `test('TC-P2P-001 p2p success @P1', ...)`.
  - JUnit: `@Tag("TC-P2P-001")` hoặc `@DisplayName("TC-P2P-001 ...")`.
- Trường **Tự động hoá** trong TC ghi đường dẫn file và tên test sau khi đã viết.
- Báo cáo CI gom theo tag → cập nhật cột trạng thái phủ trong RTM.

## Dữ liệu & môi trường cho auto test
- **Tiền điều kiện** của TC phải tạo được bằng API hoặc fixture, không tạo tay. Ví dụ "ví số dư 1.000.000" → helper nạp tiền qua `FakeBankGateway` ở môi trường dev/test.
- Tình huống tích hợp (timeout ngân hàng, trả về lỗi) → dùng kịch bản của fake. Ví dụ ở dự án này: số tiền kết thúc bằng `13` thì timeout, `99` thì bị từ chối.
- Mỗi auto test tự tạo dữ liệu riêng (khách hàng hoặc ví mới), không dùng chung tài khoản giữa các test, để chạy song song được.
- TC `MANUAL` vì phụ thuộc hệ thống thật: ghi kèm "khi có adapter thật thì chuyển sang API test".

## Khi người dùng yêu cầu viết luôn code auto test
1. Chỉ viết cho các TC đã có kết quả mong đợi rõ ràng (không viết cho TC `BLOCKED`).
2. `UNIT`/`INTEGRATION` → làm theo skill `unit-test-java-nextjs`.
3. `API`/`E2E` → Page Object hoặc helper cho các thao tác lặp lại (đăng nhập, tạo ví, nạp tiền); assert theo kết quả mong đợi của TC, ghi đúng con số.
4. Chạy test và báo kết quả thật. Cập nhật trường **Tự động hoá** của TC.
