package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModuleRepository extends JpaRepository<Module, Long> {

    List<Module> findByFiliereId(Long filiereId);

    @Query("""
            SELECT DISTINCT m
            FROM Module m
            LEFT JOIN FETCH m.filiere f
            LEFT JOIN FETCH m.teacher t
            WHERE f.id = :filiereId
            ORDER BY m.semestre, m.nom
            """)
    List<Module> findByFiliereIdOrdered(@Param("filiereId") Long filiereId);

    List<Module> findBySemestre(String semestre);

    List<Module> findByTeacherId(Long teacherId);

    @Query("SELECT m FROM Module m WHERE m.filiere.id = :filiereId AND m.semestre = :semestre")
    List<Module> findByFiliereIdAndSemestre(@Param("filiereId") Long filiereId,
                                             @Param("semestre") String semestre);

    @Query("SELECT m FROM Module m LEFT JOIN FETCH m.filiere WHERE m.teacher.id = :teacherId ORDER BY m.semestre, m.nom")
    List<Module> findByTeacherIdOrdered(@Param("teacherId") Long teacherId);

    boolean existsByCode(String code);
}
