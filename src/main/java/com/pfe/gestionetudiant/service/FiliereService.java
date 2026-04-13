package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Filiere;

import java.util.List;
import java.util.Optional;

public interface FiliereService {
    Filiere save(Filiere filiere);
    void delete(Long id);
    Optional<Filiere> findById(Long id);
    List<Filiere> findAll();
    Optional<Filiere> findByChefFiliereId(Long userId);
}
