package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Announcement> findByTargetFiliereIdOrderByCreatedAtDesc(Long filiereId);

    Optional<Announcement> findByIdAndAuthorId(Long id, Long authorId);

    long countByAuthorId(Long authorId);

    @Query("""
            SELECT DISTINCT a
            FROM Announcement a
            WHERE (:classeId IS NOT NULL AND a.targetClasse.id = :classeId)
               OR (:filiereId IS NOT NULL AND a.targetFiliere.id = :filiereId)
            ORDER BY a.createdAt DESC
            """)
    List<Announcement> findVisibleForStudent(@Param("classeId") Long classeId,
                                             @Param("filiereId") Long filiereId);
}

