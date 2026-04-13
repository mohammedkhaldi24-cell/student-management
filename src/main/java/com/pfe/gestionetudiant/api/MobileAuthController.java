package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
@RequiredArgsConstructor
public class MobileAuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final MobileApiMapper mapper;
    private final MobileAccessService accessService;

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/login")
    public ResponseEntity<MobileDtos.AuthResponse> login(@RequestBody MobileDtos.LoginRequest request,
                                                         HttpServletRequest httpRequest,
                                                         HttpServletResponse httpResponse) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new IllegalArgumentException("Username et password sont obligatoires.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            httpRequest.getSession(true);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            User user = userService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable."));

            return ResponseEntity.ok(new MobileDtos.AuthResponse(
                    true,
                    mapper.toUserSummary(user, accessService.redirectPath(user.getRole())),
                    "Connexion reussie"
            ));
        } catch (DisabledException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MobileDtos.AuthResponse(false, null, "Compte desactive."));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MobileDtos.AuthResponse(false, null, "Identifiants invalides."));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MobileDtos.AuthResponse(false, null, "Echec d'authentification."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<MobileDtos.AuthResponse> me() {
        User user = accessService.currentUser();
        return ResponseEntity.ok(new MobileDtos.AuthResponse(
                true,
                mapper.toUserSummary(user, accessService.redirectPath(user.getRole())),
                "Session active"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<MobileDtos.ApiMessage> logout(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.ok(new MobileDtos.ApiMessage("Deconnexion effectuee."));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
