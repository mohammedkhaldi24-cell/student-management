package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Announcement;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface AnnouncementService {

    Announcement createAnnouncement(String title,
                                    String message,
                                    Long authorId,
                                    Long classeId,
                                    Long filiereId);

    Announcement createAnnouncement(String title,
                                    String message,
                                    Long authorId,
                                    Long classeId,
                                    Long filiereId,
                                    Long moduleId);

    Announcement createAnnouncement(String title,
                                    String message,
                                    Long authorId,
                                    Long classeId,
                                    Long filiereId,
                                    MultipartFile attachment);

    Announcement createAnnouncement(String title,
                                    String message,
                                    Long authorId,
                                    Long classeId,
                                    Long filiereId,
                                    Long moduleId,
                                    MultipartFile attachment);

    void deleteAnnouncement(Long id, Long authorId);

    Announcement replaceAttachment(Long id, Long authorId, MultipartFile attachment);

    Announcement removeAttachment(Long id, Long authorId);

    Resource loadAttachmentAsResource(Announcement announcement);

    Optional<Announcement> findById(Long id);

    List<Announcement> findByAuthorId(Long authorId);

    List<Announcement> findByFiliereId(Long filiereId);

    List<Announcement> findForStudent(Long classeId, Long filiereId);
}
