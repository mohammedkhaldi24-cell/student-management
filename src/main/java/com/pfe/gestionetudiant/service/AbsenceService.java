package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Absence;

import java.util.List;
import java.util.Optional;

public interface AbsenceService {

    Absence saveAbsence(Absence absence);

    void deleteAbsence(Long id);

    void justifierAbsence(Long id, String motif);

    Optional<Absence> findById(Long id);

    List<Absence> findByStudentId(Long studentId);

    List<Absence> findByStudentAndModule(Long studentId, Long moduleId);

    List<Absence> findByModuleId(Long moduleId);

    List<Absence> findByClasseId(Long classeId);

    List<Absence> findByClasseAndModuleId(Long classeId, Long moduleId);

    int getTotalHeuresByStudent(Long studentId);

    int getTotalHeuresNonJustifiesByStudent(Long studentId);
}
