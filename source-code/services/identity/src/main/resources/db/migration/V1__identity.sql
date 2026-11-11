-- F-ACC-01: bảng của service identity. Nguồn: docs/design/2026-11-04-ewallet-data-model.md, mục identity.

create schema identity;

create table identity.customer (
  id          uuid primary key,                          -- UUIDv7
  phone       varchar(15) not null,                      -- E.164 đã chuẩn hoá (F-ACC-01.BR-01)
  full_name   varchar(128) not null,                     -- đã chuẩn hoá (BR-03)
  status      varchar(16) not null,
  kyc_tier    smallint not null default 0,               -- 0 = chưa eKYC (FR-03)
  version     bigint not null default 1,                 -- = aggregateversion của CustomerRegistered / CustomerUpdated
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  constraint uq_customer_phone      unique (phone),     -- BR-10: một SĐT một tài khoản, kể cả CLOSED
  constraint ck_customer_phone      check (phone ~ '^\+84[0-9]{9}$'),
  constraint ck_customer_status     check (status in ('PENDING','ACTIVE','LOCKED','CLOSED')),
  constraint ck_customer_tier       check (kyc_tier >= 0),
  constraint ck_customer_version    check (version >= 1),
  constraint ck_customer_full_name  check (char_length(full_name) >= 1
                                           and full_name = btrim(full_name)
                                           and full_name !~ '\s{2,}'
                                           and full_name !~ '[[:cntrl:]]')
);

create table identity.credential (
  customer_id    uuid primary key,
  password_hash  varchar(255) not null,                  -- Argon2id (NFR-02)
  failed_count   int not null default 0,                 -- BR-07
  locked_until   timestamptz,                            -- null = không bị khoá tạm
  changed_at     timestamptz not null default now(),
  constraint fk_credential_customer foreign key (customer_id) references identity.customer (id),
  constraint ck_credential_algo     check (password_hash like '$argon2id$%'),
  constraint ck_credential_failed   check (failed_count >= 0)
);

create table identity.device (
  id                uuid primary key,
  customer_id       uuid not null,
  fingerprint_hash  bytea not null,                      -- HMAC-SHA256 của dấu vân tay; không lưu bản gốc (FR-23)
  label             varchar(64),                         -- null = chưa đặt nhãn
  trusted_at        timestamptz,                         -- null = đã bị thu hồi tin cậy (FR-39)
  last_seen_at      timestamptz not null default now(),
  created_at        timestamptz not null default now(),
  constraint fk_device_customer     foreign key (customer_id) references identity.customer (id),
  constraint uq_device_fingerprint  unique (customer_id, fingerprint_hash),
  constraint ck_device_fingerprint  check (octet_length(fingerprint_hash) = 32)
);

create table identity.refresh_token (
  id                 uuid primary key,
  customer_id        uuid not null,
  device_id          uuid not null,
  family_id          uuid not null,                      -- chung cho mọi token xoay vòng từ một lần đăng nhập
  token_hash         bytea not null,                     -- SHA-256 của token (token ngẫu nhiên 256 bit)
  created_at         timestamptz not null default now(),
  expires_at         timestamptz not null,               -- = least(created_at + idle_timeout, family_expires_at)
  family_expires_at  timestamptz not null,               -- hạn tuyệt đối tính từ lúc đăng nhập (auth.refresh.ttl)
  used_at            timestamptz,                        -- null = chưa dùng để làm mới (ST-13)
  revoked_at         timestamptz,                        -- null = chưa thu hồi (ST-14)
  constraint fk_refresh_token_customer  foreign key (customer_id) references identity.customer (id),
  constraint fk_refresh_token_device    foreign key (device_id) references identity.device (id),
  constraint uq_refresh_token_hash      unique (token_hash),
  constraint ck_refresh_token_hash      check (octet_length(token_hash) = 32),
  constraint ck_refresh_token_expiry    check (expires_at > created_at and expires_at <= family_expires_at),
  constraint ck_refresh_token_used      check (used_at is null or used_at >= created_at)
);
create index ix_refresh_token_customer_active on identity.refresh_token (customer_id)
  where revoked_at is null;                                -- Q6

create table identity.otp_challenge (
  id             uuid primary key,
  customer_id    uuid,                                   -- chủ challenge; null chỉ khi purpose = REGISTER
  phone          varchar(15),                            -- nơi nhận OTP (E.164); null khi factor = FACE
  purpose        varchar(32) not null,
  factor         varchar(8) not null default 'OTP',
  payload_hash   bytea,                                  -- gắn challenge với đúng nội dung lệnh (F-ACC-01.FR-41)
  code_hash      bytea,                                  -- HMAC-SHA256(khoá KMS, id || mã); null khi FACE
  attempts       smallint not null default 0,            -- số lần nhập sai (FR-11)
  created_at     timestamptz not null default now(),
  expires_at     timestamptz not null,
  consumed_at    timestamptz,                            -- ST-07
  superseded_at  timestamptz,                            -- ST-10: bị thay bằng challenge mới, hoặc không giao được OTP
  consumer_ref   varchar(64),                            -- khoá idempotency của lệnh đã dùng challenge (FR-42)
  constraint fk_challenge_customer      foreign key (customer_id) references identity.customer (id),
  constraint ck_challenge_purpose       check (purpose in ('REGISTER','LOGIN_NEW_DEVICE','RESET_PASSWORD','CHANGE_PHONE','P2P','WITHDRAW')),
  constraint ck_challenge_factor        check (factor in ('OTP','FACE')),
  constraint ck_challenge_factor_purpose check (factor = 'OTP' or purpose in ('CHANGE_PHONE','P2P','WITHDRAW')),
  constraint ck_challenge_owner         check ((purpose = 'REGISTER') = (customer_id is null)),
  constraint ck_challenge_phone         check ((factor = 'OTP') = (phone is not null)),
  constraint ck_challenge_code          check ((factor = 'OTP') = (code_hash is not null)),
  constraint ck_challenge_code_len      check (code_hash is null or octet_length(code_hash) = 32),
  constraint ck_challenge_payload_req   check (purpose not in ('P2P','WITHDRAW') or payload_hash is not null),
  constraint ck_challenge_payload_none  check (purpose not in ('REGISTER','LOGIN_NEW_DEVICE','RESET_PASSWORD') or payload_hash is null),
  constraint ck_challenge_attempts      check (attempts >= 0),
  constraint ck_challenge_expiry        check (expires_at > created_at),
  constraint ck_challenge_terminal      check (consumed_at is null or superseded_at is null),
  constraint ck_challenge_consumer_ref  check (consumer_ref is null or (consumed_at is not null and purpose in ('P2P','WITHDRAW')))
);
-- Q8: tối đa một challenge còn sống cho cùng (mục đích, yếu tố, đối tượng, nội dung lệnh)
create unique index uq_challenge_active on identity.otp_challenge
  (purpose, factor, (coalesce(customer_id::text, phone)), (coalesce(payload_hash, '\x'::bytea)))
  where consumed_at is null and superseded_at is null;

create table identity.consent (
  id             uuid primary key,
  customer_id    uuid not null,
  purpose        varchar(32) not null,
  terms_version  varchar(16) not null,
  granted        boolean not null,
  ip             inet not null,
  user_agent     varchar(256) not null,
  created_at     timestamptz not null default now(),
  constraint fk_consent_customer foreign key (customer_id) references identity.customer (id),
  constraint ck_consent_purpose  check (purpose in ('TERMS','BIOMETRIC_PROCESSING','MARKETING'))
);
create index ix_consent_customer on identity.consent (customer_id, purpose, created_at desc);   -- Q9
-- Chỉ thêm (FR-04). Job ẩn danh chạy bằng role riêng chỉ có SELECT và UPDATE (ip, user_agent).
revoke update, delete on identity.consent from current_user;
