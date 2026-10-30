package com.team.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A staff account an Admin has removed (Sprint 3). Maps to `deactivated_users` in schema.sql.
 *
 * The user row itself is kept so their past sales still point at them; this row just means
 * "can't log in any more".
 */
@Entity
@Table(name = "deactivated_users")
public class DeactivatedUser {

    @Id
    @Column(name = "user_id")
    private Long userId;

    protected DeactivatedUser() {
        // required by JPA
    }

    public DeactivatedUser(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
}
