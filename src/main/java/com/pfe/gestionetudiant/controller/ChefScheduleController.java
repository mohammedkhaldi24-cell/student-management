package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.FiliereService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/chef/emploi-du-temps")
@PreAuthorize("hasRole('CHEF_FILIERE')")
@RequiredArgsConstructor
public class ChefScheduleController {

    private final EmploiDuTempsService emploiDuTempsService;
    private final UserService userService;
    private final FiliereService filiereService;
    private final ClasseService classeService;
    private final ModuleService moduleService;

    @GetMapping
    public String list(Model model) {
        Filiere filiere = getManagedFiliere();
        model.addAttribute("filiere", filiere);
        model.addAttribute("emplois", emploiDuTempsService.findByFiliereId(filiere.getId()));
        return "chef/emploi-du-temps/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        Filiere filiere = getManagedFiliere();
        model.addAttribute("filiere", filiere);
        model.addAttribute("emploi", new EmploiDuTemps());
        loadReferences(model, filiere);
        return "chef/emploi-du-temps/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("emploi") EmploiDuTemps emploi,
                         @RequestParam Long moduleId,
                         @RequestParam Long classeId,
                         @RequestParam(required = false) Long teacherId,
                         RedirectAttributes flash) {
        Filiere filiere = getManagedFiliere();
        enrichEntity(emploi, moduleId, classeId, teacherId, filiere);
        emploiDuTempsService.save(emploi);
        flash.addFlashAttribute("successMessage", "Seance ajoutee avec succes.");
        return "redirect:/chef/emploi-du-temps";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Filiere filiere = getManagedFiliere();
        EmploiDuTemps emploi = emploiDuTempsService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable."));
        if (emploi.getFiliere() == null || !filiere.getId().equals(emploi.getFiliere().getId())) {
            throw new IllegalArgumentException("Seance hors filiere.");
        }

        model.addAttribute("filiere", filiere);
        model.addAttribute("emploi", emploi);
        loadReferences(model, filiere);
        return "chef/emploi-du-temps/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("emploi") EmploiDuTemps emploi,
                         @RequestParam Long moduleId,
                         @RequestParam Long classeId,
                         @RequestParam(required = false) Long teacherId,
                         RedirectAttributes flash) {
        Filiere filiere = getManagedFiliere();
        emploi.setId(id);
        enrichEntity(emploi, moduleId, classeId, teacherId, filiere);
        emploiDuTempsService.save(emploi);
        flash.addFlashAttribute("successMessage", "Seance modifiee.");
        return "redirect:/chef/emploi-du-temps";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        Filiere filiere = getManagedFiliere();
        EmploiDuTemps emploi = emploiDuTempsService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable."));
        if (emploi.getFiliere() == null || !filiere.getId().equals(emploi.getFiliere().getId())) {
            throw new IllegalArgumentException("Seance hors filiere.");
        }
        emploiDuTempsService.delete(id);
        flash.addFlashAttribute("successMessage", "Seance supprimee.");
        return "redirect:/chef/emploi-du-temps";
    }

    private Filiere getManagedFiliere() {
        User currentUser = userService.getCurrentUser();
        return filiereService.findByChefFiliereId(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucune filiere assignee."));
    }

    private void enrichEntity(EmploiDuTemps emploi,
                              Long moduleId,
                              Long classeId,
                              Long teacherId,
                              Filiere filiere) {
        Classe classe = classeService.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));
        if (classe.getFiliere() == null || !filiere.getId().equals(classe.getFiliere().getId())) {
            throw new IllegalArgumentException("Classe hors filiere.");
        }

        emploi.setFiliere(filiere);
        emploi.setClasse(classe);
        emploi.setModule(moduleService.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable.")));
        if (teacherId != null) {
            User teacher = userService.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
            if (teacher.getRole() != Role.TEACHER) {
                throw new IllegalArgumentException("Utilisateur non enseignant.");
            }
            emploi.setTeacher(teacher);
        } else {
            emploi.setTeacher(null);
        }
        emploi.setValide(true);
    }

    private void loadReferences(Model model, Filiere filiere) {
        List<Classe> classes = classeService.findByFiliereId(filiere.getId());
        model.addAttribute("classes", classes);
        model.addAttribute("modules", moduleService.findByFiliereId(filiere.getId()));
        model.addAttribute("teachers", userService.findByRole(Role.TEACHER));
        model.addAttribute("jours", List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI"));
    }
}

