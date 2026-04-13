package com.pfe.gestionetudiant.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Collection;

@Controller
public class AuthController {

    @GetMapping("/")
    public RedirectView home(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return new RedirectView("/login");
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String redirectUrl = "/login";
        for (GrantedAuthority authority : authorities) {
            switch (authority.getAuthority()) {
                case "ROLE_ADMIN" -> redirectUrl = "/admin/dashboard";
                case "ROLE_CHEF_FILIERE" -> redirectUrl = "/chef/dashboard";
                case "ROLE_TEACHER" -> redirectUrl = "/teacher/dashboard";
                case "ROLE_STUDENT" -> redirectUrl = "/student/dashboard";
                default -> {
                }
            }
        }
        return new RedirectView(redirectUrl);
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            @RequestParam(value = "expired", required = false) String expired,
                            Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Nom d'utilisateur ou mot de passe incorrect.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Vous avez été déconnecté avec succès.");
        }
        if (expired != null) {
            model.addAttribute("errorMessage", "Votre session a expiré. Veuillez vous reconnecter.");
        }
        return "auth/login";
    }

    @GetMapping("/error/403")
    public String accessDenied() {
        return "error/403";
    }
}
