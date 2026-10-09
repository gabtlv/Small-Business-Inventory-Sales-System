package com.team.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.team.inventory.dto.SaleRequest;
import com.team.inventory.model.Sale;
import com.team.inventory.repository.SaleRepository;
import com.team.inventory.service.SaleService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Story 5: if saving the sale fails AFTER stock was already taken away,
 * the stock change must be undone too (one transaction).
 *
 * Separate class because the spy gives it its own Spring context.
 */
@SpringBootTest
class SaleRollbackTests {

    @Autowired
    private SaleService saleService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private SaleRepository sales;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sale_request_keys");
        jdbc.update("DELETE FROM sale_items");
        jdbc.update("DELETE FROM sales");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void databaseErrorWhileSavingSaleLeavesStockUnchanged() {
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 10)");
        long pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);
        doThrow(new DataIntegrityViolationException("simulated database failure")).when(sales).save(any(Sale.class));

        assertThatThrownBy(() ->
            saleService.recordSale("staff", new SaleRequest(List.of(new SaleRequest.Item(pen, 3)))))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, pen)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_items", Integer.class)).isZero();
    }

    @Test
    void retryAfterServerErrorWithSameKeyRecordsTheSaleExactlyOnce() {
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 10)");
        long pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);
        SaleRequest request = new SaleRequest(List.of(new SaleRequest.Item(pen, 3)));

        // First attempt fails after stock was taken (the page would show "couldn't confirm")
        doThrow(new DataIntegrityViolationException("simulated database failure")).when(sales).save(any(Sale.class));
        assertThatThrownBy(() -> saleService.recordSale("staff", request, "uncertain-sale-1"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_request_keys", Integer.class)).isZero();

        // "Check again" sends the same key: recorded now, once
        reset(sales);
        var first = saleService.recordSale("staff", request, "uncertain-sale-1");
        var again = saleService.recordSale("staff", request, "uncertain-sale-1");

        assertThat(first.alreadyRecorded()).isFalse();
        assertThat(again.alreadyRecorded()).isTrue();
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, pen)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales", Integer.class)).isEqualTo(1);
    }

    @Test
    void saleIsSavedNormallyWhenNothingFails() {
        jdbc.update("INSERT INTO products (name, price, quantity) VALUES ('Pen', 2.50, 10)");
        long pen = jdbc.queryForObject("SELECT id FROM products WHERE name = 'Pen'", Long.class);

        var receipt = saleService.recordSale("staff", new SaleRequest(List.of(new SaleRequest.Item(pen, 3))));

        assertThat(receipt.total()).isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(jdbc.queryForObject("SELECT quantity FROM products WHERE id = ?", Integer.class, pen)).isEqualTo(7);
    }
}
