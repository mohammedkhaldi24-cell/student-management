package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Module;

import java.util.List;
import java.util.Optional;

public interface ModuleService {
    Module save(Module module);
    void delete(Long id);
    Optional<Module> findById(Long id);
    List<Module> findAll();
    List<Module> findByFiliereId(Long filiereId);
    List<Module> findByTeacherId(Long teacherId);
    void affecterEnseignant(Long moduleId, Long teacherId);
}
