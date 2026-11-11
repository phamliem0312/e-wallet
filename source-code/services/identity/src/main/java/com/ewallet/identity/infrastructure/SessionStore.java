package com.ewallet.identity.infrastructure;

import static com.ewallet.identity.infrastructure.Sql.instant;
import static com.ewallet.identity.infrastructure.Sql.ts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bảng device và refresh_token. Trạng thái token suy từ used_at, revoked_at, expires_at (ST-12..15). */
@Repository
public class SessionStore {

    public record Device(UUID id, String label, Instant trustedAt, Instant lastSeenAt) {}

    public record Token(UUID id, UUID customerId, UUID deviceId, UUID familyId, Instant familyExpiresAt,
            Instant usedAt, Instant revokedAt) {}

    private static final RowMapper<Device> DEVICE = (rs, n) -> new Device(rs.getObject("id", UUID.class),
            rs.getString("label"), instant(rs, "trusted_at"), instant(rs, "last_seen_at"));
    private static final RowMapper<Token> TOKEN = (rs, n) -> new Token(rs.getObject("id", UUID.class),
            rs.getObject("customer_id", UUID.class), rs.getObject("device_id", UUID.class),
            rs.getObject("family_id", UUID.class), instant(rs, "family_expires_at"), instant(rs, "used_at"),
            instant(rs, "revoked_at"));

    private final JdbcClient jdbc;

    public SessionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- device ----

    public Optional<Device> findDevice(UUID customerId, byte[] fingerprintHash) {
        return jdbc.sql("select * from identity.device where customer_id = ? and fingerprint_hash = ?")
                .params(customerId, fingerprintHash).query(DEVICE).optional();
    }

    /** Tin cậy thiết bị; thiết bị đã thu hồi được tin lại trên cùng dòng (FR-39). */
    public UUID trustDevice(UUID id, UUID customerId, byte[] fingerprintHash, String label, Instant now) {
        return jdbc.sql("insert into identity.device (id, customer_id, fingerprint_hash, label, trusted_at, "
                        + "last_seen_at, created_at) values (?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (customer_id, fingerprint_hash) do update "
                        + "set trusted_at = excluded.trusted_at, last_seen_at = excluded.last_seen_at returning id")
                .params(id, customerId, fingerprintHash, label, ts(now), ts(now), ts(now))
                .query(UUID.class).single();
    }

    public void touchDevice(UUID deviceId, Instant now) {
        jdbc.sql("update identity.device set last_seen_at = ? where id = ?").params(ts(now), deviceId).update();
    }

    public List<Device> trustedDevices(UUID customerId) {
        return jdbc.sql("select * from identity.device where customer_id = ? and trusted_at is not null "
                + "order by last_seen_at desc").param(customerId).query(DEVICE).list();
    }

    public boolean deviceExists(UUID customerId, UUID deviceId) {
        return jdbc.sql("select count(*) from identity.device where id = ? and customer_id = ?")
                .params(deviceId, customerId).query(Long.class).single() == 1;
    }

    public void untrustDevice(UUID customerId, UUID deviceId) {
        jdbc.sql("update identity.device set trusted_at = null where id = ? and customer_id = ?")
                .params(deviceId, customerId).update();
    }

    // ---- refresh token ----

    public void insertToken(UUID id, UUID customerId, UUID deviceId, UUID familyId, byte[] tokenHash, Instant now,
            Instant expiresAt, Instant familyExpiresAt) {
        jdbc.sql("insert into identity.refresh_token (id, customer_id, device_id, family_id, token_hash, "
                        + "created_at, expires_at, family_expires_at) values (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, customerId, deviceId, familyId, tokenHash, ts(now), ts(expiresAt), ts(familyExpiresAt))
                .update();
    }

    /** Đánh dấu đã dùng; chỉ một yêu cầu thắng (FR-25). Rỗng = token đã dùng, đã thu hồi, hết hạn hoặc không có. */
    public Optional<Token> useToken(byte[] tokenHash, Instant now) {
        return jdbc.sql("update identity.refresh_token set used_at = ? where token_hash = ? and used_at is null "
                        + "and revoked_at is null and expires_at > ? returning *")
                .params(ts(now), tokenHash, ts(now)).query(TOKEN).optional();
    }

    public Optional<Token> findToken(byte[] tokenHash) {
        return jdbc.sql("select * from identity.refresh_token where token_hash = ?").param(tokenHash)
                .query(TOKEN).optional();
    }

    public Optional<UUID> deviceOfFamily(UUID customerId, UUID familyId) {
        return jdbc.sql("select device_id from identity.refresh_token where customer_id = ? and family_id = ? "
                + "limit 1").params(customerId, familyId).query(UUID.class).optional();
    }

    public int revokeFamily(UUID customerId, UUID familyId, Instant now) {
        return jdbc.sql("update identity.refresh_token set revoked_at = ? where customer_id = ? and family_id = ? "
                + "and revoked_at is null").params(ts(now), customerId, familyId).update();
    }

    /** Thu hồi mọi phiên của khách, trừ family {@code keepFamilyId} nếu khác null (BR-08). */
    public int revokeAll(UUID customerId, UUID keepFamilyId, Instant now) {
        if (keepFamilyId == null) {
            return jdbc.sql("update identity.refresh_token set revoked_at = ? where customer_id = ? "
                    + "and revoked_at is null").params(ts(now), customerId).update();
        }
        return jdbc.sql("update identity.refresh_token set revoked_at = ? where customer_id = ? "
                + "and revoked_at is null and family_id <> ?").params(ts(now), customerId, keepFamilyId).update();
    }

    public int revokeDevice(UUID customerId, UUID deviceId, Instant now) {
        return jdbc.sql("update identity.refresh_token set revoked_at = ? where customer_id = ? and device_id = ? "
                + "and revoked_at is null").params(ts(now), customerId, deviceId).update();
    }
}
