package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.service.ClasseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClasseServiceImpl implements ClasseService {

    private final ClasseRepository classeRepository;

    @Override
    public Classe save(Classe classe) {
        return classeRepository.save(classe);
    }

    @Override
    public void delete(Long id) {
        classeRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Classe> findById(Long id) {
        return classeRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Classe> findAll() {
        return classeRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Classe> findByFiliereId(Long filiereId) {
        return classeRepository.findByFiliereId(filiereId);
    }
}
