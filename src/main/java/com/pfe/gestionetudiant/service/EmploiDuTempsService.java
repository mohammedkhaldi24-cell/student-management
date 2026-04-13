package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.EmploiDuTemps;

import java.util.List;
import java.util.Optional;

public interface EmploiDuTempsService {

    EmploiDuTemps save(EmploiDuTemps emploiDuTemps);

    void delete(Long id);

    Optional<EmploiDuTemps> findById(Long id);

    List<EmploiDuTemps> findAll();

    List<EmploiDuTemps> findByFiliereId(Long filiereId);

    List<EmploiDuTemps> findByClasseId(Long classeId);
}

