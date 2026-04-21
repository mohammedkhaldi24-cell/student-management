package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.AssignmentRepository;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.repository.ModuleRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.AssignmentService;
import com.pfe.gestionetudiant.service.EmailService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ModuleRepository moduleRepository;
    private final ClasseRepository classeRepository;
    private final FiliereRepository filiereRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public Assignment createAssignment(String title,
                                       String description,
                                       LocalDateTime dueDate,
                                       MultipartFile attachment,
                                       Long teacherId,
                                       Long moduleId,
                                       Long classeId,
                                       Long filiereId,
                                       boolean published) {
        User teacher = loadTeacher(teacherId);
        Module module = loadModule(moduleId, teacherId);
        Target target = resolveTarget(classeId, filiereId, module);
        validateModuleTarget(module, target.filiere());
        validateDueDate(dueDate);

        Assignment assignment = new Assignment();
        assignment.setTitle(normalizeRequired(title, "Le titre est obligatoire."));
        assignment.setDescription(normalizeRequired(description, "La description est obligatoire."));
        assignment.setDueDate(dueDate);
        assignment.setAttachmentPath(storeFile(attachment, "assignments"));
        assignment.setTeacher(teacher);
        assignment.setModule(module);
        assignment.setTargetClasse(target.classe());
        assignment.setTargetFiliere(target.classe() != null ? null : target.filiere());
        assignment.setPublished(published);

        Assignment saved = assignmentRepository.save(assignment);
        if (saved.isPublished()) {
            emailService.sendAssignmentNotification(saved, collectRecipientEmails(saved));
        }
        return saved;
    }

    @Override
    public Assignment updateAssignment(Long assignmentId,
                                       String title,
                                       String description,
                                       LocalDateTime dueDate,
                                       MultipartFile attachment,
                                       Long teacherId,
                                       Long moduleId,
                                       Long classeId,
                                       Long filiereId,
                                       boolean published) {
        Assignment assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));

        Module module = loadModule(moduleId, teacherId);
        Target target = resolveTarget(classeId, filiereId, module);
        validateModuleTarget(module, target.filiere());
        validateDueDate(dueDate);

        boolean shouldNotify = !assignment.isPublished() && published;

        assignment.setTitle(normalizeRequired(title, "Le titre est obligatoire."));
        assignment.setDescription(normalizeRequired(description, "La description est obligatoire."));
        assignment.setDueDate(dueDate);
        assignment.setModule(module);
        assignment.setTargetClasse(target.classe());
        assignment.setTargetFiliere(target.classe() != null ? null : target.filiere());
        assignment.setPublished(published);

        if (attachment != null && !attachment.isEmpty()) {
            deleteFileQuietly(assignment.getAttachmentPath());
            assignment.setAttachmentPath(storeFile(attachment, "assignments"));
        }

        Assignment saved = assignmentRepository.save(assignment);
        if (shouldNotify) {
            emailService.sendAssignmentNotification(saved, collectRecipientEmails(saved));
        }
        return saved;
    }

    @Override
    public void deleteAssignment(Long assignmentId, Long teacherId) {
        Assignment assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));
        deleteFileQuietly(assignment.getAttachmentPath());
        assignmentRepository.delete(assignment);
    }

    @Override
    public Assignment replaceAssignmentAttachment(Long assignmentId, Long teacherId, MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            throw new IllegalArgumentException("Veuillez selectionner un fichier valide.");
        }

        Assignment assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));

        deleteFileQuietly(assignment.getAttachmentPath());
        assignment.setAttachmentPath(storeFile(attachment, "assignments"));
        return assignmentRepository.save(assignment);
    }

    @Override
    public Assignment removeAssignmentAttachment(Long assignmentId, Long teacherId) {
        Assignment assignment = assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Devoir introuvable ou non autorise."));

        deleteFileQuietly(assignment.getAttachmentPath());
        assignment.setAttachmentPath(null);
        return assignmentRepository.save(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Assignment> findById(Long assignmentId) {
        return assignmentRepository.findById(assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Assignment> findByIdAndTeacher(Long assignmentId, Long teacherId) {
        return assignmentRepository.findByIdAndTeacherId(assignmentId, teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assignment> findByTeacher(Long teacherId) {
        return assignmentRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assignment> findVisibleForStudent(Long classeId, Long filiereId) {
        return assignmentRepository.findVisibleForStudent(classeId, filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Assignment> findVisibleByIdForStudent(Long assignmentId, Long classeId, Long filiereId) {
        return assignmentRepository.findVisibleByIdForStudent(assignmentId, classeId, filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Student> getTargetStudents(Assignment assignment) {
        if (assignment.getTargetClasse() != null) {
            return studentRepository.findByClasseId(assignment.getTargetClasse().getId());
        }
        if (assignment.getTargetFiliere() != null) {
            return studentRepository.findByFiliereId(assignment.getTargetFiliere().getId());
        }
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadAssignmentAttachment(Assignment assignment) {
        if (!StringUtils.hasText(assignment.getAttachmentPath())) {
            throw new IllegalArgumentException("Aucun fichier joint pour ce devoir.");
        }
        try {
            Path path = Paths.get(assignment.getAttachmentPath()).normalize();
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

    private User loadTeacher(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
        if (teacher.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("Seul un enseignant peut gerer des devoirs.");
        }
        return teacher;
    }

    private Module loadModule(Long moduleId, Long teacherId) {
        if (moduleId == null) {
            return null;
        }
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable."));
        if (module.getTeacher() == null || !teacherId.equals(module.getTeacher().getId())) {
            throw new IllegalArgumentException("Vous ne pouvez utiliser que vos modules.");
        }
        return module;
    }

    private Target resolveTarget(Long classeId, Long filiereId, Module module) {
        if (classeId == null && filiereId == null) {
            if (module != null && module.getFiliere() != null) {
                return new Target(null, module.getFiliere());
            }
            throw new IllegalArgumentException("Veuillez choisir un module, une classe ou une filiere cible.");
        }

        if (classeId != null) {
            Classe classe = classeRepository.findById(classeId)
                    .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));
            if (classe.getFiliere() == null) {
                throw new IllegalArgumentException("Classe sans filiere.");
            }
            if (filiereId != null && !filiereId.equals(classe.getFiliere().getId())) {
                throw new IllegalArgumentException("Classe et filiere incompatibles.");
            }
            if (module != null && module.getFiliere() != null
                    && !module.getFiliere().getId().equals(classe.getFiliere().getId())) {
                throw new IllegalArgumentException("La classe ne correspond pas a la filiere du module.");
            }
            return new Target(classe, classe.getFiliere());
        }

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
        if (module != null && module.getFiliere() != null
                && !module.getFiliere().getId().equals(filiere.getId())) {
            throw new IllegalArgumentException("La filiere ne correspond pas au module.");
        }
        return new Target(null, filiere);
    }

    private void validateModuleTarget(Module module, Filiere targetFiliere) {
        if (module != null && targetFiliere != null) {
            if (module.getFiliere() == null || !targetFiliere.getId().equals(module.getFiliere().getId())) {
                throw new IllegalArgumentException("Le module ne correspond pas a la filiere cible.");
            }
        }
    }

    private void validateDueDate(LocalDateTime dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("La date limite est obligatoire.");
        }
        if (dueDate.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("La date limite doit etre dans le futur.");
        }
    }

    private String storeFile(MultipartFile file, String folderName) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
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

    private List<String> collectRecipientEmails(Assignment assignment) {
        Set<String> emails = new LinkedHashSet<>();
        for (Student student : getTargetStudents(assignment)) {
            if (student.getUser() != null && StringUtils.hasText(student.getUser().getEmail())) {
                emails.add(student.getUser().getEmail().trim());
            }
        }
        return List.copyOf(emails);
    }

    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private record Target(Classe classe, Filiere filiere) {
    }
}
