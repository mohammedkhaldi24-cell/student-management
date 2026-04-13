package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface AssignmentSubmissionService {

    AssignmentSubmission submitAssignment(Long assignmentId,
                                          Long studentId,
                                          String submissionText,
                                          MultipartFile[] files);

    default AssignmentSubmission submitAssignment(Long assignmentId,
                                                  Long studentId,
                                                  String submissionText,
                                                  MultipartFile file) {
        MultipartFile[] files = (file == null) ? null : new MultipartFile[]{file};
        return submitAssignment(assignmentId, studentId, submissionText, files);
    }

    Optional<AssignmentSubmission> findByAssignmentAndStudent(Long assignmentId, Long studentId);

    List<AssignmentSubmission> findByAssignmentForTeacher(Long assignmentId, Long teacherId);

    AssignmentSubmission reviewSubmission(Long assignmentId,
                                          Long submissionId,
                                          Long teacherId,
                                          Double score,
                                          String feedback,
                                          SubmissionStatus status);

    long countPendingSubmissionsForTeacher(Long teacherId);

    List<AssignmentSubmission> findRecentFeedbackForStudent(Long studentId, int maxItems);

    Resource loadSubmissionFile(AssignmentSubmission submission);

    Resource loadSubmissionFile(AssignmentSubmissionFile file);

    List<AssignmentSubmissionFile> findFilesForSubmission(Long submissionId);

    AssignmentSubmissionFile findFileForStudentSubmission(Long assignmentId, Long studentId, Long fileId);

    AssignmentSubmissionFile findFileForTeacherSubmission(Long assignmentId, Long teacherId, Long submissionId, Long fileId);

    AssignmentSubmission removeSubmissionFileByStudent(Long assignmentId, Long studentId, Long fileId);
}
