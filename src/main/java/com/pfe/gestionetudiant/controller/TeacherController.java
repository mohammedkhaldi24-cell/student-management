package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.NoteService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Controller ENSEIGNANT - Notes et absences.
 */
@Controller
@RequestMapping("/teacher")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherController {

    private final UserService userService;
    private final ModuleService moduleService;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final ClasseService classeService;
    private final StudentRepository studentRepository;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;
    private final EmploiDuTempsService emploiDuTempsService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User currentUser = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(currentUser.getId());

        model.addAttribute("teacher", currentUser);
        model.addAttribute("modules", modules);
        model.addAttribute("totalModules", modules.size());
        model.addAttribute("totalCourses", courseContentService.findByTeacherId(currentUser.getId()).size());
        model.addAttribute("totalAnnouncements", announcementService.findByAuthorId(currentUser.getId()).size());
        List<Assignment> assignments = assignmentService.findByTeacher(currentUser.getId());
        model.addAttribute("totalAssignments", assignments.size());
        model.addAttribute("pendingSubmissions", assignmentSubmissionService.countPendingSubmissionsForTeacher(currentUser.getId()));
        model.addAttribute("recentAssignments", assignments.stream().limit(5).toList());
        model.addAttribute("recentCourses",
                courseContentService.findByTeacherId(currentUser.getId()).stream().limit(5).toList());
        model.addAttribute("recentAnnouncements",
                announcementService.findByAuthorId(currentUser.getId()).stream().limit(5).toList());
        model.addAttribute("now", LocalDateTime.now());

        long totalStudents = modules.stream()
                .filter(m -> m.getFiliere() != null)
                .flatMap(m -> classeService.findByFiliereId(m.getFiliere().getId()).stream())
                .map(Classe::getId)
                .distinct()
                .flatMap(classeId -> studentRepository.findByClasseId(classeId).stream())
                .map(Student::getId)
                .distinct()
                .count();
        model.addAttribute("totalStudents", totalStudents);

        return "teacher/dashboard";
    }

    @GetMapping("/modules")
    public String listModules(Model model) {
        User currentUser = userService.getCurrentUser();
        model.addAttribute("modules", moduleService.findByTeacherId(currentUser.getId()));
        return "teacher/modules";
    }

    @GetMapping("/modules/{moduleId}/etudiants")
    public String listeEtudiants(@PathVariable Long moduleId,
                                 @RequestParam(required = false) Long classeId,
                                 Model model) {
        User currentUser = userService.getCurrentUser();
        Module module = getTeacherModuleOrThrow(moduleId, currentUser.getId());

        Long filiereId = module.getFiliere() != null ? module.getFiliere().getId() : null;
        List<Classe> classes = filiereId != null ? classeService.findByFiliereId(filiereId) : List.of();

        model.addAttribute("module", module);
        model.addAttribute("classes", classes);
        model.addAttribute("classeSelectionnee", classeId);

        if (classeId != null) {
            Classe classe = classeService.findById(classeId)
                    .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));
            validateClasseBelongsToModuleFiliere(module, classe);
            model.addAttribute("etudiants", studentRepository.findByClasseId(classeId));
        }

        return "teacher/etudiants";
    }

    @GetMapping("/notes")
    public String notesPage(Model model) {
        User currentUser = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(currentUser.getId());
        model.addAttribute("modules", modules);
        model.addAttribute("classesByFiliere", buildClassesByFiliere(modules));
        model.addAttribute("anneeAcademique", "2024-2025");
        return "teacher/notes";
    }

    @GetMapping("/notes/module/{moduleId}/classe/{classeId}")
    public String notesParModuleClasse(@PathVariable Long moduleId,
                                       @PathVariable Long classeId,
                                       @RequestParam(defaultValue = "S1") String semestre,
                                       @RequestParam(defaultValue = "2024-2025") String annee,
                                       Model model) {
        return notesParModule(moduleId, classeId, semestre, annee, model);
    }

    @GetMapping("/notes/module/{moduleId}")
    public String notesParModule(@PathVariable Long moduleId,
                                 @RequestParam(required = false) Long classeId,
                                 @RequestParam(defaultValue = "S1") String semestre,
                                 @RequestParam(defaultValue = "2024-2025") String annee,
                                 Model model) {
        User currentUser = userService.getCurrentUser();
        Module module = getTeacherModuleOrThrow(moduleId, currentUser.getId());
        Classe classe = resolveClasseForModule(module, classeId).orElse(null);

        List<Student> etudiants = targetStudents(module, classe);
        Set<Long> targetStudentIds = etudiants.stream().map(Student::getId).collect(java.util.stream.Collectors.toSet());
        List<Note> notes = noteService.findByModuleId(moduleId).stream()
                .filter(note -> note.getStudent() != null && targetStudentIds.contains(note.getStudent().getId()))
                .toList();

        var notesMap = new HashMap<Long, Note>();
        notes.forEach(n -> notesMap.put(n.getStudent().getId(), n));

        model.addAttribute("module", module);
        model.addAttribute("classe", classe);
        model.addAttribute("classes", classesForModule(module));
        model.addAttribute("scopeLabel", scopeLabel(module, classe));
        model.addAttribute("etudiants", etudiants);
        model.addAttribute("notesMap", notesMap);
        model.addAttribute("semestre", semestre);
        model.addAttribute("annee", annee);

        return "teacher/saisie-notes";
    }

    @PostMapping("/notes/sauvegarder")
    public String sauvegarderNotes(@RequestParam Long moduleId,
                                   @RequestParam(required = false) Long classeId,
                                   @RequestParam String semestre,
                                   @RequestParam String anneeAcademique,
                                   @RequestParam Map<String, String> allParams,
                                   RedirectAttributes flash) {
        User currentUser = userService.getCurrentUser();
        Module module = getTeacherModuleOrThrow(moduleId, currentUser.getId());
        Classe classe = resolveClasseForModule(module, classeId).orElse(null);

        List<Student> etudiants = targetStudents(module, classe);

        for (Student etudiant : etudiants) {
            String ccKey = "noteCC_" + etudiant.getId();
            String exKey = "noteExamen_" + etudiant.getId();

            if (allParams.containsKey(ccKey) || allParams.containsKey(exKey)) {
                Double noteCC = parseNote(allParams.get(ccKey));
                Double noteExamen = parseNote(allParams.get(exKey));

                var existingNote = noteService.findByStudentModuleSemestreAnnee(
                        etudiant.getId(), moduleId, semestre, anneeAcademique);

                if (existingNote.isPresent()) {
                    noteService.updateNote(existingNote.get().getId(), noteCC, noteExamen);
                } else {
                    Note note = new Note();
                    note.setStudent(etudiant);
                    note.setModule(module);
                    note.setNoteCC(noteCC);
                    note.setNoteExamen(noteExamen);
                    note.setSemestre(semestre);
                    note.setAnneeAcademique(anneeAcademique);
                    noteService.saveNote(note);
                }
            }
        }

        flash.addFlashAttribute("successMessage", "Notes enregistrees avec succes !");
        return "redirect:/teacher/notes/module/" + moduleId
                + (classe != null ? "?classeId=" + classe.getId() + "&" : "?")
                + "semestre=" + semestre + "&annee=" + anneeAcademique;
    }

    @PostMapping("/notes/{id}/delete")
    public String deleteNote(@PathVariable Long id, RedirectAttributes flash) {
        noteService.deleteNote(id);
        flash.addFlashAttribute("successMessage", "Note supprimee !");
        return "redirect:/teacher/notes";
    }

    @GetMapping("/absences")
    public String absencesPage(Model model) {
        User currentUser = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(currentUser.getId());
        Map<Long, List<Map<String, Object>>> classesByFiliere = buildClassesByFiliere(modules);
        List<Map<String, Object>> classes = classesByFiliere.values().stream()
                .flatMap(List::stream)
                .toList();

        model.addAttribute("modules", modules);
        model.addAttribute("classes", classes);
        model.addAttribute("classesByFiliere", classesByFiliere);
        model.addAttribute("today", LocalDate.now().toString());
        return "teacher/absences";
    }

    @GetMapping("/absences/module/{moduleId}/classe/{classeId}")
    public String absencesParModuleClasse(@PathVariable Long moduleId,
                                          @PathVariable Long classeId,
                                          Model model) {
        return absencesParModule(moduleId, classeId, model);
    }

    @GetMapping("/absences/module/{moduleId}")
    public String absencesParModule(@PathVariable Long moduleId,
                                    @RequestParam(required = false) Long classeId,
                                    Model model) {
        User currentUser = userService.getCurrentUser();
        Module module = getTeacherModuleOrThrow(moduleId, currentUser.getId());
        Classe classe = resolveClasseForModule(module, classeId).orElse(null);
        EmploiDuTemps session = currentSessionForModule(module, classe).orElse(null);
        int suggestedHours = sessionHours(session);

        List<Student> etudiants = targetStudents(module, classe);
        Set<Long> targetStudentIds = etudiants.stream().map(Student::getId).collect(java.util.stream.Collectors.toSet());
        List<Absence> absences = absenceService.findByModuleId(moduleId).stream()
                .filter(absence -> absence.getStudent() != null && targetStudentIds.contains(absence.getStudent().getId()))
                .toList();

        model.addAttribute("module", module);
        model.addAttribute("classe", classe);
        model.addAttribute("classes", classesForModule(module));
        model.addAttribute("scopeLabel", scopeLabel(module, classe));
        model.addAttribute("etudiants", etudiants);
        model.addAttribute("absences", absences);
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("suggestedHours", suggestedHours);
        model.addAttribute("sessionLabel", sessionLabel(session));

        return "teacher/marquer-absences";
    }

    @PostMapping("/absences/marquer")
    public String marquerAbsences(@RequestParam Long moduleId,
                                  @RequestParam(required = false) Long classeId,
                                  @RequestParam String dateAbsence,
                                  @RequestParam(required = false) List<Long> etudiantsAbsents,
                                  @RequestParam(defaultValue = "3") int nombreHeures,
                                  RedirectAttributes flash) {
        User currentUser = userService.getCurrentUser();
        Module module = getTeacherModuleOrThrow(moduleId, currentUser.getId());
        Classe classe = resolveClasseForModule(module, classeId).orElse(null);
        LocalDate date = LocalDate.parse(dateAbsence);
        List<Student> targetStudents = targetStudents(module, classe);
        Set<Long> targetStudentIds = targetStudents.stream().map(Student::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> absentIds = etudiantsAbsents == null
                ? Set.of()
                : new HashSet<>(etudiantsAbsents);

        List<Absence> existingForDate = absenceService.findByModuleId(moduleId).stream()
                .filter(absence -> absence.getStudent() != null
                        && targetStudentIds.contains(absence.getStudent().getId())
                        && date.equals(absence.getDateAbsence()))
                .toList();

        int changes = 0;
        Set<Long> alreadyAbsent = new HashSet<>();
        for (Absence absence : existingForDate) {
            Long studentId = absence.getStudent().getId();
            alreadyAbsent.add(studentId);
            if (absentIds.contains(studentId)) {
                if (absence.getNombreHeures() == null || !absence.getNombreHeures().equals(nombreHeures)) {
                    absence.setNombreHeures(nombreHeures);
                    absenceService.saveAbsence(absence);
                    changes++;
                }
            } else {
                absenceService.deleteAbsence(absence.getId());
                changes++;
            }
        }

        for (Student student : targetStudents) {
            if (!absentIds.contains(student.getId()) || alreadyAbsent.contains(student.getId())) {
                continue;
            }
            Absence absence = new Absence();
            absence.setStudent(student);
            absence.setModule(module);
            absence.setDateAbsence(date);
            absence.setNombreHeures(nombreHeures);
            absence.setJustifiee(false);
            absenceService.saveAbsence(absence);
            changes++;
        }

        if (changes > 0) {
            flash.addFlashAttribute("successMessage", changes + " changement(s) d'absence enregistre(s) !");
        } else {
            flash.addFlashAttribute("infoMessage", "Aucun changement d'absence.");
        }

        return "redirect:/teacher/absences/module/" + moduleId
                + (classe != null ? "?classeId=" + classe.getId() : "");
    }

    @PostMapping("/absences/{id}/justifier")
    public String justifierAbsence(@PathVariable Long id,
                                   @RequestParam String motif,
                                   @RequestParam(required = false) Long moduleId,
                                   @RequestParam(required = false) Long classeId,
                                   RedirectAttributes flash) {
        absenceService.justifierAbsence(id, motif);
        flash.addFlashAttribute("successMessage", "Absence justifiee !");

        if (moduleId != null && classeId != null) {
            return "redirect:/teacher/absences/module/" + moduleId + "/classe/" + classeId;
        }
        return "redirect:/teacher/absences";
    }

    @PostMapping("/absences/{id}/delete")
    public String deleteAbsence(@PathVariable Long id, RedirectAttributes flash) {
        absenceService.deleteAbsence(id);
        flash.addFlashAttribute("successMessage", "Absence supprimee !");
        return "redirect:/teacher/absences";
    }

    private Module getTeacherModuleOrThrow(Long moduleId, Long teacherId) {
        return moduleService.findByTeacherId(teacherId).stream()
                .filter(module -> moduleId.equals(module.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable ou non autorise"));
    }

    private void validateClasseBelongsToModuleFiliere(Module module, Classe classe) {
        if (module.getFiliere() == null || classe.getFiliere() == null
                || !module.getFiliere().getId().equals(classe.getFiliere().getId())) {
            throw new IllegalArgumentException("La classe selectionnee n'appartient pas a la filiere du module.");
        }
    }

    private Optional<Classe> resolveClasseForModule(Module module, Long requestedClasseId) {
        if (requestedClasseId != null) {
            Classe classe = classeService.findById(requestedClasseId)
                    .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));
            validateClasseBelongsToModuleFiliere(module, classe);
            return Optional.of(classe);
        }

        Optional<Classe> sessionClasse = currentSessionForModule(module, null)
                .map(EmploiDuTemps::getClasse);
        if (sessionClasse.isPresent()) {
            return sessionClasse;
        }

        List<Classe> classes = classesForModule(module);
        return classes.size() == 1 ? Optional.of(classes.get(0)) : Optional.empty();
    }

    private List<Classe> classesForModule(Module module) {
        Long filiereId = module.getFiliere() != null ? module.getFiliere().getId() : null;
        return filiereId != null ? classeService.findByFiliereId(filiereId) : List.of();
    }

    private List<Student> targetStudents(Module module, Classe classe) {
        if (classe != null) {
            return studentRepository.findByClasseId(classe.getId());
        }
        Long filiereId = module.getFiliere() != null ? module.getFiliere().getId() : null;
        return filiereId != null ? studentRepository.findByFiliereId(filiereId) : List.of();
    }

    private String scopeLabel(Module module, Classe classe) {
        if (classe != null) {
            return classe.getNom();
        }
        return module.getFiliere() != null ? module.getFiliere().getNom() : "Filiere du module";
    }

    private Optional<EmploiDuTemps> currentSessionForModule(Module module, Classe preferredClasse) {
        Long filiereId = module.getFiliere() != null ? module.getFiliere().getId() : null;
        if (filiereId == null) {
            return Optional.empty();
        }
        int today = LocalDate.now().getDayOfWeek().getValue();
        return emploiDuTempsService.findByFiliereId(filiereId).stream()
                .filter(EmploiDuTemps::isValide)
                .filter(session -> session.getModule() != null && module.getId().equals(session.getModule().getId()))
                .filter(session -> preferredClasse == null
                        || (session.getClasse() != null && preferredClasse.getId().equals(session.getClasse().getId())))
                .filter(session -> dayOrder(session.getJour()) == today)
                .min(Comparator.comparing(EmploiDuTemps::getHeureDebut, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private int sessionHours(EmploiDuTemps session) {
        if (session == null || session.getHeureDebut() == null || session.getHeureFin() == null) {
            return 2;
        }
        long hours = Math.max(1, Duration.between(session.getHeureDebut(), session.getHeureFin()).toHours());
        return (int) Math.min(hours, 8);
    }

    private String sessionLabel(EmploiDuTemps session) {
        if (session == null) {
            return "Date pre-remplie aujourd'hui";
        }
        return "Session du jour: " + session.getHeureDebut() + " - " + session.getHeureFin()
                + (session.getSalle() != null ? " | Salle " + session.getSalle() : "");
    }

    private int dayOrder(String day) {
        String value = day != null ? day.trim().toLowerCase() : "";
        if (value.startsWith("lun") || value.startsWith("mon")) return 1;
        if (value.startsWith("mar") || value.startsWith("tue")) return 2;
        if (value.startsWith("mer") || value.startsWith("wed")) return 3;
        if (value.startsWith("jeu") || value.startsWith("thu")) return 4;
        if (value.startsWith("ven") || value.startsWith("fri")) return 5;
        if (value.startsWith("sam") || value.startsWith("sat")) return 6;
        if (value.startsWith("dim") || value.startsWith("sun")) return 7;
        return 8;
    }

    private Map<Long, List<Map<String, Object>>> buildClassesByFiliere(List<Module> modules) {
        Map<Long, List<Map<String, Object>>> classesByFiliere = new LinkedHashMap<>();

        for (Module module : modules) {
            Long filiereId = module.getFiliere() != null ? module.getFiliere().getId() : null;
            if (filiereId == null || classesByFiliere.containsKey(filiereId)) {
                continue;
            }

            List<Map<String, Object>> classes = classeService.findByFiliereId(filiereId).stream()
                    .map(classe -> {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("id", classe.getId());
                        info.put("nom", classe.getNom());
                        info.put("niveau", classe.getNiveau());
                        return info;
                    })
                    .toList();
            classesByFiliere.put(filiereId, classes);
        }

        return classesByFiliere;
    }

    private Double parseNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.replace(",", "."));
            return (d >= 0 && d <= 20) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
