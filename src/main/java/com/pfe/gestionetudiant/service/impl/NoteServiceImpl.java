package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.dto.BulletinDto;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.repository.*;
import com.pfe.gestionetudiant.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final StudentRepository studentRepository;
    private final ClasseRepository classeRepository;
    private final AbsenceRepository absenceRepository;

    @Override
    public Note saveNote(Note note) {
        note.calculerNoteFinal();
        return noteRepository.save(note);
    }

    @Override
    public Note updateNote(Long id, Double noteCC, Double noteExamen) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note introuvable"));
        note.setNoteCC(noteCC);
        note.setNoteExamen(noteExamen);
        note.calculerNoteFinal();
        return noteRepository.save(note);
    }

    @Override
    public void deleteNote(Long id) {
        noteRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Note> findById(Long id) {
        return noteRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Note> findByStudentId(Long studentId) {
        return noteRepository.findByStudentId(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Note> findByModuleId(Long moduleId) {
        return noteRepository.findByModuleId(moduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Note> findByModuleAndClasse(Long moduleId, Long classeId) {
        return noteRepository.findByModuleAndClasse(moduleId, classeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Note> findByStudentModuleSemestreAnnee(Long studentId, Long moduleId,
                                                            String semestre, String annee) {
        return noteRepository.findByStudentIdAndModuleIdAndSemestreAndAnneeAcademique(
                studentId, moduleId, semestre, annee);
    }

    @Override
    @Transactional(readOnly = true)
    public double calculerMoyenneEtudiant(Long studentId, String semestre, String annee) {
        List<Note> notes = noteRepository.findByStudentSemestreAnnee(studentId, semestre, annee);

        if (notes.isEmpty()) return 0.0;

        double sommeNotesXCoeff = 0;
        int sommeCoeffs = 0;

        for (Note note : notes) {
            if (note.getNoteFinal() != null) {
                int coeff = note.getModule().getCoefficient();
                sommeNotesXCoeff += note.getNoteFinal() * coeff;
                sommeCoeffs += coeff;
            }
        }

        if (sommeCoeffs == 0) return 0.0;

        double moyenne = sommeNotesXCoeff / sommeCoeffs;
        return Math.round(moyenne * 100.0) / 100.0;
    }

    @Override
    @Transactional(readOnly = true)
    public BulletinDto genererBulletin(Long studentId, String semestre, String annee) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Étudiant introuvable"));

        List<Note> notes = noteRepository.findByStudentSemestreAnnee(studentId, semestre, annee);

        BulletinDto bulletin = new BulletinDto();
        bulletin.setMatricule(student.getMatricule());
        bulletin.setNomEtudiant(student.getUser().getLastName());
        bulletin.setPrenomEtudiant(student.getUser().getFirstName());
        bulletin.setAnneeAcademique(annee);
        bulletin.setSemestre(semestre);

        if (student.getClasse() != null) {
            bulletin.setNomClasse(student.getClasse().getNom());
            if (student.getClasse().getFiliere() != null) {
                bulletin.setFiliere(student.getClasse().getFiliere().getNom());
            }
        }

        List<BulletinDto.LigneNoteDto> lignes = new ArrayList<>();
        double sommeNotesXCoeff = 0;
        int sommeCoeffs = 0;

        for (Note note : notes) {
            BulletinDto.LigneNoteDto ligne = new BulletinDto.LigneNoteDto();
            ligne.setModuleNom(note.getModule().getNom());
            ligne.setModuleCode(note.getModule().getCode());
            ligne.setCoefficient(note.getModule().getCoefficient());
            ligne.setNoteCC(note.getNoteCC());
            ligne.setNoteExamen(note.getNoteExamen());
            ligne.setNoteFinal(note.getNoteFinal());
            ligne.setStatut(note.getStatut());

            if (note.getNoteFinal() != null) {
                double nxc = note.getNoteFinal() * note.getModule().getCoefficient();
                ligne.setNoteXCoeff(Math.round(nxc * 100.0) / 100.0);
                sommeNotesXCoeff += nxc;
                sommeCoeffs += note.getModule().getCoefficient();
            }

            lignes.add(ligne);
        }

        bulletin.setNotes(lignes);

        if (sommeCoeffs > 0) {
            double moyenne = Math.round((sommeNotesXCoeff / sommeCoeffs) * 100.0) / 100.0;
            bulletin.setMoyenneGenerale(moyenne);
        }

        // Absences
        Integer totalAbsences = absenceRepository.getTotalHeuresByStudent(studentId);
        Integer absencesNJ = absenceRepository.getTotalHeuresNonJustifiesByStudent(studentId);
        bulletin.setTotalHeuresAbsence(totalAbsences != null ? totalAbsences : 0);
        bulletin.setTotalHeuresAbsenceNonJustifiee(absencesNJ != null ? absencesNJ : 0);

        // Rang dans la classe
        if (student.getClasse() != null) {
            List<Student> classmates = studentRepository.findByClasseId(student.getClasse().getId());
            bulletin.setTotalEtudiants(classmates.size());

            List<Double> moyennes = new ArrayList<>();
            for (Student s : classmates) {
                double moy = calculerMoyenneEtudiant(s.getId(), semestre, annee);
                moyennes.add(moy);
            }
            Collections.sort(moyennes, Collections.reverseOrder());
            double myMoy = bulletin.getMoyenneGenerale();
            int rang = moyennes.indexOf(myMoy) + 1;
            bulletin.setRangClasse(rang);
        }

        return bulletin;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Double> getMoyennesParClasse(Long filiereId, String annee) {
        List<Classe> classes = classeRepository.findByFiliereId(filiereId);
        Map<String, Double> result = new LinkedHashMap<>();
        for (Classe classe : classes) {
            Double moy = noteRepository.getAverageByClasse(classe.getId(), annee);
            result.put(classe.getNom(), moy != null ? Math.round(moy * 100.0) / 100.0 : 0.0);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Double getAverageByClasse(Long classeId, String annee) {
        Double moy = noteRepository.getAverageByClasse(classeId, annee);
        return moy != null ? Math.round(moy * 100.0) / 100.0 : 0.0;
    }
}
