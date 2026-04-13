package com.pfe.gestionetudiant.repository;

import com.pfe.gestionetudiant.model.EmploiDuTemps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmploiDuTempsRepository extends JpaRepository<EmploiDuTemps, Long> {

    List<EmploiDuTemps> findAllByOrderByJourAscHeureDebutAsc();

    List<EmploiDuTemps> findByFiliereIdOrderByJourAscHeureDebutAsc(Long filiereId);

    List<EmploiDuTemps> findByClasseIdOrderByJourAscHeureDebutAsc(Long classeId);

    List<EmploiDuTemps> findByTeacherIdOrderByJourAscHeureDebutAsc(Long teacherId);
}

