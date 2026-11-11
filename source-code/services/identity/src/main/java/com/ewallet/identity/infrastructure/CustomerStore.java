package com.ewallet.identity.infrastructure;

import static com.ewallet.identity.infrastructure.Sql.instant;
import static com.ewallet.identity.infrastructure.Sql.ts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Bảng customer, credential, consent. Câu SQL theo data model, mục identity. */
@Repository
public class CustomerStore {

    public record Customer(UUID id, String phone, String fullName, String status, int kycTier, long version) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    public record Credential(String passwordHash, int failedCount, Instant lockedUntil) {}

    public record Consent(String purpose, String termsVersion, boolean granted, Instant createdAt) {}

    private static final RowMapper<Customer> CUSTOMER = (rs, n) -> new Customer(rs.getObject("id", UUID.class),
            rs.getString("phone"), rs.getString("full_name"), rs.getString("status"), rs.getInt("kyc_tier"),
            rs.getLong("version"));
    private static final RowMapper<Credential> CREDENTIAL = (rs, n) -> new Credential(rs.getString("password_hash"),
            rs.getInt("failed_count"), instant(rs, "locked_until"));

    private final JdbcClient jdbc;

    public CustomerStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Customer> findByPhone(String phone) {
        return jdbc.sql("select * from identity.customer where phone = ?").param(phone).query(CUSTOMER).optional();
    }

    public Optional<Customer> findById(UUID id) {
        return jdbc.sql("select * from identity.customer where id = ?").param(id).query(CUSTOMER).optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException khi SĐT đã có tài khoản (uq_customer_phone) */
    public void insert(UUID id, String phone, String fullName, String passwordHash, Instant now) {
        jdbc.sql("insert into identity.customer (id, phone, full_name, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', ?, ?)").params(id, phone, fullName, ts(now), ts(now)).update();
        jdbc.sql("insert into identity.credential (customer_id, password_hash, changed_at) values (?, ?, ?)")
                .params(id, passwordHash, ts(now)).update();
    }

    public Credential credential(UUID customerId) {
        return jdbc.sql("select * from identity.credential where customer_id = ?").param(customerId)
                .query(CREDENTIAL).single();
    }

    /** Cộng bộ đếm nguyên tử; khoá khi đạt ngưỡng. Không cộng khi đang khoá (FR-17). */
    public Credential recordFailure(UUID customerId, Instant now, int maxFailures, Duration lockDuration) {
        jdbc.sql("update identity.credential set failed_count = failed_count + 1, "
                        + "locked_until = case when failed_count + 1 >= ? then ? else locked_until end "
                        + "where customer_id = ? and (locked_until is null or locked_until <= ?)")
                .params(maxFailures, ts(now.plus(lockDuration)), customerId, ts(now)).update();
        return credential(customerId);
    }

    public void resetFailures(UUID customerId) {
        jdbc.sql("update identity.credential set failed_count = 0, locked_until = null "
                + "where customer_id = ? and (failed_count <> 0 or locked_until is not null)").param(customerId).update();
    }

    public void updatePassword(UUID customerId, String passwordHash, Instant now) {
        jdbc.sql("update identity.credential set password_hash = ?, changed_at = ?, failed_count = 0, "
                + "locked_until = null where customer_id = ?").params(passwordHash, ts(now), customerId).update();
    }

    /**
     * @return version mới của khách (= aggregateversion của CustomerUpdated)
     * @throws org.springframework.dao.DuplicateKeyException khi SĐT mới đã thuộc tài khoản khác
     */
    public long updatePhone(UUID customerId, String newPhone, Instant now) {
        return jdbc.sql("update identity.customer set phone = ?, version = version + 1, updated_at = ? "
                + "where id = ? returning version").params(newPhone, ts(now), customerId).query(Long.class).single();
    }

    public void insertConsent(UUID id, UUID customerId, String purpose, String termsVersion, boolean granted,
            String ip, String userAgent, Instant now) {
        jdbc.sql("insert into identity.consent (id, customer_id, purpose, terms_version, granted, ip, user_agent, "
                        + "created_at) values (?, ?, ?, ?, ?, ?::inet, ?, ?)")
                .params(id, customerId, purpose, termsVersion, granted, ip, userAgent, ts(now)).update();
    }

    /** Đồng ý hiện tại = dòng mới nhất của mỗi mục đích (FR-40). */
    public List<Consent> currentConsents(UUID customerId) {
        return jdbc.sql("select distinct on (purpose) purpose, terms_version, granted, created_at "
                        + "from identity.consent where customer_id = ? order by purpose, created_at desc, id desc")
                .param(customerId)
                .query((rs, n) -> new Consent(rs.getString("purpose"), rs.getString("terms_version"),
                        rs.getBoolean("granted"), instant(rs, "created_at"))).list();
    }
}
