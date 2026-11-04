# Next.js / React unit test

## Thư viện
- **Vitest** (runner), **@testing-library/react** + **@testing-library/user-event**, **@testing-library/jest-dom** (matcher), môi trường `jsdom`.
- Mock network bằng **MSW** khi component tự gọi API ở phía client. Ưu tiên MSW hơn mock `fetch` bằng tay, vì MSW chặn ở tầng request thật.
- Repo đang dùng Jest thì giữ Jest; cú pháp gần như giống nhau.

```ts
// vitest.config.ts (tối thiểu)
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  test: { environment: 'jsdom', setupFiles: ['./vitest.setup.ts'], restoreMocks: true },
  resolve: { alias: { '@': new URL('./', import.meta.url).pathname } },
});
```

## Test cái gì ở mức nào

| Code | Cách test |
|---|---|
| Hàm thuần (format tiền, che SĐT, parse lỗi ProblemDetail) | Unit thuần |
| Schema Zod dùng chung cho form và server action | Unit: input hợp lệ / không hợp lệ, thông báo lỗi |
| Client Component (form chuyển tiền, OTP) | Testing Library: render → thao tác như người dùng → assert những gì hiển thị |
| Hook (`useOrderPolling`) | `renderHook` + fake timer |
| Server Action / Route Handler | Tách logic ra hàm thuần rồi unit test hàm đó; phần gọi backend thì mock client API |
| Async Server Component | Không unit test trực tiếp; logic nằm ở hàm phụ trợ (unit) và luồng tổng thể kiểm bằng Playwright E2E |

## Nguyên tắc
- Query theo **vai trò và nhãn** như người dùng thấy: `getByRole('button', { name: 'Xác nhận' })`, `getByLabelText('Số tiền')`. Tránh `data-testid` trừ khi không còn cách nào khác.
- Dùng `userEvent` (không `fireEvent`) để mô phỏng gõ phím và click thật.
- Assert trên DOM người dùng thấy, không assert state nội bộ của component.
- Fake timer (`vi.useFakeTimers()`) cho polling, đếm ngược OTP, debounce. Không `await` thời gian thật.
- Locale: format tiền theo `vi-VN` và assert chuỗi chính xác (`"1.000.000 ₫"`). Chú ý `Intl` có thể chèn non-breaking space, nên so bằng regex hoặc chuẩn hoá khoảng trắng.

## Ca test bắt buộc cho form tiền

```tsx
it('giữ nguyên Idempotency-Key khi bấm lại, sinh key mới sau khi lệnh FAILED', async () => {
  const calls: string[] = [];
  server.use(http.post('*/transfers', ({ request }) => {
    calls.push(request.headers.get('Idempotency-Key')!);
    if (calls.length === 1) return HttpResponse.error();                                  // mất mạng
    if (calls.length === 2) return HttpResponse.json({ order: { status: 'FAILED', failureCode: 'INSUFFICIENT_BALANCE' } });
    return HttpResponse.json({ order: { status: 'SUCCEEDED' } });
  }));
  const user = userEvent.setup();
  render(<TransferForm />);

  await fillValidTransfer(user);
  await user.click(screen.getByRole('button', { name: 'Xác nhận' }));   // lỗi mạng
  await user.click(screen.getByRole('button', { name: 'Thử lại' }));    // FAILED
  await user.click(screen.getByRole('button', { name: 'Xác nhận' }));   // lệnh mới

  expect(calls[1]).toBe(calls[0]);       // retry → cùng key
  expect(calls[2]).not.toBe(calls[1]);   // sau FAILED → key mới
});
```

Các ca khác:
- [ ] Nút submit bị disable khi đang gửi (không gửi được 2 request khi double-click)
- [ ] Response `202 PROCESSING`/`UNKNOWN` → hiển thị "Đang xử lý", **không** hiển thị "Thất bại"
- [ ] `FEE_CHANGED` → tải lại phí và yêu cầu xác nhận lại
- [ ] `failureCode` → thông báo tiếng Việt đúng; mã lạ → thông báo chung kèm mã tra soát
- [ ] Không có token hay dữ liệu nhạy cảm trong `localStorage`/`sessionStorage` (assert rỗng sau luồng đăng nhập)
- [ ] Số tiền nhập có dấu chấm ngăn cách được parse đúng; số âm, số 0 và chữ bị chặn

## Chạy
`pnpm vitest run <path>` cho file vừa viết, sau đó `pnpm vitest run` toàn bộ. Báo pass/fail thật.
