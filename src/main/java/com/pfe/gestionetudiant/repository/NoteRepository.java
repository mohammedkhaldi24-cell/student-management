package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByStudentId(Long studentId);

    List<Note> findByModuleId(Long moduleId);

    Optional<Note> findByStudentIdAndModuleIdAndSemestreAndAnneeAcademique(
            Long studentId, Long moduleId, String semestre, String anneeAcademique);

    @Query("SELECT n FROM Note n WHERE n.student.id = :studentId AND n.semestre = :semestre AND n.anneeAcademique = :annee ORDER BY n.module.nom")
    List<Note> findByStudentSemestreAnnee(@Param("studentId") Long studentId,
                                           @Param("semestre") String semestre,
                                           @Param("annee") String annee);

    @Query("SELECT n FROM Note n WHERE n.module.id = :moduleId AND n.student.classe.id = :classeId ORDER BY n.student.user.lastName")
    List<Note> findByModuleAndClasse(@Param("moduleId") Long moduleId,
                                      @Param("classeId") Long classeId);

    @Query("SELECT AVG(n.noteFinal) FROM Note n WHERE n.student.classe.id = :classeId AND n.anneeAcademique = :annee")
    Double getAverageByClasse(@Param("classeId") Long classeId, @Param("annee") String annee);

    @Query("SELECT AVG(n.noteFinal) FROM Note n WHERE n.module.id = :moduleId AND n.anneeAcademique = :annee")
    Double getAverageByModule(@Param("moduleId") Long moduleId, @Param("annee") String annee);

    @Query("SELECT n FROM Note n WHERE n.student.classe.filiere.id = :filiereId AND n.anneeAcademique = :annee")
    List<Note> findByFiliereAndAnnee(@Param("filiereId") Long filiereId, @Param("annee") String annee);
}
