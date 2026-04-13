package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.CourseContent;

import java.util.List;

public interface EmailService {

    void sendAnnouncementNotification(Announcement announcement, List<String> recipients);

    void sendCourseContentNotification(CourseContent courseContent, List<String> recipients);

    void sendAssignmentNotification(Assignment assignment, List<String> recipients);
}
