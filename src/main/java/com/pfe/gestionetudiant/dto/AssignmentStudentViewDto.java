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

    public String getDisplayStatus() {
        if (status == SubmissionStatus.GRADED || status == SubmissionStatus.REVIEWED) {
            return "reviewed";
        }
        if (late || (status == SubmissionStatus.NOT_SUBMITTED && overdue)) {
            return "late";
        }
        if (status != SubmissionStatus.NOT_SUBMITTED) {
            return "submitted";
        }
        return "pending";
    }

    public String getDisplayStatusLabel() {
        return switch (getDisplayStatus()) {
            case "reviewed" -> "reviewed";
            case "late" -> "late";
            case "submitted" -> "submitted";
            default -> "pending";
        };
    }

    public String getDisplayStatusBadgeClass() {
        return switch (getDisplayStatus()) {
            case "reviewed" -> "bg-success";
            case "late" -> "bg-danger";
            case "submitted" -> "bg-primary";
            default -> "bg-warning text-dark";
        };
    }
}
