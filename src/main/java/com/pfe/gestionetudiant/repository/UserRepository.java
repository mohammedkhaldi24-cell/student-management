package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByRoleAndEnabled(Role role, boolean enabled);

    @Query("SELECT u FROM User u WHERE u.role != 'ADMIN' AND u.enabled = true ORDER BY u.lastName")
    List<User> findAllNonAdminUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(Role role);
}
