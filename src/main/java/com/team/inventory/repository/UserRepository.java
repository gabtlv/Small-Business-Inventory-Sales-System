package com.team.inventory.repository;

import com.team.inventory.model.Role;
import com.team.inventory.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring generates the SQL for these methods automatically. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Case-insensitive, matching MySQL's default comparison, so "Bob" and "bob" can't both exist. */
    Optional<User> findByUsernameIgnoreCase(String username);

    /** Accounts with this role that haven't been removed (staff management list, Sprint 3). */
    @Query("""
        select u from User u
        where u.role = :role
          and not exists (select d from DeactivatedUser d where d.userId = u.id)
        order by lower(u.username)
        """)
    List<User> findActiveByRole(@Param("role") Role role);
}
