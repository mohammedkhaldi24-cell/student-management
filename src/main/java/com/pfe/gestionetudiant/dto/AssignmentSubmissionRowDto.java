package com.pfe.gestionetudiant.dto;

import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentSubmissionRowDto {
    private Student student;
    private AssignmentSubmission submission;
    private SubmissionStatus status;
    private boolean late;
}

