package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.CourseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseDocumentRepository extends JpaRepository<CourseDocument, Long> {

    List<CourseDocument> findByCourseContentIdOrderByUploadedAtAsc(Long courseContentId);

    Optional<CourseDocument> findByIdAndCourseContentId(Long id, Long courseContentId);

    void deleteByCourseContentId(Long courseContentId);
}
