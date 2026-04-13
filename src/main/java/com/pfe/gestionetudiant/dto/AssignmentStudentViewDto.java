package com.pfe.gestionetudiant.dto;

import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentStudentViewDto {
    private Assignment assignment;
    private AssignmentSubmission submission;
    private SubmissionStatus status;
    private boolean upcoming;
    private boolean overdue;
    private boolean late;
}

