package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.dto.AssignmentStudentViewDto;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
import java.util.Optional;
import java.util.stream.Stream;

@Controller
@RequestMapping("/student/assignments")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentAssignmentController {

    private final UserService userService;
    private final StudentRepository studentRepository;
    private final AssignmentService assignmentService;
    private final AssignmentSubmissionService assignmentSubmissionService;

    @GetMapping
    public String listAssignments(@RequestParam(defaultValue = "all") String filter,
                                  @RequestParam(required = false) Long moduleId,
                                  Model model) {
        Student student = getCurrentStudent();
        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long filiereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        LocalDateTime now = LocalDateTime.now();
        List<AssignmentStudentViewDto> allViews = assignmentService.findVisibleForStudent(classeId, filiereId).stream()
                .map(a -> buildStudentView(a, student.getId(), now))
                .toList();

        String normalizedFilter = filter == null ? "all" : filter.trim().toLowerCase();
        if (!List.of("all", "upcoming", "overdue", "submitted", "not_submitted").contains(normalizedFilter)) {
            normalizedFilter = "all";
        }

        List<AssignmentStudentViewDto> statusFiltered = filterAssignmentViews(allViews, normalizedFilter).toList();
        Long requestedModuleId = normalizeModuleId(moduleId);
        List<AssignmentModuleGroupView> availableModuleGroups = buildModuleGroups(allViews);
        List<AssignmentModuleGroupView> moduleGroups = buildModuleGroups(statusFiltered);
        Long selectedModuleId = requestedModuleId != null
                && availableModuleGroups.stream().anyMatch(g -> requestedModuleId.equals(g.moduleId()))
                ? requestedModuleId
                : null;
        Long finalSelectedModuleId = selectedModuleId;
        List<AssignmentStudentViewDto> filtered = finalSelectedModuleId == null
                ? List.of()
                : statusFiltered.stream()
                .filter(v -> moduleMatches(v, finalSelectedModuleId))
                .toList();

        model.addAttribute("student", student);
        model.addAttribute("assignmentViews", filtered);
        model.addAttribute("moduleGroups", moduleGroups);
        model.addAttribute("selectedModuleId", selectedModuleId);
        model.addAttribute("selectedModuleLabel", selectedModuleLabel(availableModuleGroups, selectedModuleId));
        model.addAttribute("showModuleSelection", selectedModuleId == null);
        model.addAttribute("filter", normalizedFilter);
        model.addAttribute("totalCount", allViews.size());
        model.addAttribute("upcomingCount", allViews.stream().filter(AssignmentStudentViewDto::isUpcoming).count());
        model.addAttribute("overdueCount", allViews.stream().filter(AssignmentStudentViewDto::isOverdue).count());
        model.addAttribute("submittedCount", allViews.stream()
                .filter(v -> v.getStatus() != SubmissionStatus.NOT_SUBMITTED)
                .count());
        model.addAttribute("now", now);
        return "student/assignments/list";
    }

    @GetMapping("/{id}")
    public String assignmentDetails(@PathVariable Long id, Model model) {
        Student student = getCurrentStudent();
        Assignment assignment = loadVisibleAssignmentForStudent(id, student);
        Optional<AssignmentSubmission> submissionOpt =
                assignmentSubmissionService.findByAssignmentAndStudent(id, student.getId());

        LocalDateTime now = LocalDateTime.now();
        boolean canUpdateSubmission = submissionOpt.isEmpty() || !now.isAfter(assignment.getDueDate());
        boolean lateSubmissionAllowed = submissionOpt.isEmpty() && assignment.getDueDate().isBefore(now);
        SubmissionStatus status = submissionOpt.map(AssignmentSubmission::getStatus).orElse(SubmissionStatus.NOT_SUBMITTED);
        boolean overdue = status == SubmissionStatus.NOT_SUBMITTED && assignment.getDueDate().isBefore(now);

        model.addAttribute("student", student);
        model.addAttribute("assignment", assignment);
        model.addAttribute("submission", submissionOpt.orElse(null));
        model.addAttribute("submissionFiles", submissionOpt
                .map(s -> assignmentSubmissionService.findFilesForSubmission(s.getId()))
                .orElse(List.of()));
        model.addAttribute("canUpdateSubmission", canUpdateSubmission);
        model.addAttribute("lateSubmissionAllowed", lateSubmissionAllowed);
        model.addAttribute("status", status);
        model.addAttribute("overdue", overdue);
        model.addAttribute("now", now);
        return "student/assignments/detail";
    }

    @PostMapping("/{id}/submit")
    public Object submitAssignment(@PathVariable Long id,
                                   @RequestParam(required = false) String submissionText,
                                   @RequestParam(required = false) MultipartFile[] files,
                                   @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                   RedirectAttributes flash) {
        Student student = getCurrentStudent();
        loadVisibleAssignmentForStudent(id, student);
        AssignmentSubmission submission = assignmentSubmissionService.submitAssignment(id, student.getId(), submissionText, files);

        String message;
        if (submission.isLateSubmission()) {
            message = "Soumission enregistree, mais apres la date limite.";
            flash.addFlashAttribute("warningMessage", message);
        } else {
            message = "Soumission enregistree avec succes.";
            flash.addFlashAttribute("successMessage", message);
        }

        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return ResponseEntity.ok(Map.of(
                    "redirectUrl", "/student/assignments/" + id,
                    "message", message,
                    "late", submission.isLateSubmission()
            ));
        }
        return "redirect:/student/assignments/" + id;
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> downloadAssignmentAttachment(@PathVariable Long id) {
        Student student = getCurrentStudent();
        Assignment assignment = loadVisibleAssignmentForStudent(id, student);
        Resource resource = assignmentService.loadAssignmentAttachment(assignment);
        return buildFileResponse(resource, assignment.getAttachmentPath());
    }

    @GetMapping("/{id}/submission-file")
    public ResponseEntity<Resource> downloadSubmissionFile(@PathVariable Long id) {
        Student student = getCurrentStudent();
        loadVisibleAssignmentForStudent(id, student);
        AssignmentSubmission submission = assignmentSubmissionService.findByAssignmentAndStudent(id, student.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucune soumission disponible."));

        List<AssignmentSubmissionFile> files = assignmentSubmissionService.findFilesForSubmission(submission.getId());
        if (!files.isEmpty()) {
            AssignmentSubmissionFile first = files.get(0);
            Resource resource = assignmentSubmissionService.loadSubmissionFile(first);
            return buildFileResponse(resource, first.getFilePath());
        }

        Resource resource = assignmentSubmissionService.loadSubmissionFile(submission);
        return buildFileResponse(resource, submission.getFilePath());
    }

    @GetMapping("/{id}/submission-files/{fileId}")
    public ResponseEntity<Resource> downloadSubmissionFileById(@PathVariable Long id,
                                                               @PathVariable Long fileId) {
        Student student = getCurrentStudent();
        loadVisibleAssignmentForStudent(id, student);

        AssignmentSubmissionFile file = assignmentSubmissionService.findFileForStudentSubmission(id, student.getId(), fileId);
        Resource resource = assignmentSubmissionService.loadSubmissionFile(file);
        return buildFileResponse(resource, file.getFilePath());
    }

    @PostMapping("/{id}/submission-files/{fileId}/delete")
    public String deleteSubmissionFile(@PathVariable Long id,
                                       @PathVariable Long fileId,
                                       RedirectAttributes flash) {
        Student student = getCurrentStudent();
        loadVisibleAssignmentForStudent(id, student);

        assignmentSubmissionService.removeSubmissionFileByStudent(id, student.getId(), fileId);
        flash.addFlashAttribute("successMessage", "Fichier supprime de la soumission.");
        return "redirect:/student/assignments/" + id;
    }

    private Stream<AssignmentStudentViewDto> filterAssignmentViews(List<AssignmentStudentViewDto> allViews,
                                                                   String normalizedFilter) {
        Stream<AssignmentStudentViewDto> stream = allViews.stream();
        switch (normalizedFilter) {
            case "upcoming" -> stream = stream.filter(v -> v.getStatus() == SubmissionStatus.NOT_SUBMITTED && v.isUpcoming());
            case "overdue" -> stream = stream.filter(v -> v.getStatus() == SubmissionStatus.NOT_SUBMITTED && v.isOverdue());
            case "submitted" -> stream = stream.filter(v -> v.getStatus() != SubmissionStatus.NOT_SUBMITTED);
            case "not_submitted" -> stream = stream.filter(v -> v.getStatus() == SubmissionStatus.NOT_SUBMITTED);
            default -> {
            }
        }
        return stream;
    }

    private Long normalizeModuleId(Long moduleId) {
        return moduleId != null && moduleId > 0 ? moduleId : null;
    }

    private boolean moduleMatches(AssignmentStudentViewDto view, Long moduleId) {
        return view.getAssignment() != null
                && view.getAssignment().getModule() != null
                && moduleId.equals(view.getAssignment().getModule().getId());
    }

    private List<AssignmentModuleGroupView> buildModuleGroups(List<AssignmentStudentViewDto> views) {
        Map<Long, List<AssignmentStudentViewDto>> grouped = new LinkedHashMap<>();
        views.stream()
                .filter(v -> v.getAssignment() != null && v.getAssignment().getModule() != null)
                .sorted((a, b) -> {
                    String left = a.getAssignment().getModule().getNom() != null
                            ? a.getAssignment().getModule().getNom()
                            : "";
                    String right = b.getAssignment().getModule().getNom() != null
                            ? b.getAssignment().getModule().getNom()
                            : "";
                    return String.CASE_INSENSITIVE_ORDER.compare(left, right);
                })
                .forEach(view -> grouped
                        .computeIfAbsent(view.getAssignment().getModule().getId(), ignored -> new java.util.ArrayList<>())
                        .add(view));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<AssignmentStudentViewDto> moduleViews = entry.getValue();
                    AssignmentStudentViewDto first = moduleViews.get(0);
                    String moduleName = first.getAssignment().getModule().getNom() != null
                            ? first.getAssignment().getModule().getNom()
                            : "Module";
                    String moduleCode = first.getAssignment().getModule().getCode();
                    long pending = moduleViews.stream().filter(v -> "pending".equals(v.getDisplayStatus())).count();
                    long late = moduleViews.stream().filter(v -> "late".equals(v.getDisplayStatus())).count();
                    long submitted = moduleViews.stream().filter(v -> "submitted".equals(v.getDisplayStatus())).count();
                    long reviewed = moduleViews.stream().filter(v -> "reviewed".equals(v.getDisplayStatus())).count();
                    return new AssignmentModuleGroupView(
                            entry.getKey(),
                            moduleName,
                            moduleCode,
                            moduleCode != null && !moduleCode.isBlank()
                                    ? moduleName + " (" + moduleCode + ")"
                                    : moduleName,
                            moduleViews.size(),
                            pending,
                            late,
                            submitted,
                            reviewed
                    );
                })
                .toList();
    }

    private String selectedModuleLabel(List<AssignmentModuleGroupView> groups, Long selectedModuleId) {
        if (selectedModuleId == null) {
            return null;
        }
        return groups.stream()
                .filter(group -> selectedModuleId.equals(group.moduleId()))
                .map(AssignmentModuleGroupView::label)
                .findFirst()
                .orElse("Module selectionne");
    }

    private AssignmentStudentViewDto buildStudentView(Assignment assignment, Long studentId, LocalDateTime now) {
        Optional<AssignmentSubmission> submission =
                assignmentSubmissionService.findByAssignmentAndStudent(assignment.getId(), studentId);
        SubmissionStatus status = submission.map(AssignmentSubmission::getStatus).orElse(SubmissionStatus.NOT_SUBMITTED);
        boolean submitted = status != SubmissionStatus.NOT_SUBMITTED;
        boolean overdue = !submitted && assignment.getDueDate().isBefore(now);
        boolean upcoming = !submitted && !overdue;
        boolean late = submission.map(AssignmentSubmission::isLateSubmission).orElse(false);

        return new AssignmentStudentViewDto(
                assignment,
                submission.orElse(null),
                status,
                upcoming,
                overdue,
                late
        );
    }

    private Assignment loadVisibleAssignmentForStudent(Long assignmentId, Student student) {
        Long classeId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long filiereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        return assignmentService.findVisibleByIdForStudent(assignmentId, classeId, filiereId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non accessible."));
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

    public record AssignmentModuleGroupView(Long moduleId,
                                            String moduleName,
                                            String moduleCode,
                                            String label,
                                            int count,
                                            long pending,
                                            long late,
                                            long submitted,
                                            long reviewed) {
    }
}
