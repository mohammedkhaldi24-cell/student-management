package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.dto.AssignmentStudentViewDto;
import com.pfe.gestionetudiant.dto.BulletinDto;
import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.NoteService;
import com.pfe.gestionetudiant.service.PdfService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentController {

    private final UserService userService;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final PdfService pdfService;
    private final StudentRepository studentRepository;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;
    private final EmploiDuTempsService emploiDuTempsService;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Student student = getCurrentStudent();
        String annee = "2024-2025";

        List<Note> notesS1 = noteService.findByStudentId(student.getId()).stream()
                .filter(n -> "S1".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();
        List<Note> notesS2 = noteService.findByStudentId(student.getId()).stream()
                .filter(n -> "S2".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();

        double moyenneS1 = noteService.calculerMoyenneEtudiant(student.getId(), "S1", annee);
        double moyenneS2 = noteService.calculerMoyenneEtudiant(student.getId(), "S2", annee);
        double moyenneGenerale = (moyenneS1 + moyenneS2) / 2;

        List<Absence> absences = absenceService.findByStudentId(student.getId());
        int totalHeuresAbsence = absenceService.getTotalHeuresByStudent(student.getId());
        int totalHeuresNJ = absenceService.getTotalHeuresNonJustifiesByStudent(student.getId());

        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long filiereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        List<CourseContent> recentCourses = courseContentService.findForStudent(classeId, filiereId).stream()
                .limit(5)
                .toList();
        List<Announcement> recentAnnouncements = announcementService.findForStudent(classeId, filiereId).stream()
                .limit(5)
                .toList();
        List<EmploiDuTemps> upcomingSessions = classeId != null
                ? emploiDuTempsService.findByClasseId(classeId).stream().limit(6).toList()
                : List.of();

        LocalDateTime now = LocalDateTime.now();
        List<AssignmentStudentViewDto> assignmentViews = assignmentService.findVisibleForStudent(classeId, filiereId).stream()
                .map(assignment -> {
                    Optional<AssignmentSubmission> submission =
                            assignmentSubmissionService.findByAssignmentAndStudent(assignment.getId(), student.getId());
                    boolean submitted = submission.isPresent();
                    boolean overdue = !submitted && assignment.getDueDate().isBefore(now);
                    boolean upcoming = !submitted && !overdue;
                    SubmissionStatus status = submission.map(AssignmentSubmission::getStatus)
                            .orElse(SubmissionStatus.NOT_SUBMITTED);
                    boolean late = submission.map(AssignmentSubmission::isLateSubmission).orElse(false);
                    return new AssignmentStudentViewDto(
                            assignment,
                            submission.orElse(null),
                            status,
                            upcoming,
                            overdue,
                            late
                    );
                })
                .toList();

        List<AssignmentStudentViewDto> upcomingAssignments = assignmentViews.stream()
                .filter(AssignmentStudentViewDto::isUpcoming)
                .limit(5)
                .toList();
        long overdueAssignmentsCount = assignmentViews.stream()
                .filter(AssignmentStudentViewDto::isOverdue)
                .count();
        List<AssignmentSubmission> recentFeedback =
                assignmentSubmissionService.findRecentFeedbackForStudent(student.getId(), 5);

        model.addAttribute("student", student);
        model.addAttribute("notesS1", notesS1);
        model.addAttribute("notesS2", notesS2);
        model.addAttribute("moyenneS1", moyenneS1);
        model.addAttribute("moyenneS2", moyenneS2);
        model.addAttribute("moyenneGenerale", moyenneGenerale);
        model.addAttribute("absences", absences);
        model.addAttribute("totalHeuresAbsence", totalHeuresAbsence);
        model.addAttribute("totalHeuresNJ", totalHeuresNJ);
        model.addAttribute("anneeAcademique", annee);
        model.addAttribute("recentCourses", recentCourses);
        model.addAttribute("recentAnnouncements", recentAnnouncements);
        model.addAttribute("upcomingSessions", upcomingSessions);
        model.addAttribute("upcomingAssignments", upcomingAssignments);
        model.addAttribute("overdueAssignmentsCount", overdueAssignmentsCount);
        model.addAttribute("recentFeedback", recentFeedback);

        List<String> modulesLabels = notesS1.stream()
                .map(n -> n.getModule().getNom())
                .toList();
        List<Double> notesFinals = notesS1.stream()
                .map(n -> n.getNoteFinal() != null ? n.getNoteFinal() : 0.0)
                .toList();

        model.addAttribute("chartLabels", modulesLabels);
        model.addAttribute("chartData", notesFinals);

        return "student/dashboard";
    }

    @GetMapping("/notes")
    public String notes(Model model) {
        Student student = getCurrentStudent();
        String annee = "2024-2025";

        List<Note> notesS1 = noteService.findByStudentId(student.getId()).stream()
                .filter(n -> "S1".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();
        List<Note> notesS2 = noteService.findByStudentId(student.getId()).stream()
                .filter(n -> "S2".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();

        double moyenneS1 = noteService.calculerMoyenneEtudiant(student.getId(), "S1", annee);
        double moyenneS2 = noteService.calculerMoyenneEtudiant(student.getId(), "S2", annee);
        double moyenneGenerale = (moyenneS1 + moyenneS2) / 2;

        model.addAttribute("student", student);
        model.addAttribute("notesS1", notesS1);
        model.addAttribute("notesS2", notesS2);
        model.addAttribute("moyenneS1", moyenneS1);
        model.addAttribute("moyenneS2", moyenneS2);
        model.addAttribute("moyenneGenerale", moyenneGenerale);
        model.addAttribute("anneeAcademique", annee);
        return "student/notes";
    }

    @GetMapping("/absences")
    public String absences(Model model) {
        Student student = getCurrentStudent();
        List<Absence> absences = absenceService.findByStudentId(student.getId());
        int totalHeuresAbsence = absenceService.getTotalHeuresByStudent(student.getId());
        int totalHeuresNJ = absenceService.getTotalHeuresNonJustifiesByStudent(student.getId());

        model.addAttribute("student", student);
        model.addAttribute("absences", absences);
        model.addAttribute("totalHeuresAbsence", totalHeuresAbsence);
        model.addAttribute("totalHeuresNJ", totalHeuresNJ);
        return "student/absences";
    }

    @GetMapping("/bulletin/{semestre}")
    public ResponseEntity<byte[]> telechargerBulletin(@PathVariable String semestre) {
        Student student = getCurrentStudent();
        BulletinDto bulletin = noteService.genererBulletin(student.getId(), semestre, "2024-2025");
        byte[] pdfBytes = pdfService.genererBulletinPdf(bulletin);

        String filename = "bulletin_" + student.getMatricule() + "_" + semestre + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private Student getCurrentStudent() {
        User currentUser = userService.getCurrentUser();
        return studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Profil etudiant introuvable."));
    }
}
