package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.CourseDocument;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.repository.CourseContentRepository;
import com.pfe.gestionetudiant.repository.CourseDocumentRepository;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.repository.ModuleRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.CourseContentService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseContentServiceImpl implements CourseContentService {

    private final CourseContentRepository courseContentRepository;
    private final CourseDocumentRepository courseDocumentRepository;
    private final ModuleRepository moduleRepository;
    private final UserRepository userRepository;
    private final ClasseRepository classeRepository;
    private final FiliereRepository filiereRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public CourseContent createCourse(String title,
                                      String description,
                                      MultipartFile file,
                                      Long moduleId,
                                      Long teacherId,
                                      Long classeId,
                                      Long filiereId) {
        MultipartFile[] files = file == null ? null : new MultipartFile[]{file};
        return createCourse(title, description, files, moduleId, teacherId, classeId, filiereId);
    }

    @Override
    public CourseContent createCourse(String title,
                                      String description,
                                      MultipartFile[] files,
                                      Long moduleId,
                                      Long teacherId,
                                      Long classeId,
                                      Long filiereId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
        if (teacher.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("Le createur doit etre un enseignant.");
        }

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable."));
        if (module.getTeacher() == null || !teacherId.equals(module.getTeacher().getId())) {
            throw new IllegalArgumentException("Vous ne pouvez publier que dans vos modules.");
        }

        Target target = resolveTarget(classeId, filiereId, module);

        CourseContent content = new CourseContent();
        content.setTitle(title != null ? title.trim() : null);
        content.setDescription(description);
        content.setModule(module);
        content.setTeacher(teacher);
        content.setClasse(target.classe());
        content.setFiliere(target.filiere());

        CourseContent saved = courseContentRepository.save(content);
        storeCourseDocuments(normalizedFiles(files), saved);
        updatePrimaryFilePath(saved);
        saved = courseContentRepository.save(saved);

        emailService.sendCourseContentNotification(saved, collectRecipients(target.classe(), target.filiere()));
        return saved;
    }

    @Override
    public void deleteCourse(Long courseId, Long teacherId) {
        CourseContent content = courseContentRepository.findByIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable ou non autorise."));
        for (CourseDocument document : courseDocumentRepository.findByCourseContentIdOrderByUploadedAtAsc(content.getId())) {
            deleteFileQuietly(document.getFilePath());
        }
        courseDocumentRepository.deleteByCourseContentId(content.getId());
        deleteFileQuietly(content.getFilePath());
        courseContentRepository.delete(content);
    }

    @Override
    public CourseContent replaceCourseFile(Long courseId, Long teacherId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Veuillez selectionner un fichier valide.");
        }

        CourseContent content = courseContentRepository.findByIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable ou non autorise."));

        removeAllCourseDocuments(content);
        storeCourseDocuments(List.of(file), content);
        updatePrimaryFilePath(content);
        return courseContentRepository.save(content);
    }

    @Override
    public CourseContent removeCourseFile(Long courseId, Long teacherId) {
        CourseContent content = courseContentRepository.findByIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable ou non autorise."));

        removeAllCourseDocuments(content);
        return courseContentRepository.save(content);
    }

    @Override
    public CourseContent addCourseFiles(Long courseId, Long teacherId, MultipartFile[] files) {
        CourseContent content = courseContentRepository.findByIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable ou non autorise."));

        List<MultipartFile> incomingFiles = normalizedFiles(files);
        if (incomingFiles.isEmpty()) {
            throw new IllegalArgumentException("Veuillez selectionner au moins un fichier valide.");
        }

        storeCourseDocuments(incomingFiles, content);
        updatePrimaryFilePath(content);
        return courseContentRepository.save(content);
    }

    @Override
    public CourseContent removeCourseFile(Long courseId, Long teacherId, Long fileId) {
        CourseContent content = courseContentRepository.findByIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Cours introuvable ou non autorise."));
        CourseDocument document = courseDocumentRepository.findByIdAndCourseContentId(fileId, content.getId())
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable."));

        deleteFileQuietly(document.getFilePath());
        courseDocumentRepository.delete(document);
        updatePrimaryFilePath(content);
        return courseContentRepository.save(content);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseContent> findById(Long id) {
        return courseContentRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContent> findByTeacherId(Long teacherId) {
        return courseContentRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContent> findByFiliereId(Long filiereId) {
        return courseContentRepository.findByFiliereIdOrderByCreatedAtDesc(filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContent> findForStudent(Long classeId, Long filiereId) {
        return courseContentRepository.findVisibleForStudent(classeId, filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(CourseContent courseContent) {
        List<CourseDocument> documents = findFilesForCourse(courseContent.getId());
        if (!documents.isEmpty()) {
            return loadFileAsResource(documents.get(0));
        }
        if (!StringUtils.hasText(courseContent.getFilePath())) {
            throw new IllegalArgumentException("Aucun fichier joint pour ce cours.");
        }

        return loadPathAsResource(courseContent.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(CourseDocument courseDocument) {
        if (courseDocument == null || !StringUtils.hasText(courseDocument.getFilePath())) {
            throw new IllegalArgumentException("Document introuvable.");
        }
        return loadPathAsResource(courseDocument.getFilePath());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDocument> findFilesForCourse(Long courseId) {
        return courseDocumentRepository.findByCourseContentIdOrderByUploadedAtAsc(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDocument findFileForCourse(Long courseId, Long fileId) {
        return courseDocumentRepository.findByIdAndCourseContentId(fileId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("Document introuvable."));
    }

    private Resource loadPathAsResource(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Fichier introuvable sur le serveur.");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Le fichier n'est pas lisible.");
            }
            return resource;
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de charger le fichier.");
        }
    }

    private List<MultipartFile> normalizedFiles(MultipartFile[] files) {
        if (files == null) {
            return List.of();
        }
        return java.util.Arrays.stream(files)
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private List<CourseDocument> storeCourseDocuments(List<MultipartFile> files, CourseContent content) {
        if (files.isEmpty()) {
            return List.of();
        }
        List<CourseDocument> stored = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            CourseDocument document = new CourseDocument();
            document.setCourseContent(content);
            document.setOriginalFileName(file.getOriginalFilename());
            document.setContentType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setFilePath(storeFile(file));
            stored.add(courseDocumentRepository.save(document));
        }
        if (content.getFiles() != null) {
            content.getFiles().addAll(stored);
        }
        return stored;
    }

    private void removeAllCourseDocuments(CourseContent content) {
        List<CourseDocument> documents = courseDocumentRepository.findByCourseContentIdOrderByUploadedAtAsc(content.getId());
        for (CourseDocument document : documents) {
            deleteFileQuietly(document.getFilePath());
        }
        courseDocumentRepository.deleteByCourseContentId(content.getId());
        if (content.getFiles() != null) {
            content.getFiles().clear();
        }
        if (StringUtils.hasText(content.getFilePath())) {
            deleteFileQuietly(content.getFilePath());
        }
        content.setFilePath(null);
    }

    private void updatePrimaryFilePath(CourseContent content) {
        List<CourseDocument> remaining = courseDocumentRepository.findByCourseContentIdOrderByUploadedAtAsc(content.getId());
        if (content.getFiles() != null) {
            content.getFiles().clear();
            content.getFiles().addAll(remaining);
        }
        content.setFilePath(remaining.isEmpty() ? null : remaining.get(0).getFilePath());
    }

    private Target resolveTarget(Long classeId, Long filiereId, Module module) {
        if (classeId == null && filiereId == null) {
            if (module.getFiliere() == null) {
                throw new IllegalArgumentException("Aucune filiere cible valide.");
            }
            return new Target(null, module.getFiliere());
        }

        if (classeId != null) {
            Classe classe = classeRepository.findById(classeId)
                    .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));
            if (classe.getFiliere() == null) {
                throw new IllegalArgumentException("Classe sans filiere.");
            }
            if (filiereId != null && !filiereId.equals(classe.getFiliere().getId())) {
                throw new IllegalArgumentException("La classe ne correspond pas a la filiere cible.");
            }
            if (module.getFiliere() == null || !module.getFiliere().getId().equals(classe.getFiliere().getId())) {
                throw new IllegalArgumentException("Le module n'appartient pas a la filiere de la classe.");
            }
            return new Target(classe, classe.getFiliere());
        }

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
        if (module.getFiliere() == null || !module.getFiliere().getId().equals(filiere.getId())) {
            throw new IllegalArgumentException("Le module n'appartient pas a la filiere cible.");
        }
        return new Target(null, filiere);
    }

    private String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = StringUtils.cleanPath(
                Optional.ofNullable(file.getOriginalFilename()).orElse("document")
        );
        String safeName = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = System.currentTimeMillis() + "_" + safeName;

        try {
            Path folder = Paths.get(uploadDir, "courses").toAbsolutePath().normalize();
            Files.createDirectories(folder);
            Path targetFile = folder.resolve(filename).normalize();
            file.transferTo(targetFile);
            return targetFile.toString();
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

    private List<String> collectRecipients(Classe classe, Filiere filiere) {
        List<Student> students = classe != null
                ? studentRepository.findByClasseId(classe.getId())
                : studentRepository.findByFiliereId(filiere.getId());

        Set<String> emails = new LinkedHashSet<>();
        for (Student student : students) {
            if (student.getUser() != null && StringUtils.hasText(student.getUser().getEmail())) {
                emails.add(student.getUser().getEmail().trim());
            }
        }
        return List.copyOf(emails);
    }

    private record Target(Classe classe, Filiere filiere) {
    }
}
