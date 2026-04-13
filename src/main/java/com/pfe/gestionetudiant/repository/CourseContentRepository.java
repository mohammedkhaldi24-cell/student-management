package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.CourseContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseContentRepository extends JpaRepository<CourseContent, Long> {

    List<CourseContent> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    List<CourseContent> findByFiliereIdOrderByCreatedAtDesc(Long filiereId);

    Optional<CourseContent> findByIdAndTeacherId(Long id, Long teacherId);

    long countByTeacherId(Long teacherId);

    @Query("""
            SELECT DISTINCT c
            FROM CourseContent c
            WHERE (:classeId IS NOT NULL AND c.classe.id = :classeId)
               OR (:filiereId IS NOT NULL AND c.filiere.id = :filiereId)
            ORDER BY c.createdAt DESC
            """)
    List<CourseContent> findVisibleForStudent(@Param("classeId") Long classeId,
                                              @Param("filiereId") Long filiereId);
}

