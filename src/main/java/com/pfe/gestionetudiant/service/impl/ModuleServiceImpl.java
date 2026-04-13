package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Module;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.repository.ModuleRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.ModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ModuleServiceImpl implements ModuleService {

    private final ModuleRepository moduleRepository;
    private final UserRepository userRepository;

    @Override
    public Module save(Module module) {
        return moduleRepository.save(module);
    }

    @Override
    public void delete(Long id) {
        moduleRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Module> findById(Long id) {
        return moduleRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Module> findAll() {
        return moduleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Module> findByFiliereId(Long filiereId) {
        return moduleRepository.findByFiliereId(filiereId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Module> findByTeacherId(Long teacherId) {
        return moduleRepository.findByTeacherIdOrdered(teacherId);
    }

    @Override
    public void affecterEnseignant(Long moduleId, Long teacherId) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Module introuvable"));
        var teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable"));
        if (teacher.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("L'utilisateur selectionne n'est pas un enseignant.");
        }
        module.setTeacher(teacher);
        moduleRepository.save(module);
    }
}
