package com.pfe.gestionetudiant.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour la génération du bulletin PDF d'un étudiant
 */
@Data
@NoArgsConstructor
public class BulletinDto {

    private String matricule;
    private String nomEtudiant;
    private String prenomEtudiant;
    private String nomClasse;
    private String filiere;
    private String anneeAcademique;
    private String semestre;

    private List<LigneNoteDto> notes;

    private double moyenneGenerale;
    private int rangClasse;
    private int totalEtudiants;
    private int totalHeuresAbsence;
    private int totalHeuresAbsenceNonJustifiee;
    private String mention;
    private String decision;

    @Data
    @NoArgsConstructor
    public static class LigneNoteDto {
        private String moduleNom;
        private String moduleCode;
        private int coefficient;
        private Double noteCC;
        private Double noteExamen;
        private Double noteFinal;
        private String statut;
        private double noteXCoeff;
    }

    public String getMention() {
        if (moyenneGenerale >= 16) return "Très Bien";
        if (moyenneGenerale >= 14) return "Bien";
        if (moyenneGenerale >= 12) return "Assez Bien";
        if (moyenneGenerale >= 10) return "Passable";
        return "Insuffisant";
    }

    public String getDecision() {
        if (moyenneGenerale >= 10) return "ADMIS(E)";
        if (moyenneGenerale >= 7) return "RATTRAPAGE";
        return "AJOURNÉ(E)";
    }
}
