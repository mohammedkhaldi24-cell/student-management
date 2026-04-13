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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
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
    public String listAssignments(@RequestParam(defaultValue = "all") String filter, Model model) {
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
        Stream<AssignmentStudentViewDto> stream = allViews.stream();
        switch (normalizedFilter) {
            case "upcoming" -> stream = stream.filter(v -> v.getStatus() == SubmissionStatus.NOT_SUBMITTED && v.isUpcoming());
            case "overdue" -> stream = stream.filter(v -> v.getStatus() == SubmissionStatus.NOT_SUBMITTED && v.isOverdue());
            case "submitted" -> stream = stream.filter(v -> v.getStatus() != SubmissionStatus.NOT_SUBMITTED);
            case "not_submitted" -> stream = stream.filter(v -> v.getStatus() == SubmissionStatus.NOT_SUBMITTED);
            default -> normalizedFilter = "all";
        }

        List<AssignmentStudentViewDto> filtered = stream.toList();

        model.addAttribute("student", student);
        model.addAttribute("assignmentViews", filtered);
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
        boolean canUpdateSubmission = !now.isAfter(assignment.getDueDate());
        SubmissionStatus status = submissionOpt.map(AssignmentSubmission::getStatus).orElse(SubmissionStatus.NOT_SUBMITTED);
        boolean overdue = status == SubmissionStatus.NOT_SUBMITTED && assignment.getDueDate().isBefore(now);

        model.addAttribute("student", student);
        model.addAttribute("assignment", assignment);
        model.addAttribute("submission", submissionOpt.orElse(null));
        model.addAttribute("submissionFiles", submissionOpt
                .map(s -> assignmentSubmissionService.findFilesForSubmission(s.getId()))
                .orElse(List.of()));
        model.addAttribute("canUpdateSubmission", canUpdateSubmission);
        model.addAttribute("status", status);
        model.addAttribute("overdue", overdue);
        model.addAttribute("now", now);
        return "student/assignments/detail";
    }

    @PostMapping("/{id}/submit")
    public String submitAssignment(@PathVariable Long id,
                                   @RequestParam(required = false) String submissionText,
                                   @RequestParam(required = false) MultipartFile[] files,
                                   RedirectAttributes flash) {
        Student student = getCurrentStudent();
        loadVisibleAssignmentForStudent(id, student);
        AssignmentSubmission submission = assignmentSubmissionService.submitAssignment(id, student.getId(), submissionText, files);

        if (submission.isLateSubmission()) {
            flash.addFlashAttribute("warningMessage", "Soumission enregistree, mais apres la date limite.");
        } else {
            flash.addFlashAttribute("successMessage", "Soumission enregistree avec succes.");
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
}
