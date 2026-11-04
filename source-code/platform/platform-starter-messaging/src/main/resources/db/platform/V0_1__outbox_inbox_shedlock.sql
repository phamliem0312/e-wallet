-- Bảng dùng chung trong mọi service (data model: "Bảng dùng chung trong mọi service").
-- Service dùng: spring.flyway.locations=classpath:db/migration,classpath:db/platform

create table outbox (
  id            uuid primary key,          -- = CloudEvents id
  topic         varchar(128) not null,
  event_key     varchar(128) not null,     -- id của aggregate, dùng làm key Kafka
  event_type    varchar(128) not null,
  payload       jsonb not null,            -- phần `data` của CloudEvents
  headers       jsonb not null,            -- source, aggregateversion
  created_at    timestamptz not null default now(),
  published_at  timestamptz
);
create index ix_outbox_unpublished on outbox (created_at) where published_at is null;

create table inbox (
  event_id      uuid not null,
  consumer      varchar(64) not null,      -- tên handler, để một event có thể được nhiều handler trong cùng service xử lý
  processed_at  timestamptz not null default now(),
  primary key (event_id, consumer)
);

-- DDL chuẩn của ShedLock
create table shedlock (
  name        varchar(64) primary key,
  lock_until  timestamp(3) not null,
  locked_at   timestamp(3) not null,
  locked_by   varchar(255) not null
);
