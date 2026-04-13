package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.dto.AssignmentSubmissionRowDto;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher/assignments")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherAssignmentController {

    private final UserService userService;
    private final ModuleService moduleService;
    private final ClasseService classeService;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;

    @GetMapping
    public String listAssignments(Model model) {
        User teacher = userService.getCurrentUser();
        List<Assignment> assignments = assignmentService.findByTeacher(teacher.getId());
        model.addAttribute("assignments", assignments);
        model.addAttribute("now", LocalDateTime.now());
        return "teacher/assignments/list";
    }

    @GetMapping("/new")
    public String newAssignmentForm(Model model) {
        User teacher = userService.getCurrentUser();
        Assignment assignment = new Assignment();
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setPublished(true);
        populateFormModel(model, teacher, assignment);
        return "teacher/assignments/form";
    }

    @PostMapping("/new")
    public String createAssignment(@RequestParam String title,
                                   @RequestParam String description,
                                   @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime dueDate,
                                   @RequestParam(required = false) MultipartFile attachment,
                                   @RequestParam(required = false) Long moduleId,
                                   @RequestParam(required = false) Long classeId,
                                   @RequestParam(required = false) Long filiereId,
                                   @RequestParam(defaultValue = "false") boolean published,
                                   RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        assignmentService.createAssignment(
                title,
                description,
                dueDate,
                attachment,
                teacher.getId(),
                moduleId,
                classeId,
                filiereId,
                published
        );
        flash.addFlashAttribute("successMessage", "Devoir cree avec succes.");
        return "redirect:/teacher/assignments";
    }

    @GetMapping("/{id}/edit")
    public String editAssignmentForm(@PathVariable Long id, Model model) {
        User teacher = userService.getCurrentUser();
        Assignment assignment = loadTeacherAssignment(id, teacher.getId());
        populateFormModel(model, teacher, assignment);
        return "teacher/assignments/form";
    }

    @PostMapping("/{id}/edit")
    public String updateAssignment(@PathVariable Long id,
                                   @RequestParam String title,
                                   @RequestParam String description,
                                   @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime dueDate,
                                   @RequestParam(required = false) MultipartFile attachment,
                                   @RequestParam(required = false) Long moduleId,
                                   @RequestParam(required = false) Long classeId,
                                   @RequestParam(required = false) Long filiereId,
                                   @RequestParam(defaultValue = "false") boolean published,
                                   RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        assignmentService.updateAssignment(
                id,
                title,
                description,
                dueDate,
                attachment,
                teacher.getId(),
                moduleId,
                classeId,
                filiereId,
                published
        );
        flash.addFlashAttribute("successMessage", "Devoir mis a jour.");
        return "redirect:/teacher/assignments";
    }

    @PostMapping("/{id}/delete")
    public String deleteAssignment(@PathVariable Long id, RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        assignmentService.deleteAssignment(id, teacher.getId());
        flash.addFlashAttribute("successMessage", "Devoir supprime.");
        return "redirect:/teacher/assignments";
    }

    @GetMapping("/{id}")
    public String assignmentDetails(@PathVariable Long id, Model model) {
        User teacher = userService.getCurrentUser();
        Assignment assignment = loadTeacherAssignment(id, teacher.getId());
        List<Student> students = assignmentService.getTargetStudents(assignment);
        List<AssignmentSubmission> submissions =
                assignmentSubmissionService.findByAssignmentForTeacher(id, teacher.getId());

        model.addAttribute("assignment", assignment);
        model.addAttribute("targetStudents", students);
        model.addAttribute("submissions", submissions.stream().limit(5).toList());
        model.addAttribute("submittedCount", submissions.size());
        model.addAttribute("targetCount", students.size());
        model.addAttribute("pendingCount", Math.max(students.size() - submissions.size(), 0));
        model.addAttribute("now", LocalDateTime.now());
        return "teacher/assignments/detail";
    }

    @GetMapping("/{id}/submissions")
    public String assignmentSubmissions(@PathVariable Long id, Model model) {
        User teacher = userService.getCurrentUser();
        Assignment assignment = loadTeacherAssignment(id, teacher.getId());
        List<Student> students = assignmentService.getTargetStudents(assignment);
        List<AssignmentSubmission> submissions =
                assignmentSubmissionService.findByAssignmentForTeacher(id, teacher.getId());

        Map<Long, AssignmentSubmission> submissionByStudentId = submissions.stream()
                .collect(Collectors.toMap(
                        s -> s.getStudent().getId(),
                        s -> s,
                        (first, second) -> first
                ));

        List<AssignmentSubmissionRowDto> rows = students.stream()
                .map(student -> {
                    AssignmentSubmission submission = submissionByStudentId.get(student.getId());
                    SubmissionStatus status = submission != null
                            ? submission.getStatus()
                            : SubmissionStatus.NOT_SUBMITTED;
                    boolean late = submission != null && submission.isLateSubmission();
                    return new AssignmentSubmissionRowDto(student, submission, status, late);
                })
                .toList();

        model.addAttribute("assignment", assignment);
        model.addAttribute("rows", rows);
        model.addAttribute("now", LocalDateTime.now());
        return "teacher/assignments/submissions";
    }

    @PostMapping("/{assignmentId}/submissions/{submissionId}/review")
    public String reviewSubmission(@PathVariable Long assignmentId,
                                   @PathVariable Long submissionId,
                                   @RequestParam(required = false) Double score,
                                   @RequestParam(required = false) String feedback,
                                   @RequestParam(required = false) String status,
                                   RedirectAttributes flash) {
        User teacher = userService.getCurrentUser();
        SubmissionStatus submissionStatus = null;
        if (StringUtils.hasText(status)) {
            submissionStatus = SubmissionStatus.valueOf(status.trim().toUpperCase());
        }

        assignmentSubmissionService.reviewSubmission(
                assignmentId,
                submissionId,
                teacher.getId(),
                score,
                feedback,
                submissionStatus
        );
        flash.addFlashAttribute("successMessage", "Soumission evaluee avec succes.");
        return "redirect:/teacher/assignments/" + assignmentId + "/submissions";
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> downloadAssignmentAttachment(@PathVariable Long id) {
        User teacher = userService.getCurrentUser();
        Assignment assignment = loadTeacherAssignment(id, teacher.getId());
        Resource resource = assignmentService.loadAssignmentAttachment(assignment);
        return buildFileResponse(resource, assignment.getAttachmentPath());
    }

    @GetMapping("/{assignmentId}/submissions/{submissionId}/file")
    public ResponseEntity<Resource> downloadSubmissionFile(@PathVariable Long assignmentId,
                                                           @PathVariable Long submissionId) {
        User teacher = userService.getCurrentUser();
        loadTeacherAssignment(assignmentId, teacher.getId());
        AssignmentSubmission submission = assignmentSubmissionService.findByAssignmentForTeacher(assignmentId, teacher.getId())
                .stream()
                .filter(s -> submissionId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Soumission introuvable."));

        List<AssignmentSubmissionFile> files = assignmentSubmissionService.findFilesForSubmission(submission.getId());
        if (!files.isEmpty()) {
            AssignmentSubmissionFile first = files.get(0);
            Resource resource = assignmentSubmissionService.loadSubmissionFile(first);
            return buildFileResponse(resource, first.getFilePath());
        }
        Resource resource = assignmentSubmissionService.loadSubmissionFile(submission);
        return buildFileResponse(resource, submission.getFilePath());
    }

    @GetMapping("/{assignmentId}/submissions/{submissionId}/files/{fileId}")
    public ResponseEntity<Resource> downloadSubmissionFileById(@PathVariable Long assignmentId,
                                                               @PathVariable Long submissionId,
                                                               @PathVariable Long fileId) {
        User teacher = userService.getCurrentUser();
        loadTeacherAssignment(assignmentId, teacher.getId());
        AssignmentSubmissionFile file = assignmentSubmissionService
                .findFileForTeacherSubmission(assignmentId, teacher.getId(), submissionId, fileId);
        Resource resource = assignmentSubmissionService.loadSubmissionFile(file);
        return buildFileResponse(resource, file.getFilePath());
    }

    private Assignment loadTeacherAssignment(Long assignmentId, Long teacherId) {
        return assignmentService.findByIdAndTeacher(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));
    }

    private void populateFormModel(Model model, User teacher, Assignment assignment) {
        List<Module> modules = moduleService.findByTeacherId(teacher.getId());
        Map<Long, List<Map<String, Object>>> classesByFiliere = buildClassesByFiliere(modules);

        Long selectedClasseId = assignment.getTargetClasse() != null ? assignment.getTargetClasse().getId() : null;
        Long selectedFiliereId = assignment.getTargetFiliere() != null
                ? assignment.getTargetFiliere().getId()
                : (assignment.getTargetClasse() != null && assignment.getTargetClasse().getFiliere() != null
                    ? assignment.getTargetClasse().getFiliere().getId()
                    : null);
        Long selectedModuleId = assignment.getModule() != null ? assignment.getModule().getId() : null;

        model.addAttribute("assignment", assignment);
        model.addAttribute("modules", modules);
        model.addAttribute("classesByFiliere", classesByFiliere);
        model.addAttribute("selectedClasseId", selectedClasseId);
        model.addAttribute("selectedFiliereId", selectedFiliereId);
        model.addAttribute("selectedModuleId", selectedModuleId);
        model.addAttribute("nowDateTime", LocalDateTime.now().plusMinutes(5));
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
