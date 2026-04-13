package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.AnnouncementRepository;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.AnnouncementService;
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
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final ClasseRepository classeRepository;
    private final FiliereRepository filiereRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public Announcement createAnnouncement(String title,
                                           String message,
                                           Long authorId,
                                           Long classeId,
                                           Long filiereId) {
        return createAnnouncement(title, message, authorId, classeId, filiereId, null);
    }

    @Override
    public Announcement createAnnouncement(String title,
                                           String message,
                                           Long authorId,
                                           Long classeId,
                                           Long filiereId,
                                           MultipartFile attachment) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("Auteur introuvable."));
        if (author.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("Seul un enseignant peut publier des annonces.");
        }

        Target target = resolveTarget(classeId, filiereId);

        Announcement announcement = new Announcement();
        announcement.setTitle(title != null ? title.trim() : null);
        announcement.setMessage(message != null ? message.trim() : null);
        announcement.setAuthor(author);
        announcement.setTargetClasse(target.classe());
        announcement.setTargetFiliere(target.filiere());
        announcement.setAttachmentPath(storeFile(attachment, "announcements"));

        Announcement saved = announcementRepository.save(announcement);
        emailService.sendAnnouncementNotification(saved, collectRecipients(target.classe(), target.filiere()));
        return saved;
    }

    @Override
    public void deleteAnnouncement(Long id, Long authorId) {
        Announcement announcement = announcementRepository.findByIdAndAuthorId(id, authorId)
                .orElseThrow(() -> new IllegalArgumentException("Annonce introuvable ou non autorisee."));
        deleteFileQuietly(announcement.getAttachmentPath());
        announcementRepository.delete(announcement);
    }

    @Override
    public Announcement replaceAttachment(Long id, Long authorId, MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            throw new IllegalArgumentException("Veuillez selectionner un fichier valide.");
        }

        Announcement announcement = announcementRepository.findByIdAndAuthorId(id, authorId)
                .orElseThrow(() -> new IllegalArgumentException("Annonce introuvable ou non autorisee."));

        deleteFileQuietly(announcement.getAttachmentPath());
        announcement.setAttachmentPath(storeFile(attachment, "announcements"));
        return announcementRepository.save(announcement);
    }

    @Override
    public Announcement removeAttachment(Long id, Long authorId) {
        Announcement announcement = announcementRepository.findByIdAndAuthorId(id, authorId)
                .orElseThrow(() -> new IllegalArgumentException("Annonce introuvable ou non autorisee."));
        deleteFileQuietly(announcement.getAttachmentPath());
        announcement.setAttachmentPath(null);
        return announcementRepository.save(announcement);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadAttachmentAsResource(Announcement announcement) {
        if (!StringUtils.hasText(announcement.getAttachmentPath())) {
            throw new IllegalArgumentException("Aucun fichier joint pour cette annonce.");
        }

        try {
            Path path = Paths.get(announcement.getAttachmentPath()).normalize();
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

    @Override
    @Transactional(readOnly = true)
    public Optional<Announcement> findById(Long id) {
        return announcementRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> findByAuthorId(Long authorId) {
        return announcementRepository.findByAuthorIdOrderByCreatedAtDesc(authorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> findByFiliereId(Long filiereId) {
        return announcementRepository.findByTargetFiliereIdOrderByCreatedAtDesc(filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> findForStudent(Long classeId, Long filiereId) {
        return announcementRepository.findVisibleForStudent(classeId, filiereId);
    }

    private Target resolveTarget(Long classeId, Long filiereId) {
        if (classeId == null && filiereId == null) {
            throw new IllegalArgumentException("Veuillez choisir une classe ou une filiere cible.");
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
            return new Target(classe, classe.getFiliere());
        }

        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
        return new Target(null, filiere);
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

    private record Target(Classe classe, Filiere filiere) {
    }
}
