-- ============================================================
-- SQL Script - GestionEtu (PFE)
-- ============================================================

CREATE DATABASE IF NOT EXISTS gestion_etudiant
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gestion_etudiant;

-- ============================================================
-- users
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
-- filieres
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
-- classes
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
-- modules
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
-- students
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
-- teachers
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
-- notes
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
-- absences
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
-- emplois_du_temps
-- ============================================================
CREATE TABLE IF NOT EXISTS emplois_du_temps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jour VARCHAR(20) NOT NULL,
    heure_debut TIME NOT NULL,
    heure_fin TIME NOT NULL,
    module_id BIGINT NOT NULL,
    teacher_id BIGINT,
    classe_id BIGINT NOT NULL,
    filiere_id BIGINT NOT NULL,
    salle VARCHAR(50) NOT NULL,
    valide BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE,
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (classe_id) REFERENCES classes(id) ON DELETE CASCADE,
    FOREIGN KEY (filiere_id) REFERENCES filieres(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- course_contents
-- ============================================================
CREATE TABLE IF NOT EXISTS course_contents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    file_path VARCHAR(500),
    module_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    classe_id BIGINT,
    filiere_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE CASCADE,
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (classe_id) REFERENCES classes(id) ON DELETE SET NULL,
    FOREIGN KEY (filiere_id) REFERENCES filieres(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- announcements
-- ============================================================
CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    attachment_path VARCHAR(500),
    author_id BIGINT NOT NULL,
    target_classe_id BIGINT,
    target_filiere_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (target_classe_id) REFERENCES classes(id) ON DELETE SET NULL,
    FOREIGN KEY (target_filiere_id) REFERENCES filieres(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- assignments
-- ============================================================
CREATE TABLE IF NOT EXISTS assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_date DATETIME NOT NULL,
    attachment_path VARCHAR(500),
    teacher_id BIGINT NOT NULL,
    module_id BIGINT,
    target_classe_id BIGINT,
    target_filiere_id BIGINT,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES modules(id) ON DELETE SET NULL,
    FOREIGN KEY (target_classe_id) REFERENCES classes(id) ON DELETE SET NULL,
    FOREIGN KEY (target_filiere_id) REFERENCES filieres(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- assignment_submissions
-- ============================================================
CREATE TABLE IF NOT EXISTS assignment_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    submission_text TEXT,
    file_path VARCHAR(500),
    submitted_at DATETIME NOT NULL,
    late_submission BOOLEAN NOT NULL DEFAULT FALSE,
    score DOUBLE,
    feedback TEXT,
    status ENUM('NOT_SUBMITTED', 'SUBMITTED', 'REVIEWED', 'GRADED') NOT NULL DEFAULT 'SUBMITTED',
    UNIQUE KEY uk_assignment_student (assignment_id, student_id),
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- assignment_submission_files (multi-fichiers pour un devoir)
-- ============================================================
CREATE TABLE IF NOT EXISTS assignment_submission_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255),
    content_type VARCHAR(120),
    file_size BIGINT,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (submission_id) REFERENCES assignment_submissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Seed data
-- ============================================================

-- Passwords (BCrypt):
-- admin123, chef123, teacher123, student123
INSERT INTO users (id, username, password, email, first_name, last_name, role, enabled)
VALUES
(1, 'admin', '$2a$12$EYr2A4SX9YZT7Yc2ip1dluShQPeYc/OZIVXNiybxh/ZwMlK6Fde..', 'admin@universite.dz', 'Super', 'Admin', 'ADMIN', true),
(2, 'chef.info', '$2a$12$pNH3WI67DsCSmH2SQJdQ0eD6m4S1kC0aU2igrZIfdb6oBIugn76PO', 'chef.info@universite.dz', 'Mohammed', 'Benali', 'CHEF_FILIERE', true),
(3, 'prof.ali', '$2a$12$FUzYd2A0DFomrNtQE9dy1.neF8r5ioJiC3QzNZMJ5cvED1bwZc77G', 'ali.brahimi@universite.dz', 'Ali', 'Brahimi', 'TEACHER', true),
(4, 'prof.sara', '$2a$12$FUzYd2A0DFomrNtQE9dy1.neF8r5ioJiC3QzNZMJ5cvED1bwZc77G', 'sara.kaci@universite.dz', 'Sara', 'Kaci', 'TEACHER', true),
(5, 'etudiant.1', '$2a$12$nPX0bN2NDcz4KehIuw3Pl.xN9iJDep/rfE9/WU83A68H3V.ZMVC6e', 'ahmed.meziane@etudiant.dz', 'Ahmed', 'Meziane', 'STUDENT', true),
(6, 'etudiant.2', '$2a$12$nPX0bN2NDcz4KehIuw3Pl.xN9iJDep/rfE9/WU83A68H3V.ZMVC6e', 'fatima.ait@etudiant.dz', 'Fatima', 'Ait Yahia', 'STUDENT', true),
(7, 'etudiant.3', '$2a$12$nPX0bN2NDcz4KehIuw3Pl.xN9iJDep/rfE9/WU83A68H3V.ZMVC6e', 'karim.bensalem@etudiant.dz', 'Karim', 'Bensalem', 'STUDENT', true)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO filieres (id, nom, code, description, chef_filiere_id)
VALUES (1, 'Informatique', 'INFO', 'Licence en Sciences Informatiques', 2)
ON DUPLICATE KEY UPDATE nom = VALUES(nom);

INSERT INTO classes (id, nom, niveau, annee_academique, filiere_id)
VALUES
(1, 'L3 Info A', 'L3', '2024-2025', 1),
(2, 'L3 Info B', 'L3', '2024-2025', 1),
(3, 'L2 Info', 'L2', '2024-2025', 1)
ON DUPLICATE KEY UPDATE nom = VALUES(nom);

INSERT INTO teachers (id, user_id, specialite, grade, bureau)
VALUES
(1, 3, 'Algorithmique et Structures de Donnees', 'Maitre de Conferences A', 'Bureau 12'),
(2, 4, 'Genie Logiciel et Base de Donnees', 'Maitre Assistant A', 'Bureau 08')
ON DUPLICATE KEY UPDATE specialite = VALUES(specialite);

INSERT INTO modules (id, nom, code, coefficient, volume_horaire, semestre, filiere_id, teacher_id)
VALUES
(1, 'Algorithmique Avancee', 'ALG301', 3, 45, 'S1', 1, 3),
(2, 'Base de Donnees', 'BDD301', 3, 45, 'S1', 1, 4),
(3, 'Reseaux Informatiques', 'RES301', 2, 30, 'S1', 1, 3),
(4, 'Genie Logiciel', 'GL301', 3, 45, 'S2', 1, 4),
(5, 'Intelligence Artificielle', 'IA301', 2, 30, 'S2', 1, 3)
ON DUPLICATE KEY UPDATE nom = VALUES(nom);

INSERT INTO students (id, matricule, user_id, classe_id, date_naissance)
VALUES
(1, '2024INFO001', 5, 1, '2002-03-15'),
(2, '2024INFO002', 6, 1, '2001-11-22'),
(3, '2024INFO003', 7, 2, '2002-07-08')
ON DUPLICATE KEY UPDATE matricule = VALUES(matricule);

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
(3, 3, 12.5, 11.5, 11.9, 'S1', '2024-2025')
ON DUPLICATE KEY UPDATE note_final = VALUES(note_final);

INSERT INTO absences (student_id, module_id, date_absence, nombre_heures, justifiee, motif)
VALUES
(1, 1, '2024-10-15', 3, false, NULL),
(1, 3, '2024-11-02', 2, true, 'Maladie - Certificat medical'),
(2, 2, '2024-10-28', 3, false, NULL),
(3, 1, '2024-10-10', 3, false, NULL),
(3, 1, '2024-11-05', 3, false, NULL),
(3, 3, '2024-11-20', 2, true, 'Participation a une conference');

INSERT INTO emplois_du_temps (jour, heure_debut, heure_fin, module_id, teacher_id, classe_id, filiere_id, salle, valide)
VALUES
('LUNDI', '08:30:00', '10:30:00', 1, 3, 1, 1, 'B12', true),
('LUNDI', '10:45:00', '12:30:00', 2, 4, 1, 1, 'B12', true),
('MARDI', '09:00:00', '11:00:00', 3, 3, 1, 1, 'LAB-3', true),
('JEUDI', '08:30:00', '10:30:00', 1, 3, 2, 1, 'A06', true),
('VENDREDI', '14:00:00', '16:00:00', 2, 4, 2, 1, 'A06', true);

INSERT INTO course_contents (title, description, file_path, module_id, teacher_id, classe_id, filiere_id)
VALUES
('Introduction au module', 'Support de cours introductif et objectifs pedagogiques.', NULL, 1, 3, 1, 1),
('Serie d''exercices S1', 'Exercices d''entrainement pour evaluation continue.', NULL, 2, 4, NULL, 1);

INSERT INTO announcements (title, message, author_id, target_classe_id, target_filiere_id)
VALUES
('Bienvenue sur GestionEtu', 'Consultez regulierement les cours et les annonces de votre filiere.', 3, NULL, 1),
('Rappel - Evaluation continue', 'La remise des TP est prevue la semaine prochaine.', 4, 1, 1);

INSERT INTO assignments (id, title, description, due_date, teacher_id, module_id, target_classe_id, target_filiere_id, published)
VALUES
(1, 'TP SQL - Requetes avancees', 'Ecrire les requetes demandees dans l''enonce et remettre un fichier SQL.', '2026-12-15 23:59:00', 3, 1, 1, NULL, true),
(2, 'Mini projet UML', 'Concevoir le diagramme de classes et le diagramme de sequence du systeme propose.', '2026-12-10 20:00:00', 4, 2, NULL, 1, true),
(3, 'Compte rendu de TP reseaux', 'Remettre un rapport PDF de 3 pages sur le TP reseaux.', '2026-03-28 23:59:00', 3, 3, 1, NULL, true)
ON DUPLICATE KEY UPDATE
title = VALUES(title),
description = VALUES(description),
due_date = VALUES(due_date),
published = VALUES(published);

INSERT INTO assignment_submissions (id, assignment_id, student_id, submission_text, submitted_at, late_submission, score, feedback, status)
VALUES
(1, 1, 1, 'Travail rendu avec script SQL et captures d''ecran.', '2026-12-14 19:00:00', false, NULL, 'Bon travail. Pensez a optimiser certaines requetes.', 'REVIEWED'),
(2, 3, 1, 'Rapport remis apres la date limite.', '2026-03-29 11:30:00', true, 13.5, 'Contenu correct, mais rendu tardif.', 'GRADED'),
(3, 2, 2, 'Diagrammes UML fournis en PDF.', '2026-12-09 18:20:00', false, NULL, NULL, 'SUBMITTED')
ON DUPLICATE KEY UPDATE
submission_text = VALUES(submission_text),
submitted_at = VALUES(submitted_at),
late_submission = VALUES(late_submission),
score = VALUES(score),
feedback = VALUES(feedback),
status = VALUES(status);

-- Indexes
CREATE INDEX idx_notes_student ON notes(student_id);
CREATE INDEX idx_notes_module ON notes(module_id);
CREATE INDEX idx_absences_student ON absences(student_id);
CREATE INDEX idx_absences_module ON absences(module_id);
CREATE INDEX idx_students_classe ON students(classe_id);
CREATE INDEX idx_modules_filiere ON modules(filiere_id);
CREATE INDEX idx_modules_teacher ON modules(teacher_id);
CREATE INDEX idx_edt_classe ON emplois_du_temps(classe_id);
CREATE INDEX idx_course_filiere ON course_contents(filiere_id);
CREATE INDEX idx_announcement_filiere ON announcements(target_filiere_id);
CREATE INDEX idx_assignment_teacher ON assignments(teacher_id);
CREATE INDEX idx_assignment_due ON assignments(due_date);
CREATE INDEX idx_assignment_target_classe ON assignments(target_classe_id);
CREATE INDEX idx_assignment_target_filiere ON assignments(target_filiere_id);
CREATE INDEX idx_submission_assignment ON assignment_submissions(assignment_id);
CREATE INDEX idx_submission_student ON assignment_submissions(student_id);
CREATE INDEX idx_submission_file_submission ON assignment_submission_files(submission_id);
