package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.CourseDocument;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.model.Teacher;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.NoteService;
import com.pfe.gestionetudiant.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mobile/teacher")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class MobileTeacherController {

    private static final String DEFAULT_ACADEMIC_YEAR = "2024-2025";

    private final MobileAccessService accessService;
    private final MobileApiMapper mapper;
    private final ModuleService moduleService;
    private final ClasseService classeService;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final EmploiDuTempsService emploiDuTempsService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;
    private final StudentRepository studentRepository;

    @GetMapping("/profile")
    public MobileDtos.TeacherProfile profile() {
        Teacher teacher = accessService.currentTeacher();
        return mapper.toTeacherProfile(teacher);
    }

    @GetMapping("/dashboard")
    public MobileDtos.TeacherDashboard dashboard() {
        Long teacherUserId = accessService.currentUser().getId();
        List<Module> modules = moduleService.findByTeacherId(teacherUserId);
        List<Assignment> assignments = assignmentService.findByTeacher(teacherUserId);
        List<CourseContent> courses = courseContentService.findByTeacherId(teacherUserId);
        List<Announcement> announcements = announcementService.findByAuthorId(teacherUserId);

        long totalStudents = modules.stream()
                .filter(m -> m.getFiliere() != null)
                .flatMap(m -> classeService.findByFiliereId(m.getFiliere().getId()).stream())
                .map(Classe::getId)
                .distinct()
                .flatMap(classeId -> studentRepository.findByClasseId(classeId).stream())
                .map(Student::getId)
                .distinct()
                .count();

        List<MobileDtos.AssignmentItem> recentAssignments = assignments.stream()
                .limit(5)
                .map(a -> mapper.toAssignmentItem(a, null, LocalDateTime.now(), "/api/mobile/teacher/assignments/" + a.getId() + "/attachment"))
                .toList();

        List<MobileDtos.CourseItem> recentCourses = courses.stream()
                .limit(5)
                .map(c -> mapper.toCourseItem(c, "/api/mobile/teacher/courses/" + c.getId() + "/download"))
                .toList();

        List<MobileDtos.AnnouncementItem> recentAnnouncements = announcements.stream()
                .limit(5)
                .map(a -> mapper.toAnnouncementItem(
                        a,
                        "/api/mobile/teacher/announcements/" + a.getId() + "/attachment"
                ))
                .toList();

        return new MobileDtos.TeacherDashboard(
                modules.size(),
                totalStudents,
                courses.size(),
                announcements.size(),
                assignments.size(),
                assignmentSubmissionService.countPendingSubmissionsForTeacher(teacherUserId),
                recentAssignments,
                recentCourses,
                recentAnnouncements
        );
    }

    @GetMapping("/modules")
    public List<MobileDtos.TeacherModuleItem> modules() {
        Long teacherUserId = accessService.currentUser().getId();
        return moduleService.findByTeacherId(teacherUserId).stream()
                .map(mapper::toTeacherModuleItem)
                .toList();
    }

    @GetMapping("/timetable")
    public List<MobileDtos.TimetableItem> timetable() {
        Long teacherUserId = accessService.currentUser().getId();
        Set<Long> moduleIds = moduleService.findByTeacherId(teacherUserId).stream()
                .map(Module::getId)
                .collect(Collectors.toSet());

        if (moduleIds.isEmpty()) {
            return List.of();
        }

        return emploiDuTempsService.findAll().stream()
                .filter(e -> e.getModule() != null && moduleIds.contains(e.getModule().getId()))
                .filter(EmploiDuTemps::isValide)
                .collect(Collectors.toMap(EmploiDuTemps::getId, e -> e, (a, b) -> a))
                .values()
                .stream()
                .sorted(Comparator.comparingInt(MobileTeacherController::dayOrder)
                        .thenComparing(EmploiDuTemps::getHeureDebut, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(mapper::toTimetableItem)
                .toList();
    }

    @GetMapping("/classes")
    public List<MobileDtos.ClasseItem> classes(@RequestParam(required = false) Long moduleId,
                                               @RequestParam(required = false) Long filiereId) {
        Long teacherUserId = accessService.currentUser().getId();
        List<Module> teacherModules = moduleService.findByTeacherId(teacherUserId);

        Set<Long> allowedFiliereIds = teacherModules.stream()
                .filter(m -> m.getFiliere() != null)
                .map(m -> m.getFiliere().getId())
                .collect(Collectors.toSet());

        if (allowedFiliereIds.isEmpty()) {
            return List.of();
        }

        if (moduleId != null) {
            Module module = requireTeacherModule(moduleId, teacherUserId);
            filiereId = module.getFiliere() != null ? module.getFiliere().getId() : null;
        }

        if (filiereId != null && !allowedFiliereIds.contains(filiereId)) {
            throw new IllegalArgumentException("Filiere non autorisee.");
        }

        Set<Long> effectiveFiliereIds = (filiereId != null)
                ? Set.of(filiereId)
                : allowedFiliereIds;

        List<MobileDtos.ClasseItem> result = new ArrayList<>();
        for (Long fid : effectiveFiliereIds) {
            for (Classe c : classeService.findByFiliereId(fid)) {
                result.add(new MobileDtos.ClasseItem(
                        c.getId(),
                        c.getNom(),
                        c.getFiliere() != null ? c.getFiliere().getId() : null,
                        c.getFiliere() != null ? c.getFiliere().getNom() : null
                ));
            }
        }

        return result.stream()
                .collect(Collectors.toMap(MobileDtos.ClasseItem::id, i -> i, (a, b) -> a))
                .values()
                .stream()
                .sorted(Comparator.comparing(MobileDtos.ClasseItem::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @GetMapping("/students")
    public List<MobileDtos.StudentProfile> students(@RequestParam(required = false) Long moduleId,
                                                    @RequestParam(required = false) Long classeId,
                                                    @RequestParam(required = false) Long filiereId,
                                                    @RequestParam(required = false, name = "q") String query) {
        Long teacherUserId = accessService.currentUser().getId();
        List<Module> teacherModules = moduleService.findByTeacherId(teacherUserId);
        if (teacherModules.isEmpty()) {
            return List.of();
        }

        Set<Long> allowedFiliereIds = teacherModules.stream()
                .filter(m -> m.getFiliere() != null)
                .map(m -> m.getFiliere().getId())
                .collect(Collectors.toSet());

        Module selectedModule = null;
        if (moduleId != null) {
            selectedModule = requireTeacherModule(moduleId, teacherUserId);
            filiereId = selectedModule.getFiliere() != null ? selectedModule.getFiliere().getId() : null;
        }

        if (filiereId != null && !allowedFiliereIds.contains(filiereId)) {
            throw new IllegalArgumentException("Filiere non autorisee.");
        }

        List<Student> students;
        if (filiereId != null) {
            students = studentRepository.findByFiliereId(filiereId);
        } else {
            Set<Long> uniqueIds = new HashSet<>();
            students = new ArrayList<>();
            for (Long fid : allowedFiliereIds) {
                for (Student s : studentRepository.findByFiliereId(fid)) {
                    if (uniqueIds.add(s.getId())) {
                        students.add(s);
                    }
                }
            }
        }

        if (classeId != null) {
            Classe classe = requireClasse(classeId);
            if (classe.getFiliere() == null || !allowedFiliereIds.contains(classe.getFiliere().getId())) {
                throw new IllegalArgumentException("Classe non autorisee.");
            }
            if (selectedModule != null && selectedModule.getFiliere() != null
                    && !selectedModule.getFiliere().getId().equals(classe.getFiliere().getId())) {
                throw new IllegalArgumentException("Classe non compatible avec le module.");
            }
            students = students.stream()
                    .filter(s -> s.getClasse() != null && classeId.equals(s.getClasse().getId()))
                    .toList();
        }

        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (!q.isEmpty()) {
            students = students.stream().filter(s -> {
                String fullName = s.getFullName() != null ? s.getFullName().toLowerCase(Locale.ROOT) : "";
                String matricule = s.getMatricule() != null ? s.getMatricule().toLowerCase(Locale.ROOT) : "";
                String email = s.getEmail() != null ? s.getEmail().toLowerCase(Locale.ROOT) : "";
                return fullName.contains(q) || matricule.contains(q) || email.contains(q);
            }).toList();
        }

        return students.stream()
                .sorted(Comparator.comparing(Student::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(mapper::toStudentProfile)
                .toList();
    }

    @GetMapping("/notes")
    public List<MobileDtos.NoteItem> notes(@RequestParam(required = false) Long moduleId,
                                           @RequestParam(required = false) Long classeId,
                                           @RequestParam(required = false, name = "q") String query) {
        Long teacherUserId = accessService.currentUser().getId();

        List<Note> notes;
        if (moduleId != null && classeId != null) {
            Module module = requireTeacherModule(moduleId, teacherUserId);
            Classe classe = requireClasse(classeId);
            validateClasseForModule(classe, module);
            notes = noteService.findByModuleAndClasse(moduleId, classeId);
        } else if (moduleId != null) {
            requireTeacherModule(moduleId, teacherUserId);
            notes = noteService.findByModuleId(moduleId);
        } else {
            Set<Long> ids = new LinkedHashSet<>();
            notes = moduleService.findByTeacherId(teacherUserId).stream()
                    .flatMap(module -> noteService.findByModuleId(module.getId()).stream())
                    .filter(n -> ids.add(n.getId()))
                    .toList();
        }

        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        return notes.stream()
                .filter(n -> {
                    if (q.isEmpty()) {
                        return true;
                    }
                    String fullName = n.getStudent() != null ? n.getStudent().getFullName() : "";
                    String matricule = n.getStudent() != null ? n.getStudent().getMatricule() : "";
                    String fullNameLower = fullName != null ? fullName.toLowerCase(Locale.ROOT) : "";
                    String matriculeLower = matricule != null ? matricule.toLowerCase(Locale.ROOT) : "";
                    return fullNameLower.contains(q) || matriculeLower.contains(q);
                })
                .map(mapper::toNoteItem)
                .toList();
    }

    @PostMapping("/notes/upsert")
    public MobileDtos.NoteItem upsertNote(@RequestBody MobileDtos.NoteUpsertRequest request) {
        if (request == null || request.studentId() == null || request.moduleId() == null) {
            throw new IllegalArgumentException("studentId et moduleId sont obligatoires.");
        }

        Long teacherUserId = accessService.currentUser().getId();
        Module module = requireTeacherModule(request.moduleId(), teacherUserId);
        Student student = requireStudent(request.studentId());
        validateStudentForModule(student, module);

        String semestre = StringUtils.hasText(request.semestre()) ? request.semestre().trim() : "S1";
        String annee = StringUtils.hasText(request.anneeAcademique())
                ? request.anneeAcademique().trim()
                : DEFAULT_ACADEMIC_YEAR;

        Optional<Note> existing = noteService.findByStudentModuleSemestreAnnee(student.getId(), module.getId(), semestre, annee);
        Note saved;

        if (existing.isPresent()) {
            saved = noteService.updateNote(existing.get().getId(), request.noteCc(), request.noteExamen());
        } else {
            Note note = new Note();
            note.setStudent(student);
            note.setModule(module);
            note.setSemestre(semestre);
            note.setAnneeAcademique(annee);
            note.setNoteCC(request.noteCc());
            note.setNoteExamen(request.noteExamen());
            saved = noteService.saveNote(note);
        }

        return mapper.toNoteItem(saved);
    }

    @PostMapping("/notes/bulk")
    public List<MobileDtos.NoteItem> upsertNotesBulk(@RequestBody MobileDtos.NoteBulkRequest request) {
        if (request == null || request.moduleId() == null || request.notes() == null) {
            throw new IllegalArgumentException("moduleId et notes sont obligatoires.");
        }

        Long teacherUserId = accessService.currentUser().getId();
        Module module = requireTeacherModule(request.moduleId(), teacherUserId);
        String semestre = StringUtils.hasText(request.semestre()) ? request.semestre().trim() : "S1";
        String annee = StringUtils.hasText(request.anneeAcademique())
                ? request.anneeAcademique().trim()
                : DEFAULT_ACADEMIC_YEAR;

        List<Note> savedNotes = new ArrayList<>();
        for (MobileDtos.NoteBulkItem item : request.notes()) {
            if (item == null || item.studentId() == null) {
                continue;
            }
            Student student = requireStudent(item.studentId());
            validateStudentForModule(student, module);
            Optional<Note> existing = noteService.findByStudentModuleSemestreAnnee(
                    student.getId(), module.getId(), semestre, annee);

            if (existing.isPresent()) {
                savedNotes.add(noteService.updateNote(existing.get().getId(), item.noteCc(), item.noteExamen()));
            } else {
                Note note = new Note();
                note.setStudent(student);
                note.setModule(module);
                note.setSemestre(semestre);
                note.setAnneeAcademique(annee);
                note.setNoteCC(item.noteCc());
                note.setNoteExamen(item.noteExamen());
                savedNotes.add(noteService.saveNote(note));
            }
        }

        return savedNotes.stream().map(mapper::toNoteItem).toList();
    }

    @DeleteMapping("/notes/{noteId}")
    public MobileDtos.ApiMessage deleteNote(@PathVariable Long noteId) {
        Long teacherUserId = accessService.currentUser().getId();
        Note note = noteService.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Note introuvable."));

        if (note.getModule() == null || note.getModule().getTeacher() == null
                || !teacherUserId.equals(note.getModule().getTeacher().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        noteService.deleteNote(noteId);
        return new MobileDtos.ApiMessage("Note supprimee.");
    }

    @GetMapping("/absences")
    public List<MobileDtos.AbsenceItem> absences(@RequestParam(required = false) Long moduleId,
                                                  @RequestParam(required = false) Long classeId,
                                                  @RequestParam(required = false, name = "q") String query) {
        Long teacherUserId = accessService.currentUser().getId();

        List<Absence> absences;
        if (moduleId != null && classeId != null) {
            Module module = requireTeacherModule(moduleId, teacherUserId);
            Classe classe = requireClasse(classeId);
            validateClasseForModule(classe, module);
            absences = absenceService.findByClasseAndModuleId(classeId, moduleId);
        } else if (moduleId != null) {
            requireTeacherModule(moduleId, teacherUserId);
            absences = absenceService.findByModuleId(moduleId);
        } else {
            absences = moduleService.findByTeacherId(teacherUserId).stream()
                    .flatMap(m -> absenceService.findByModuleId(m.getId()).stream())
                    .distinct()
                    .sorted(Comparator.comparing(Absence::getDateAbsence).reversed())
                    .toList();
        }

        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        return absences.stream()
                .filter(a -> {
                    if (q.isEmpty()) {
                        return true;
                    }
                    String fullName = a.getStudent() != null ? a.getStudent().getFullName() : "";
                    String matricule = a.getStudent() != null ? a.getStudent().getMatricule() : "";
                    String fullNameLower = fullName != null ? fullName.toLowerCase(Locale.ROOT) : "";
                    String matriculeLower = matricule != null ? matricule.toLowerCase(Locale.ROOT) : "";
                    return fullNameLower.contains(q) || matriculeLower.contains(q);
                })
                .map(mapper::toAbsenceItem)
                .toList();
    }

    @PostMapping("/absences")
    public MobileDtos.AbsenceItem createAbsence(@RequestBody MobileDtos.AbsenceCreateRequest request) {
        if (request == null || request.studentId() == null || request.moduleId() == null) {
            throw new IllegalArgumentException("studentId et moduleId sont obligatoires.");
        }

        Long teacherUserId = accessService.currentUser().getId();
        Module module = requireTeacherModule(request.moduleId(), teacherUserId);
        Student student = requireStudent(request.studentId());
        validateStudentForModule(student, module);

        Absence absence = new Absence();
        absence.setStudent(student);
        absence.setModule(module);
        absence.setDateAbsence(request.dateAbsence() != null ? request.dateAbsence() : LocalDate.now());
        absence.setNombreHeures(request.nombreHeures() != null && request.nombreHeures() > 0 ? request.nombreHeures() : 2);
        absence.setJustifiee(false);

        return mapper.toAbsenceItem(absenceService.saveAbsence(absence));
    }

    @PostMapping("/absences/session")
    public MobileDtos.AbsenceSessionResponse saveAbsenceSession(@RequestBody MobileDtos.AbsenceSessionRequest request) {
        if (request == null || request.moduleId() == null || request.dateAbsence() == null) {
            throw new IllegalArgumentException("moduleId et dateAbsence sont obligatoires.");
        }

        Long teacherUserId = accessService.currentUser().getId();
        Module module = requireTeacherModule(request.moduleId(), teacherUserId);
        int hours = request.nombreHeures() != null && request.nombreHeures() > 0
                ? request.nombreHeures()
                : 2;

        List<Student> targetStudents = studentsForModuleScope(module, request.classeId());
        Set<Long> targetStudentIds = targetStudents.stream()
                .map(Student::getId)
                .collect(Collectors.toSet());
        Set<Long> absentStudentIds = request.absentStudentIds() == null
                ? Set.of()
                : request.absentStudentIds().stream()
                        .filter(targetStudentIds::contains)
                        .collect(Collectors.toSet());

        List<Absence> existingForDate = absenceService.findByModuleId(module.getId()).stream()
                .filter(absence -> absence.getStudent() != null
                        && targetStudentIds.contains(absence.getStudent().getId())
                        && request.dateAbsence().equals(absence.getDateAbsence()))
                .toList();

        Set<Long> changedStudentIds = new HashSet<>();
        for (Absence existing : existingForDate) {
            Long studentId = existing.getStudent().getId();
            if (absentStudentIds.contains(studentId)) {
                if (existing.getNombreHeures() == null || !existing.getNombreHeures().equals(hours)) {
                    existing.setNombreHeures(hours);
                    absenceService.saveAbsence(existing);
                    changedStudentIds.add(studentId);
                }
            } else {
                absenceService.deleteAbsence(existing.getId());
                changedStudentIds.add(studentId);
            }
        }

        Set<Long> alreadyAbsent = existingForDate.stream()
                .map(Absence::getStudent)
                .filter(student -> student != null)
                .map(Student::getId)
                .collect(Collectors.toSet());

        for (Student student : targetStudents) {
            if (!absentStudentIds.contains(student.getId()) || alreadyAbsent.contains(student.getId())) {
                continue;
            }
            Absence absence = new Absence();
            absence.setStudent(student);
            absence.setModule(module);
            absence.setDateAbsence(request.dateAbsence());
            absence.setNombreHeures(hours);
            absence.setJustifiee(false);
            absenceService.saveAbsence(absence);
            changedStudentIds.add(student.getId());
        }

        List<MobileDtos.AbsenceItem> currentAbsences = absenceService.findByModuleId(module.getId()).stream()
                .filter(absence -> absence.getStudent() != null
                        && targetStudentIds.contains(absence.getStudent().getId())
                        && request.dateAbsence().equals(absence.getDateAbsence()))
                .map(mapper::toAbsenceItem)
                .toList();

        String message = changedStudentIds.isEmpty()
                ? "Aucun changement d'absence."
                : changedStudentIds.size() + " changement(s) d'absence enregistre(s).";
        return new MobileDtos.AbsenceSessionResponse(message, currentAbsences);
    }

    @PostMapping("/absences/{absenceId}/justify")
    public MobileDtos.AbsenceItem justifyAbsence(@PathVariable Long absenceId,
                                                 @RequestParam(required = false) String motif) {
        Long teacherUserId = accessService.currentUser().getId();
        Absence absence = absenceService.findById(absenceId)
                .orElseThrow(() -> new IllegalArgumentException("Absence introuvable."));
        if (absence.getModule() == null || absence.getModule().getTeacher() == null
                || !teacherUserId.equals(absence.getModule().getTeacher().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }
        absenceService.justifierAbsence(absenceId, motif);
        return mapper.toAbsenceItem(absenceService.findById(absenceId)
                .orElseThrow(() -> new IllegalArgumentException("Absence introuvable.")));
    }

    @DeleteMapping("/absences/{absenceId}")
    public MobileDtos.ApiMessage deleteAbsence(@PathVariable Long absenceId) {
        Long teacherUserId = accessService.currentUser().getId();
        Absence absence = absenceService.findById(absenceId)
                .orElseThrow(() -> new IllegalArgumentException("Absence introuvable."));
        if (absence.getModule() == null || absence.getModule().getTeacher() == null
                || !teacherUserId.equals(absence.getModule().getTeacher().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }
        absenceService.deleteAbsence(absenceId);
        return new MobileDtos.ApiMessage("Absence supprimee.");
    }

    @GetMapping("/courses")
    public List<MobileDtos.CourseItem> courses(@RequestParam(required = false) Long moduleId) {
        Long teacherUserId = accessService.currentUser().getId();
        if (moduleId != null) {
            requireTeacherModule(moduleId, teacherUserId);
        }
        return courseContentService.findByTeacherId(teacherUserId).stream()
                .filter(c -> moduleId == null
                        || (c.getModule() != null && moduleId.equals(c.getModule().getId())))
                .map(c -> mapper.toCourseItem(c, "/api/mobile/teacher/courses/" + c.getId() + "/download"))
                .toList();
    }

    @PostMapping(value = "/courses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.CourseItem createCourse(@RequestParam String title,
                                              @RequestParam(required = false) String description,
                                              @RequestParam Long moduleId,
                                              @RequestParam(required = false) Long classeId,
                                              @RequestParam(required = false) Long filiereId,
                                              @RequestParam(required = false) MultipartFile[] files,
                                              @RequestParam(required = false) MultipartFile file) {
        Long teacherUserId = accessService.currentUser().getId();
        MultipartFile[] normalizedFiles = files;
        if ((normalizedFiles == null || normalizedFiles.length == 0) && file != null && !file.isEmpty()) {
            normalizedFiles = new MultipartFile[]{file};
        }
        CourseContent saved = courseContentService.createCourse(
                title,
                description,
                normalizedFiles,
                moduleId,
                teacherUserId,
                classeId,
                filiereId
        );
        return mapper.toCourseItem(saved, "/api/mobile/teacher/courses/" + saved.getId() + "/download");
    }

    @DeleteMapping("/courses/{id}")
    public MobileDtos.ApiMessage deleteCourse(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        courseContentService.deleteCourse(id, teacherUserId);
        return new MobileDtos.ApiMessage("Cours supprime.");
    }

    @PutMapping(value = "/courses/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.CourseItem replaceCourseFile(@PathVariable Long id,
                                                   @RequestParam MultipartFile file) {
        Long teacherUserId = accessService.currentUser().getId();
        CourseContent updated = courseContentService.replaceCourseFile(id, teacherUserId, file);
        return mapper.toCourseItem(updated, "/api/mobile/teacher/courses/" + updated.getId() + "/download");
    }

    @PostMapping(value = "/courses/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.CourseItem addCourseFiles(@PathVariable Long id,
                                                @RequestParam MultipartFile[] files) {
        Long teacherUserId = accessService.currentUser().getId();
        CourseContent updated = courseContentService.addCourseFiles(id, teacherUserId, files);
        return mapper.toCourseItem(updated, "/api/mobile/teacher/courses/" + updated.getId() + "/download");
    }

    @DeleteMapping("/courses/{id}/file")
    public MobileDtos.CourseItem removeCourseFile(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        CourseContent updated = courseContentService.removeCourseFile(id, teacherUserId);
        return mapper.toCourseItem(updated, "/api/mobile/teacher/courses/" + updated.getId() + "/download");
    }

    @DeleteMapping("/courses/{id}/files/{fileId}")
    public MobileDtos.CourseItem removeCourseFileById(@PathVariable Long id,
                                                      @PathVariable Long fileId) {
        Long teacherUserId = accessService.currentUser().getId();
        CourseContent updated = courseContentService.removeCourseFile(id, teacherUserId, fileId);
        return mapper.toCourseItem(updated, "/api/mobile/teacher/courses/" + updated.getId() + "/download");
    }

    @GetMapping("/courses/{id}/download")
    public ResponseEntity<Resource> downloadCourse(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        CourseContent course = courseContentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
        if (course.getTeacher() == null || !teacherUserId.equals(course.getTeacher().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }
        Resource resource = courseContentService.loadFileAsResource(course);
        return MobileFileResponseBuilder.asDownload(resource, course.getFilePath());
    }

    @GetMapping("/courses/{id}/files/{fileId}")
    public ResponseEntity<Resource> downloadCourseFileById(@PathVariable Long id,
                                                           @PathVariable Long fileId) {
        Long teacherUserId = accessService.currentUser().getId();
        CourseContent course = courseContentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
        if (course.getTeacher() == null || !teacherUserId.equals(course.getTeacher().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        CourseDocument document = courseContentService.findFileForCourse(id, fileId);
        Resource resource = courseContentService.loadFileAsResource(document);
        return MobileFileResponseBuilder.asDownload(resource, document.getFilePath());
    }

    @GetMapping("/announcements")
    public List<MobileDtos.AnnouncementItem> announcements() {
        Long teacherUserId = accessService.currentUser().getId();
        return announcementService.findByAuthorId(teacherUserId).stream()
                .map(a -> mapper.toAnnouncementItem(
                        a,
                        "/api/mobile/teacher/announcements/" + a.getId() + "/attachment"
                ))
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, value = "/announcements")
    public MobileDtos.AnnouncementItem createAnnouncementJson(@RequestBody MobileDtos.AnnouncementCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payload invalide.");
        }

        Long teacherUserId = accessService.currentUser().getId();
        Announcement saved = announcementService.createAnnouncement(
                request.title(),
                request.message(),
                teacherUserId,
                request.classeId(),
                request.filiereId(),
                request.moduleId()
        );

        return mapper.toAnnouncementItem(
                saved,
                "/api/mobile/teacher/announcements/" + saved.getId() + "/attachment"
        );
    }

    @PostMapping(value = "/announcements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.AnnouncementItem createAnnouncement(@RequestParam String title,
                                                          @RequestParam String message,
                                                          @RequestParam(required = false) Long moduleId,
                                                          @RequestParam(required = false) Long classeId,
                                                          @RequestParam(required = false) Long filiereId,
                                                          @RequestParam(required = false) MultipartFile attachment) {
        Long teacherUserId = accessService.currentUser().getId();
        Announcement saved = announcementService.createAnnouncement(
                title,
                message,
                teacherUserId,
                classeId,
                filiereId,
                moduleId,
                attachment
        );

        return mapper.toAnnouncementItem(
                saved,
                "/api/mobile/teacher/announcements/" + saved.getId() + "/attachment"
        );
    }

    @DeleteMapping("/announcements/{id}")
    public MobileDtos.ApiMessage deleteAnnouncement(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        announcementService.deleteAnnouncement(id, teacherUserId);
        return new MobileDtos.ApiMessage("Annonce supprimee.");
    }

    @PutMapping(value = "/announcements/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.AnnouncementItem replaceAnnouncementAttachment(@PathVariable Long id,
                                                                     @RequestParam MultipartFile attachment) {
        Long teacherUserId = accessService.currentUser().getId();
        Announcement updated = announcementService.replaceAttachment(id, teacherUserId, attachment);
        return mapper.toAnnouncementItem(
                updated,
                "/api/mobile/teacher/announcements/" + updated.getId() + "/attachment"
        );
    }

    @DeleteMapping("/announcements/{id}/attachment")
    public MobileDtos.AnnouncementItem removeAnnouncementAttachment(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        Announcement updated = announcementService.removeAttachment(id, teacherUserId);
        return mapper.toAnnouncementItem(
                updated,
                "/api/mobile/teacher/announcements/" + updated.getId() + "/attachment"
        );
    }

    @GetMapping("/announcements/{id}/attachment")
    public ResponseEntity<Resource> downloadAnnouncementAttachment(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        Announcement announcement = announcementService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Annonce introuvable."));
        if (announcement.getAuthor() == null || !teacherUserId.equals(announcement.getAuthor().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        Resource resource = announcementService.loadAttachmentAsResource(announcement);
        return MobileFileResponseBuilder.asDownload(resource, announcement.getAttachmentPath());
    }

    @GetMapping("/assignments")
    public List<MobileDtos.AssignmentItem> assignments(@RequestParam(required = false) Long moduleId) {
        Long teacherUserId = accessService.currentUser().getId();
        if (moduleId != null) {
            requireTeacherModule(moduleId, teacherUserId);
        }
        return assignmentService.findByTeacher(teacherUserId).stream()
                .filter(a -> moduleId == null
                        || (a.getModule() != null && moduleId.equals(a.getModule().getId())))
                .map(a -> mapper.toAssignmentItem(
                        a,
                        null,
                        LocalDateTime.now(),
                        "/api/mobile/teacher/assignments/" + a.getId() + "/attachment"
                ))
                .toList();
    }

    @GetMapping("/assignments/{id}")
    public MobileDtos.AssignmentItem assignmentDetails(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        Assignment assignment = requireTeacherAssignment(id, teacherUserId);
        return mapper.toAssignmentItem(
                assignment,
                null,
                LocalDateTime.now(),
                "/api/mobile/teacher/assignments/" + assignment.getId() + "/attachment"
        );
    }

    @PostMapping(value = "/assignments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.AssignmentItem createAssignment(@RequestParam String title,
                                                      @RequestParam String description,
                                                      @RequestParam String dueDate,
                                                      @RequestParam(required = false) MultipartFile attachment,
                                                      @RequestParam(required = false) Long moduleId,
                                                      @RequestParam(required = false) Long classeId,
                                                      @RequestParam(required = false) Long filiereId,
                                                      @RequestParam(defaultValue = "false") boolean published) {
        Long teacherUserId = accessService.currentUser().getId();

        Assignment saved = assignmentService.createAssignment(
                title,
                description,
                parseDueDate(dueDate),
                attachment,
                teacherUserId,
                moduleId,
                classeId,
                filiereId,
                published
        );

        return mapper.toAssignmentItem(
                saved,
                null,
                LocalDateTime.now(),
                "/api/mobile/teacher/assignments/" + saved.getId() + "/attachment"
        );
    }

    @PutMapping(value = "/assignments/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.AssignmentItem updateAssignment(@PathVariable Long id,
                                                      @RequestParam String title,
                                                      @RequestParam String description,
                                                      @RequestParam String dueDate,
                                                      @RequestParam(required = false) MultipartFile attachment,
                                                      @RequestParam(required = false) Long moduleId,
                                                      @RequestParam(required = false) Long classeId,
                                                      @RequestParam(required = false) Long filiereId,
                                                      @RequestParam(defaultValue = "false") boolean published) {
        Long teacherUserId = accessService.currentUser().getId();

        Assignment updated = assignmentService.updateAssignment(
                id,
                title,
                description,
                parseDueDate(dueDate),
                attachment,
                teacherUserId,
                moduleId,
                classeId,
                filiereId,
                published
        );

        return mapper.toAssignmentItem(
                updated,
                null,
                LocalDateTime.now(),
                "/api/mobile/teacher/assignments/" + updated.getId() + "/attachment"
        );
    }

    @DeleteMapping("/assignments/{id}")
    public MobileDtos.ApiMessage deleteAssignment(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        assignmentService.deleteAssignment(id, teacherUserId);
        return new MobileDtos.ApiMessage("Devoir supprime.");
    }

    @PutMapping(value = "/assignments/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MobileDtos.AssignmentItem replaceAssignmentAttachment(@PathVariable Long id,
                                                                 @RequestParam MultipartFile attachment) {
        Long teacherUserId = accessService.currentUser().getId();
        Assignment updated = assignmentService.replaceAssignmentAttachment(id, teacherUserId, attachment);
        return mapper.toAssignmentItem(
                updated,
                null,
                LocalDateTime.now(),
                "/api/mobile/teacher/assignments/" + updated.getId() + "/attachment"
        );
    }

    @DeleteMapping("/assignments/{id}/attachment")
    public MobileDtos.AssignmentItem removeAssignmentAttachment(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        Assignment updated = assignmentService.removeAssignmentAttachment(id, teacherUserId);
        return mapper.toAssignmentItem(
                updated,
                null,
                LocalDateTime.now(),
                "/api/mobile/teacher/assignments/" + updated.getId() + "/attachment"
        );
    }

    @GetMapping("/assignments/{id}/submissions")
    public List<MobileDtos.AssignmentSubmissionItem> assignmentSubmissions(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        requireTeacherAssignment(id, teacherUserId);

        return assignmentSubmissionService.findByAssignmentForTeacher(id, teacherUserId).stream()
                .map(s -> mapper.toSubmissionItem(
                        s,
                        file -> "/api/mobile/teacher/assignments/" + id + "/submissions/" + s.getId() + "/files/" + file.getId(),
                        s.getFilePath() != null
                                ? "/api/mobile/teacher/assignments/" + id + "/submissions/" + s.getId() + "/file"
                                : null
                ))
                .toList();
    }

    @PostMapping("/assignments/{assignmentId}/submissions/{submissionId}/review")
    public MobileDtos.AssignmentSubmissionItem reviewSubmission(@PathVariable Long assignmentId,
                                                                @PathVariable Long submissionId,
                                                                @RequestBody MobileDtos.SubmissionReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payload invalide.");
        }

        Long teacherUserId = accessService.currentUser().getId();
        SubmissionStatus status = null;
        if (StringUtils.hasText(request.status())) {
            status = SubmissionStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        }

        AssignmentSubmission reviewed = assignmentSubmissionService.reviewSubmission(
                assignmentId,
                submissionId,
                teacherUserId,
                request.score(),
                request.feedback(),
                status
        );

        String legacyFileUrl = reviewed.getFilePath() != null
                ? "/api/mobile/teacher/assignments/" + assignmentId + "/submissions/" + reviewed.getId() + "/file"
                : null;

        return mapper.toSubmissionItem(
                reviewed,
                file -> "/api/mobile/teacher/assignments/" + assignmentId + "/submissions/" + reviewed.getId() + "/files/" + file.getId(),
                legacyFileUrl
        );
    }

    @GetMapping("/assignments/{id}/attachment")
    public ResponseEntity<Resource> downloadAssignmentAttachment(@PathVariable Long id) {
        Long teacherUserId = accessService.currentUser().getId();
        Assignment assignment = requireTeacherAssignment(id, teacherUserId);
        Resource resource = assignmentService.loadAssignmentAttachment(assignment);
        return MobileFileResponseBuilder.asDownload(resource, assignment.getAttachmentPath());
    }

    @GetMapping("/assignments/{assignmentId}/submissions/{submissionId}/file")
    public ResponseEntity<Resource> downloadSubmissionFile(@PathVariable Long assignmentId,
                                                           @PathVariable Long submissionId) {
        Long teacherUserId = accessService.currentUser().getId();
        requireTeacherAssignment(assignmentId, teacherUserId);

        AssignmentSubmission submission = assignmentSubmissionService.findByAssignmentForTeacher(assignmentId, teacherUserId)
                .stream()
                .filter(s -> submissionId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Soumission introuvable."));

        Resource resource = assignmentSubmissionService.loadSubmissionFile(submission);
        return MobileFileResponseBuilder.asDownload(resource, submission.getFilePath());
    }

    @GetMapping("/assignments/{assignmentId}/submissions/{submissionId}/files/{fileId}")
    public ResponseEntity<Resource> downloadSubmissionFileById(@PathVariable Long assignmentId,
                                                               @PathVariable Long submissionId,
                                                               @PathVariable Long fileId) {
        Long teacherUserId = accessService.currentUser().getId();
        requireTeacherAssignment(assignmentId, teacherUserId);
        AssignmentSubmissionFile file = assignmentSubmissionService
                .findFileForTeacherSubmission(assignmentId, teacherUserId, submissionId, fileId);
        Resource resource = assignmentSubmissionService.loadSubmissionFile(file);
        return MobileFileResponseBuilder.asDownload(resource, file.getFilePath());
    }

    private Module requireTeacherModule(Long moduleId, Long teacherUserId) {
        Module module = moduleService.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable."));
        if (module.getTeacher() == null || !teacherUserId.equals(module.getTeacher().getId())) {
            throw new IllegalArgumentException("Module non autorise.");
        }
        return module;
    }

    private Assignment requireTeacherAssignment(Long assignmentId, Long teacherUserId) {
        return assignmentService.findByIdAndTeacher(assignmentId, teacherUserId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));
    }

    private Classe requireClasse(Long classeId) {
        return classeService.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));
    }

    private Student requireStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Etudiant introuvable."));
    }

    private List<Student> studentsForModuleScope(Module module, Long classeId) {
        if (classeId != null) {
            Classe classe = requireClasse(classeId);
            validateClasseForModule(classe, module);
            return studentRepository.findByClasseId(classe.getId());
        }
        if (module.getFiliere() == null) {
            return List.of();
        }
        return studentRepository.findByFiliereId(module.getFiliere().getId());
    }

    private void validateClasseForModule(Classe classe, Module module) {
        if (classe.getFiliere() == null || module.getFiliere() == null
                || !classe.getFiliere().getId().equals(module.getFiliere().getId())) {
            throw new IllegalArgumentException("La classe n'appartient pas a la filiere du module.");
        }
    }

    private void validateStudentForModule(Student student, Module module) {
        if (student.getClasse() == null || student.getClasse().getFiliere() == null || module.getFiliere() == null
                || !student.getClasse().getFiliere().getId().equals(module.getFiliere().getId())) {
            throw new IllegalArgumentException("L'etudiant n'appartient pas a la filiere du module.");
        }
    }

    private LocalDateTime parseDueDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("La date limite est obligatoire.");
        }

        String value = raw.trim();
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        } catch (Exception ignored) {
        }

        throw new IllegalArgumentException("Format de date limite invalide.");
    }

    private static int dayOrder(EmploiDuTemps emploiDuTemps) {
        String day = emploiDuTemps.getJour() != null
                ? emploiDuTemps.getJour().trim().toLowerCase(Locale.ROOT)
                : "";
        if (day.startsWith("lun") || day.startsWith("mon")) return 1;
        if (day.startsWith("mar") || day.startsWith("tue")) return 2;
        if (day.startsWith("mer") || day.startsWith("wed")) return 3;
        if (day.startsWith("jeu") || day.startsWith("thu")) return 4;
        if (day.startsWith("ven") || day.startsWith("fri")) return 5;
        if (day.startsWith("sam") || day.startsWith("sat")) return 6;
        if (day.startsWith("dim") || day.startsWith("sun")) return 7;
        return 8;
    }
}
