package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Classe;

import java.util.List;
import java.util.Optional;

public interface ClasseService {
    Classe save(Classe classe);
    void delete(Long id);
    Optional<Classe> findById(Long id);
    List<Classe> findAll();
    List<Classe> findByFiliereId(Long filiereId);
}
