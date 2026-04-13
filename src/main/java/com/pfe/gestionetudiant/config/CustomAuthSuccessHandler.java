package com.pfe.gestionetudiant.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Collection;

/**
 * Redirige chaque utilisateur vers son tableau de bord selon son rôle après connexion
 */
public class CustomAuthSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        String redirectUrl = "/login";

        for (GrantedAuthority authority : authorities) {
            switch (authority.getAuthority()) {
                case "ROLE_ADMIN"        -> redirectUrl = "/admin/dashboard";
                case "ROLE_CHEF_FILIERE" -> redirectUrl = "/chef/dashboard";
                case "ROLE_TEACHER"      -> redirectUrl = "/teacher/dashboard";
                case "ROLE_STUDENT"      -> redirectUrl = "/student/dashboard";
            }
        }

        response.sendRedirect(request.getContextPath() + redirectUrl);
    }
}
