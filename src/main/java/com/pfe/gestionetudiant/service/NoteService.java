package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.dto.BulletinDto;
import com.pfe.gestionetudiant.model.Note;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NoteService {

    Note saveNote(Note note);

    Note updateNote(Long id, Double noteCC, Double noteExamen);

    void deleteNote(Long id);

    Optional<Note> findById(Long id);

    List<Note> findByStudentId(Long studentId);

    List<Note> findByStudentAndModule(Long studentId, Long moduleId);

    List<Note> findByModuleId(Long moduleId);

    List<Note> findByModuleAndClasse(Long moduleId, Long classeId);

    Optional<Note> findByStudentModuleSemestreAnnee(Long studentId, Long moduleId,
                                                     String semestre, String annee);

    /**
     * Calcule la moyenne générale pondérée d'un étudiant
     * Formule : Σ(noteFinal_i × coefficient_i) / Σ(coefficient_i)
     */
    double calculerMoyenneEtudiant(Long studentId, String semestre, String annee);

    BulletinDto genererBulletin(Long studentId, String semestre, String annee);

    Map<String, Double> getMoyennesParClasse(Long filiereId, String annee);

    Double getAverageByClasse(Long classeId, String annee);
}
