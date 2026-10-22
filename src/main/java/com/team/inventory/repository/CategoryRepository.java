package com.team.inventory.repository;

import com.team.inventory.dto.CategoryDto;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Product categories (story 8). */
@Repository
public class CategoryRepository {

    private static final String SELECT = """
        SELECT c.id, c.name, (SELECT COUNT(*) FROM products p WHERE p.category_id = c.id) AS product_count
        FROM categories c
        """;

    private static final RowMapper<CategoryDto> ROW =
        (rs, n) -> new CategoryDto(rs.getLong("id"), rs.getString("name"), rs.getInt("product_count"));

    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryDto> findAll() {
        return jdbc.query(SELECT + " ORDER BY c.name, c.id", ROW);
    }

    public Optional<CategoryDto> findById(long id) {
        return jdbc.query(SELECT + " WHERE c.id = ?", ROW, id).stream().findFirst();
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public boolean nameTaken(String name) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE LOWER(name) = LOWER(?)", Integer.class, name);
        return n != null && n > 0;
    }

    public long insert(String name) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO categories (name) VALUES (?)", new String[] {"id"});
            ps.setString(1, name);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    /** Products in the category become uncategorized; they are not deleted. */
    public void unassignProducts(long id) {
        jdbc.update("UPDATE products SET category_id = NULL WHERE category_id = ?", id);
    }

    public boolean delete(long id) {
        return jdbc.update("DELETE FROM categories WHERE id = ?", id) == 1;
    }
}
