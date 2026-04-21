package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.dto.AssignmentStudentViewDto;
import com.pfe.gestionetudiant.dto.BulletinDto;
import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Module;
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
import com.pfe.gestionetudiant.service.ModuleService;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentController {

    private static final String DEFAULT_ACADEMIC_YEAR = "2024-2025";

    private final UserService userService;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final PdfService pdfService;
    private final StudentRepository studentRepository;
    private final ModuleService moduleService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;
    private final EmploiDuTempsService emploiDuTempsService;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Student student = getCurrentStudent();
        String annee = DEFAULT_ACADEMIC_YEAR;

        List<Note> allNotes = noteService.findByStudentId(student.getId());
        List<Note> notesS1 = allNotes.stream()
                .filter(n -> "S1".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();
        List<Note> notesS2 = allNotes.stream()
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

        List<Module> studentModules = getStudentModules(student);

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
        model.addAttribute("studentModules", studentModules);
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

    @GetMapping("/modules")
    public String modules(Model model) {
        Student student = getCurrentStudent();
        List<Module> modules = getStudentModules(student);

        model.addAttribute("student", student);
        model.addAttribute("modules", modules);
        return "student/modules";
    }

    @GetMapping("/modules/{moduleId}")
    public String moduleDetails(@PathVariable Long moduleId, Model model) {
        Student student = getCurrentStudent();
        Module module = requireStudentModule(student, moduleId);

        List<Note> notes = noteService.findByStudentAndModule(student.getId(), moduleId).stream()
                .sorted(Comparator.comparing(Note::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        List<Absence> absences = absenceService.findByStudentAndModule(student.getId(), moduleId);

        double moduleAverage = notes.stream()
                .map(Note::getNoteFinal)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        model.addAttribute("student", student);
        model.addAttribute("selectedModule", module);
        model.addAttribute("notes", notes);
        model.addAttribute("absences", absences);
        model.addAttribute("moduleAverage", moduleAverage);
        return "student/module-details";
    }

    @GetMapping("/notes")
    public String notes(@RequestParam(required = false) Long moduleId, Model model) {
        Student student = getCurrentStudent();
        String annee = DEFAULT_ACADEMIC_YEAR;

        List<Note> allStudentNotes = noteService.findByStudentId(student.getId());
        List<Module> studentModules = mergeModules(
                getStudentModules(student),
                allStudentNotes.stream().map(Note::getModule).toList(),
                absenceService.findByStudentId(student.getId()).stream().map(Absence::getModule).toList()
        );
        Module selectedModule = moduleId != null ? requireStudentModule(student, moduleId) : null;

        List<Note> noteBase = selectedModule != null
                ? noteService.findByStudentAndModule(student.getId(), selectedModule.getId())
                : allStudentNotes;

        List<Note> notesS1 = noteBase.stream()
                .filter(n -> "S1".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();
        List<Note> notesS2 = noteBase.stream()
                .filter(n -> "S2".equals(n.getSemestre()) && annee.equals(n.getAnneeAcademique()))
                .toList();

        double moyenneS1 = selectedModule == null
                ? noteService.calculerMoyenneEtudiant(student.getId(), "S1", annee)
                : averageFinal(notesS1);
        double moyenneS2 = selectedModule == null
                ? noteService.calculerMoyenneEtudiant(student.getId(), "S2", annee)
                : averageFinal(notesS2);
        double moyenneGenerale = (moyenneS1 + moyenneS2) / 2;

        model.addAttribute("student", student);
        model.addAttribute("availableModules", studentModules);
        model.addAttribute("selectedModule", selectedModule);
        model.addAttribute("selectedModuleId", selectedModule != null ? selectedModule.getId() : null);
        model.addAttribute("notesS1", notesS1);
        model.addAttribute("notesS2", notesS2);
        model.addAttribute("moyenneS1", moyenneS1);
        model.addAttribute("moyenneS2", moyenneS2);
        model.addAttribute("moyenneGenerale", moyenneGenerale);
        model.addAttribute("anneeAcademique", annee);
        return "student/notes";
    }

    @GetMapping("/absences")
    public String absences(@RequestParam(required = false) Long moduleId, Model model) {
        Student student = getCurrentStudent();

        List<Absence> allStudentAbsences = absenceService.findByStudentId(student.getId());
        List<Module> studentModules = mergeModules(
                getStudentModules(student),
                allStudentAbsences.stream().map(Absence::getModule).toList(),
                noteService.findByStudentId(student.getId()).stream().map(Note::getModule).toList()
        );
        Module selectedModule = moduleId != null ? requireStudentModule(student, moduleId) : null;

        List<Absence> absences = selectedModule != null
                ? absenceService.findByStudentAndModule(student.getId(), selectedModule.getId())
                : allStudentAbsences;

        int totalHeuresAbsence = absences.stream()
                .map(Absence::getNombreHeures)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .sum();
        int totalHeuresNJ = absences.stream()
                .filter(a -> !a.isJustifiee())
                .map(Absence::getNombreHeures)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .sum();

        model.addAttribute("student", student);
        model.addAttribute("availableModules", studentModules);
        model.addAttribute("selectedModule", selectedModule);
        model.addAttribute("selectedModuleId", selectedModule != null ? selectedModule.getId() : null);
        model.addAttribute("absences", absences);
        model.addAttribute("totalHeuresAbsence", totalHeuresAbsence);
        model.addAttribute("totalHeuresNJ", totalHeuresNJ);
        return "student/absences";
    }

    @GetMapping("/bulletin/{semestre}")
    public ResponseEntity<byte[]> telechargerBulletin(@PathVariable String semestre) {
        Student student = getCurrentStudent();
        BulletinDto bulletin = noteService.genererBulletin(student.getId(), semestre, DEFAULT_ACADEMIC_YEAR);
        byte[] pdfBytes = pdfService.genererBulletinPdf(bulletin);

        String filename = "bulletin_" + student.getMatricule() + "_" + semestre + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private List<Module> getStudentModules(Student student) {
        Map<Long, Module> modulesById = new LinkedHashMap<>();

        if (student.getClasse() != null && student.getClasse().getFiliere() != null) {
            List<Module> filiereModules = moduleService.findByFiliereId(student.getClasse().getFiliere().getId());
            filiereModules.forEach(module -> addModule(modulesById, module));
        }

        if (student.getClasse() != null && student.getClasse().getId() != null) {
            emploiDuTempsService.findByClasseId(student.getClasse().getId()).stream()
                    .map(EmploiDuTemps::getModule)
                    .forEach(module -> addModule(modulesById, module));
        }

        noteService.findByStudentId(student.getId()).stream()
                .map(Note::getModule)
                .forEach(module -> addModule(modulesById, module));

        absenceService.findByStudentId(student.getId()).stream()
                .map(Absence::getModule)
                .forEach(module -> addModule(modulesById, module));

        return sortModules(modulesById.values());
    }

    private void addModule(Map<Long, Module> modulesById, Module module) {
        if (module == null || module.getId() == null) {
            return;
        }
        modulesById.putIfAbsent(module.getId(), module);
    }

    private List<Module> mergeModules(List<Module>... moduleLists) {
        Map<Long, Module> modulesById = new LinkedHashMap<>();
        for (List<Module> moduleList : moduleLists) {
            for (Module module : moduleList) {
                addModule(modulesById, module);
            }
        }
        return sortModules(modulesById.values());
    }

    private List<Module> sortModules(Collection<Module> modules) {
        return modules.stream()
                .sorted(Comparator
                        .comparing((Module m) -> m.getSemestre() != null ? m.getSemestre() : "")
                        .thenComparing(m -> m.getNom() != null ? m.getNom() : "", String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Module requireStudentModule(Student student, Long moduleId) {
        return getStudentModules(student).stream()
                .filter(m -> m.getId().equals(moduleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Module non accessible pour cet etudiant."));
    }

    private double averageFinal(List<Note> notes) {
        return notes.stream()
                .map(Note::getNoteFinal)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private Student getCurrentStudent() {
        User currentUser = userService.getCurrentUser();
        return studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Profil etudiant introuvable."));
    }
}
