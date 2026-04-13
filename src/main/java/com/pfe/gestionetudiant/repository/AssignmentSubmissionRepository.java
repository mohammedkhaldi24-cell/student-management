package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    List<AssignmentSubmission> findByAssignmentIdOrderBySubmittedAtDesc(Long assignmentId);

    Optional<AssignmentSubmission> findByIdAndAssignmentId(Long submissionId, Long assignmentId);

    List<AssignmentSubmission> findByStudentIdAndStatusInOrderBySubmittedAtDesc(Long studentId,
                                                                                List<SubmissionStatus> statuses);

    long countByAssignmentId(Long assignmentId);
}

