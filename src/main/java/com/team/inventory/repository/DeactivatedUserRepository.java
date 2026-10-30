package com.team.inventory.repository;

import com.team.inventory.model.DeactivatedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Removed staff accounts (Sprint 3). existsById(userId) = "is this account removed?" */
public interface DeactivatedUserRepository extends JpaRepository<DeactivatedUser, Long> {

    /** Used on login and on every signed-in request, to shut out removed accounts. */
    @Query("""
        select case when count(d) > 0 then true else false end
        from DeactivatedUser d, User u
        where d.userId = u.id and u.username = :username
        """)
    boolean isDeactivated(@Param("username") String username);
}
