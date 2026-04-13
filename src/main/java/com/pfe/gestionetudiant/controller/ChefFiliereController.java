package com.pfe.gestionetudiant.controller;

import com.pfe.gestionetudiant.dto.BulletinDto;
import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.service.AnnouncementService;
import com.pfe.gestionetudiant.service.AbsenceService;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.CourseContentService;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import com.pfe.gestionetudiant.service.FiliereService;
import com.pfe.gestionetudiant.service.NoteService;
import com.pfe.gestionetudiant.service.PdfService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller CHEF DE FILIERE - statistiques, notes, absences, rapports.
 */
@Controller
@RequestMapping("/chef")
@PreAuthorize("hasRole('CHEF_FILIERE')")
@RequiredArgsConstructor
public class ChefFiliereController {

    private static final String ANNEE_ACADEMIQUE = "2024-2025";

    private final UserService userService;
    private final FiliereService filiereService;
    private final ClasseService classeService;
    private final NoteService noteService;
    private final AbsenceService absenceService;
    private final PdfService pdfService;
    private final StudentRepository studentRepository;
    private final EmploiDuTempsService emploiDuTempsService;
    private final CourseContentService courseContentService;
    private final AnnouncementService announcementService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User currentUser = userService.getCurrentUser();

        Filiere filiere = filiereService.findByChefFiliereId(currentUser.getId()).orElse(null);
        if (filiere == null) {
            model.addAttribute("warningMessage", "Aucune filiere ne vous est assignee.");
            return "chef/dashboard";
        }

        List<Classe> classes = classeService.findByFiliereId(filiere.getId());
        List<Student> tousEtudiants = studentRepository.findByFiliereId(filiere.getId());

        Map<String, Double> moyennesParClasse = noteService.getMoyennesParClasse(filiere.getId(), ANNEE_ACADEMIQUE);

        Map<String, Integer> absencesParClasse = new LinkedHashMap<>();
        for (Classe classe : classes) {
            List<Absence> absences = absenceService.findByClasseId(classe.getId());
            int totalHeures = absences.stream().mapToInt(Absence::getNombreHeures).sum();
            absencesParClasse.put(classe.getNom(), totalHeures);
        }

        List<String> labels = new ArrayList<>(moyennesParClasse.keySet());
        List<Double> moyennesData = new ArrayList<>(moyennesParClasse.values());
        List<Integer> absencesData = labels.stream()
                .map(l -> absencesParClasse.getOrDefault(l, 0))
                .toList();

        long totalEtudiants = tousEtudiants.size();
        long totalAbsencesHeures = tousEtudiants.stream()
                .mapToInt(s -> absenceService.getTotalHeuresByStudent(s.getId()))
                .sum();

        model.addAttribute("filiere", filiere);
        model.addAttribute("classes", classes);
        model.addAttribute("totalEtudiants", totalEtudiants);
        model.addAttribute("totalClasses", classes.size());
        model.addAttribute("totalAbsencesHeures", totalAbsencesHeures);
        model.addAttribute("moyennesParClasse", moyennesParClasse);
        model.addAttribute("absencesParClasse", absencesParClasse);
        model.addAttribute("chartLabels", labels);
        model.addAttribute("chartMoyennes", moyennesData);
        model.addAttribute("chartAbsences", absencesData);
        model.addAttribute("anneeAcademique", ANNEE_ACADEMIQUE);
        model.addAttribute("totalSeances", emploiDuTempsService.findByFiliereId(filiere.getId()).size());
        model.addAttribute("totalCourses", courseContentService.findByFiliereId(filiere.getId()).size());
        model.addAttribute("totalAnnouncements", announcementService.findByFiliereId(filiere.getId()).size());

        return "chef/dashboard";
    }

    @GetMapping("/etudiants")
    public String listeEtudiants(@RequestParam(required = false) Long classeId, Model model) {
        User currentUser = userService.getCurrentUser();
        Filiere filiere = filiereService.findByChefFiliereId(currentUser.getId()).orElse(null);

        if (filiere != null) {
            List<Student> etudiants;
            if (classeId != null) {
                Classe classe = requireClasseInFiliere(classeId, filiere.getId());
                etudiants = studentRepository.findByClasseId(classe.getId());
                model.addAttribute("classeSelectionnee", classe.getId());
            } else {
                etudiants = studentRepository.findByFiliereId(filiere.getId());
            }

            model.addAttribute("etudiants", etudiants);
            model.addAttribute("classes", classeService.findByFiliereId(filiere.getId()));
            model.addAttribute("filiere", filiere);
        }
        return "chef/etudiants";
    }

    @GetMapping("/notes")
    public String voirNotes(@RequestParam(required = false) Long classeId, Model model) {
        User currentUser = userService.getCurrentUser();
        Filiere filiere = filiereService.findByChefFiliereId(currentUser.getId()).orElse(null);
        List<Classe> classes = filiere != null ? classeService.findByFiliereId(filiere.getId()) : List.of();

        model.addAttribute("classes", classes);
        model.addAttribute("filiere", filiere);

        if (filiere != null && classeId != null) {
            Classe classe = requireClasseInFiliere(classeId, filiere.getId());
            List<Student> etudiants = studentRepository.findByClasseId(classe.getId());

            Map<Student, Double> moyennesEtudiants = new LinkedHashMap<>();
            for (Student s : etudiants) {
                double moy = (noteService.calculerMoyenneEtudiant(s.getId(), "S1", ANNEE_ACADEMIQUE)
                        + noteService.calculerMoyenneEtudiant(s.getId(), "S2", ANNEE_ACADEMIQUE)) / 2;
                moyennesEtudiants.put(s, Math.round(moy * 100.0) / 100.0);
            }

            Map<Student, Double> sorted = moyennesEtudiants.entrySet().stream()
                    .sorted(Map.Entry.<Student, Double>comparingByValue(Comparator.reverseOrder()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));

            model.addAttribute("moyennesEtudiants", sorted);
            model.addAttribute("classeId", classeId);
            model.addAttribute("classe", classe);
        }

        return "chef/notes";
    }

    @GetMapping("/absences")
    public String absences(@RequestParam(required = false) Long classeId, Model model) {
        User currentUser = userService.getCurrentUser();
        Filiere filiere = filiereService.findByChefFiliereId(currentUser.getId()).orElse(null);
        if (filiere == null) {
            model.addAttribute("warningMessage", "Aucune filiere ne vous est assignee.");
            return "chef/absences";
        }

        List<Classe> classes = classeService.findByFiliereId(filiere.getId());
        List<Absence> absences;

        if (classeId != null) {
            Classe classe = requireClasseInFiliere(classeId, filiere.getId());
            absences = absenceService.findByClasseId(classe.getId());
            model.addAttribute("classeSelectionnee", classe.getId());
            model.addAttribute("classe", classe);
        } else {
            absences = classes.stream()
                    .flatMap(c -> absenceService.findByClasseId(c.getId()).stream())
                    .sorted(Comparator.comparing(Absence::getDateAbsence).reversed())
                    .toList();
        }

        model.addAttribute("filiere", filiere);
        model.addAttribute("classes", classes);
        model.addAttribute("absences", absences);

        return "chef/absences";
    }

    @GetMapping("/rapport")
    public String pageRapport(@RequestParam(required = false) Long classeId,
                              @RequestParam(defaultValue = "S1") String semestre,
                              Model model) {
        User currentUser = userService.getCurrentUser();
        Filiere filiere = filiereService.findByChefFiliereId(currentUser.getId()).orElse(null);

        if (filiere != null) {
            List<Classe> classes = classeService.findByFiliereId(filiere.getId());
            model.addAttribute("classes", classes);
            model.addAttribute("filiere", filiere);
            model.addAttribute("semestre", semestre);

            if (classeId != null) {
                Classe classe = requireClasseInFiliere(classeId, filiere.getId());
                model.addAttribute("classeSelectionnee", classe.getId());
                model.addAttribute("etudiants", studentRepository.findByClasseId(classe.getId()));
            } else {
                model.addAttribute("etudiants", List.of());
            }
        }

        return "chef/rapport";
    }

    @GetMapping("/rapport/etudiant/{studentId}/{semestre}")
    public ResponseEntity<byte[]> genererRapportPdf(@PathVariable Long studentId,
                                                     @PathVariable String semestre) {
        if (!"S1".equalsIgnoreCase(semestre) && !"S2".equalsIgnoreCase(semestre)) {
            throw new IllegalArgumentException("Semestre invalide");
        }

        User currentUser = userService.getCurrentUser();
        Filiere filiereChef = filiereService.findByChefFiliereId(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Aucune filiere assignee"));

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Etudiant introuvable"));

        if (student.getClasse() == null
                || student.getClasse().getFiliere() == null
                || !filiereChef.getId().equals(student.getClasse().getFiliere().getId())) {
            throw new IllegalArgumentException("Etudiant hors filiere");
        }

        BulletinDto bulletin = noteService.genererBulletin(studentId, semestre.toUpperCase(), ANNEE_ACADEMIQUE);
        byte[] pdf = pdfService.genererBulletinPdf(bulletin);

        String filename = "bulletin_" + student.getMatricule() + "_" + semestre.toUpperCase() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Classe requireClasseInFiliere(Long classeId, Long filiereId) {
        Classe classe = classeService.findById(classeId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable"));

        if (classe.getFiliere() == null || !filiereId.equals(classe.getFiliere().getId())) {
            throw new IllegalArgumentException("Classe non autorisee pour votre filiere.");
        }
        return classe;
    }
}
