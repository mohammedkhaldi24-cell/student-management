package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Classe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClasseRepository extends JpaRepository<Classe, Long> {

    List<Classe> findByFiliereId(Long filiereId);

    List<Classe> findByNiveau(String niveau);

    List<Classe> findByAnneeAcademique(String anneeAcademique);

    @Query("SELECT c FROM Classe c WHERE c.filiere.id = :filiereId ORDER BY c.niveau, c.nom")
    List<Classe> findByFiliereIdOrdered(@Param("filiereId") Long filiereId);

    @Query("SELECT c FROM Classe c LEFT JOIN FETCH c.students WHERE c.filiere.id = :filiereId")
    List<Classe> findByFiliereIdWithStudents(@Param("filiereId") Long filiereId);
}
