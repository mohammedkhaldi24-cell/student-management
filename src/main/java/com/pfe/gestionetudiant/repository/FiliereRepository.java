package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.Filiere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FiliereRepository extends JpaRepository<Filiere, Long> {

    Optional<Filiere> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Filiere> findFirstByChefFiliereIdOrderByIdAsc(Long userId);

    List<Filiere> findAllByChefFiliereId(Long userId);

    @Query("SELECT f FROM Filiere f LEFT JOIN FETCH f.classes ORDER BY f.nom")
    List<Filiere> findAllWithClasses();
}
