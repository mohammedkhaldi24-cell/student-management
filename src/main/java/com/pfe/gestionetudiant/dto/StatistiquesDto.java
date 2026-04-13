package com.pfe.gestionetudiant.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les statistiques du dashboard
 */
@Data
@NoArgsConstructor
public class StatistiquesDto {

    // Compteurs globaux (Admin)
    private long totalEtudiants;
    private long totalEnseignants;
    private long totalChefsFilieres;
    private long totalFilieres;
    private long totalClasses;
    private long totalModules;

    // Statistiques par classe (Chef Filière)
    private Map<String, Double> moyenneParClasse = new HashMap<>();
    private Map<String, Integer> absencesParClasse = new HashMap<>();

    // Statistiques pour chart (labels + data)
    private List<String> classesLabels;
    private List<Double> moyennesData;
    private List<Integer> absencesData;

    // Taux absences
    private double tauxAbsencesJustifiees;
    private double tauxAbsencesNonJustifiees;

    // Répartition notes (pour graphique en secteurs)
    private long nbAdmis;
    private long nbRattrapage;
    private long nbAjournes;
}
