package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Role;
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

@Controller
@RequestMapping("/admin/emploi-du-temps")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final EmploiDuTempsService emploiDuTempsService;
    private final ModuleService moduleService;
    private final UserService userService;
    private final ClasseService classeService;
    private final FiliereService filiereService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("emplois", emploiDuTempsService.findAll());
        return "admin/emploi-du-temps/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("emploi", new EmploiDuTemps());
        loadReferences(model);
        return "admin/emploi-du-temps/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("emploi") EmploiDuTemps emploi,
                         @RequestParam Long moduleId,
                         @RequestParam Long classeId,
                         @RequestParam Long filiereId,
                         @RequestParam(required = false) Long teacherId,
                         RedirectAttributes flash) {
        enrichEntity(emploi, moduleId, classeId, filiereId, teacherId);
        emploiDuTempsService.save(emploi);
        flash.addFlashAttribute("successMessage", "Seance ajoutee avec succes.");
        return "redirect:/admin/emploi-du-temps";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        EmploiDuTemps emploi = emploiDuTempsService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable."));
        model.addAttribute("emploi", emploi);
        loadReferences(model);
        return "admin/emploi-du-temps/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("emploi") EmploiDuTemps emploi,
                         @RequestParam Long moduleId,
                         @RequestParam Long classeId,
                         @RequestParam Long filiereId,
                         @RequestParam(required = false) Long teacherId,
                         RedirectAttributes flash) {
        emploi.setId(id);
        enrichEntity(emploi, moduleId, classeId, filiereId, teacherId);
        emploiDuTempsService.save(emploi);
        flash.addFlashAttribute("successMessage", "Seance modifiee avec succes.");
        return "redirect:/admin/emploi-du-temps";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        emploiDuTempsService.delete(id);
        flash.addFlashAttribute("successMessage", "Seance supprimee.");
        return "redirect:/admin/emploi-du-temps";
    }

    @PostMapping("/{id}/valider")
    public String toggleValidation(@PathVariable Long id, RedirectAttributes flash) {
        EmploiDuTemps emploi = emploiDuTempsService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable."));
        emploi.setValide(!emploi.isValide());
        emploiDuTempsService.save(emploi);
        flash.addFlashAttribute("successMessage",
                emploi.isValide() ? "Seance validee." : "Validation retiree.");
        return "redirect:/admin/emploi-du-temps";
    }

    private void enrichEntity(EmploiDuTemps emploi,
                              Long moduleId,
                              Long classeId,
                              Long filiereId,
                              Long teacherId) {
        emploi.setModule(moduleService.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable.")));
        emploi.setClasse(classeService.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable.")));
        emploi.setFiliere(filiereService.findById(filiereId)
                .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable.")));
        if (teacherId != null) {
            emploi.setTeacher(userService.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable.")));
        } else {
            emploi.setTeacher(null);
        }
    }

    private void loadReferences(Model model) {
        model.addAttribute("modules", moduleService.findAll());
        model.addAttribute("teachers", userService.findByRole(Role.TEACHER));
        model.addAttribute("classes", classeService.findAll());
        model.addAttribute("filieres", filiereService.findAll());
        model.addAttribute("jours", java.util.List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI"));
    }
}

