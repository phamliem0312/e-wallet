# Ma trận truy vết yêu cầu → test case

- Cập nhật: 2026-11-10
- Phạm vi hiện có: F-PAY-03, F-ACC-01

Trạng thái phủ: ✅ đã phủ · ⚠️ phủ một phần · ⏸️ bị chặn · ❌ chưa phủ

## F-PAY-03 — Chuyển tiền ví–ví ([test case](F-PAY-03-chuyen-tien-vi-vi.md))

### Yêu cầu gốc (requirements.md)
| Yêu cầu | SRS | Test case | Phủ |
|---|---|---|---|
| FR-P2P-01 | FR-01..06, BR-04 | TC-P2P-006, 030, 035..038, 050 | ⚠️ định dạng che tên chờ Q4 |
| FR-P2P-02 | FR-21..23, BR-02 | TC-P2P-010, 012, 019, 020, 021 | ✅ |
| FR-P2P-03 | FR-31 | TC-P2P-005, 017 | ✅ (ký tự bị cấm là giả định của SRS) |
| FR-P2P-04 | FR-16, FR-17, BR-07 | TC-P2P-002, 023, 030..032, 051, 059 | ⚠️ ngưỡng ngày chờ Q1; FACE chờ UQ1 |

### Chi tiết theo SRS
| SRS | Test case | Mức | Phủ |
|---|---|---|---|
| FR-01 | 036, 050 | API, E2E | ⚠️ Q4 |
| FR-02 | 006 | UNIT | ⚠️ TQ2 |
| FR-03 | 035 | API | ✅ |
| FR-04 | 037 | API | ✅ |
| FR-05 | 030, 036, 050 | API, E2E | ✅ |
| FR-06 | 038 | API | ✅ |
| FR-07 | 025 | API | ✅ |
| FR-08 | 003, 022, 023, 045, 052 | UNIT, API, E2E | ✅ |
| FR-09 | 024, 056 | API, UNIT FE | ✅ |
| FR-10 | 027 | API | ✅ |
| FR-11 | 011 | INTEGRATION | ✅ |
| FR-12 | 033, 045 | API | ✅ |
| FR-13 | 034 | API | ⏸️ Q2 (giá trị `T_MIN`) |
| FR-14 | 004, 005 | UNIT | ✅ |
| FR-15 | 044, 017 | API, INTEGRATION | ✅ |
| FR-16 | 002, 023, 030, 031, 051, 059 | UNIT, API, E2E | ⚠️ Q1, UQ1 |
| FR-17 | 032 | API | ✅ |
| FR-18 | 028, 053 | API, E2E | ✅ |
| FR-19 | 020 | API | ✅ |
| FR-20 | 029 | API | ⚠️ UQ5 (`remaining`) |
| FR-21 | 010, 012, 020 | INTEGRATION, API | ✅ |
| FR-22 | 010 | INTEGRATION | ✅ |
| FR-23 | 021 | API | ✅ |
| FR-24 | 015, 020 | INTEGRATION, API | ✅ |
| FR-25 | 014 | INTEGRATION | ✅ |
| FR-26 | 013, 039, 054 | INTEGRATION, API, E2E | ⏸️ TQ3 (fault injection) |
| FR-27 | 013, 054 | INTEGRATION, E2E | ⏸️ TQ3 |
| FR-28 | 026, 055 | API, UNIT FE | ✅ |
| FR-29 | 042 | API | ⚠️ Q8 |
| FR-30 | 043 | API | ✅ |
| FR-31 | 005, 017 | UNIT, INTEGRATION | ✅ |
| FR-32 | 041 | API | ✅ |
| BR-01 | 001, 020, 028 | UNIT, API | ✅ |
| BR-02 | 020 | API | ✅ |
| BR-03 | 044, 017 | API, INTEGRATION | ✅ |
| BR-04 | 035 | API | ✅ |
| BR-05 | 004 | UNIT | ✅ |
| BR-06 | 016, 029 | INTEGRATION, API | ✅ |
| BR-07 | 002, 030 | UNIT, API | ⚠️ Q1 |
| BR-08 | 003, 045 | UNIT, API | ✅ |
| ST-01..04 | 008 | UNIT | ✅ |
| EV-01, EV-02 | 042 | API | ⚠️ Q8 |
| EV-03 | 041 | API | ✅ |
| EV-04 | 018 | INTEGRATION | ✅ |
| NFR-01 | 060 | NFR | ✅ |
| NFR-02 | 019 | INTEGRATION | ✅ |
| NFR-03 | 040 | API | ⚠️ TQ1 |
| NFR-04 | 061 | NFR | ✅ |
| NFR-05 | 041 | API | ✅ |
| NFR-06 | — | — | ❌ kiểm ở mức hệ thống (CC-AVAIL), không theo từng tính năng |
| NFR-07 | 058 | NFR | ✅ |

### Tiêu chí chấp nhận
Mọi AC-01..42 đều có ít nhất một TC (cột "AC" trong từng test case). AC có ca bị chặn một phần: AC-13 (UQ5), AC-16/17 với ngưỡng ngày (Q1), AC-22 (Q2), AC-27 (Q4), AC-30/31 (TQ3), AC-33 (Q8), AC-37 (TQ1).

### Ngoài SRS (từ UI và data model)
| Nguồn | Test case |
|---|---|
| UI §4.5 vòng đời `Idempotency-Key` | TC-P2P-052, 055, 056 |
| UI S-02 chống bấm đúp | TC-P2P-057 |
| UI S-02 phí đổi, S-04 đang xử lý | TC-P2P-053, 054 |
| Data model ràng buộc `payment` | TC-P2P-017 |

### Tổng hợp
- Mục SRS trong phạm vi (SRS v0.2): 32 FR + 8 BR + 4 ST + 4 EV + 7 NFR = 55. Đã phủ đầy đủ: 42 · phủ một phần: 9 · bị chặn: 3 · không phủ theo tính năng: 1 (NFR-06).
- 55 TC: UNIT 10 · INTEGRATION 10 · API 26 · E2E 6 · NFR 3. Có 12 TC bị chặn toàn bộ hoặc một phần.

## F-ACC-01 — Đăng ký & đăng nhập ([test case](F-ACC-01-dang-ky-dang-nhap.md))

### Yêu cầu gốc (requirements.md)
| Yêu cầu | SRS | Test case | Phủ |
|---|---|---|---|
| FR-ID-01 | FR-01..14, BR-01..03, 06, 10 | TC-ACC-001..003, 010, 011, 020..026, 030..036 | ⚠️ TC-011 chờ OQ-024 |
| FR-ID-05 | FR-15..23, 38, 39, BR-04, 05, 07 | TC-ACC-005, 040..049, 070, 071, 086 | ⚠️ 2FA mỗi lần đăng nhập chờ OQ-030 |
| FR-ID-06 | FR-32..37, BR-08 | TC-ACC-060..063, 065..068, 088 | ⚠️ khuôn mặt chờ OQ-028 |
| NFR-S-02 | FR-27, 29, 30 | TC-ACC-053, 085, 089, 093 | ✅ |
| NFR-S-03 | FR-11, 13, 17, 43, BR-07 | TC-ACC-033, 035, 042, 043, 080, 091 | ✅ (giá trị chờ OQ-036) |
| NFR-L-03 | FR-04, 23, 40 | TC-ACC-022, 023, 040, 072 | ✅ |

### Chi tiết theo SRS
| SRS | Test case | Mức | Phủ |
|---|---|---|---|
| FR-01, FR-02 | 020, 021 | API | ✅ |
| FR-03..FR-05, FR-07 | 022, 023, 026, 011 | API, INTEGRATION | ⚠️ FR-05 nhánh lỗi chờ OQ-024 |
| FR-06 | 010 | INTEGRATION | ✅ |
| FR-08 | 025 | API | ✅ |
| FR-09..FR-14 | 006, 030..036 | UNIT, API | ✅ |
| FR-15..FR-23 | 040..049 | API | ✅ |
| FR-24..FR-28 | 050..054, 012 | API, INTEGRATION | ⚠️ FR-26 chờ OQ-037 |
| FR-29, FR-30 | 085, 089 | E2E | ✅ (chạy được khi có web) |
| FR-31 | 055 | API | ✅ |
| FR-32..FR-34 | 060..063 | API | ✅ |
| FR-35..FR-37 | 065..068 | API | ⚠️ OQ-028, OQ-045 |
| FR-38..FR-40 | 070..072 | API | ✅ |
| FR-41, FR-42 | 075, 076 | API | ✅ |
| FR-43 | 080 | API | ✅ |
| BR-01..BR-03 | 001..003 | UNIT | ✅ |
| BR-04 | 005, 045 | UNIT, API | ⚠️ OQ-032 |
| BR-05, BR-06 | 048, 023, 024, 062 | API | ✅ |
| BR-07, BR-08 | 042, 043, 061, 063 | API | ✅ |
| BR-09 | 046, 092 | API, NFR | ✅ |
| BR-10 | 010, 066 | INTEGRATION, API | ✅ |
| ST-01..ST-05 | 022, 044, 049, 055 | API | ✅ |
| ST-06..ST-11 | 020, 025, 030, 031, 033, 034, 076 | API | ✅ |
| ST-12..ST-15 | 051, 052, 054 | API | ✅ |
| EV-01, EV-02 | 022, 065, 004 | API, UNIT | ⚠️ `displayMasked` chờ OQ-009, 047 |
| EV-03..EV-07 | 020, 021, 046, 061, 063, 065 | API | ⚠️ kênh gửi chờ OQ-040, 051 |
| EV-08 | 081 | API | ⚠️ TQ1 (OQ-052) |
| EV-09 | 052, 094 | API, NFR | ✅ |
| NFR-01..NFR-07 | 090..094, 092 | NFR | ✅ (NFR-05 kiểm trên staging) |

### Tiêu chí chấp nhận
Mọi AC-01..53 đều có ít nhất một TC. AC bị chặn hoặc chặn một phần: AC-08 (OQ-024), AC-41, AC-43 (OQ-028), AC-51 (TQ1).

### Tổng hợp
- Mục SRS trong phạm vi (SRS v0.4): 43 FR + 10 BR + 15 ST + 9 EV + 7 NFR = 84, tất cả có TC.
- 64 TC: UNIT 6 · INTEGRATION 3 · API 45 · E2E 5 · NFR 5. Bị chặn toàn bộ: 1 (TC-ACC-011); chặn một phần: 3 (TC-ACC-004, 065, 067).
