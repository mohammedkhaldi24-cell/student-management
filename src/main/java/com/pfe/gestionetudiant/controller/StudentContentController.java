package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentContentController {

    private final UserService userService;
    private final StudentRepository studentRepository;
    private final EmploiDuTempsService emploiDuTempsService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;

    @GetMapping("/emploi-du-temps")
    public String timetable(Model model) {
        Student student = getCurrentStudent();
        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        model.addAttribute("student", student);
        model.addAttribute("emplois", classeId != null
                ? emploiDuTempsService.findByClasseId(classeId)
                : java.util.List.of());
        return "student/emploi-du-temps";
    }

    @GetMapping("/courses")
    public String courses(Model model) {
        Student student = getCurrentStudent();
        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long filiereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        model.addAttribute("student", student);
        model.addAttribute("courses", courseContentService.findForStudent(classeId, filiereId));
        return "student/courses";
    }

    @GetMapping("/announcements")
    public String announcements(Model model) {
        Student student = getCurrentStudent();
        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long filiereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        model.addAttribute("student", student);
        model.addAttribute("announcements", announcementService.findForStudent(classeId, filiereId));
        return "student/announcements";
    }

    @GetMapping("/courses/{id}/download")
    public ResponseEntity<Resource> downloadCourseFile(@PathVariable Long id) {
        Student student = getCurrentStudent();
        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long filiereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        Set<Long> visibleIds = courseContentService.findForStudent(classeId, filiereId).stream()
                .map(CourseContent::getId)
                .collect(Collectors.toSet());
        if (!visibleIds.contains(id)) {
            throw new IllegalArgumentException("Acces non autorise a ce document.");
        }

        CourseContent course = courseContentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
        Resource resource = courseContentService.loadFileAsResource(course);
        return buildFileResponse(resource, course.getFilePath());
    }

    private Student getCurrentStudent() {
        User currentUser = userService.getCurrentUser();
        return studentRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Profil etudiant introuvable."));
    }

    private ResponseEntity<Resource> buildFileResponse(Resource resource, String filePath) {
        String contentType = "application/octet-stream";
        String fileName = "document";
        try {
            if (StringUtils.hasText(filePath)) {
                Path path = Paths.get(filePath);
                fileName = path.getFileName().toString();
                String detected = Files.probeContentType(path);
                if (detected != null) {
                    contentType = detected;
                }
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }
}

