package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.dto.UserDto;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.TeacherRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.repository.AnnouncementRepository;
import com.pfe.gestionetudiant.repository.CourseContentRepository;
import com.pfe.gestionetudiant.repository.EmploiDuTempsRepository;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.FiliereService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Controller ADMIN - Gestion complète du système
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final FiliereService filiereService;
    private final ClasseService classeService;
    private final ModuleService moduleService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final CourseContentRepository courseContentRepository;
    private final AnnouncementRepository announcementRepository;

    // ═══════════════════════════════════════════════
    // DASHBOARD
    // ═══════════════════════════════════════════════

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalEtudiants",   userRepository.countByRole(Role.STUDENT));
        model.addAttribute("totalEnseignants", userRepository.countByRole(Role.TEACHER));
        model.addAttribute("totalFilieres",    filiereService.findAll().size());
        model.addAttribute("totalClasses",     classeService.findAll().size());
        model.addAttribute("totalModules",     moduleService.findAll().size());
        model.addAttribute("totalSeances",     emploiDuTempsRepository.count());
        model.addAttribute("totalCourses",     courseContentRepository.count());
        model.addAttribute("totalAnnouncements", announcementRepository.count());
        model.addAttribute("recentUsers",      userService.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5).toList());
        return "admin/dashboard";
    }

    // ═══════════════════════════════════════════════
    // GESTION UTILISATEURS
    // ═══════════════════════════════════════════════

    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users/list";
    }

    @GetMapping("/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("userDto", new UserDto());
        model.addAttribute("roles", Role.values());
        model.addAttribute("classes", classeService.findAll());
        model.addAttribute("filieres", filiereService.findAll());
        return "admin/users/form";
    }

    @PostMapping("/users/new")
    public String createUser(@Valid @ModelAttribute("userDto") UserDto dto,
                             BindingResult result,
                             RedirectAttributes flash,
                             Model model) {
        validatePassword(dto, result, true);

        if (result.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("classes", classeService.findAll());
            model.addAttribute("filieres", filiereService.findAll());
            return "admin/users/form";
        }

        try {
            userService.createUser(dto);
            flash.addFlashAttribute("successMessage", "Utilisateur créé avec succès !");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRole(user.getRole());
        dto.setEnabled(user.isEnabled());
        if (user.getRole() == Role.STUDENT) {
            studentRepository.findByUserId(user.getId()).ifPresent(student -> {
                dto.setMatricule(student.getMatricule());
                if (student.getClasse() != null) {
                    dto.setClasseId(student.getClasse().getId());
                }
            });
        }
        if (user.getRole() == Role.TEACHER) {
            teacherRepository.findByUserId(user.getId()).ifPresent(teacher -> {
                dto.setSpecialite(teacher.getSpecialite());
                dto.setGrade(teacher.getGrade());
            });
        }
        if (user.getRole() == Role.CHEF_FILIERE) {
            filiereService.findByChefFiliereId(user.getId())
                    .ifPresent(filiere -> dto.setFiliereId(filiere.getId()));
        }

        model.addAttribute("userDto", dto);
        model.addAttribute("roles", Role.values());
        model.addAttribute("classes", classeService.findAll());
        model.addAttribute("filieres", filiereService.findAll());
        return "admin/users/form";
    }

    @PostMapping("/users/{id}/edit")
    public String updateUser(@PathVariable Long id,
                             @Valid @ModelAttribute("userDto") UserDto dto,
                             BindingResult result,
                             RedirectAttributes flash,
                             Model model) {
        validatePassword(dto, result, false);

        if (result.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("classes", classeService.findAll());
            model.addAttribute("filieres", filiereService.findAll());
            return "admin/users/form";
        }
        try {
            userService.updateUser(id, dto);
            flash.addFlashAttribute("successMessage", "Utilisateur modifié avec succès !");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes flash) {
        try {
            userService.deleteUser(id);
            flash.addFlashAttribute("successMessage", "Utilisateur supprimé avec succès !");
        } catch (Exception e) {
            flash.addFlashAttribute("errorMessage", "Impossible de supprimer cet utilisateur.");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUserStatus(@PathVariable Long id, RedirectAttributes flash) {
        userService.toggleUserStatus(id);
        flash.addFlashAttribute("successMessage", "Statut de l'utilisateur modifié.");
        return "redirect:/admin/users";
    }

    // ═══════════════════════════════════════════════
    // GESTION FILIÈRES
    // ═══════════════════════════════════════════════

    @GetMapping("/filieres")
    public String listFilieres(Model model) {
        model.addAttribute("filieres", filiereService.findAll());
        return "admin/filieres/list";
    }

    @GetMapping("/filieres/new")
    public String newFiliereForm(Model model) {
        model.addAttribute("filiere", new Filiere());
        model.addAttribute("chefsFiliere", userService.findByRole(Role.CHEF_FILIERE));
        return "admin/filieres/form";
    }

    @PostMapping("/filieres/new")
    public String createFiliere(@ModelAttribute("filiere") Filiere filiere,
                                @RequestParam(required = false) Long chefFiliereId,
                                RedirectAttributes flash) {
        if (chefFiliereId != null) {
            userService.findById(chefFiliereId).ifPresent(filiere::setChefFiliere);
        }
        filiereService.save(filiere);
        flash.addFlashAttribute("successMessage", "Filière créée avec succès !");
        return "redirect:/admin/filieres";
    }

    @GetMapping("/filieres/{id}/edit")
    public String editFiliereForm(@PathVariable Long id, Model model) {
        Filiere filiere = filiereService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Filière introuvable"));
        model.addAttribute("filiere", filiere);
        model.addAttribute("chefsFiliere", userService.findByRole(Role.CHEF_FILIERE));
        return "admin/filieres/form";
    }

    @PostMapping("/filieres/{id}/edit")
    public String updateFiliere(@PathVariable Long id,
                                @ModelAttribute("filiere") Filiere filiere,
                                @RequestParam(required = false) Long chefFiliereId,
                                RedirectAttributes flash) {
        filiere.setId(id);
        if (chefFiliereId != null) {
            userService.findById(chefFiliereId).ifPresent(filiere::setChefFiliere);
        }
        filiereService.save(filiere);
        flash.addFlashAttribute("successMessage", "Filière modifiée avec succès !");
        return "redirect:/admin/filieres";
    }

    @PostMapping("/filieres/{id}/delete")
    public String deleteFiliere(@PathVariable Long id, RedirectAttributes flash) {
        filiereService.delete(id);
        flash.addFlashAttribute("successMessage", "Filière supprimée !");
        return "redirect:/admin/filieres";
    }

    // ═══════════════════════════════════════════════
    // GESTION CLASSES
    // ═══════════════════════════════════════════════

    @GetMapping("/classes")
    public String listClasses(Model model) {
        model.addAttribute("classes", classeService.findAll());
        return "admin/classes/list";
    }

    @GetMapping("/classes/new")
    public String newClasseForm(Model model) {
        model.addAttribute("classe", new Classe());
        model.addAttribute("filieres", filiereService.findAll());
        return "admin/classes/form";
    }

    @PostMapping("/classes/new")
    public String createClasse(@ModelAttribute("classe") Classe classe,
                               @RequestParam Long filiereId,
                               RedirectAttributes flash) {
        filiereService.findById(filiereId).ifPresent(classe::setFiliere);
        classeService.save(classe);
        flash.addFlashAttribute("successMessage", "Classe créée avec succès !");
        return "redirect:/admin/classes";
    }

    @GetMapping("/classes/{id}/edit")
    public String editClasseForm(@PathVariable Long id, Model model) {
        Classe classe = classeService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));
        model.addAttribute("classe", classe);
        model.addAttribute("filieres", filiereService.findAll());
        return "admin/classes/form";
    }

    @PostMapping("/classes/{id}/edit")
    public String updateClasse(@PathVariable Long id,
                               @ModelAttribute("classe") Classe classe,
                               @RequestParam Long filiereId,
                               RedirectAttributes flash) {
        classe.setId(id);
        filiereService.findById(filiereId).ifPresent(classe::setFiliere);
        classeService.save(classe);
        flash.addFlashAttribute("successMessage", "Classe modifiée !");
        return "redirect:/admin/classes";
    }

    @PostMapping("/classes/{id}/delete")
    public String deleteClasse(@PathVariable Long id, RedirectAttributes flash) {
        classeService.delete(id);
        flash.addFlashAttribute("successMessage", "Classe supprimée !");
        return "redirect:/admin/classes";
    }

    // ═══════════════════════════════════════════════
    // GESTION MODULES
    // ═══════════════════════════════════════════════

    @GetMapping("/modules")
    public String listModules(Model model) {
        model.addAttribute("modules", moduleService.findAll());
        return "admin/modules/list";
    }

    @GetMapping("/modules/new")
    public String newModuleForm(Model model) {
        model.addAttribute("module", new Module());
        model.addAttribute("filieres", filiereService.findAll());
        model.addAttribute("teachers", userService.findByRole(Role.TEACHER));
        model.addAttribute("semestres", List.of("S1", "S2"));
        return "admin/modules/form";
    }

    @PostMapping("/modules/new")
    public String createModule(@ModelAttribute("module") Module module,
                               @RequestParam Long filiereId,
                               @RequestParam(required = false) Long teacherId,
                               RedirectAttributes flash) {
        filiereService.findById(filiereId).ifPresent(module::setFiliere);
        if (teacherId != null) {
            userService.findById(teacherId).ifPresent(module::setTeacher);
        }
        moduleService.save(module);
        flash.addFlashAttribute("successMessage", "Module créé avec succès !");
        return "redirect:/admin/modules";
    }

    @GetMapping("/modules/{id}/edit")
    public String editModuleForm(@PathVariable Long id, Model model) {
        Module module = moduleService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable"));
        model.addAttribute("module", module);
        model.addAttribute("filieres", filiereService.findAll());
        model.addAttribute("teachers", userService.findByRole(Role.TEACHER));
        model.addAttribute("semestres", List.of("S1", "S2"));
        return "admin/modules/form";
    }

    @PostMapping("/modules/{id}/edit")
    public String updateModule(@PathVariable Long id,
                               @ModelAttribute("module") Module module,
                               @RequestParam Long filiereId,
                               @RequestParam(required = false) Long teacherId,
                               RedirectAttributes flash) {
        module.setId(id);
        filiereService.findById(filiereId).ifPresent(module::setFiliere);
        if (teacherId != null) {
            userService.findById(teacherId).ifPresent(module::setTeacher);
        }
        moduleService.save(module);
        flash.addFlashAttribute("successMessage", "Module modifié !");
        return "redirect:/admin/modules";
    }

    @PostMapping("/modules/{id}/delete")
    public String deleteModule(@PathVariable Long id, RedirectAttributes flash) {
        moduleService.delete(id);
        flash.addFlashAttribute("successMessage", "Module supprimé !");
        return "redirect:/admin/modules";
    }

    @PostMapping("/modules/{moduleId}/affecter")
    public String affecterEnseignant(@PathVariable Long moduleId,
                                     @RequestParam Long teacherId,
                                     RedirectAttributes flash) {
        moduleService.affecterEnseignant(moduleId, teacherId);
        flash.addFlashAttribute("successMessage", "Enseignant affecté au module !");
        return "redirect:/admin/modules";
    }

    private void validatePassword(UserDto dto, BindingResult result, boolean creation) {
        String password = dto.getPassword();
        boolean passwordBlank = (password == null || password.isBlank());

        if (creation && passwordBlank) {
            result.rejectValue("password", "password.required", "Le mot de passe est obligatoire.");
            return;
        }

        if (!passwordBlank && password.length() < 6) {
            result.rejectValue("password", "password.size",
                    "Le mot de passe doit avoir au moins 6 caractères.");
        }

        if (!passwordBlank) {
            String confirmPassword = dto.getConfirmPassword();
            if (confirmPassword == null || !password.equals(confirmPassword)) {
                result.rejectValue("confirmPassword", "password.mismatch",
                        "Les mots de passe ne correspondent pas.");
            }
        }
    }
}
