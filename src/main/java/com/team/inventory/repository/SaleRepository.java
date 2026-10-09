package com.team.inventory.repository;

import com.team.inventory.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

/** Saving a sale also saves its line items (cascade on Sale.items). */
public interface SaleRepository extends JpaRepository<Sale, Long> {
}
