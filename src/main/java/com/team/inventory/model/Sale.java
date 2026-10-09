package com.team.inventory.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One completed checkout (story 5). Maps to the `sales` table in schema.sql. */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The logged-in user who recorded the sale (taken from the session, never from the request). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User cashier;

    /** Calculated on the server from database prices. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("productId")
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() {
        // required by JPA
    }

    public Sale(User cashier) {
        this.cashier = cashier;
        this.total = BigDecimal.ZERO;
    }

    /** Adds a line item and keeps the sale total in sync. */
    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
        total = total.add(item.getLineTotal());
    }

    public Long getId() { return id; }
    public User getCashier() { return cashier; }
    public BigDecimal getTotal() { return total; }
    public List<SaleItem> getItems() { return items; }
}
