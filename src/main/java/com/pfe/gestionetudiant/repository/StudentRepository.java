package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUser(User user);

    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByMatricule(String matricule);

    List<Student> findByClasse(Classe classe);

    List<Student> findByClasseId(Long classeId);

    @Query("SELECT s FROM Student s WHERE s.classe.filiere = :filiere ORDER BY s.user.lastName")
    List<Student> findByFiliere(@Param("filiere") Filiere filiere);

    @Query("SELECT s FROM Student s WHERE s.classe.filiere.id = :filiereId ORDER BY s.user.lastName")
    List<Student> findByFiliereId(@Param("filiereId") Long filiereId);

    @Query("SELECT COUNT(s) FROM Student s WHERE s.classe.id = :classeId")
    long countByClasseId(@Param("classeId") Long classeId);

    @Query("SELECT COUNT(s) FROM Student s WHERE s.classe.filiere.id = :filiereId")
    long countByFiliereId(@Param("filiereId") Long filiereId);

    boolean existsByMatricule(String matricule);
}
