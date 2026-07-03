package com.strataresolve.shared.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * JDBC-based implementation of MembershipCheckRepository.
 * Uses JdbcTemplate directly to avoid depending on JPA entities
 * that may not exist yet during early project scaffolding.
 *
 * This will be superseded or delegated to by the full MembershipRepository
 * once the user module is implemented (task 3.5).
 */
@Repository
public class JdbcMembershipCheckRepository implements MembershipCheckRepository {

    private static final String CHECK_MEMBERSHIP_SQL =
            "SELECT COUNT(*) FROM membership WHERE user_id = ? AND property_id = ? AND is_active = true";

    private final JdbcTemplate jdbcTemplate;

    public JdbcMembershipCheckRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasActiveMembership(UUID userId, UUID propertyId) {
        Integer count = jdbcTemplate.queryForObject(
                CHECK_MEMBERSHIP_SQL,
                Integer.class,
                userId,
                propertyId
        );
        return count != null && count > 0;
    }
}
