package com.pfe.gestionetudiant.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * DTOs JSON pour l'application mobile Android.
 */
public final class MobileDtos {

    private MobileDtos() {
    }

    public record ApiMessage(String message) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AuthResponse(boolean authenticated, UserSummary user, String message) {
    }

    public record UserSummary(Long id,
                              String username,
                              String fullName,
                              String email,
                              String role,
                              String redirectPath) {
    }

    public record TopStudentItem(Long studentId,
                                 String name,
                                 Double average) {
    }

    public record StudentProfile(Long id,
                                 String matricule,
                                 String fullName,
                                 String email,
                                 String classe,
                                 String filiere,
                                 String telephone,
                                 String adresse,
                                 LocalDate dateNaissance,
                                 String photoUrl) {
    }

    public record TeacherProfile(Long id,
                                 String fullName,
                                 String email,
                                 String grade,
                                 String specialite,
                                 String telephone,
                                 String bureau) {
    }

    public record NoteItem(Long id,
                           Long studentId,
                           String studentName,
                           String matricule,
                           Long moduleId,
                           String moduleNom,
                           String moduleCode,
                           Integer coefficient,
                           Double noteCc,
                           Double noteExamen,
                           Double noteFinal,
                           String semestre,
                           String anneeAcademique,
                           String statut,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt,
                           String actorName) {
    }

    public record AbsenceItem(Long id,
                              Long studentId,
                              String studentName,
                              String matricule,
                              Long moduleId,
                              String moduleNom,
                              String moduleCode,
                              LocalDate dateAbsence,
                              Integer nombreHeures,
                              boolean justifiee,
                              String motif,
                              LocalDateTime createdAt,
                              String actorName) {
    }

    public record TimetableItem(Long id,
                                String jour,
                                LocalTime heureDebut,
                                LocalTime heureFin,
                                Long moduleId,
                                String moduleNom,
                                String moduleCode,
                                Long teacherId,
                                String teacherName,
                                Long classeId,
                                String classeNom,
                                Long filiereId,
                                String filiereNom,
                                String salle,
                                boolean valide) {
    }

    public record CourseItem(Long id,
                             String title,
                             String description,
                             String filePath,
                             String fileName,
                             Long moduleId,
                             String moduleNom,
                             String moduleCode,
                             Long teacherId,
                             String teacherName,
                             Long classeId,
                             String classeNom,
                             Long filiereId,
                             String filiereNom,
                             LocalDateTime createdAt,
                             List<CourseDocumentItem> files) {
    }

    public record CourseDocumentItem(Long id,
                                     String filePath,
                                     String fileName,
                                     String contentType,
                                     Long fileSize,
                                     LocalDateTime uploadedAt) {
    }

    public record AnnouncementItem(Long id,
                                   String title,
                                   String message,
                                   String attachmentPath,
                                   String attachmentName,
                                   Long authorId,
                                   String authorName,
                                   Long targetClasseId,
                                   String targetClasseNom,
                                   Long targetFiliereId,
                                   String targetFiliereNom,
                                   LocalDateTime createdAt) {
    }

    public record AssignmentItem(Long id,
                                 String title,
                                 String description,
                                 LocalDateTime createdAt,
                                 LocalDateTime dueDate,
                                 String attachmentPath,
                                 String attachmentName,
                                 Long teacherId,
                                 String teacherName,
                                 Long moduleId,
                                 String moduleNom,
                                 String moduleCode,
                                 Long targetClasseId,
                                 String targetClasseNom,
                                 Long targetFiliereId,
                                 String targetFiliereNom,
                                 boolean published,
                                 String submissionStatus,
                                 LocalDateTime submittedAt,
                                 boolean lateSubmission,
                                 Double score,
                                 String feedback,
                                 boolean overdue,
                                 boolean upcoming) {
    }

    public record AssignmentSubmissionItem(Long id,
                                           Long assignmentId,
                                           String assignmentTitle,
                                           Long studentId,
                                           String studentName,
                                           String matricule,
                                           String submissionText,
                                           String filePath,
                                           String fileName,
                                           List<SubmissionFileItem> files,
                                           LocalDateTime submittedAt,
                                           boolean lateSubmission,
                                           Double score,
                                           String feedback,
                                           String status) {
    }

    public record SubmissionFileItem(Long id,
                                     String filePath,
                                     String fileName,
                                     String contentType,
                                     Long fileSize,
                                     LocalDateTime uploadedAt) {
    }

    public record NotificationItem(String eventId,
                                   String type,
                                   String title,
                                   String message,
                                   LocalDateTime createdAt,
                                   String actionPath,
                                   boolean emailRelated) {
    }

    public record StudentDashboard(double moyenneS1,
                                   double moyenneS2,
                                   double moyenneGenerale,
                                   int totalAbsenceHours,
                                   int totalNonJustifiedHours,
                                   long overdueAssignmentsCount,
                                   List<AssignmentItem> upcomingAssignments,
                                   List<AnnouncementItem> recentAnnouncements,
                                   List<CourseItem> recentCourses,
                                   List<TimetableItem> upcomingSessions,
                                   List<NotificationItem> notifications) {
    }

    public record TeacherDashboard(long totalModules,
                                   long totalStudents,
                                   long totalCourses,
                                   long totalAnnouncements,
                                   long totalAssignments,
                                   long pendingSubmissions,
                                   List<AssignmentItem> recentAssignments,
                                   List<CourseItem> recentCourses,
                                   List<AnnouncementItem> recentAnnouncements) {
    }

    public record AdminDashboard(long totalUsers,
                                 long totalStudents,
                                 long totalTeachers,
                                 long totalChefs,
                                 long totalFilieres,
                                 long totalClasses,
                                 long totalModules,
                                 long totalNotes,
                                 long totalAbsences) {
    }

    public record ChefDashboard(String filiereNom,
                                long totalClasses,
                                long totalStudents,
                                long totalCourses,
                                long totalAnnouncements,
                                long totalAbsences) {
    }

    public record TeacherModuleItem(Long id,
                                    String nom,
                                    String code,
                                    Integer coefficient,
                                    Integer volumeHoraire,
                                    String semestre,
                                    Long filiereId,
                                    String filiereNom,
                                    Long teacherId,
                                    String teacherName) {
    }

    public record StudentModuleItem(Long id,
                                    String nom,
                                    String code,
                                    String semestre,
                                    Integer volumeHoraire,
                                    Long teacherId,
                                    String teacherName,
                                    Long classeId,
                                    String classeNom,
                                    Long filiereId,
                                    String filiereNom) {
    }

    public record ClasseItem(Long id,
                             String nom,
                             Long filiereId,
                             String filiereNom) {
    }

    public record NoteUpsertRequest(Long studentId,
                                    Long moduleId,
                                    String semestre,
                                    String anneeAcademique,
                                    Double noteCc,
                                    Double noteExamen) {
    }

    public record NoteBulkItem(Long studentId,
                               Double noteCc,
                               Double noteExamen) {
    }

    public record NoteBulkRequest(Long moduleId,
                                  String semestre,
                                  String anneeAcademique,
                                  List<NoteBulkItem> notes) {
    }

    public record AbsenceCreateRequest(Long studentId,
                                       Long moduleId,
                                       LocalDate dateAbsence,
                                       Integer nombreHeures) {
    }

    public record AbsenceSessionRequest(Long moduleId,
                                        Long classeId,
                                        LocalDate dateAbsence,
                                        Integer nombreHeures,
                                        List<Long> absentStudentIds) {
    }

    public record AbsenceSessionResponse(String message,
                                         List<AbsenceItem> absences) {
    }

    public record AnnouncementCreateRequest(String title,
                                            String message,
                                            Long classeId,
                                            Long filiereId,
                                            Long moduleId) {
    }

    public record SubmissionReviewRequest(Double score,
                                          String feedback,
                                          String status) {
    }

    public record AdminUserUpsertRequest(String username,
                                         String password,
                                         String email,
                                         String firstName,
                                         String lastName,
                                         String role,
                                         Boolean enabled,
                                         String matricule,
                                         Long classeId,
                                         String specialite,
                                         String grade,
                                         Long filiereId) {
    }

    public record AdminFiliereUpsertRequest(String nom,
                                            String code,
                                            String description,
                                            Long chefFiliereId) {
    }

    public record AdminClasseUpsertRequest(String nom,
                                           String niveau,
                                           String anneeAcademique,
                                           Long filiereId) {
    }

    public record AdminModuleUpsertRequest(String nom,
                                           String code,
                                           Integer coefficient,
                                           Integer volumeHoraire,
                                           String semestre,
                                           Long filiereId,
                                           Long teacherId) {
    }

    public record AdminTimetableUpsertRequest(String jour,
                                              String heureDebut,
                                              String heureFin,
                                              Long moduleId,
                                              Long classeId,
                                              Long filiereId,
                                              Long teacherId,
                                              String salle,
                                              Boolean valide) {
    }
}
