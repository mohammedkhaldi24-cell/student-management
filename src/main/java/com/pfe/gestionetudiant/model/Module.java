package com.pfe.gestionetudiant.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité Module (Matière)
 */
@Entity
@Table(name = "modules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom du module est obligatoire")
    @Column(nullable = false, length = 150)
    private String nom;

    @NotBlank(message = "Le code du module est obligatoire")
    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @NotNull(message = "Le coefficient est obligatoire")
    @Min(value = 1, message = "Le coefficient doit être au moins 1")
    @Column(nullable = false)
    private Integer coefficient = 1;

    @Column(name = "volume_horaire")
    private Integer volumeHoraire = 30;

    @NotBlank(message = "Le semestre est obligatoire")
    @Column(nullable = false, length = 10)
    private String semestre; // S1 ou S2

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Note> notes = new ArrayList<>();

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Absence> absences = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
