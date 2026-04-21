package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.AssignmentSubmissionFile;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.repository.AssignmentRepository;
import com.pfe.gestionetudiant.repository.AssignmentSubmissionFileRepository;
import com.pfe.gestionetudiant.repository.AssignmentSubmissionRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.AssignmentSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentSubmissionServiceImpl implements AssignmentSubmissionService {

    private final AssignmentSubmissionRepository submissionRepository;
    private final AssignmentSubmissionFileRepository submissionFileRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final AssignmentService assignmentService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public AssignmentSubmission submitAssignment(Long assignmentId,
                                                 Long studentId,
                                                 String submissionText,
                                                 MultipartFile[] files) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable."));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Etudiant introuvable."));

        ensureStudentCanAccessAssignment(student, assignment);

        List<MultipartFile> incomingFiles = files == null
                ? List.of()
                : java.util.Arrays.stream(files)
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if ((submissionText == null || submissionText.trim().isEmpty())
                && incomingFiles.isEmpty()) {
            throw new IllegalArgumentException("Veuillez fournir un texte ou au moins un fichier.");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<AssignmentSubmission> existingOpt =
                submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);

        if (existingOpt.isPresent() && now.isAfter(assignment.getDueDate())) {
            throw new IllegalArgumentException("Modification impossible apres la date limite.");
        }

        boolean resubmission = existingOpt.isPresent();
        AssignmentSubmission submission = existingOpt.orElseGet(AssignmentSubmission::new);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setSubmissionText(submissionText != null ? submissionText.trim() : null);
        submission.setSubmittedAt(now);
        submission.setLateSubmission(now.isAfter(assignment.getDueDate()));
        submission.setStatus(SubmissionStatus.SUBMITTED);
        if (resubmission) {
            submission.setScore(null);
            submission.setFeedback(null);
        }

        submission = submissionRepository.save(submission);

        if (!incomingFiles.isEmpty()) {
            storeSubmissionFiles(incomingFiles, submission);
            updateSubmissionPrimaryFilePath(submission);
        } else if (submission.getFiles() != null && !submission.getFiles().isEmpty()) {
            // Conserver les fichiers existants si la mise a jour ne contient pas de nouveaux fichiers.
            submission.setFilePath(submission.getFiles().get(0).getFilePath());
        } else if (StringUtils.hasText(submission.getFilePath())) {
            // Cas legacy (ancienne soumission avec une seule colonne file_path)
            submission.setFilePath(submission.getFilePath());
        } else {
            submission.setFilePath(null);
        }

        return submissionRepository.save(submission);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentSubmission> findByAssignmentAndStudent(Long assignmentId, Long studentId) {
        return submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmission> findByAssignmentForTeacher(Long assignmentId, Long teacherId) {
        Assignment assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));
        return submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignment.getId());
    }

    @Override
    public AssignmentSubmission reviewSubmission(Long assignmentId,
                                                 Long submissionId,
                                                 Long teacherId,
                                                 Double score,
                                                 String feedback,
                                                 SubmissionStatus status) {
        assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));

        AssignmentSubmission submission = submissionRepository.findByIdAndAssignmentId(submissionId, assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Soumission introuvable."));

        if (status == null) {
            status = (score != null) ? SubmissionStatus.GRADED : SubmissionStatus.REVIEWED;
        }

        if (status == SubmissionStatus.GRADED && score == null) {
            throw new IllegalArgumentException("Une note est requise pour le statut GRADED.");
        }

        submission.setScore(score);
        submission.setFeedback(feedback);
        submission.setStatus(status);

        return submissionRepository.save(submission);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingSubmissionsForTeacher(Long teacherId) {
        List<Assignment> assignments = assignmentRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        long pending = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Assignment assignment : assignments) {
            if (!assignment.isPublished()) {
                continue;
            }
            if (assignment.getDueDate() != null && assignment.getDueDate().isBefore(now)) {
                continue;
            }

            List<Student> targetStudents = assignmentService.getTargetStudents(assignment);
            if (targetStudents.isEmpty()) {
                continue;
            }

            Set<Long> submittedStudentIds = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignment.getId())
                    .stream()
                    .map(s -> s.getStudent().getId())
                    .collect(Collectors.toSet());

            pending += targetStudents.stream()
                    .map(Student::getId)
                    .filter(id -> !submittedStudentIds.contains(id))
                    .count();
        }

        return pending;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmission> findRecentFeedbackForStudent(Long studentId, int maxItems) {
        List<AssignmentSubmission> list = submissionRepository.findByStudentIdAndStatusInOrderBySubmittedAtDesc(
                studentId,
                List.of(SubmissionStatus.REVIEWED, SubmissionStatus.GRADED)
        );
        return list.stream().limit(Math.max(maxItems, 0)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadSubmissionFile(AssignmentSubmission submission) {
        List<AssignmentSubmissionFile> files = findFilesForSubmission(submission.getId());
        if (!files.isEmpty()) {
            return loadSubmissionFile(files.get(0));
        }
        if (!StringUtils.hasText(submission.getFilePath())) {
            throw new IllegalArgumentException("Aucun fichier joint a cette soumission.");
        }
        return loadPathAsResource(submission.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadSubmissionFile(AssignmentSubmissionFile file) {
        if (file == null || !StringUtils.hasText(file.getFilePath())) {
            throw new IllegalArgumentException("Fichier de soumission introuvable.");
        }
        return loadPathAsResource(file.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionFile> findFilesForSubmission(Long submissionId) {
        return submissionFileRepository.findBySubmissionIdOrderByUploadedAtAsc(submissionId);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentSubmissionFile findFileForStudentSubmission(Long assignmentId, Long studentId, Long fileId) {
        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Soumission introuvable."));

        return submissionFileRepository.findByIdAndSubmissionId(fileId, submission.getId())
                .orElseThrow(() -> new IllegalArgumentException("Fichier introuvable."));
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentSubmissionFile findFileForTeacherSubmission(Long assignmentId, Long teacherId, Long submissionId, Long fileId) {
        assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));

        AssignmentSubmission submission = submissionRepository.findByIdAndAssignmentId(submissionId, assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Soumission introuvable."));

        return submissionFileRepository.findByIdAndSubmissionId(fileId, submission.getId())
                .orElseThrow(() -> new IllegalArgumentException("Fichier introuvable."));
    }

    @Override
    public AssignmentSubmission removeSubmissionFileByStudent(Long assignmentId, Long studentId, Long fileId) {
        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Soumission introuvable."));

        Assignment assignment = submission.getAssignment();
        if (assignment == null) {
            throw new IllegalArgumentException("Devoir introuvable.");
        }

        if (LocalDateTime.now().isAfter(assignment.getDueDate())) {
            throw new IllegalArgumentException("Suppression impossible apres la date limite.");
        }

        AssignmentSubmissionFile file = submissionFileRepository.findByIdAndSubmissionId(fileId, submission.getId())
                .orElseThrow(() -> new IllegalArgumentException("Fichier introuvable."));

        List<AssignmentSubmissionFile> currentFiles = submissionFileRepository
                .findBySubmissionIdOrderByUploadedAtAsc(submission.getId());

        boolean isLastFile = currentFiles.size() <= 1;
        boolean hasText = StringUtils.hasText(submission.getSubmissionText());
        if (isLastFile && !hasText) {
            throw new IllegalArgumentException("Au moins un fichier ou un texte est requis pour la soumission.");
        }

        deleteFileQuietly(file.getFilePath());
        submissionFileRepository.delete(file);

        updateSubmissionPrimaryFilePath(submission);
        return submissionRepository.save(submission);
    }

    private void ensureStudentCanAccessAssignment(Student student, Assignment assignment) {
        if (!assignment.isPublished()) {
            throw new IllegalArgumentException("Ce devoir n'est pas publie.");
        }

        Long studentClasseId = student.getClasse() != null ? student.getClasse().getId() : null;
        Long studentFiliereId = (student.getClasse() != null && student.getClasse().getFiliere() != null)
                ? student.getClasse().getFiliere().getId()
                : null;

        boolean classMatch = assignment.getTargetClasse() != null
                && studentClasseId != null
                && assignment.getTargetClasse().getId().equals(studentClasseId);
        boolean filiereMatch = assignment.getTargetFiliere() != null
                && studentFiliereId != null
                && assignment.getTargetFiliere().getId().equals(studentFiliereId);

        if (!(classMatch || filiereMatch)) {
            throw new IllegalArgumentException("Vous n'etes pas autorise a soumettre ce devoir.");
        }
    }

    private List<AssignmentSubmissionFile> storeSubmissionFiles(List<MultipartFile> files, AssignmentSubmission submission) {
        List<AssignmentSubmissionFile> stored = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            AssignmentSubmissionFile item = new AssignmentSubmissionFile();
            item.setSubmission(submission);
            item.setOriginalFileName(file.getOriginalFilename());
            item.setContentType(file.getContentType());
            item.setFileSize(file.getSize());
            item.setFilePath(storeFile(file, Paths.get("assignment-submissions", String.valueOf(submission.getId())).toString()));
            stored.add(submissionFileRepository.save(item));
        }
        stored.sort(java.util.Comparator.comparing(AssignmentSubmissionFile::getUploadedAt));
        return stored;
    }

    private String storeFile(MultipartFile file, String folderName) {
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("submission");
        String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = System.currentTimeMillis() + "_" + safe;

        try {
            Path folder = Paths.get(uploadDir, folderName).toAbsolutePath().normalize();
            Files.createDirectories(folder);
            Path target = folder.resolve(filename).normalize();
            file.transferTo(target);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("Erreur lors de l'upload du fichier.");
        }
    }

    private void deleteFileQuietly(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ignored) {
        }
    }

    private Resource loadPathAsResource(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Fichier introuvable.");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Fichier non lisible.");
            }
            return resource;
        } catch (IOException e) {
            throw new IllegalArgumentException("Erreur de lecture du fichier.");
        }
    }

    private void removeSubmissionFiles(AssignmentSubmission submission) {
        List<AssignmentSubmissionFile> oldFiles = submissionFileRepository.findBySubmissionIdOrderByUploadedAtAsc(submission.getId());
        for (AssignmentSubmissionFile old : oldFiles) {
            deleteFileQuietly(old.getFilePath());
        }
        submissionFileRepository.deleteBySubmissionId(submission.getId());

        if (submission.getFiles() != null) {
            submission.getFiles().clear();
        }

        if (StringUtils.hasText(submission.getFilePath())) {
            deleteFileQuietly(submission.getFilePath());
            submission.setFilePath(null);
        }
    }

    private void updateSubmissionPrimaryFilePath(AssignmentSubmission submission) {
        List<AssignmentSubmissionFile> remainingFiles =
                submissionFileRepository.findBySubmissionIdOrderByUploadedAtAsc(submission.getId());

        if (submission.getFiles() != null) {
            submission.getFiles().clear();
            submission.getFiles().addAll(remainingFiles);
        }

        if (remainingFiles.isEmpty()) {
            submission.setFilePath(null);
        } else {
            submission.setFilePath(remainingFiles.get(0).getFilePath());
        }
    }
}
