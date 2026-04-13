package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.repository.AbsenceRepository;
import com.pfe.gestionetudiant.service.AbsenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AbsenceServiceImpl implements AbsenceService {

    private final AbsenceRepository absenceRepository;

    @Override
    public Absence saveAbsence(Absence absence) {
        return absenceRepository.save(absence);
    }

    @Override
    public void deleteAbsence(Long id) {
        absenceRepository.deleteById(id);
    }

    @Override
    public void justifierAbsence(Long id, String motif) {
        Absence absence = absenceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Absence introuvable"));
        absence.setJustifiee(true);
        absence.setMotif(motif);
        absenceRepository.save(absence);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Absence> findById(Long id) {
        return absenceRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Absence> findByStudentId(Long studentId) {
        return absenceRepository.findByStudentIdOrdered(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Absence> findByModuleId(Long moduleId) {
        return absenceRepository.findByModuleId(moduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Absence> findByClasseId(Long classeId) {
        return absenceRepository.findByClasseId(classeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Absence> findByClasseAndModuleId(Long classeId, Long moduleId) {
        return absenceRepository.findByClasseIdAndModuleId(classeId, moduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public int getTotalHeuresByStudent(Long studentId) {
        Integer total = absenceRepository.getTotalHeuresByStudent(studentId);
        return total != null ? total : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public int getTotalHeuresNonJustifiesByStudent(Long studentId) {
        Integer total = absenceRepository.getTotalHeuresNonJustifiesByStudent(studentId);
        return total != null ? total : 0;
    }
}
