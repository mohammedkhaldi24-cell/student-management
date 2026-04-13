package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.FiliereService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/chef")
@PreAuthorize("hasRole('CHEF_FILIERE')")
@RequiredArgsConstructor
public class ChefContentController {

    private final UserService userService;
    private final FiliereService filiereService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;

    @GetMapping("/courses")
    public String courses(Model model) {
        Filiere filiere = getManagedFiliere();
        model.addAttribute("filiere", filiere);
        model.addAttribute("courses", courseContentService.findByFiliereId(filiere.getId()));
        return "chef/courses";
    }

    @GetMapping("/announcements")
    public String announcements(Model model) {
        Filiere filiere = getManagedFiliere();
        model.addAttribute("filiere", filiere);
        model.addAttribute("announcements", announcementService.findByFiliereId(filiere.getId()));
        return "chef/announcements";
    }

    private Filiere getManagedFiliere() {
        User currentUser = userService.getCurrentUser();
        return filiereService.findByChefFiliereId(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucune filiere assignee."));
    }
}

