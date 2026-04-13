package com.pfe.gestionetudiant.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité Classe (groupe d'étudiants)
 */
@Entity
@Table(name = "classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Classe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom de la classe est obligatoire")
    @Column(nullable = false, length = 100)
    private String nom;

    @NotBlank(message = "Le niveau est obligatoire")
    @Column(nullable = false, length = 20)
    private String niveau; // L1, L2, L3, M1, M2

    @NotBlank(message = "L'année académique est obligatoire")
    @Column(name = "annee_academique", nullable = false, length = 20)
    private String anneeAcademique; // ex: 2024-2025

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @OneToMany(mappedBy = "classe", fetch = FetchType.LAZY)
    private List<Student> students = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public int getNombreEtudiants() {
        return students != null ? students.size() : 0;
    }
}
