package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.NoteService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/mobile/chef")
@PreAuthorize("hasRole('CHEF_FILIERE')")
@RequiredArgsConstructor
public class MobileChefController {

    private final MobileAccessService accessService;
    private final MobileApiMapper mapper;
    private final ClasseService classeService;
    private final StudentRepository studentRepository;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;
    private final EmploiDuTempsService emploiDuTempsService;
    private final ModuleService moduleService;
    private final UserService userService;

    @GetMapping("/dashboard")
    public MobileDtos.ChefDashboard dashboard() {
        Filiere filiere = accessService.currentChefFiliere();
        List<Classe> classes = classeService.findByFiliereId(filiere.getId());

        long totalStudents = studentRepository.countByFiliereId(filiere.getId());
        long totalAbsences = classes.stream()
                .flatMap(c -> absenceService.findByClasseId(c.getId()).stream())
                .count();

        return new MobileDtos.ChefDashboard(
                filiere.getNom(),
                classes.size(),
                totalStudents,
                courseContentService.findByFiliereId(filiere.getId()).size(),
                announcementService.findByFiliereId(filiere.getId()).size(),
                totalAbsences
        );
    }

    @GetMapping("/classes")
    public List<MobileDtos.ClasseItem> classes() {
        Filiere filiere = accessService.currentChefFiliere();
        return classeService.findByFiliereId(filiere.getId()).stream()
                .map(c -> new MobileDtos.ClasseItem(
                        c.getId(),
                        c.getNom(),
                        filiere.getId(),
                        filiere.getNom()
                ))
                .toList();
    }

    @GetMapping("/modules")
    public List<MobileDtos.TeacherModuleItem> modules() {
        Filiere filiere = accessService.currentChefFiliere();
        return moduleService.findByFiliereId(filiere.getId()).stream()
                .map(mapper::toTeacherModuleItem)
                .toList();
    }

    @GetMapping("/students")
    public List<MobileDtos.StudentProfile> students(@RequestParam(required = false) Long classeId) {
        Filiere filiere = accessService.currentChefFiliere();

        List<Student> students;
        if (classeId != null) {
            Classe classe = requireClasseInFiliere(classeId, filiere.getId());
            students = studentRepository.findByClasseId(classe.getId());
        } else {
            students = studentRepository.findByFiliereId(filiere.getId());
        }

        return students.stream()
                .map(mapper::toStudentProfile)
                .toList();
    }

    @GetMapping("/notes")
    public List<MobileDtos.NoteItem> notes(@RequestParam(required = false) Long classeId) {
        Filiere filiere = accessService.currentChefFiliere();

        List<Student> students;
        if (classeId != null) {
            Classe classe = requireClasseInFiliere(classeId, filiere.getId());
            students = studentRepository.findByClasseId(classe.getId());
        } else {
            students = studentRepository.findByFiliereId(filiere.getId());
        }

        Set<Long> ids = new HashSet<>();
        return students.stream()
                .flatMap(s -> noteService.findByStudentId(s.getId()).stream())
                .filter(n -> ids.add(n.getId()))
                .map(mapper::toNoteItem)
                .toList();
    }

    @GetMapping("/absences")
    public List<MobileDtos.AbsenceItem> absences(@RequestParam(required = false) Long classeId) {
        Filiere filiere = accessService.currentChefFiliere();

        List<Absence> absences;
        if (classeId != null) {
            Classe classe = requireClasseInFiliere(classeId, filiere.getId());
            absences = absenceService.findByClasseId(classe.getId());
        } else {
            absences = classeService.findByFiliereId(filiere.getId()).stream()
                    .flatMap(c -> absenceService.findByClasseId(c.getId()).stream())
                    .toList();
        }

        return absences.stream()
                .map(mapper::toAbsenceItem)
                .toList();
    }

    @GetMapping("/courses")
    public List<MobileDtos.CourseItem> courses() {
        Filiere filiere = accessService.currentChefFiliere();
        return courseContentService.findByFiliereId(filiere.getId()).stream()
                .map(c -> mapper.toCourseItem(c, "/api/mobile/chef/courses/" + c.getId() + "/download"))
                .toList();
    }

    @GetMapping("/courses/{id}/download")
    public ResponseEntity<Resource> downloadCourse(@PathVariable Long id) {
        Filiere filiere = accessService.currentChefFiliere();
        var course = courseContentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));

        Long courseFiliereId = course.getFiliere() != null ? course.getFiliere().getId() : null;
        if (courseFiliereId == null || !courseFiliereId.equals(filiere.getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        Resource resource = courseContentService.loadFileAsResource(course);
        return MobileFileResponseBuilder.asDownload(resource, course.getFilePath());
    }

    @GetMapping("/announcements")
    public List<MobileDtos.AnnouncementItem> announcements() {
        Filiere filiere = accessService.currentChefFiliere();
        return announcementService.findByFiliereId(filiere.getId()).stream()
                .map(a -> mapper.toAnnouncementItem(
                        a,
                        "/api/mobile/chef/announcements/" + a.getId() + "/attachment"
                ))
                .toList();
    }

    @GetMapping("/announcements/{id}/attachment")
    public ResponseEntity<Resource> downloadAnnouncementAttachment(@PathVariable Long id) {
        Filiere filiere = accessService.currentChefFiliere();
        Announcement announcement = announcementService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Annonce introuvable."));

        Long targetFiliereId = announcement.getTargetFiliere() != null ? announcement.getTargetFiliere().getId() : null;
        if (targetFiliereId == null || !targetFiliereId.equals(filiere.getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        Resource resource = announcementService.loadAttachmentAsResource(announcement);
        return MobileFileResponseBuilder.asDownload(resource, announcement.getAttachmentPath());
    }

    @GetMapping("/timetable")
    public List<MobileDtos.TimetableItem> timetable() {
        Filiere filiere = accessService.currentChefFiliere();
        return emploiDuTempsService.findByFiliereId(filiere.getId()).stream()
                .map(mapper::toTimetableItem)
                .toList();
    }

    @PostMapping("/timetable")
    public MobileDtos.TimetableItem createTimetable(@RequestBody MobileDtos.AdminTimetableUpsertRequest request) {
        Filiere filiere = accessService.currentChefFiliere();
        EmploiDuTemps emploiDuTemps = new EmploiDuTemps();
        applyTimetableRequest(emploiDuTemps, request, filiere, true);
        return mapper.toTimetableItem(emploiDuTempsService.save(emploiDuTemps));
    }

    @PutMapping("/timetable/{id}")
    public MobileDtos.TimetableItem updateTimetable(@PathVariable Long id,
                                                    @RequestBody MobileDtos.AdminTimetableUpsertRequest request) {
        Filiere filiere = accessService.currentChefFiliere();
        EmploiDuTemps emploiDuTemps = requireTimetableInFiliere(id, filiere.getId());
        applyTimetableRequest(emploiDuTemps, request, filiere, false);
        return mapper.toTimetableItem(emploiDuTempsService.save(emploiDuTemps));
    }

    @DeleteMapping("/timetable/{id}")
    public MobileDtos.ApiMessage deleteTimetable(@PathVariable Long id) {
        Filiere filiere = accessService.currentChefFiliere();
        EmploiDuTemps emploiDuTemps = requireTimetableInFiliere(id, filiere.getId());
        emploiDuTempsService.delete(emploiDuTemps.getId());
        return new MobileDtos.ApiMessage("Seance EDT supprimee.");
    }

    private Classe requireClasseInFiliere(Long classeId, Long filiereId) {
        Classe classe = classeService.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));

        if (classe.getFiliere() == null || !filiereId.equals(classe.getFiliere().getId())) {
            throw new IllegalArgumentException("Classe non autorisee pour votre filiere.");
        }
        return classe;
    }

    private EmploiDuTemps requireTimetableInFiliere(Long id, Long filiereId) {
        EmploiDuTemps emploiDuTemps = emploiDuTempsService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seance EDT introuvable."));
        if (emploiDuTemps.getFiliere() == null || !filiereId.equals(emploiDuTemps.getFiliere().getId())) {
            throw new IllegalArgumentException("Seance hors filiere.");
        }
        return emploiDuTemps;
    }

    private void applyTimetableRequest(EmploiDuTemps emploiDuTemps,
                                       MobileDtos.AdminTimetableUpsertRequest request,
                                       Filiere filiere,
                                       boolean creation) {
        if (request == null) {
            throw new IllegalArgumentException("Payload invalide.");
        }
        if (creation || StringUtils.hasText(request.jour())) {
            emploiDuTemps.setJour(requireText(request.jour(), "Le jour est obligatoire."));
        }
        if (creation || StringUtils.hasText(request.heureDebut())) {
            emploiDuTemps.setHeureDebut(parseTime(request.heureDebut(), "Heure de debut invalide (HH:mm)."));
        }
        if (creation || StringUtils.hasText(request.heureFin())) {
            emploiDuTemps.setHeureFin(parseTime(request.heureFin(), "Heure de fin invalide (HH:mm)."));
        }
        if (creation || StringUtils.hasText(request.salle())) {
            emploiDuTemps.setSalle(requireText(request.salle(), "La salle est obligatoire."));
        }

        emploiDuTemps.setFiliere(filiere);
        emploiDuTemps.setValide(true);

        if (request.classeId() != null) {
            emploiDuTemps.setClasse(requireClasseInFiliere(request.classeId(), filiere.getId()));
        } else if (creation || emploiDuTemps.getClasse() == null) {
            throw new IllegalArgumentException("La classe est obligatoire.");
        }

        if (request.moduleId() != null) {
            Module module = moduleService.findById(request.moduleId())
                    .orElseThrow(() -> new IllegalArgumentException("Module introuvable."));
            if (module.getFiliere() == null || !filiere.getId().equals(module.getFiliere().getId())) {
                throw new IllegalArgumentException("Module hors filiere.");
            }
            emploiDuTemps.setModule(module);
        } else if (creation || emploiDuTemps.getModule() == null) {
            throw new IllegalArgumentException("Le module est obligatoire.");
        }

        if (request.teacherId() != null) {
            if (request.teacherId() == 0L) {
                emploiDuTemps.setTeacher(null);
            } else {
                User teacher = userService.findById(request.teacherId())
                        .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
                if (teacher.getRole() != Role.TEACHER) {
                    throw new IllegalArgumentException("Utilisateur non enseignant.");
                }
                emploiDuTemps.setTeacher(teacher);
            }
        }
    }

    private LocalTime parseTime(String value, String errorMessage) {
        try {
            return LocalTime.parse(requireText(value, errorMessage));
        } catch (Exception ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String requireText(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }
}
