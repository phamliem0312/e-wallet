package com.ewallet.identity.infrastructure;

import static com.ewallet.identity.infrastructure.Sql.instant;
import static com.ewallet.identity.infrastructure.Sql.ts;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bảng otp_challenge. Mọi chuyển trạng thái là UPDATE có điều kiện (ST-06..11). */
@Repository
public class ChallengeStore {

    public record Challenge(UUID id, UUID customerId, String phone, String purpose, String factor,
            byte[] payloadHash, byte[] codeHash, int attempts, Instant expiresAt, Instant consumedAt,
            Instant supersededAt, String consumerRef) {}

    private static final RowMapper<Challenge> CHALLENGE = (rs, n) -> new Challenge(rs.getObject("id", UUID.class),
            rs.getObject("customer_id", UUID.class), rs.getString("phone"), rs.getString("purpose"),
            rs.getString("factor"), rs.getBytes("payload_hash"), rs.getBytes("code_hash"), rs.getInt("attempts"),
            instant(rs, "expires_at"), instant(rs, "consumed_at"), instant(rs, "superseded_at"),
            rs.getString("consumer_ref"));

    private static final byte[] NO_PAYLOAD = new byte[0];

    private final JdbcClient jdbc;

    public ChallengeStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Challenge> find(UUID id) {
        return jdbc.sql("select * from identity.otp_challenge where id = ?").param(id).query(CHALLENGE).optional();
    }

    /**
     * Vô hiệu challenge còn sống cùng (mục đích, yếu tố, đối tượng, nội dung) rồi thêm challenge mới (FR-12).
     * Gọi trong một transaction. {@code uq_challenge_active} chặn hai challenge cùng sống khi chạy đồng thời.
     */
    public void replace(UUID id, UUID customerId, String phone, String purpose, String factor, byte[] payloadHash,
            byte[] codeHash, Instant now, Instant expiresAt) {
        jdbc.sql("update identity.otp_challenge set superseded_at = ? where purpose = ? and factor = ? "
                        + "and coalesce(customer_id::text, phone) = ? and coalesce(payload_hash, ''::bytea) = ? "
                        + "and consumed_at is null and superseded_at is null")
                .params(ts(now), purpose, factor, customerId == null ? phone : customerId.toString(),
                        payloadHash == null ? NO_PAYLOAD : payloadHash)
                .update();
        jdbc.sql("insert into identity.otp_challenge (id, customer_id, phone, purpose, factor, payload_hash, "
                        + "code_hash, created_at, expires_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, customerId, phone, purpose, factor, payloadHash, codeHash, ts(now), ts(expiresAt))
                .update();
    }

    public void supersede(UUID id, Instant now) {
        jdbc.sql("update identity.otp_challenge set superseded_at = ? where id = ? and consumed_at is null "
                + "and superseded_at is null").params(ts(now), id).update();
    }

    /**
     * Ghi nhận một lần nhập mã (đúng hay sai), nguyên tử (FR-11).
     * @return số lần đã nhập sau lần này; rỗng nếu challenge hết hạn, hết lượt, đã dùng hoặc đã bị thay
     */
    public OptionalInt registerAttempt(UUID id, Instant now, int maxAttempts) {
        return jdbc.sql("update identity.otp_challenge set attempts = attempts + 1 where id = ? "
                        + "and consumed_at is null and superseded_at is null and expires_at > ? and attempts < ? "
                        + "returning attempts").params(id, ts(now), maxAttempts).query(Integer.class).optional()
                .map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /** Chốt challenge đã dùng. {@code consumerRef} null với mục đích không phải giao dịch. Chỉ một bên thắng. */
    public boolean consume(UUID id, String consumerRef, Instant now) {
        return jdbc.sql("update identity.otp_challenge set consumed_at = ?, consumer_ref = ? where id = ? "
                        + "and consumed_at is null and superseded_at is null and expires_at > ?")
                .params(ts(now), consumerRef, id, ts(now)).update() == 1;
    }

    /** Challenge FACE đã đạt (consumed_at do F-ACC-02 đặt): gắn với đúng một lệnh (FR-42). */
    public boolean claimVerifiedFace(UUID id, String consumerRef, Instant now) {
        return jdbc.sql("update identity.otp_challenge set consumer_ref = ? where id = ? and factor = 'FACE' "
                        + "and consumed_at is not null and consumer_ref is null and expires_at > ?")
                .params(consumerRef, id, ts(now)).update() == 1;
    }

    /** Có challenge FACE đã đạt, còn hạn, cho đúng nội dung này không (FR-35: khuôn mặt trước, OTP sau). */
    public boolean hasVerifiedFace(UUID customerId, String purpose, byte[] payloadHash, Instant now) {
        return jdbc.sql("select count(*) from identity.otp_challenge where customer_id = ? and purpose = ? "
                        + "and factor = 'FACE' and payload_hash = ? and consumed_at is not null and expires_at > ?")
                .params(customerId, purpose, payloadHash, ts(now)).query(Long.class).single() > 0;
    }
}
