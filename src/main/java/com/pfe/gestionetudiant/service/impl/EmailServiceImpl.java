package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:no-reply@gestionetu.local}")
    private String fromAddress;

    @Override
    public void sendAnnouncementNotification(Announcement announcement, List<String> recipients) {
        String subject = "[GestionEtu] Nouvelle annonce: " + announcement.getTitle();
        String body = """
                Bonjour,

                Une nouvelle annonce est disponible sur votre plateforme:
                Titre: %s

                Message:
                %s

                Connectez-vous a GestionEtu pour plus de details.
                """.formatted(announcement.getTitle(), announcement.getMessage());

        sendToRecipients(subject, body, recipients);
    }

    @Override
    public void sendCourseContentNotification(CourseContent courseContent, List<String> recipients) {
        String subject = "[GestionEtu] Nouveau contenu de cours: " + courseContent.getTitle();
        String body = """
                Bonjour,

                Un nouveau contenu de cours a ete publie:
                Cours: %s
                Module: %s

                Description:
                %s

                Connectez-vous a GestionEtu pour consulter le contenu.
                """.formatted(
                courseContent.getTitle(),
                courseContent.getModule() != null ? courseContent.getModule().getNom() : "-",
                courseContent.getDescription() != null ? courseContent.getDescription() : "-"
        );

        sendToRecipients(subject, body, recipients);
    }

    @Override
    public void sendAssignmentNotification(Assignment assignment, List<String> recipients) {
        String moduleName = assignment.getModule() != null ? assignment.getModule().getNom() : "General";
        String due = assignment.getDueDate() != null
                ? assignment.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "-";
        String target = assignment.getTargetClasse() != null
                ? "Classe: " + assignment.getTargetClasse().getNom()
                : (assignment.getTargetFiliere() != null ? "Filiere: " + assignment.getTargetFiliere().getNom() : "-");

        String subject = "[GestionEtu] Nouveau devoir: " + assignment.getTitle();
        String body = """
                Bonjour,

                Un nouveau devoir a ete publie:
                Titre: %s
                Module: %s
                Cible: %s
                Date limite: %s

                Instructions:
                %s

                Connectez-vous a GestionEtu pour consulter et soumettre votre travail.
                """.formatted(
                assignment.getTitle(),
                moduleName,
                target,
                due,
                assignment.getDescription() != null ? assignment.getDescription() : "-"
        );

        sendToRecipients(subject, body, recipients);
    }

    private void sendToRecipients(String subject, String body, List<String> recipients) {
        if (!mailEnabled) {
            log.info("Email disabled (app.mail.enabled=false). Subject: {}", subject);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("JavaMailSender indisponible. Email ignored for subject: {}", subject);
            return;
        }

        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        for (String recipient : recipients) {
            if (!StringUtils.hasText(recipient)) {
                continue;
            }

            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);
                sender.send(message);
            } catch (Exception ex) {
                log.warn("Email send failed for {}: {}", recipient, ex.getMessage());
            }
        }
    }
}
