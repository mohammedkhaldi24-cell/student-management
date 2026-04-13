package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.service.FiliereService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FiliereServiceImpl implements FiliereService {

    private final FiliereRepository filiereRepository;

    @Override
    public Filiere save(Filiere filiere) {
        if (filiere.getChefFiliere() != null && filiere.getChefFiliere().getId() != null) {
            Long chefId = filiere.getChefFiliere().getId();
            List<Filiere> filieresDuChef = filiereRepository.findAllByChefFiliereId(chefId);
            for (Filiere existante : filieresDuChef) {
                boolean autreFiliere = filiere.getId() == null || !existante.getId().equals(filiere.getId());
                if (autreFiliere) {
                    existante.setChefFiliere(null);
                    filiereRepository.save(existante);
                }
            }
        }
        return filiereRepository.save(filiere);
    }

    @Override
    public void delete(Long id) {
        filiereRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Filiere> findById(Long id) {
        return filiereRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Filiere> findAll() {
        return filiereRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Filiere> findByChefFiliereId(Long userId) {
        return filiereRepository.findFirstByChefFiliereIdOrderByIdAsc(userId);
    }
}
