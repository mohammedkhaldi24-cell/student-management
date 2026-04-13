-- ============================================================
-- Script SQL - Gestion des Notes et Absences des Étudiants
-- PFE - Plateforme Web
-- ============================================================

-- Création de la base de données
CREATE DATABASE IF NOT EXISTS gestion_etudiant
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gestion_etudiant;

-- ============================================================
-- TABLE : users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role ENUM('ADMIN', 'CHEF_FILIERE', 'TEACHER', 'STUDENT') NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : filieres
-- ============================================================
CREATE TABLE IF NOT EXISTS filieres (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    description TEXT,
    chef_filiere_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chef_filiere_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : classes
-- ============================================================
CREATE TABLE IF NOT EXISTS classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    niveau VARCHAR(20) NOT NULL,
    annee_academique VARCHAR(20) NOT NULL,
    filiere_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (filiere_id) REFERENCES filieres(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : modules (matières)
-- ============================================================
CREATE TABLE IF NOT EXISTS modules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    coefficient INT NOT NULL DEFAULT 1,
    volume_horaire INT DEFAULT 30,
    semestre VARCHAR(10) NOT NULL,
    filiere_id BIGINT NOT NULL,
    teacher_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (filiere_id) REFERENCES filieres(id) ON DELETE CASCADE,
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : students (profil étudiant)
-- ============================================================
CREATE TABLE IF NOT EXISTS students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    matricule VARCHAR(20) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE,
    classe_id BIGINT,
    date_naissance DATE,
    adresse VARCHAR(255),
    telephone VARCHAR(20),
    photo_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (classe_id) REFERENCES classes(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : teachers (profil enseignant)
-- ============================================================
CREATE TABLE IF NOT EXISTS teachers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    specialite VARCHAR(150),
    grade VARCHAR(100),
    telephone VARCHAR(20),
    bureau VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : notes
-- ============================================================
CREATE TABLE IF NOT EXISTS notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    note_cc DOUBLE,
    note_examen DOUBLE,
    note_final DOUBLE,
    semestre VARCHAR(10) NOT NULL,
    annee_academique VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_note (student_id, module_id, semestre, annee_academique),
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- TABLE : absences
-- ============================================================
CREATE TABLE IF NOT EXISTS absences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    date_absence DATE NOT NULL,
    nombre_heures INT NOT NULL DEFAULT 2,
    justifiee BOOLEAN DEFAULT FALSE,
    motif TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- DONNÉES INITIALES
-- ============================================================

-- Mot de passe : "admin123" encodé BCrypt
INSERT INTO users (username, password, email, first_name, last_name, role, enabled)
VALUES
('admin', '$2a$12$LCNkGPBFqFoMdBr3Fq3OCeLY0G1.VsFe7O7X59N9e/tlAXZiUCT5m', 'admin@universite.dz', 'Super', 'Admin', 'ADMIN', true),

-- Mot de passe : "chef123"
('chef.info', '$2a$12$K8QdW8IH1cFIRXlz3Y0wM.3E4MFbB3X8FLNpfO5gFJiQq9YRHAlsC', 'chef.info@universite.dz', 'Mohammed', 'Benali', 'CHEF_FILIERE', true),

-- Mot de passe : "teacher123"
('prof.ali', '$2a$12$7bG9vQ2XhPg1wR3mN5kLseuOHKWPFAIRLg8dJo1MVBqTBc0z4gzOO', 'ali.brahimi@universite.dz', 'Ali', 'Brahimi', 'TEACHER', true),
('prof.sara', '$2a$12$7bG9vQ2XhPg1wR3mN5kLseuOHKWPFAIRLg8dJo1MVBqTBc0z4gzOO', 'sara.kaci@universite.dz', 'Sara', 'Kaci', 'TEACHER', true),

-- Mot de passe : "student123"
('etudiant.1', '$2a$12$eAdB0xSAH6K5T78rHQYWG.OUGP1lmByWJgGE7I5TQPQ9FzM7fOk3u', 'ahmed.meziane@etudiant.dz', 'Ahmed', 'Meziane', 'STUDENT', true),
('etudiant.2', '$2a$12$eAdB0xSAH6K5T78rHQYWG.OUGP1lmByWJgGE7I5TQPQ9FzM7fOk3u', 'fatima.ait@etudiant.dz', 'Fatima', 'Ait Yahia', 'STUDENT', true),
('etudiant.3', '$2a$12$eAdB0xSAH6K5T78rHQYWG.OUGP1lmByWJgGE7I5TQPQ9FzM7fOk3u', 'karim.bensalem@etudiant.dz', 'Karim', 'Bensalem', 'STUDENT', true);

-- Filière
INSERT INTO filieres (nom, code, description, chef_filiere_id)
VALUES ('Informatique', 'INFO', 'Licence en Sciences Informatiques', 2);

-- Classes
INSERT INTO classes (nom, niveau, annee_academique, filiere_id)
VALUES
('L3 Info A', 'L3', '2024-2025', 1),
('L3 Info B', 'L3', '2024-2025', 1),
('L2 Info', 'L2', '2024-2025', 1);

-- Enseignants
INSERT INTO teachers (user_id, specialite, grade, bureau)
VALUES
(3, 'Algorithmique et Structures de Données', 'Maître de Conférences A', 'Bureau 12'),
(4, 'Génie Logiciel et Base de Données', 'Maître Assistant A', 'Bureau 08');

-- Modules
INSERT INTO modules (nom, code, coefficient, volume_horaire, semestre, filiere_id, teacher_id)
VALUES
('Algorithmique Avancée', 'ALG301', 3, 45, 'S1', 1, 3),
('Base de Données', 'BDD301', 3, 45, 'S1', 1, 4),
('Réseaux Informatiques', 'RES301', 2, 30, 'S1', 1, 3),
('Génie Logiciel', 'GL301', 3, 45, 'S2', 1, 4),
('Intelligence Artificielle', 'IA301', 2, 30, 'S2', 1, 3);

-- Étudiants
INSERT INTO students (matricule, user_id, classe_id, date_naissance)
VALUES
('2024INFO001', 5, 1, '2002-03-15'),
('2024INFO002', 6, 1, '2001-11-22'),
('2024INFO003', 7, 2, '2002-07-08');

-- Notes S1
INSERT INTO notes (student_id, module_id, note_cc, note_examen, note_final, semestre, annee_academique)
VALUES
(1, 1, 14.5, 16.0, 15.4, 'S1', '2024-2025'),
(1, 2, 12.0, 13.5, 12.9, 'S1', '2024-2025'),
(1, 3, 15.0, 14.0, 14.4, 'S1', '2024-2025'),
(2, 1, 11.0, 10.5, 10.7, 'S1', '2024-2025'),
(2, 2, 16.0, 17.5, 16.9, 'S1', '2024-2025'),
(2, 3, 13.5, 12.0, 12.6, 'S1', '2024-2025'),
(3, 1, 9.5, 10.0, 9.8, 'S1', '2024-2025'),
(3, 2, 14.0, 13.5, 13.7, 'S1', '2024-2025'),
(3, 3, 12.5, 11.5, 11.9, 'S1', '2024-2025');

-- Absences
INSERT INTO absences (student_id, module_id, date_absence, nombre_heures, justifiee, motif)
VALUES
(1, 1, '2024-10-15', 3, false, NULL),
(1, 3, '2024-11-02', 2, true, 'Maladie - Certificat médical'),
(2, 2, '2024-10-28', 3, false, NULL),
(3, 1, '2024-10-10', 3, false, NULL),
(3, 1, '2024-11-05', 3, false, NULL),
(3, 3, '2024-11-20', 2, true, 'Participation à une conférence');

-- ============================================================
-- INDEX pour les performances
-- ============================================================
CREATE INDEX idx_notes_student ON notes(student_id);
CREATE INDEX idx_notes_module ON notes(module_id);
CREATE INDEX idx_absences_student ON absences(student_id);
CREATE INDEX idx_absences_module ON absences(module_id);
CREATE INDEX idx_students_classe ON students(classe_id);
CREATE INDEX idx_modules_filiere ON modules(filiere_id);
CREATE INDEX idx_modules_teacher ON modules(teacher_id);
