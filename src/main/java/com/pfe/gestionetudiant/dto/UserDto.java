package com.pfe.gestionetudiant.dto;

import com.pfe.gestionetudiant.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la crÃ©ation et modification d'un utilisateur
 */
@Data
@NoArgsConstructor
public class UserDto {

    private Long id;

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 50)
    private String username;

    private String password;

    private String confirmPassword;

    @Email(message = "Format email invalide")
    private String email;

    @NotBlank(message = "Le prÃ©nom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotNull(message = "Le rÃ´le est obligatoire")
    private Role role;

    private boolean enabled = true;

    // Champs spÃ©cifiques selon le rÃ´le
    private String matricule;      // STUDENT
    private Long classeId;         // STUDENT
    private String specialite;     // TEACHER
    private String grade;          // TEACHER
    private Long filiereId;        // CHEF_FILIERE

    public boolean isPasswordMatching() {
        if (password == null) return true; // pas de changement de mdp
        return password.equals(confirmPassword);
    }
}
