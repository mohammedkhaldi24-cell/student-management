package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentSubmissionFileRepository extends JpaRepository<AssignmentSubmissionFile, Long> {

    List<AssignmentSubmissionFile> findBySubmissionIdOrderByUploadedAtAsc(Long submissionId);

    Optional<AssignmentSubmissionFile> findByIdAndSubmissionId(Long fileId, Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
