package com.team.inventory.repository;

import com.team.inventory.dto.ProductDto;
import com.team.inventory.dto.ProductRequest;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Product add/edit/delete/list (stories 2, 3), with category (8), search (9) and low stock (6).
 *
 * Plain SQL with JdbcTemplate, same style as ProductStockRepository, so checkout keeps working
 * on the same `products` table without a JPA entity in between.
 */
@Repository
public class ProductRepository {

    private static final String SELECT = """
        SELECT p.id, p.name, p.sku, p.price, p.quantity, p.category_id, c.name AS category_name,
               p.low_stock_threshold
        FROM products p
        LEFT JOIN categories c ON c.id = p.category_id
        """;

    private static final RowMapper<ProductDto> ROW = (rs, n) -> {
        int quantity = rs.getInt("quantity");
        int threshold = rs.getInt("low_stock_threshold");
        long rawCategoryId = rs.getLong("category_id");
        Long categoryId = rs.wasNull() ? null : rawCategoryId;   // check wasNull right after the read
        return new ProductDto(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("sku"),
            rs.getBigDecimal("price"),
            quantity,
            categoryId,
            rs.getString("category_name"),
            threshold,
            quantity <= threshold);
    };

    private final JdbcTemplate jdbc;

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Products matching an optional search term (name or SKU, case-insensitive, partial match)
     * and an optional category. Exact SKU matches come first, so a scanned/typed SKU is the top hit.
     */
    public List<ProductDto> search(String query, Long categoryId) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            String like = "%" + escapeLike(q) + "%";
            sql.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ?)");
            args.add(like);
            args.add(like);
        }
        if (categoryId != null) {
            sql.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        sql.append(" ORDER BY CASE WHEN LOWER(p.sku) = ? THEN 0 ELSE 1 END, p.name, p.id");
        args.add(q);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    /** Story 6: products at or below their low-stock threshold, emptiest first. */
    public List<ProductDto> findLowStock() {
        return jdbc.query(SELECT + " WHERE p.quantity <= p.low_stock_threshold ORDER BY p.quantity, p.name, p.id", ROW);
    }

    public Optional<ProductDto> findById(long id) {
        return jdbc.query(SELECT + " WHERE p.id = ?", ROW, id).stream().findFirst();
    }

    /** True if another product (not excludeId) already has this SKU. */
    public boolean skuTaken(String sku, Long excludeId) {
        if (sku == null) return false;
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM products WHERE LOWER(sku) = LOWER(?) AND id <> ?",
            Integer.class, sku, excludeId == null ? -1L : excludeId);
        return n != null && n > 0;
    }

    public long insert(ProductRequest p) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO products (name, sku, price, quantity, category_id, low_stock_threshold) VALUES (?, ?, ?, ?, ?, ?)",
                new String[] {"id"});
            bind(ps, p);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    /** Returns false if the product doesn't exist. */
    public boolean update(long id, ProductRequest p) {
        return jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "UPDATE products SET name = ?, sku = ?, price = ?, quantity = ?, category_id = ?, low_stock_threshold = ? WHERE id = ?");
            bind(ps, p);
            ps.setLong(7, id);
            return ps;
        }) == 1;
    }

    /** True if the product appears on any recorded sale (then it can't be deleted). */
    public boolean hasSales(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sale_items WHERE product_id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM products WHERE id = ?", id) == 1;
    }

    private static void bind(PreparedStatement ps, ProductRequest p) throws java.sql.SQLException {
        ps.setString(1, p.name().trim());
        if (p.cleanSku() == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, p.cleanSku());
        ps.setBigDecimal(3, p.price());
        ps.setInt(4, p.quantity());
        if (p.categoryId() == null) ps.setNull(5, Types.BIGINT); else ps.setLong(5, p.categoryId());
        ps.setInt(6, p.thresholdOrDefault());
    }

    /** So a search for "50%" or "a_b" matches those characters literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
