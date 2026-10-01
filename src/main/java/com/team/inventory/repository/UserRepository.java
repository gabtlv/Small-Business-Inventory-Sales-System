package com.team.inventory.repository;

import com.team.inventory.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring generates the SQL for these methods automatically. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
