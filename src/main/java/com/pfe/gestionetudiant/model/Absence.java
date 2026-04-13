package com.pfe.gestionetudiant.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité Absence d'un étudiant dans un module
 */
@Entity
@Table(name = "absences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Absence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @NotNull(message = "La date d'absence est obligatoire")
    @Column(name = "date_absence", nullable = false)
    private LocalDate dateAbsence;

    @Min(value = 1, message = "Le nombre d'heures doit être au moins 1")
    @Column(name = "nombre_heures", nullable = false)
    private Integer nombreHeures = 2;

    @Column(nullable = false)
    private boolean justifiee = false;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
