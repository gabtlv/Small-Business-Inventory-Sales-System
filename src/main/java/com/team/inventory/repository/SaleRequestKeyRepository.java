package com.team.inventory.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Checkout attempt keys (story 5), so a retried POST /api/sales can't record the same sale twice.
 * See the `sale_request_keys` table in schema.sql.
 */
@Repository
public class SaleRequestKeyRepository {

    /** Who used a key, and which sale it produced. */
    public record ClaimedKey(long userId, Long saleId) {
    }

    private final JdbcTemplate jdbc;

    public SaleRequestKeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims a key for the current transaction. Throws DuplicateKeyException if it was already used.
     * If another transaction is using the same key right now, the database makes this wait for it.
     */
    public void claim(String key, long userId) {
        jdbc.update("INSERT INTO sale_request_keys (request_key, user_id) VALUES (?, ?)", key, userId);
    }

    public void attachSale(String key, long saleId) {
        jdbc.update("UPDATE sale_request_keys SET sale_id = ? WHERE request_key = ?", saleId, key);
    }

    public Optional<ClaimedKey> find(String key) {
        return jdbc.query("SELECT user_id, sale_id FROM sale_request_keys WHERE request_key = ?",
                (rs, n) -> new ClaimedKey(rs.getLong("user_id"), rs.getObject("sale_id", Long.class)), key)
            .stream().findFirst();
    }
}
