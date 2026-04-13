package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    Optional<Assignment> findByIdAndTeacherId(Long id, Long teacherId);

    long countByTeacherId(Long teacherId);

    @Query("""
            SELECT DISTINCT a
            FROM Assignment a
            WHERE a.published = true
              AND ((:classeId IS NOT NULL AND a.targetClasse.id = :classeId)
                   OR (:filiereId IS NOT NULL AND a.targetFiliere.id = :filiereId))
            ORDER BY a.dueDate ASC
            """)
    List<Assignment> findVisibleForStudent(@Param("classeId") Long classeId,
                                           @Param("filiereId") Long filiereId);

    @Query("""
            SELECT DISTINCT a
            FROM Assignment a
            WHERE a.id = :assignmentId
              AND a.published = true
              AND ((:classeId IS NOT NULL AND a.targetClasse.id = :classeId)
                   OR (:filiereId IS NOT NULL AND a.targetFiliere.id = :filiereId))
            """)
    Optional<Assignment> findVisibleByIdForStudent(@Param("assignmentId") Long assignmentId,
                                                    @Param("classeId") Long classeId,
                                                    @Param("filiereId") Long filiereId);
}

