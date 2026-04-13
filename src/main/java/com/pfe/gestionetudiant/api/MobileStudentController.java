package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/mobile/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class MobileStudentController {

    private static final String DEFAULT_ACADEMIC_YEAR = "2024-2025";

    private final MobileAccessService accessService;
    private final MobileApiMapper mapper;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final EmploiDuTempsService emploiDuTempsService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;

    @GetMapping("/profile")
    public MobileDtos.StudentProfile profile() {
        return mapper.toStudentProfile(accessService.currentStudent());
    }

    @GetMapping("/dashboard")
    public MobileDtos.StudentDashboard dashboard() {
        Student student = accessService.currentStudent();
        Long classeId = classeId(student);
        Long filiereId = filiereId(student);
        LocalDateTime now = LocalDateTime.now();

        double moyenneS1 = noteService.calculerMoyenneEtudiant(student.getId(), "S1", DEFAULT_ACADEMIC_YEAR);
        double moyenneS2 = noteService.calculerMoyenneEtudiant(student.getId(), "S2", DEFAULT_ACADEMIC_YEAR);
        double moyenneGenerale = Math.round(((moyenneS1 + moyenneS2) / 2.0) * 100.0) / 100.0;

        List<MobileDtos.CourseItem> recentCourses = courseContentService.findForStudent(classeId, filiereId).stream()
                .limit(5)
                .map(c -> mapper.toCourseItem(c, "/api/mobile/student/courses/" + c.getId() + "/download"))
                .toList();

        List<MobileDtos.AnnouncementItem> recentAnnouncements = announcementService.findForStudent(classeId, filiereId).stream()
                .limit(5)
                .map(a -> mapper.toAnnouncementItem(
                        a,
                        "/api/mobile/student/announcements/" + a.getId() + "/attachment"
                ))
                .toList();

        List<MobileDtos.TimetableItem> upcomingSessions = (classeId != null
                ? emploiDuTempsService.findByClasseId(classeId)
                : List.<com.pfe.gestionetudiant.model.EmploiDuTemps>of()).stream()
                .limit(6)
                .map(mapper::toTimetableItem)
                .toList();

        List<MobileDtos.AssignmentItem> assignmentViews = assignmentService.findVisibleForStudent(classeId, filiereId).stream()
                .map(a -> toStudentAssignmentItem(a, student.getId(), now))
                .toList();

        List<MobileDtos.AssignmentItem> upcomingAssignments = assignmentViews.stream()
                .filter(MobileDtos.AssignmentItem::upcoming)
                .limit(5)
                .toList();

        long overdueAssignmentsCount = assignmentViews.stream()
                .filter(MobileDtos.AssignmentItem::overdue)
                .count();

        List<MobileDtos.NotificationItem> notifications = new ArrayList<>();

        for (MobileDtos.AssignmentItem item : upcomingAssignments) {
            notifications.add(new MobileDtos.NotificationItem(
                    "ASSIGNMENT",
                    item.title(),
                    "Date limite: " + (item.dueDate() != null ? item.dueDate() : "-") ,
                    item.createdAt() != null ? item.createdAt() : now,
                    "/student/assignments/" + item.id()
            ));
        }

        for (MobileDtos.AnnouncementItem item : recentAnnouncements) {
            notifications.add(new MobileDtos.NotificationItem(
                    "ANNOUNCEMENT",
                    item.title(),
                    item.message(),
                    item.createdAt() != null ? item.createdAt() : now,
                    "/student/announcements"
            ));
        }

        for (AssignmentSubmission feedback : assignmentSubmissionService.findRecentFeedbackForStudent(student.getId(), 5)) {
            if (feedback.getAssignment() == null) {
                continue;
            }
            String title = feedback.getAssignment().getTitle();
            String message = feedback.getFeedback() != null ? feedback.getFeedback() : "Votre devoir a ete evalue.";
            notifications.add(new MobileDtos.NotificationItem(
                    "FEEDBACK",
                    title,
                    message,
                    feedback.getSubmittedAt() != null ? feedback.getSubmittedAt() : now,
                    "/student/assignments/" + feedback.getAssignment().getId()
            ));
        }

        notifications = notifications.stream()
                .sorted(Comparator.comparing(MobileDtos.NotificationItem::createdAt).reversed())
                .limit(10)
                .toList();

        return new MobileDtos.StudentDashboard(
                moyenneS1,
                moyenneS2,
                moyenneGenerale,
                absenceService.getTotalHeuresByStudent(student.getId()),
                absenceService.getTotalHeuresNonJustifiesByStudent(student.getId()),
                overdueAssignmentsCount,
                upcomingAssignments,
                recentAnnouncements,
                recentCourses,
                upcomingSessions,
                notifications
        );
    }

    @GetMapping("/notes")
    public List<MobileDtos.NoteItem> notes() {
        Student student = accessService.currentStudent();
        return noteService.findByStudentId(student.getId()).stream()
                .map(mapper::toNoteItem)
                .toList();
    }

    @GetMapping("/absences")
    public List<MobileDtos.AbsenceItem> absences() {
        Student student = accessService.currentStudent();
        return absenceService.findByStudentId(student.getId()).stream()
                .map(mapper::toAbsenceItem)
                .toList();
    }

    @GetMapping("/timetable")
    public List<MobileDtos.TimetableItem> timetable() {
        Student student = accessService.currentStudent();
        Long classeId = classeId(student);
        if (classeId == null) {
            return List.of();
        }
        return emploiDuTempsService.findByClasseId(classeId).stream()
                .map(mapper::toTimetableItem)
                .toList();
    }

    @GetMapping("/courses")
    public List<MobileDtos.CourseItem> courses() {
        Student student = accessService.currentStudent();
        Long classeId = classeId(student);
        Long filiereId = filiereId(student);
        return courseContentService.findForStudent(classeId, filiereId).stream()
                .map(c -> mapper.toCourseItem(c, "/api/mobile/student/courses/" + c.getId() + "/download"))
                .toList();
    }

    @GetMapping("/announcements")
    public List<MobileDtos.AnnouncementItem> announcements() {
        Student student = accessService.currentStudent();
        return announcementService.findForStudent(classeId(student), filiereId(student)).stream()
                .map(a -> mapper.toAnnouncementItem(
                        a,
                        "/api/mobile/student/announcements/" + a.getId() + "/attachment"
                ))
                .toList();
    }

    @GetMapping("/announcements/{id}/attachment")
    public ResponseEntity<Resource> downloadAnnouncementAttachment(@PathVariable Long id) {
        Student student = accessService.currentStudent();
        Announcement announcement = loadVisibleAnnouncement(id, student);
        Resource resource = announcementService.loadAttachmentAsResource(announcement);
        return MobileFileResponseBuilder.asDownload(resource, announcement.getAttachmentPath());
    }

    @GetMapping("/assignments")
    public List<MobileDtos.AssignmentItem> assignments(@RequestParam(defaultValue = "all") String filter) {
        Student student = accessService.currentStudent();
        LocalDateTime now = LocalDateTime.now();
        String normalizedFilter = filter == null ? "all" : filter.trim().toLowerCase(Locale.ROOT);

        List<MobileDtos.AssignmentItem> all = assignmentService.findVisibleForStudent(classeId(student), filiereId(student)).stream()
                .map(a -> toStudentAssignmentItem(a, student.getId(), now))
                .toList();

        return all.stream().filter(item -> switch (normalizedFilter) {
            case "upcoming" -> "NOT_SUBMITTED".equals(item.submissionStatus()) && item.upcoming();
            case "overdue" -> "NOT_SUBMITTED".equals(item.submissionStatus()) && item.overdue();
            case "submitted" -> !"NOT_SUBMITTED".equals(item.submissionStatus());
            case "not_submitted" -> "NOT_SUBMITTED".equals(item.submissionStatus());
            default -> true;
        }).toList();
    }

    @GetMapping("/assignments/{id}")
    public MobileDtos.AssignmentItem assignmentDetails(@PathVariable Long id) {
        Student student = accessService.currentStudent();
        Assignment assignment = loadVisibleAssignment(id, student);
        AssignmentSubmission submission = assignmentSubmissionService
                .findByAssignmentAndStudent(id, student.getId())
                .orElse(null);

        return mapper.toAssignmentItem(
                assignment,
                submission,
                LocalDateTime.now(),
                "/api/mobile/student/assignments/" + assignment.getId() + "/attachment"
        );
    }

    @GetMapping("/assignments/{id}/submission")
    public MobileDtos.AssignmentSubmissionItem assignmentSubmission(@PathVariable Long id) {
        Student student = accessService.currentStudent();
        loadVisibleAssignment(id, student);
        AssignmentSubmission submission = assignmentSubmissionService.findByAssignmentAndStudent(id, student.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucune soumission disponible."));

        String legacyFileUrl = submission.getFilePath() != null
                ? "/api/mobile/student/assignments/" + id + "/submission-file"
                : null;

        return mapper.toSubmissionItem(
                submission,
                file -> "/api/mobile/student/assignments/" + id + "/submission-files/" + file.getId(),
                legacyFileUrl
        );
    }

    @PostMapping(value = "/assignments/{id}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.AssignmentSubmissionItem submitAssignment(@PathVariable Long id,
                                                                @RequestParam(required = false) String submissionText,
                                                                @RequestParam(required = false) MultipartFile[] files,
                                                                @RequestParam(required = false) MultipartFile file) {
        Student student = accessService.currentStudent();
        loadVisibleAssignment(id, student);

        MultipartFile[] normalizedFiles = files;
        if ((normalizedFiles == null || normalizedFiles.length == 0) && file != null && !file.isEmpty()) {
            normalizedFiles = new MultipartFile[]{file};
        }

        AssignmentSubmission submission = assignmentSubmissionService.submitAssignment(
                id,
                student.getId(),
                submissionText,
                normalizedFiles
        );

        String legacyFileUrl = submission.getFilePath() != null
                ? "/api/mobile/student/assignments/" + id + "/submission-file"
                : null;

        return mapper.toSubmissionItem(
                submission,
                item -> "/api/mobile/student/assignments/" + id + "/submission-files/" + item.getId(),
                legacyFileUrl
        );
    }

    @GetMapping("/courses/{id}/download")
    public ResponseEntity<Resource> downloadCourse(@PathVariable Long id) {
        Student student = accessService.currentStudent();
        Long classeId = classeId(student);
        Long filiereId = filiereId(student);

        CourseContent course = courseContentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));

        boolean visible = courseContentService.findForStudent(classeId, filiereId).stream()
                .anyMatch(c -> c.getId().equals(id));
        if (!visible) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        Resource resource = courseContentService.loadFileAsResource(course);
        return MobileFileResponseBuilder.asDownload(resource, course.getFilePath());
    }

    @GetMapping("/assignments/{id}/attachment")
    public ResponseEntity<Resource> downloadAssignmentAttachment(@PathVariable Long id) {
        Student student = accessService.currentStudent();
        Assignment assignment = loadVisibleAssignment(id, student);
        Resource resource = assignmentService.loadAssignmentAttachment(assignment);
        return MobileFileResponseBuilder.asDownload(resource, assignment.getAttachmentPath());
    }

    @GetMapping("/assignments/{id}/submission-file")
    public ResponseEntity<Resource> downloadOwnSubmission(@PathVariable Long id) {
        Student student = accessService.currentStudent();
        loadVisibleAssignment(id, student);

        AssignmentSubmission submission = assignmentSubmissionService.findByAssignmentAndStudent(id, student.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucune soumission disponible."));

        Resource resource = assignmentSubmissionService.loadSubmissionFile(submission);
        return MobileFileResponseBuilder.asDownload(resource, submission.getFilePath());
    }

    @GetMapping("/assignments/{id}/submission-files/{fileId}")
    public ResponseEntity<Resource> downloadOwnSubmissionFileById(@PathVariable Long id,
                                                                  @PathVariable Long fileId) {
        Student student = accessService.currentStudent();
        loadVisibleAssignment(id, student);

        AssignmentSubmissionFile file = assignmentSubmissionService.findFileForStudentSubmission(id, student.getId(), fileId);
        Resource resource = assignmentSubmissionService.loadSubmissionFile(file);
        return MobileFileResponseBuilder.asDownload(resource, file.getFilePath());
    }

    @DeleteMapping("/assignments/{id}/submission-files/{fileId}")
    public MobileDtos.AssignmentSubmissionItem deleteOwnSubmissionFile(@PathVariable Long id,
                                                                       @PathVariable Long fileId) {
        Student student = accessService.currentStudent();
        loadVisibleAssignment(id, student);

        AssignmentSubmission updated = assignmentSubmissionService
                .removeSubmissionFileByStudent(id, student.getId(), fileId);

        String legacyFileUrl = updated.getFilePath() != null
                ? "/api/mobile/student/assignments/" + id + "/submission-file"
                : null;

        return mapper.toSubmissionItem(
                updated,
                file -> "/api/mobile/student/assignments/" + id + "/submission-files/" + file.getId(),
                legacyFileUrl
        );
    }

    private MobileDtos.AssignmentItem toStudentAssignmentItem(Assignment assignment, Long studentId, LocalDateTime now) {
        Optional<AssignmentSubmission> submission = assignmentSubmissionService
                .findByAssignmentAndStudent(assignment.getId(), studentId);

        return mapper.toAssignmentItem(
                assignment,
                submission.orElse(null),
                now,
                "/api/mobile/student/assignments/" + assignment.getId() + "/attachment"
        );
    }

    private Assignment loadVisibleAssignment(Long assignmentId, Student student) {
        return assignmentService.findVisibleByIdForStudent(assignmentId, classeId(student), filiereId(student))
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non accessible."));
    }

    private Announcement loadVisibleAnnouncement(Long announcementId, Student student) {
        return announcementService.findForStudent(classeId(student), filiereId(student)).stream()
                .filter(a -> announcementId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Annonce introuvable ou non accessible."));
    }

    private Long classeId(Student student) {
        return student.getClasse() != null ? student.getClasse().getId() : null;
    }

    private Long filiereId(Student student) {
        return (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;
    }
}
