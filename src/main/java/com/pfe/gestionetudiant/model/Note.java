package com.pfe.gestionetudiant.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité Note d'un étudiant dans un module
 *
 * Calcul automatique :
 *   noteFinal = (noteCC * 0.40) + (noteExamen * 0.60)
 */
@Entity
@Table(name = "notes",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"student_id", "module_id", "semestre", "annee_academique"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Min(value = 0, message = "La note CC ne peut pas être négative")
    @Max(value = 20, message = "La note CC ne peut pas dépasser 20")
    @Column(name = "note_cc")
    private Double noteCC;

    @Min(value = 0, message = "La note examen ne peut pas être négative")
    @Max(value = 20, message = "La note examen ne peut pas dépasser 20")
    @Column(name = "note_examen")
    private Double noteExamen;

    @Column(name = "note_final")
    private Double noteFinal;

    @NotBlank(message = "Le semestre est obligatoire")
    @Column(nullable = false, length = 10)
    private String semestre;

    @NotBlank(message = "L'année académique est obligatoire")
    @Column(name = "annee_academique", nullable = false, length = 20)
    private String anneeAcademique;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        calculerNoteFinal();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        calculerNoteFinal();
    }

    /**
     * Calcul automatique de la note finale :
     * noteFinal = (CC * 40%) + (Examen * 60%)
     */
    public void calculerNoteFinal() {
        if (noteCC != null && noteExamen != null) {
            this.noteFinal = Math.round(((noteCC * 0.40) + (noteExamen * 0.60)) * 100.0) / 100.0;
        } else if (noteExamen != null) {
            this.noteFinal = noteExamen;
        } else if (noteCC != null) {
            this.noteFinal = noteCC;
        }
    }

    /**
     * Retourne le statut (Admis / Ajourné / Rattrapage)
     */
    public String getStatut() {
        if (noteFinal == null) return "Non noté";
        if (noteFinal >= 10) return "Admis";
        if (noteFinal >= 7) return "Rattrapage";
        return "Ajourné";
    }
}
