package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.model.Teacher;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.util.FileUiUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Component
public class MobileApiMapper {

    public MobileDtos.UserSummary toUserSummary(User user, String redirectPath) {
        return new MobileDtos.UserSummary(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                redirectPath
        );
    }

    public MobileDtos.StudentProfile toStudentProfile(Student student) {
        String classe = student.getClasse() != null ? student.getClasse().getNom() : null;
        String filiere = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getNom()
                : null;

        return new MobileDtos.StudentProfile(
                student.getId(),
                student.getMatricule(),
                student.getFullName(),
                student.getEmail(),
                classe,
                filiere,
                student.getTelephone(),
                student.getAdresse(),
                student.getDateNaissance(),
                student.getPhotoUrl()
        );
    }

    public MobileDtos.TeacherProfile toTeacherProfile(Teacher teacher) {
        return new MobileDtos.TeacherProfile(
                teacher.getId(),
                teacher.getFullName(),
                teacher.getUser() != null ? teacher.getUser().getEmail() : null,
                teacher.getGrade(),
                teacher.getSpecialite(),
                teacher.getTelephone(),
                teacher.getBureau()
        );
    }

    public MobileDtos.NoteItem toNoteItem(Note note) {
        return new MobileDtos.NoteItem(
                note.getId(),
                note.getStudent() != null ? note.getStudent().getId() : null,
                note.getStudent() != null ? note.getStudent().getFullName() : null,
                note.getStudent() != null ? note.getStudent().getMatricule() : null,
                note.getModule() != null ? note.getModule().getId() : null,
                note.getModule() != null ? note.getModule().getNom() : null,
                note.getModule() != null ? note.getModule().getCode() : null,
                note.getModule() != null ? note.getModule().getCoefficient() : null,
                note.getNoteCC(),
                note.getNoteExamen(),
                note.getNoteFinal(),
                note.getSemestre(),
                note.getAnneeAcademique(),
                note.getStatut()
        );
    }

    public MobileDtos.AbsenceItem toAbsenceItem(Absence absence) {
        return new MobileDtos.AbsenceItem(
                absence.getId(),
                absence.getStudent() != null ? absence.getStudent().getId() : null,
                absence.getStudent() != null ? absence.getStudent().getFullName() : null,
                absence.getStudent() != null ? absence.getStudent().getMatricule() : null,
                absence.getModule() != null ? absence.getModule().getId() : null,
                absence.getModule() != null ? absence.getModule().getNom() : null,
                absence.getModule() != null ? absence.getModule().getCode() : null,
                absence.getDateAbsence(),
                absence.getNombreHeures(),
                absence.isJustifiee(),
                absence.getMotif()
        );
    }

    public MobileDtos.TimetableItem toTimetableItem(EmploiDuTemps emploiDuTemps) {
        return new MobileDtos.TimetableItem(
                emploiDuTemps.getId(),
                emploiDuTemps.getJour(),
                emploiDuTemps.getHeureDebut(),
                emploiDuTemps.getHeureFin(),
                emploiDuTemps.getModule() != null ? emploiDuTemps.getModule().getId() : null,
                emploiDuTemps.getModule() != null ? emploiDuTemps.getModule().getNom() : null,
                emploiDuTemps.getModule() != null ? emploiDuTemps.getModule().getCode() : null,
                emploiDuTemps.getTeacher() != null ? emploiDuTemps.getTeacher().getId() : null,
                emploiDuTemps.getTeacher() != null ? emploiDuTemps.getTeacher().getFullName() : null,
                emploiDuTemps.getClasse() != null ? emploiDuTemps.getClasse().getId() : null,
                emploiDuTemps.getClasse() != null ? emploiDuTemps.getClasse().getNom() : null,
                emploiDuTemps.getFiliere() != null ? emploiDuTemps.getFiliere().getId() : null,
                emploiDuTemps.getFiliere() != null ? emploiDuTemps.getFiliere().getNom() : null,
                emploiDuTemps.getSalle(),
                emploiDuTemps.isValide()
        );
    }

    public MobileDtos.CourseItem toCourseItem(CourseContent courseContent, String downloadUrl) {
        return new MobileDtos.CourseItem(
                courseContent.getId(),
                courseContent.getTitle(),
                courseContent.getDescription(),
                downloadUrl,
                FileUiUtils.fileName(courseContent.getFilePath()),
                courseContent.getModule() != null ? courseContent.getModule().getId() : null,
                courseContent.getModule() != null ? courseContent.getModule().getNom() : null,
                courseContent.getModule() != null ? courseContent.getModule().getCode() : null,
                courseContent.getTeacher() != null ? courseContent.getTeacher().getId() : null,
                courseContent.getTeacher() != null ? courseContent.getTeacher().getFullName() : null,
                courseContent.getClasse() != null ? courseContent.getClasse().getId() : null,
                courseContent.getClasse() != null ? courseContent.getClasse().getNom() : null,
                courseContent.getFiliere() != null ? courseContent.getFiliere().getId() : null,
                courseContent.getFiliere() != null ? courseContent.getFiliere().getNom() : null,
                courseContent.getCreatedAt()
        );
    }

    public MobileDtos.AnnouncementItem toAnnouncementItem(Announcement announcement) {
        return toAnnouncementItem(announcement, null);
    }

    public MobileDtos.AnnouncementItem toAnnouncementItem(Announcement announcement, String attachmentDownloadUrl) {
        return new MobileDtos.AnnouncementItem(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getMessage(),
                attachmentDownloadUrl,
                FileUiUtils.fileName(announcement.getAttachmentPath()),
                announcement.getAuthor() != null ? announcement.getAuthor().getId() : null,
                announcement.getAuthor() != null ? announcement.getAuthor().getFullName() : null,
                announcement.getTargetClasse() != null ? announcement.getTargetClasse().getId() : null,
                announcement.getTargetClasse() != null ? announcement.getTargetClasse().getNom() : null,
                announcement.getTargetFiliere() != null ? announcement.getTargetFiliere().getId() : null,
                announcement.getTargetFiliere() != null ? announcement.getTargetFiliere().getNom() : null,
                announcement.getCreatedAt()
        );
    }

    public MobileDtos.AssignmentItem toAssignmentItem(Assignment assignment,
                                                      AssignmentSubmission submission,
                                                      LocalDateTime now,
                                                      String attachmentDownloadUrl) {
        String submissionStatus = submission != null ? submission.getStatus().name() : SubmissionStatus.NOT_SUBMITTED.name();
        boolean submitted = submission != null;
        boolean overdue = !submitted && assignment.getDueDate() != null && assignment.getDueDate().isBefore(now);
        boolean upcoming = !submitted && !overdue;

        return new MobileDtos.AssignmentItem(
                assignment.getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getCreatedAt(),
                assignment.getDueDate(),
                attachmentDownloadUrl,
                FileUiUtils.fileName(assignment.getAttachmentPath()),
                assignment.getTeacher() != null ? assignment.getTeacher().getId() : null,
                assignment.getTeacher() != null ? assignment.getTeacher().getFullName() : null,
                assignment.getModule() != null ? assignment.getModule().getId() : null,
                assignment.getModule() != null ? assignment.getModule().getNom() : null,
                assignment.getModule() != null ? assignment.getModule().getCode() : null,
                assignment.getTargetClasse() != null ? assignment.getTargetClasse().getId() : null,
                assignment.getTargetClasse() != null ? assignment.getTargetClasse().getNom() : null,
                assignment.getTargetFiliere() != null ? assignment.getTargetFiliere().getId() : null,
                assignment.getTargetFiliere() != null ? assignment.getTargetFiliere().getNom() : null,
                assignment.isPublished(),
                submissionStatus,
                submission != null ? submission.getSubmittedAt() : null,
                submission != null && submission.isLateSubmission(),
                submission != null ? submission.getScore() : null,
                submission != null ? submission.getFeedback() : null,
                overdue,
                upcoming
        );
    }

    public MobileDtos.AssignmentSubmissionItem toSubmissionItem(AssignmentSubmission submission,
                                                                Function<AssignmentSubmissionFile, String> fileUrlResolver,
                                                                String legacyFileDownloadUrl) {
        List<MobileDtos.SubmissionFileItem> files = submission.getFiles() == null
                ? List.of()
                : submission.getFiles().stream()
                .sorted(Comparator.comparing(AssignmentSubmissionFile::getUploadedAt))
                .map(file -> new MobileDtos.SubmissionFileItem(
                        file.getId(),
                        fileUrlResolver != null ? fileUrlResolver.apply(file) : null,
                        FileUiUtils.fileName(file.getFilePath()),
                        file.getContentType(),
                        file.getFileSize(),
                        file.getUploadedAt()
                ))
                .toList();

        String filePath = files.isEmpty()
                ? legacyFileDownloadUrl
                : files.get(0).filePath();
        String fileName = files.isEmpty()
                ? FileUiUtils.fileName(submission.getFilePath())
                : files.get(0).fileName();

        return new MobileDtos.AssignmentSubmissionItem(
                submission.getId(),
                submission.getAssignment() != null ? submission.getAssignment().getId() : null,
                submission.getAssignment() != null ? submission.getAssignment().getTitle() : null,
                submission.getStudent() != null ? submission.getStudent().getId() : null,
                submission.getStudent() != null ? submission.getStudent().getFullName() : null,
                submission.getStudent() != null ? submission.getStudent().getMatricule() : null,
                submission.getSubmissionText(),
                filePath,
                fileName,
                files,
                submission.getSubmittedAt(),
                submission.isLateSubmission(),
                submission.getScore(),
                submission.getFeedback(),
                submission.getStatus() != null ? submission.getStatus().name() : SubmissionStatus.SUBMITTED.name()
        );
    }

    public MobileDtos.TeacherModuleItem toTeacherModuleItem(Module module) {
        return new MobileDtos.TeacherModuleItem(
                module.getId(),
                module.getNom(),
                module.getCode(),
                module.getCoefficient(),
                module.getVolumeHoraire(),
                module.getSemestre(),
                module.getFiliere() != null ? module.getFiliere().getId() : null,
                module.getFiliere() != null ? module.getFiliere().getNom() : null,
                module.getTeacher() != null ? module.getTeacher().getId() : null,
                module.getTeacher() != null ? module.getTeacher().getFullName() : null
        );
    }
}
