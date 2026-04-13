package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Absence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AbsenceRepository extends JpaRepository<Absence, Long> {

    List<Absence> findByStudentId(Long studentId);

    List<Absence> findByModuleId(Long moduleId);

    List<Absence> findByStudentIdAndModuleId(Long studentId, Long moduleId);

    @Query("SELECT a FROM Absence a WHERE a.student.classe.id = :classeId ORDER BY a.dateAbsence DESC")
    List<Absence> findByClasseId(@Param("classeId") Long classeId);

    @Query("SELECT a FROM Absence a WHERE a.student.classe.id = :classeId AND a.module.id = :moduleId ORDER BY a.dateAbsence DESC")
    List<Absence> findByClasseIdAndModuleId(@Param("classeId") Long classeId,
                                            @Param("moduleId") Long moduleId);

    @Query("SELECT SUM(a.nombreHeures) FROM Absence a WHERE a.student.id = :studentId")
    Integer getTotalHeuresByStudent(@Param("studentId") Long studentId);

    @Query("SELECT SUM(a.nombreHeures) FROM Absence a WHERE a.student.id = :studentId AND a.justifiee = false")
    Integer getTotalHeuresNonJustifiesByStudent(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(a) FROM Absence a WHERE a.student.classe.filiere.id = :filiereId")
    long countByFiliereId(@Param("filiereId") Long filiereId);

    @Query("SELECT a FROM Absence a WHERE a.student.id = :studentId ORDER BY a.dateAbsence DESC")
    List<Absence> findByStudentIdOrdered(@Param("studentId") Long studentId);

    @Query("SELECT a FROM Absence a WHERE a.module.teacher.id = :teacherId ORDER BY a.dateAbsence DESC")
    List<Absence> findByTeacherId(@Param("teacherId") Long teacherId);

    List<Absence> findByDateAbsenceBetween(LocalDate dateDebut, LocalDate dateFin);
}
