# Mẫu OpenAPI & JSON Schema

## 1. `common.yaml` (component dùng chung)

```yaml
openapi: 3.1.0
info: { title: EWallet common components, version: 0.1.0 }
paths: {}
components:
  parameters:
    IdempotencyKey:
      name: Idempotency-Key
      in: header
      required: true
      description: UUID do client sinh cho mỗi lần dự định thực hiện lệnh; giữ nguyên khi gửi lại.
      schema: { type: string, format: uuid }
    Traceparent:
      name: traceparent
      in: header
      required: false
      schema: { type: string }
  schemas:
    MoneyVnd:
      type: integer
      format: int64
      minimum: 0
      description: Số tiền, đơn vị đồng (VND), không có phần thập phân.
    ErrorCode:
      type: string
      description: Mã lỗi nghiệp vụ ổn định. Danh sách có thể mở rộng; client phải xử lý mã chưa biết như lỗi chung.
      enum: [VALIDATION_ERROR, IDEMPOTENCY_KEY_REQUIRED, IDEMPOTENCY_KEY_REUSED, ...]
    Problem:
      type: object
      required: [type, title, status, code, traceId]
      properties:
        type: { type: string, format: uri }
        title: { type: string }
        status: { type: integer }
        code: { $ref: '#/components/schemas/ErrorCode' }
        traceId: { type: string }
        detail: { type: string }
        errors:
          type: array
          items:
            type: object
            required: [field, code]
            properties:
              field: { type: string }
              code: { type: string }
              message: { type: string }
  responses:
    Problem400: { description: Yêu cầu không hợp lệ, content: { application/problem+json: { schema: { $ref: '#/components/schemas/Problem' } } } }
    # Problem401, 403, 404, 409, 422, 429 tương tự
  securitySchemes:
    customerJwt: { type: http, scheme: bearer, bearerFormat: JWT }
    staffJwt:    { type: http, scheme: bearer, bearerFormat: JWT }
```

File của service tham chiếu qua `$ref: '../common.yaml#/components/...'`.

## 2. Endpoint lệnh tiền

```yaml
  /transfers:
    post:
      operationId: createTransfer
      summary: Tạo lệnh chuyển tiền ví–ví
      description: |
        Tra `Idempotency-Key` trước mọi kiểm tra khác (F-PAY-03.FR-08).
        Mã lỗi trước khi tạo lệnh: VALIDATION_ERROR, IDEMPOTENCY_KEY_REQUIRED, WALLET_FROZEN, KYC_REQUIRED,
        RECIPIENT_UNAVAILABLE, AMOUNT_OUT_OF_RANGE, CHALLENGE_REQUIRED, CHALLENGE_INVALID, IDEMPOTENCY_KEY_REUSED, FEE_CHANGED.
        Sau khi tạo lệnh: 200/202 kèm order; failureCode ∈ {INSUFFICIENT_BALANCE, LIMIT_EXCEEDED, ACCOUNT_BLOCKED}.
      security: [ { customerJwt: [] } ]
      parameters:
        - $ref: '../common.yaml#/components/parameters/IdempotencyKey'
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateTransferRequest' }
      responses:
        '200': { description: Lệnh đã có kết quả cuối, content: { application/json: { schema: { $ref: '#/components/schemas/Order' } } } }
        '202': { description: Lệnh đang xử lý; hỏi lại bằng GET /orders/{id}, content: { application/json: { schema: { $ref: '#/components/schemas/Order' } } } }
        '400': { $ref: '../common.yaml#/components/responses/Problem400' }
        # 403, 409, 422 ...
```

- Request schema có `additionalProperties: false`.
- `Order.status` và `Order.failureCode` ghi rõ "có thể mở rộng".
- `example` lấy từ AC của SRS (ví dụ AC-01: 100.000, phí 1.000).

## 3. Schema event (`events/<service>/<EventName>/v1.json`)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "contracts/events/payment/PaymentSucceeded/v1.json",
  "title": "PaymentSucceeded (data)",
  "description": "Phần data của CloudEvents. Envelope: source=payment, subject=orderId, aggregateversion=version của lệnh.",
  "type": "object",
  "required": ["orderId", "type", "walletId", "amount", "fee", "currency"],
  "properties": {
    "orderId": { "type": "string", "format": "uuid" },
    "type": { "type": "string", "enum": ["TOPUP", "WITHDRAW", "P2P"] },
    "amount": { "type": "integer", "minimum": 1 }
  },
  "additionalProperties": true
}
```
`additionalProperties: true` để thêm field về sau không phá consumer cũ (BACKWARD).
