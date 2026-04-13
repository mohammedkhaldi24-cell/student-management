package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public List<Map<String, Object>> classes() {
        Filiere filiere = accessService.currentChefFiliere();
        return classeService.findByFiliereId(filiere.getId()).stream()
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.getId());
                    row.put("nom", c.getNom());
                    row.put("niveau", c.getNiveau());
                    row.put("anneeAcademique", c.getAnneeAcademique());
                    row.put("nombreEtudiants", studentRepository.countByClasseId(c.getId()));
                    return row;
                })
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

    private Classe requireClasseInFiliere(Long classeId, Long filiereId) {
        Classe classe = classeService.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));

        if (classe.getFiliere() == null || !filiereId.equals(classe.getFiliere().getId())) {
            throw new IllegalArgumentException("Classe non autorisee pour votre filiere.");
        }
        return classe;
    }
}
