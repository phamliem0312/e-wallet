#!/bin/sh
# Mỗi service một database và một user riêng (database-per-service). Tên DB theo data model.
# Chỉ chạy lần đầu khi volume còn trống. Mật khẩu dev: "ewallet" (khác prod).
set -e

for svc in identity kyc aml wallet ledger limit fee payment bank recon dispute approval audit notification; do
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres <<SQL
create role ${svc}_svc login password 'ewallet';
create database ${svc}_db owner ${svc}_svc;
-- Mặc định PUBLIC được CONNECT vào mọi database; thu hồi để service chỉ vào được DB của chính nó.
revoke connect on database ${svc}_db from public;
SQL
done
