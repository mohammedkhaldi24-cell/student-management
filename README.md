# GestionEtu — Plateforme de Gestion des Notes et Absences

## Projet de Fin d'Études (PFE)

---

## Technologies utilisées

| Technologie   | Version   | Rôle                         |
|---------------|-----------|------------------------------|
| Java          | 17        | Langage principal             |
| Spring Boot   | 3.2.0     | Framework backend             |
| Spring Security | 6.x    | Authentification & Autorisation |
| Spring Data JPA | 3.x    | Couche d'accès aux données   |
| Hibernate     | 6.x       | ORM                          |
| MySQL         | 8.x       | Base de données               |
| Thymeleaf     | 3.x       | Moteur de templates HTML      |
| OpenPDF       | 1.3.30    | Génération de bulletins PDF   |
| Bootstrap     | 5.3       | Interface utilisateur         |
| Chart.js      | 4.4       | Graphiques statistiques       |
| Maven         | 3.x       | Gestion de dépendances        |

---

## Architecture MVC

```
src/main/java/com/pfe/gestionetudiant/
├── config/         → Spring Security, Handlers
├── controller/     → Couche C (MVC) - Controllers
├── model/          → Couche M (MVC) - Entités JPA
├── repository/     → Couche DAO - Spring Data JPA
├── service/        → Logique métier (interfaces + impl)
└── dto/            → Objets de transfert de données
```

---

## Prérequis

- Java JDK 17+
- MySQL 8.x
- Maven 3.8+
- IntelliJ IDEA (recommandé)
- Git

---

## ÉTAPES D'EXÉCUTION

### Étape 1 — Créer la base de données MySQL

```sql
-- Ouvrir MySQL Workbench ou la console MySQL et exécuter :
CREATE DATABASE gestion_etudiant CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Puis exécuter le script complet :
```bash
mysql -u root -p gestion_etudiant < src/main/resources/sql/init.sql
```

### Étape 2 — Configurer la connexion MySQL

Ouvrir `src/main/resources/application.properties` et modifier :
```properties
spring.datasource.username=root
spring.datasource.password=VOTRE_MOT_DE_PASSE_MYSQL
```

### Étape 3 — Compiler et lancer le projet

```bash
# Via Maven en ligne de commande :
cd student-management
mvn clean install
mvn spring-boot:run
```

**Ou via IntelliJ IDEA :**
1. File → Open → sélectionner le dossier `student-management`
2. IntelliJ détecte automatiquement le projet Maven
3. Attendre le téléchargement des dépendances
4. Clic droit sur `GestionEtudiantApplication.java` → Run

### Étape 4 — Accéder à l'application

Ouvrir un navigateur et aller à :
```
http://localhost:8080
```

---

## COMPTES DE DÉMONSTRATION

| Rôle         | Identifiant   | Mot de passe  | Accès                         |
|--------------|---------------|---------------|-------------------------------|
| ADMIN        | `admin`       | `admin123`    | Gestion complète              |
| CHEF_FILIERE | `chef.info`   | `chef123`     | Filière Informatique          |
| ENSEIGNANT   | `prof.ali`    | `teacher123`  | Modules assignés              |
| ENSEIGNANT   | `prof.sara`   | `teacher123`  | Modules assignés              |
| ÉTUDIANT     | `etudiant.1`  | `student123`  | Mon espace                    |
| ÉTUDIANT     | `etudiant.2`  | `student123`  | Mon espace                    |
| ÉTUDIANT     | `etudiant.3`  | `student123`  | Mon espace                    |

---

## FONCTIONNALITÉS PAR RÔLE

### ADMIN (`/admin/...`)
- Dashboard avec statistiques globales
- Gestion des utilisateurs (CRUD complet)
- Gestion des filières
- Gestion des classes
- Gestion des modules (matières)
- Affectation des enseignants aux modules

### CHEF DE FILIÈRE (`/chef/...`)
- Dashboard avec graphiques Chart.js (moyennes, absences)
- Liste des étudiants de sa filière
- Vue des notes globales par classe
- Génération de bulletins PDF pour chaque étudiant

### ENSEIGNANT (`/teacher/...`)
- Dashboard avec ses modules
- Saisie/modification/suppression des notes (CC + Examen)
- Calcul automatique de la note finale en temps réel
- Marquage des absences par séance
- Justification des absences

### ÉTUDIANT (`/student/...`)
- Vue de ses notes par semestre
- Calcul automatique de la moyenne pondérée
- Vue de ses absences (justifiées / non justifiées)
- Téléchargement de ses bulletins PDF (S1 et S2)

---

## FORMULE DE CALCUL DE LA MOYENNE

```
Note Finale = (Note CC × 40%) + (Note Examen × 60%)

Moyenne Générale = Σ(Note_Finale_i × Coefficient_i) / Σ(Coefficient_i)
```

---

## STRUCTURE DES FICHIERS CLÉS

```
student-management/
├── pom.xml                                    ← Dépendances Maven
├── src/main/resources/
│   ├── application.properties                 ← Configuration BD, serveur
│   ├── sql/init.sql                           ← Script SQL complet
│   ├── static/
│   │   ├── css/style.css                      ← Styles personnalisés
│   │   └── js/app.js                          ← Scripts JavaScript
│   └── templates/
│       ├── auth/login.html                    ← Page de connexion
│       ├── fragments/layout.html              ← Fragments réutilisables
│       ├── admin/                             ← Templates ADMIN
│       ├── chef/                              ← Templates CHEF FILIÈRE
│       ├── teacher/                           ← Templates ENSEIGNANT
│       └── student/                           ← Templates ÉTUDIANT
└── src/main/java/com/pfe/gestionetudiant/
    ├── GestionEtudiantApplication.java        ← Point d'entrée
    ├── config/
    │   ├── SecurityConfig.java                ← Configuration sécurité
    │   ├── CustomUserDetailsService.java      ← Authentification BD
    │   └── CustomAuthSuccessHandler.java      ← Redirection après login
    ├── model/                                 ← Entités JPA (8 classes)
    ├── repository/                            ← Interfaces Spring Data (8)
    ├── service/                               ← Interfaces + Impl (7)
    ├── controller/                            ← Controllers (5)
    └── dto/                                   ← DTOs (3)
```

---

## DIAGRAMME UML — CLASSES PRINCIPALES

```
User ──────────── Role (enum)
  │
  ├── Student ──── Classe ──── Filiere ──── Module ──── Note
  │                                    └── Absence
  ├── Teacher
  └── (Admin/ChefFiliere via role)
```

---

## DIAGRAMME UML — CAS D'UTILISATION

```
┌─────────────────────────────────────────────────────┐
│                  Système GestionEtu                  │
│                                                     │
│  ADMIN        ───► Gérer Utilisateurs               │
│               ───► Gérer Filières/Classes/Modules   │
│               ───► Affecter Enseignants             │
│                                                     │
│  CHEF_FILIERE ───► Voir Statistiques                │
│               ───► Voir Notes Globales              │
│               ───► Générer Bulletins PDF            │
│                                                     │
│  TEACHER      ───► Saisir/Modifier Notes            │
│               ───► Marquer Absences                 │
│               ───► Voir Liste Étudiants             │
│                                                     │
│  STUDENT      ───► Voir Ses Notes                   │
│               ───► Voir Ses Absences                │
│               ───► Télécharger Bulletin PDF         │
└─────────────────────────────────────────────────────┘
```

---

## RÉSOLUTION DES ERREURS COURANTES

| Erreur | Solution |
|--------|----------|
| `Access denied for user 'root'@'localhost'` | Vérifier le mot de passe dans `application.properties` |
| `Unknown database 'gestion_etudiant'` | Créer la base de données (Étape 1) |
| Port 8080 déjà utilisé | Changer `server.port=8081` dans `application.properties` |
| `Lombok` non reconnu | Activer Annotation Processing dans IntelliJ : Settings → Build → Compiler → Annotation Processors |

---

*Projet PFE — Plateforme Web de Gestion des Notes et Absences*
