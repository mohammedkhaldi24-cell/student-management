package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.dto.UserDto;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.AbsenceRepository;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.repository.ModuleRepository;
import com.pfe.gestionetudiant.repository.NoteRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.ClasseService;
import com.pfe.gestionetudiant.service.FiliereService;
import com.pfe.gestionetudiant.service.ModuleService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MobileAdminController {

    private final MobileAccessService accessService;
    private final MobileApiMapper mapper;

    private final UserService userService;
    private final FiliereService filiereService;
    private final ClasseService classeService;
    private final ModuleService moduleService;

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final FiliereRepository filiereRepository;
    private final ClasseRepository classeRepository;
    private final ModuleRepository moduleRepository;
    private final NoteRepository noteRepository;
    private final AbsenceRepository absenceRepository;

    @GetMapping("/dashboard")
    public MobileDtos.AdminDashboard dashboard() {
        accessService.currentUser();
        return new MobileDtos.AdminDashboard(
                userRepository.count(),
                userRepository.countByRole(Role.STUDENT),
                userRepository.countByRole(Role.TEACHER),
                userRepository.countByRole(Role.CHEF_FILIERE),
                filiereRepository.count(),
                classeRepository.count(),
                moduleRepository.count(),
                noteRepository.count(),
                absenceRepository.count()
        );
    }

    @GetMapping("/users")
    public List<MobileDtos.UserSummary> users(@RequestParam(required = false) String role,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Boolean enabled) {
        accessService.currentUser();

        Role parsedRole = parseRole(role, null, false);
        List<User> users = parsedRole != null ? userRepository.findByRole(parsedRole) : userRepository.findAll();

        String normalizedQuery = normalize(q);
        return users.stream()
                .filter(u -> enabled == null || u.isEnabled() == enabled)
                .filter(u -> !StringUtils.hasText(normalizedQuery) || matchesUser(u, normalizedQuery))
                .sorted((a, b) -> {
                    String aKey = (safe(a.getLastName()) + " " + safe(a.getFirstName())).trim().toLowerCase(Locale.ROOT);
                    String bKey = (safe(b.getLastName()) + " " + safe(b.getFirstName())).trim().toLowerCase(Locale.ROOT);
                    return aKey.compareTo(bKey);
                })
                .map(u -> mapper.toUserSummary(u, accessService.redirectPath(u.getRole())))
                .toList();
    }

    @PostMapping("/users")
    public MobileDtos.UserSummary createUser(@RequestBody MobileDtos.AdminUserUpsertRequest request) {
        accessService.currentUser();

        UserDto dto = toUserDto(request, null);
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire pour la creation.");
        }

        User created = userService.createUser(dto);
        return mapper.toUserSummary(created, accessService.redirectPath(created.getRole()));
    }

    @PutMapping("/users/{id}")
    public MobileDtos.UserSummary updateUser(@PathVariable Long id,
                                             @RequestBody MobileDtos.AdminUserUpsertRequest request) {
        accessService.currentUser();

        User existing = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));

        UserDto dto = toUserDto(request, existing);
        User updated = userService.updateUser(id, dto);
        return mapper.toUserSummary(updated, accessService.redirectPath(updated.getRole()));
    }

    @PostMapping("/users/{id}/toggle")
    public MobileDtos.UserSummary toggleUser(@PathVariable Long id) {
        accessService.currentUser();

        userService.toggleUserStatus(id);
        User updated = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable."));
        return mapper.toUserSummary(updated, accessService.redirectPath(updated.getRole()));
    }

    @DeleteMapping("/users/{id}")
    public MobileDtos.ApiMessage deleteUser(@PathVariable Long id) {
        accessService.currentUser();
        try {
            userService.deleteUser(id);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Impossible de supprimer cet utilisateur.");
        }
        return new MobileDtos.ApiMessage("Utilisateur supprime avec succes.");
    }

    @GetMapping("/filieres")
    public List<Map<String, Object>> filieres(@RequestParam(required = false) String q) {
        accessService.currentUser();
        String normalizedQuery = normalize(q);

        return filiereRepository.findAll().stream()
                .filter(f -> !StringUtils.hasText(normalizedQuery) || matchesFiliere(f, normalizedQuery))
                .sorted((a, b) -> safe(a.getNom()).compareToIgnoreCase(safe(b.getNom())))
                .map(this::toFiliereRow)
                .toList();
    }

    @PostMapping("/filieres")
    public Map<String, Object> createFiliere(@RequestBody MobileDtos.AdminFiliereUpsertRequest request) {
        accessService.currentUser();

        Filiere filiere = new Filiere();
        applyFiliereRequest(filiere, request, true);
        return persistFiliere(filiere);
    }

    @PutMapping("/filieres/{id}")
    public Map<String, Object> updateFiliere(@PathVariable Long id,
                                             @RequestBody MobileDtos.AdminFiliereUpsertRequest request) {
        accessService.currentUser();

        Filiere filiere = filiereRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
        applyFiliereRequest(filiere, request, false);
        return persistFiliere(filiere);
    }

    @DeleteMapping("/filieres/{id}")
    public MobileDtos.ApiMessage deleteFiliere(@PathVariable Long id) {
        accessService.currentUser();
        try {
            filiereService.delete(id);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Impossible de supprimer cette filiere.");
        }
        return new MobileDtos.ApiMessage("Filiere supprimee avec succes.");
    }

    @GetMapping("/classes")
    public List<Map<String, Object>> classes(@RequestParam(required = false) Long filiereId,
                                             @RequestParam(required = false) String q) {
        accessService.currentUser();

        var classes = filiereId != null ? classeRepository.findByFiliereId(filiereId) : classeRepository.findAll();
        String normalizedQuery = normalize(q);

        return classes.stream()
                .filter(c -> !StringUtils.hasText(normalizedQuery) || matchesClasse(c, normalizedQuery))
                .sorted((a, b) -> safe(a.getNom()).compareToIgnoreCase(safe(b.getNom())))
                .map(this::toClasseRow)
                .toList();
    }

    @PostMapping("/classes")
    public Map<String, Object> createClasse(@RequestBody MobileDtos.AdminClasseUpsertRequest request) {
        accessService.currentUser();

        Classe classe = new Classe();
        applyClasseRequest(classe, request, true);
        return persistClasse(classe);
    }

    @PutMapping("/classes/{id}")
    public Map<String, Object> updateClasse(@PathVariable Long id,
                                            @RequestBody MobileDtos.AdminClasseUpsertRequest request) {
        accessService.currentUser();

        Classe classe = classeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable."));
        applyClasseRequest(classe, request, false);
        return persistClasse(classe);
    }

    @DeleteMapping("/classes/{id}")
    public MobileDtos.ApiMessage deleteClasse(@PathVariable Long id) {
        accessService.currentUser();
        try {
            classeService.delete(id);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Impossible de supprimer cette classe.");
        }
        return new MobileDtos.ApiMessage("Classe supprimee avec succes.");
    }

    @GetMapping("/modules")
    public List<Map<String, Object>> modules(@RequestParam(required = false) Long filiereId,
                                             @RequestParam(required = false) Long teacherId,
                                             @RequestParam(required = false) String q) {
        accessService.currentUser();

        var modules = moduleRepository.findAll();
        if (filiereId != null) {
            modules = modules.stream()
                    .filter(m -> m.getFiliere() != null && filiereId.equals(m.getFiliere().getId()))
                    .toList();
        }
        if (teacherId != null) {
            modules = modules.stream()
                    .filter(m -> m.getTeacher() != null && teacherId.equals(m.getTeacher().getId()))
                    .toList();
        }

        String normalizedQuery = normalize(q);
        return modules.stream()
                .filter(m -> !StringUtils.hasText(normalizedQuery) || matchesModule(m, normalizedQuery))
                .sorted((a, b) -> safe(a.getNom()).compareToIgnoreCase(safe(b.getNom())))
                .map(this::toModuleRow)
                .toList();
    }

    @PostMapping("/modules")
    public Map<String, Object> createModule(@RequestBody MobileDtos.AdminModuleUpsertRequest request) {
        accessService.currentUser();

        Module module = new Module();
        applyModuleRequest(module, request, true);
        return persistModule(module);
    }

    @PutMapping("/modules/{id}")
    public Map<String, Object> updateModule(@PathVariable Long id,
                                            @RequestBody MobileDtos.AdminModuleUpsertRequest request) {
        accessService.currentUser();

        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable."));
        applyModuleRequest(module, request, false);
        return persistModule(module);
    }

    @DeleteMapping("/modules/{id}")
    public MobileDtos.ApiMessage deleteModule(@PathVariable Long id) {
        accessService.currentUser();
        try {
            moduleService.delete(id);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Impossible de supprimer ce module.");
        }
        return new MobileDtos.ApiMessage("Module supprime avec succes.");
    }

    private Map<String, Object> persistFiliere(Filiere filiere) {
        try {
            Filiere saved = filiereService.save(filiere);
            return toFiliereRow(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Code filiere deja utilise.");
        }
    }

    private Map<String, Object> persistClasse(Classe classe) {
        try {
            Classe saved = classeService.save(classe);
            return toClasseRow(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Impossible d'enregistrer la classe.");
        }
    }

    private Map<String, Object> persistModule(Module module) {
        try {
            Module saved = moduleService.save(module);
            return toModuleRow(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Code module deja utilise.");
        }
    }

    private void applyFiliereRequest(Filiere filiere,
                                     MobileDtos.AdminFiliereUpsertRequest request,
                                     boolean creation) {
        if (creation || StringUtils.hasText(request.nom())) {
            filiere.setNom(requireText(request.nom(), "Le nom de la filiere est obligatoire."));
        }
        if (creation || StringUtils.hasText(request.code())) {
            filiere.setCode(requireText(request.code(), "Le code de la filiere est obligatoire."));
        }
        if (creation || request.description() != null) {
            filiere.setDescription(request.description());
        }

        if (request.chefFiliereId() != null) {
            User chef = userRepository.findById(request.chefFiliereId())
                    .orElseThrow(() -> new IllegalArgumentException("Chef de filiere introuvable."));
            if (chef.getRole() != Role.CHEF_FILIERE) {
                throw new IllegalArgumentException("L'utilisateur selectionne n'est pas un chef de filiere.");
            }
            filiere.setChefFiliere(chef);
        }
    }

    private void applyClasseRequest(Classe classe,
                                    MobileDtos.AdminClasseUpsertRequest request,
                                    boolean creation) {
        if (creation || StringUtils.hasText(request.nom())) {
            classe.setNom(requireText(request.nom(), "Le nom de la classe est obligatoire."));
        }
        if (creation || StringUtils.hasText(request.niveau())) {
            classe.setNiveau(requireText(request.niveau(), "Le niveau est obligatoire."));
        }
        if (creation || StringUtils.hasText(request.anneeAcademique())) {
            classe.setAnneeAcademique(requireText(request.anneeAcademique(), "L'annee academique est obligatoire."));
        }

        if (request.filiereId() != null) {
            Filiere filiere = filiereRepository.findById(request.filiereId())
                    .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
            classe.setFiliere(filiere);
        } else if (creation || classe.getFiliere() == null) {
            throw new IllegalArgumentException("La filiere est obligatoire.");
        }
    }

    private void applyModuleRequest(Module module,
                                    MobileDtos.AdminModuleUpsertRequest request,
                                    boolean creation) {
        if (creation || StringUtils.hasText(request.nom())) {
            module.setNom(requireText(request.nom(), "Le nom du module est obligatoire."));
        }
        if (creation || StringUtils.hasText(request.code())) {
            module.setCode(requireText(request.code(), "Le code du module est obligatoire."));
        }
        if (creation || request.coefficient() != null) {
            Integer coefficient = request.coefficient() != null ? request.coefficient() : 1;
            if (coefficient < 1) {
                throw new IllegalArgumentException("Le coefficient doit etre superieur ou egal a 1.");
            }
            module.setCoefficient(coefficient);
        }
        if (creation || request.volumeHoraire() != null) {
            module.setVolumeHoraire(request.volumeHoraire() != null ? request.volumeHoraire() : 30);
        }
        if (creation || StringUtils.hasText(request.semestre())) {
            module.setSemestre(requireText(request.semestre(), "Le semestre est obligatoire."));
        }

        if (request.filiereId() != null) {
            Filiere filiere = filiereRepository.findById(request.filiereId())
                    .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
            module.setFiliere(filiere);
        } else if (creation || module.getFiliere() == null) {
            throw new IllegalArgumentException("La filiere est obligatoire.");
        }

        if (request.teacherId() != null) {
            if (request.teacherId() == 0L) {
                module.setTeacher(null);
            } else {
                User teacher = userRepository.findById(request.teacherId())
                        .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
                if (teacher.getRole() != Role.TEACHER) {
                    throw new IllegalArgumentException("L'utilisateur selectionne n'est pas un enseignant.");
                }
                module.setTeacher(teacher);
            }
        }
    }

    private UserDto toUserDto(MobileDtos.AdminUserUpsertRequest request, User existing) {
        Role role = parseRole(request.role(), existing != null ? existing.getRole() : null, existing == null);

        UserDto dto = new UserDto();
        dto.setId(existing != null ? existing.getId() : null);
        dto.setUsername(requireText(
                defaultValue(request.username(), existing != null ? existing.getUsername() : null),
                "Le nom d'utilisateur est obligatoire."
        ));
        dto.setEmail(defaultValue(request.email(), existing != null ? existing.getEmail() : null));
        dto.setFirstName(requireText(
                defaultValue(request.firstName(), existing != null ? existing.getFirstName() : null),
                "Le prenom est obligatoire."
        ));
        dto.setLastName(requireText(
                defaultValue(request.lastName(), existing != null ? existing.getLastName() : null),
                "Le nom est obligatoire."
        ));
        dto.setRole(role);
        dto.setEnabled(request.enabled() != null ? request.enabled() : existing == null || existing.isEnabled());
        dto.setPassword(request.password());
        dto.setConfirmPassword(request.password());

        dto.setMatricule(request.matricule());
        dto.setClasseId(request.classeId());
        dto.setSpecialite(request.specialite());
        dto.setGrade(request.grade());
        dto.setFiliereId(request.filiereId());

        return dto;
    }

    private Map<String, Object> toFiliereRow(Filiere f) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", f.getId());
        row.put("nom", f.getNom());
        row.put("code", f.getCode());
        row.put("description", f.getDescription());
        row.put("chefFiliere", f.getChefFiliere() != null ? f.getChefFiliere().getFullName() : null);
        row.put("chefFiliereId", f.getChefFiliere() != null ? f.getChefFiliere().getId() : null);
        row.put("classesCount", classeRepository.findByFiliereId(f.getId()).size());
        row.put("studentsCount", studentRepository.countByFiliereId(f.getId()));
        return row;
    }

    private Map<String, Object> toClasseRow(Classe c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("nom", c.getNom());
        row.put("niveau", c.getNiveau());
        row.put("anneeAcademique", c.getAnneeAcademique());
        row.put("filiereId", c.getFiliere() != null ? c.getFiliere().getId() : null);
        row.put("filiereNom", c.getFiliere() != null ? c.getFiliere().getNom() : null);
        row.put("studentsCount", studentRepository.countByClasseId(c.getId()));
        return row;
    }

    private Map<String, Object> toModuleRow(Module m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("nom", m.getNom());
        row.put("code", m.getCode());
        row.put("coefficient", m.getCoefficient());
        row.put("volumeHoraire", m.getVolumeHoraire());
        row.put("semestre", m.getSemestre());
        row.put("filiereId", m.getFiliere() != null ? m.getFiliere().getId() : null);
        row.put("filiereNom", m.getFiliere() != null ? m.getFiliere().getNom() : null);
        row.put("teacherId", m.getTeacher() != null ? m.getTeacher().getId() : null);
        row.put("teacherName", m.getTeacher() != null ? m.getTeacher().getFullName() : null);
        return row;
    }

    private Role parseRole(String role, Role fallback, boolean required) {
        if (!StringUtils.hasText(role)) {
            if (fallback != null) {
                return fallback;
            }
            if (required) {
                throw new IllegalArgumentException("Role obligatoire.");
            }
            return null;
        }

        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Role invalide.");
        }
    }

    private String defaultValue(String requestValue, String fallback) {
        return requestValue != null ? requestValue : fallback;
    }

    private String requireText(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private String normalize(String q) {
        return StringUtils.hasText(q) ? q.trim().toLowerCase(Locale.ROOT) : null;
    }

    private boolean matchesUser(User user, String q) {
        return contains(user.getUsername(), q)
                || contains(user.getFirstName(), q)
                || contains(user.getLastName(), q)
                || contains(user.getFullName(), q)
                || contains(user.getEmail(), q)
                || contains(user.getRole() != null ? user.getRole().name() : null, q);
    }

    private boolean matchesFiliere(Filiere filiere, String q) {
        return contains(filiere.getNom(), q)
                || contains(filiere.getCode(), q)
                || contains(filiere.getDescription(), q)
                || contains(filiere.getChefFiliere() != null ? filiere.getChefFiliere().getFullName() : null, q);
    }

    private boolean matchesClasse(Classe classe, String q) {
        return contains(classe.getNom(), q)
                || contains(classe.getNiveau(), q)
                || contains(classe.getAnneeAcademique(), q)
                || contains(classe.getFiliere() != null ? classe.getFiliere().getNom() : null, q);
    }

    private boolean matchesModule(Module module, String q) {
        return contains(module.getNom(), q)
                || contains(module.getCode(), q)
                || contains(module.getSemestre(), q)
                || contains(module.getFiliere() != null ? module.getFiliere().getNom() : null, q)
                || contains(module.getTeacher() != null ? module.getTeacher().getFullName() : null, q);
    }

    private boolean contains(String value, String query) {
        return safe(value).toLowerCase(Locale.ROOT).contains(query);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
