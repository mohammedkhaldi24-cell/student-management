package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.ModuleService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/teacher")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherContentController {

    private final UserService userService;
    private final ModuleService moduleService;
    private final ClasseService classeService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;

    @GetMapping("/courses")
    public String listCourses(Model model) {
        User teacher = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(teacher.getId());

        model.addAttribute("courses", courseContentService.findByTeacherId(teacher.getId()));
        model.addAttribute("modules", modules);
        model.addAttribute("classesByFiliere", buildClassesByFiliere(modules));
        return "teacher/courses/list";
    }

    @GetMapping("/courses/new")
    public String newCourseForm(Model model) {
        User teacher = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(teacher.getId());

        model.addAttribute("modules", modules);
        model.addAttribute("classesByFiliere", buildClassesByFiliere(modules));
        return "teacher/courses/form";
    }

    @PostMapping("/courses/new")
    public String createCourse(@RequestParam String title,
                               @RequestParam(required = false) String description,
                               @RequestParam Long moduleId,
                               @RequestParam(required = false) Long classeId,
                               @RequestParam(required = false) Long filiereId,
                               @RequestParam(required = false) MultipartFile file,
                               RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();

        courseContentService.createCourse(
                title, description, file, moduleId, teacher.getId(), classeId, filiereId
        );

        flash.addFlashAttribute("successMessage", "Contenu de cours publie avec succes.");
        return "redirect:/teacher/courses";
    }

    @GetMapping("/courses/{id}/download")
    public ResponseEntity<Resource> downloadCourseFile(@PathVariable Long id) {
        User teacher = userService.getCurrentUser();
        CourseContent course = courseContentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable."));
        if (course.getTeacher() == null || !teacher.getId().equals(course.getTeacher().getId())) {
            throw new IllegalArgumentException("Acces non autorise.");
        }

        Resource resource = courseContentService.loadFileAsResource(course);
        return buildFileResponse(resource, course.getFilePath());
    }

    @PostMapping("/courses/{id}/delete")
    public String deleteCourse(@PathVariable Long id, RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        courseContentService.deleteCourse(id, teacher.getId());
        flash.addFlashAttribute("successMessage", "Cours supprime.");
        return "redirect:/teacher/courses";
    }

    @GetMapping("/announcements")
    public String listAnnouncements(Model model) {
        User teacher = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(teacher.getId());

        model.addAttribute("announcements", announcementService.findByAuthorId(teacher.getId()));
        model.addAttribute("modules", modules);
        model.addAttribute("classesByFiliere", buildClassesByFiliere(modules));
        return "teacher/announcements/list";
    }

    @GetMapping("/announcements/new")
    public String newAnnouncementForm(Model model) {
        User teacher = userService.getCurrentUser();
        List<Module> modules = moduleService.findByTeacherId(teacher.getId());

        model.addAttribute("modules", modules);
        model.addAttribute("classesByFiliere", buildClassesByFiliere(modules));
        return "teacher/announcements/form";
    }

    @PostMapping("/announcements/new")
    public String createAnnouncement(@RequestParam String title,
                                     @RequestParam String message,
                                     @RequestParam(required = false) Long classeId,
                                     @RequestParam(required = false) Long filiereId,
                                     RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        announcementService.createAnnouncement(title, message, teacher.getId(), classeId, filiereId);
        flash.addFlashAttribute("successMessage", "Annonce publiee avec succes.");
        return "redirect:/teacher/announcements";
    }

    @PostMapping("/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable Long id, RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        announcementService.deleteAnnouncement(id, teacher.getId());
        flash.addFlashAttribute("successMessage", "Annonce supprimee.");
        return "redirect:/teacher/announcements";
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

