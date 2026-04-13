package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.repository.EmploiDuTempsRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.EmploiDuTempsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmploiDuTempsServiceImpl implements EmploiDuTempsService {

    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final UserRepository userRepository;

    @Override
    public EmploiDuTemps save(EmploiDuTemps emploiDuTemps) {
        validateBusinessRules(emploiDuTemps);

        if (emploiDuTemps.getTeacher() == null && emploiDuTemps.getModule() != null) {
            emploiDuTemps.setTeacher(emploiDuTemps.getModule().getTeacher());
        }

        return emploiDuTempsRepository.save(emploiDuTemps);
    }

    @Override
    public void delete(Long id) {
        emploiDuTempsRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmploiDuTemps> findById(Long id) {
        return emploiDuTempsRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmploiDuTemps> findAll() {
        return emploiDuTempsRepository.findAllByOrderByJourAscHeureDebutAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmploiDuTemps> findByFiliereId(Long filiereId) {
        return emploiDuTempsRepository.findByFiliereIdOrderByJourAscHeureDebutAsc(filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmploiDuTemps> findByClasseId(Long classeId) {
        return emploiDuTempsRepository.findByClasseIdOrderByJourAscHeureDebutAsc(classeId);
    }

    private void validateBusinessRules(EmploiDuTemps emploiDuTemps) {
        if (emploiDuTemps.getFiliere() == null) {
            throw new IllegalArgumentException("La filiere est obligatoire.");
        }
        if (emploiDuTemps.getClasse() == null) {
            throw new IllegalArgumentException("La classe est obligatoire.");
        }
        if (emploiDuTemps.getModule() == null) {
            throw new IllegalArgumentException("Le module est obligatoire.");
        }
        if (emploiDuTemps.getHeureDebut() == null || emploiDuTemps.getHeureFin() == null
                || !emploiDuTemps.getHeureFin().isAfter(emploiDuTemps.getHeureDebut())) {
            throw new IllegalArgumentException("L'heure de fin doit etre apres l'heure de debut.");
        }

        if (emploiDuTemps.getClasse().getFiliere() == null
                || !emploiDuTemps.getFiliere().getId().equals(emploiDuTemps.getClasse().getFiliere().getId())) {
            throw new IllegalArgumentException("La classe selectionnee n'appartient pas a la filiere.");
        }

        if (emploiDuTemps.getModule().getFiliere() == null
                || !emploiDuTemps.getFiliere().getId().equals(emploiDuTemps.getModule().getFiliere().getId())) {
            throw new IllegalArgumentException("Le module selectionne n'appartient pas a la filiere.");
        }

        if (emploiDuTemps.getTeacher() != null) {
            Long teacherId = emploiDuTemps.getTeacher().getId();
            var teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
            if (teacher.getRole() != Role.TEACHER) {
                throw new IllegalArgumentException("L'utilisateur selectionne n'est pas un enseignant.");
            }
            emploiDuTemps.setTeacher(teacher);
        }
    }
}

