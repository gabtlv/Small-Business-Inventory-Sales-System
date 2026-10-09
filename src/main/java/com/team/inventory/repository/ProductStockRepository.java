package com.team.inventory.repository;

import com.team.inventory.dto.CheckoutProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * What checkout needs from the `products` table (story 5): price, stock, and decrementing stock.
 *
 * Uses plain SQL instead of a Product entity on purpose: the Product entity and product API
 * belong to stories 2-4, and this keeps sales from depending on (or clashing with) them.
 * Product add/edit/delete is NOT done here.
 */
@Repository
public class ProductStockRepository {

    private static final RowMapper<CheckoutProduct> ROW = (rs, n) -> new CheckoutProduct(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("sku"),
        rs.getBigDecimal("price"),
        rs.getInt("quantity"));

    private final JdbcTemplate jdbc;

    public ProductStockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every product with its current price and stock, for the checkout product picker. */
    public List<CheckoutProduct> findAll() {
        return jdbc.query("SELECT id, name, sku, price, quantity FROM products ORDER BY name, id", ROW);
    }

    public Optional<CheckoutProduct> findById(long id) {
        return jdbc.query("SELECT id, name, sku, price, quantity FROM products WHERE id = ?", ROW, id)
            .stream().findFirst();
    }

    /**
     * Reads one product and row-locks it until the current transaction ends, so no other sale
     * can change its stock in between our check and our update. Must be called in a transaction.
     */
    public Optional<CheckoutProduct> findByIdForUpdate(long id) {
        return jdbc.query("SELECT id, name, sku, price, quantity FROM products WHERE id = ? FOR UPDATE", ROW, id)
            .stream().findFirst();
    }

    /**
     * Takes stock away only if enough is left (a conditional update, so stock can never go below
     * zero even without the lock). Returns false if there wasn't enough stock.
     */
    public boolean decrementStock(long id, int amount) {
        return jdbc.update(
            "UPDATE products SET quantity = quantity - ? WHERE id = ? AND quantity >= ?",
            amount, id, amount) == 1;
    }
}
