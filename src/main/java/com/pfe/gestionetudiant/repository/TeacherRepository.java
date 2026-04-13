package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Teacher;
import com.pfe.gestionetudiant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByUser(User user);

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
