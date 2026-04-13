package com.pfe.gestionetudiant.config;

import com.pfe.gestionetudiant.model.Absence;
import com.pfe.gestionetudiant.model.Announcement;
import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.AssignmentSubmission;
import com.pfe.gestionetudiant.model.Classe;
import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.EmploiDuTemps;
import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Note;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.SubmissionStatus;
import com.pfe.gestionetudiant.model.Teacher;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.AbsenceRepository;
import com.pfe.gestionetudiant.repository.AnnouncementRepository;
import com.pfe.gestionetudiant.repository.AssignmentRepository;
import com.pfe.gestionetudiant.repository.AssignmentSubmissionRepository;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.repository.CourseContentRepository;
import com.pfe.gestionetudiant.repository.EmploiDuTempsRepository;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.repository.ModuleRepository;
import com.pfe.gestionetudiant.repository.NoteRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.TeacherRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final FiliereRepository filiereRepository;
    private final ClasseRepository classeRepository;
    private final ModuleRepository moduleRepository;
    private final NoteRepository noteRepository;
    private final AbsenceRepository absenceRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final CourseContentRepository courseContentRepository;
    private final AnnouncementRepository announcementRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            seedExtendedData();
            return;
        }

        createUser("admin", "admin123", "admin@universite.dz", "Super", "Admin", Role.ADMIN);
        User chef = createUser("chef.info", "chef123", "chef.info@universite.dz", "Mohammed", "Benali", Role.CHEF_FILIERE);
        User profAli = createUser("prof.ali", "teacher123", "ali.brahimi@universite.dz", "Ali", "Brahimi", Role.TEACHER);
        User profSara = createUser("prof.sara", "teacher123", "sara.kaci@universite.dz", "Sara", "Kaci", Role.TEACHER);
        User etu1User = createUser("etudiant.1", "student123", "ahmed.meziane@etudiant.dz", "Ahmed", "Meziane", Role.STUDENT);
        User etu2User = createUser("etudiant.2", "student123", "fatima.ait@etudiant.dz", "Fatima", "Ait Yahia", Role.STUDENT);
        User etu3User = createUser("etudiant.3", "student123", "karim.bensalem@etudiant.dz", "Karim", "Bensalem", Role.STUDENT);

        teacherRepository.save(buildTeacher(profAli, "Algorithmique et Structures de Donnees", "Maitre de Conferences A", "Bureau 12"));
        teacherRepository.save(buildTeacher(profSara, "Genie Logiciel et Base de Donnees", "Maitre Assistant A", "Bureau 08"));

        Filiere info = new Filiere();
        info.setNom("Informatique");
        info.setCode("INFO");
        info.setDescription("Licence en Sciences Informatiques");
        info.setChefFiliere(chef);
        info = filiereRepository.save(info);

        Classe l3a = new Classe();
        l3a.setNom("L3 Info A");
        l3a.setNiveau("L3");
        l3a.setAnneeAcademique("2024-2025");
        l3a.setFiliere(info);
        l3a = classeRepository.save(l3a);

        Classe l3b = new Classe();
        l3b.setNom("L3 Info B");
        l3b.setNiveau("L3");
        l3b.setAnneeAcademique("2024-2025");
        l3b.setFiliere(info);
        l3b = classeRepository.save(l3b);

        Classe l2 = new Classe();
        l2.setNom("L2 Info");
        l2.setNiveau("L2");
        l2.setAnneeAcademique("2024-2025");
        l2.setFiliere(info);
        l2 = classeRepository.save(l2);

        com.pfe.gestionetudiant.model.Module m1 = buildModule("Algorithmique Avancee", "ALG301", 3, 45, "S1", info, profAli);
        com.pfe.gestionetudiant.model.Module m2 = buildModule("Base de Donnees", "BDD301", 3, 45, "S1", info, profSara);
        com.pfe.gestionetudiant.model.Module m3 = buildModule("Reseaux Informatiques", "RES301", 2, 30, "S1", info, profAli);
        com.pfe.gestionetudiant.model.Module m4 = buildModule("Genie Logiciel", "GL301", 3, 45, "S2", info, profSara);
        com.pfe.gestionetudiant.model.Module m5 = buildModule("Intelligence Artificielle", "IA301", 2, 30, "S2", info, profAli);
        m1 = moduleRepository.save(m1);
        m2 = moduleRepository.save(m2);
        m3 = moduleRepository.save(m3);
        m4 = moduleRepository.save(m4);
        m5 = moduleRepository.save(m5);

        Student etu1 = buildStudent("2024INFO001", etu1User, l3a, LocalDate.of(2002, 3, 15));
        Student etu2 = buildStudent("2024INFO002", etu2User, l3a, LocalDate.of(2001, 11, 22));
        Student etu3 = buildStudent("2024INFO003", etu3User, l3b, LocalDate.of(2002, 7, 8));
        etu1 = studentRepository.save(etu1);
        etu2 = studentRepository.save(etu2);
        etu3 = studentRepository.save(etu3);

        noteRepository.save(buildNote(etu1, m1, 14.5, 16.0, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu1, m2, 12.0, 13.5, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu1, m3, 15.0, 14.0, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu2, m1, 11.0, 10.5, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu2, m2, 16.0, 17.5, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu2, m3, 13.5, 12.0, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu3, m1, 9.5, 10.0, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu3, m2, 14.0, 13.5, "S1", "2024-2025"));
        noteRepository.save(buildNote(etu3, m3, 12.5, 11.5, "S1", "2024-2025"));

        absenceRepository.save(buildAbsence(etu1, m1, LocalDate.of(2024, 10, 15), 3, false, null));
        absenceRepository.save(buildAbsence(etu1, m3, LocalDate.of(2024, 11, 2), 2, true, "Maladie - Certificat medical"));
        absenceRepository.save(buildAbsence(etu2, m2, LocalDate.of(2024, 10, 28), 3, false, null));
        absenceRepository.save(buildAbsence(etu3, m1, LocalDate.of(2024, 10, 10), 3, false, null));
        absenceRepository.save(buildAbsence(etu3, m1, LocalDate.of(2024, 11, 5), 3, false, null));
        absenceRepository.save(buildAbsence(etu3, m3, LocalDate.of(2024, 11, 20), 2, true, "Participation a une conference"));

        seedExtendedData();
    }

    private void seedExtendedData() {
        if (filiereRepository.count() == 0 || classeRepository.count() == 0 || moduleRepository.count() == 0) {
            return;
        }

        Filiere filiere = filiereRepository.findAll().stream().findFirst().orElse(null);
        if (filiere == null) {
            return;
        }

        List<Classe> classes = classeRepository.findByFiliereId(filiere.getId());
        List<com.pfe.gestionetudiant.model.Module> modules = moduleRepository.findByFiliereId(filiere.getId());
        if (classes.isEmpty() || modules.isEmpty()) {
            return;
        }

        User teacher = modules.stream()
                .map(com.pfe.gestionetudiant.model.Module::getTeacher)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> userRepository.findByRole(Role.TEACHER).stream().findFirst().orElse(null));
        if (teacher == null) {
            return;
        }

        if (emploiDuTempsRepository.count() == 0) {
            Classe c1 = classes.get(0);
            Classe c2 = classes.size() > 1 ? classes.get(1) : classes.get(0);

            emploiDuTempsRepository.save(buildSeance("LUNDI", LocalTime.of(8, 30), LocalTime.of(10, 30), modules.get(0), teacher, c1, filiere, "B12"));
            emploiDuTempsRepository.save(buildSeance("LUNDI", LocalTime.of(10, 45), LocalTime.of(12, 30), modules.get(Math.min(1, modules.size() - 1)), teacher, c1, filiere, "B12"));
            emploiDuTempsRepository.save(buildSeance("MARDI", LocalTime.of(9, 0), LocalTime.of(11, 0), modules.get(Math.min(2, modules.size() - 1)), teacher, c1, filiere, "LAB-3"));
            emploiDuTempsRepository.save(buildSeance("JEUDI", LocalTime.of(8, 30), LocalTime.of(10, 30), modules.get(0), teacher, c2, filiere, "A06"));
            emploiDuTempsRepository.save(buildSeance("VENDREDI", LocalTime.of(14, 0), LocalTime.of(16, 0), modules.get(Math.min(1, modules.size() - 1)), teacher, c2, filiere, "A06"));
        }

        if (courseContentRepository.count() == 0) {
            CourseContent c1 = new CourseContent();
            c1.setTitle("Introduction au module");
            c1.setDescription("Support de cours introductif et objectifs pedagogiques.");
            c1.setModule(modules.get(0));
            c1.setTeacher(teacher);
            c1.setClasse(classes.get(0));
            c1.setFiliere(filiere);
            courseContentRepository.save(c1);

            CourseContent c2 = new CourseContent();
            c2.setTitle("Serie d'exercices S1");
            c2.setDescription("Exercices d'entrainement pour evaluation continue.");
            c2.setModule(modules.get(Math.min(1, modules.size() - 1)));
            c2.setTeacher(teacher);
            c2.setFiliere(filiere);
            courseContentRepository.save(c2);
        }

        if (announcementRepository.count() == 0) {
            Announcement a1 = new Announcement();
            a1.setTitle("Bienvenue sur GestionEtu");
            a1.setMessage("Consultez regulierement les cours et les annonces de votre filiere.");
            a1.setAuthor(teacher);
            a1.setTargetFiliere(filiere);
            announcementRepository.save(a1);

            Announcement a2 = new Announcement();
            a2.setTitle("Rappel - Evaluation continue");
            a2.setMessage("La remise des TP est prevue la semaine prochaine.");
            a2.setAuthor(teacher);
            a2.setTargetClasse(classes.get(0));
            a2.setTargetFiliere(filiere);
            announcementRepository.save(a2);
        }

        seedAssignments(classes, modules, teacher, filiere);
    }

    private User createUser(String username, String rawPassword, String email,
                            String firstName, String lastName, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private Teacher buildTeacher(User user, String specialite, String grade, String bureau) {
        Teacher teacher = new Teacher();
        teacher.setUser(user);
        teacher.setSpecialite(specialite);
        teacher.setGrade(grade);
        teacher.setBureau(bureau);
        return teacher;
    }

    private Student buildStudent(String matricule, User user, Classe classe, LocalDate dateNaissance) {
        Student student = new Student();
        student.setMatricule(matricule);
        student.setUser(user);
        student.setClasse(classe);
        student.setDateNaissance(dateNaissance);
        return student;
    }

    private com.pfe.gestionetudiant.model.Module buildModule(String nom, String code, int coeff, int volumeHoraire,
                                                             String semestre, Filiere filiere, User teacher) {
        com.pfe.gestionetudiant.model.Module module = new com.pfe.gestionetudiant.model.Module();
        module.setNom(nom);
        module.setCode(code);
        module.setCoefficient(coeff);
        module.setVolumeHoraire(volumeHoraire);
        module.setSemestre(semestre);
        module.setFiliere(filiere);
        module.setTeacher(teacher);
        return module;
    }

    private Note buildNote(Student student, com.pfe.gestionetudiant.model.Module module,
                           double noteCC, double noteExamen, String semestre, String anneeAcademique) {
        Note note = new Note();
        note.setStudent(student);
        note.setModule(module);
        note.setNoteCC(noteCC);
        note.setNoteExamen(noteExamen);
        note.setSemestre(semestre);
        note.setAnneeAcademique(anneeAcademique);
        note.calculerNoteFinal();
        return note;
    }

    private Absence buildAbsence(Student student, com.pfe.gestionetudiant.model.Module module,
                                 LocalDate dateAbsence, int heures, boolean justifiee, String motif) {
        Absence absence = new Absence();
        absence.setStudent(student);
        absence.setModule(module);
        absence.setDateAbsence(dateAbsence);
        absence.setNombreHeures(heures);
        absence.setJustifiee(justifiee);
        absence.setMotif(motif);
        return absence;
    }

    private EmploiDuTemps buildSeance(String jour,
                                      LocalTime debut,
                                      LocalTime fin,
                                      com.pfe.gestionetudiant.model.Module module,
                                      User teacher,
                                      Classe classe,
                                      Filiere filiere,
                                      String salle) {
        EmploiDuTemps e = new EmploiDuTemps();
        e.setJour(jour);
        e.setHeureDebut(debut);
        e.setHeureFin(fin);
        e.setModule(module);
        e.setTeacher(teacher);
        e.setClasse(classe);
        e.setFiliere(filiere);
        e.setSalle(salle);
        e.setValide(true);
        return e;
    }

    private void seedAssignments(List<Classe> classes,
                                 List<com.pfe.gestionetudiant.model.Module> modules,
                                 User teacher,
                                 Filiere filiere) {
        if (assignmentRepository.count() > 0 || classes.isEmpty() || modules.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Classe classe = classes.get(0);

        Assignment a1 = new Assignment();
        a1.setTitle("TP SQL - Requetes avancees");
        a1.setDescription("Ecrire les requetes demandees dans l'enonce et remettre un fichier SQL.");
        a1.setDueDate(now.plusDays(4));
        a1.setTeacher(teacher);
        a1.setModule(modules.get(0));
        a1.setTargetClasse(classe);
        a1.setTargetFiliere(null);
        a1.setPublished(true);
        a1 = assignmentRepository.save(a1);

        Assignment a2 = new Assignment();
        a2.setTitle("Mini projet UML");
        a2.setDescription("Concevoir le diagramme de classes et le diagramme de sequence du systeme propose.");
        a2.setDueDate(now.plusDays(1));
        a2.setTeacher(teacher);
        a2.setModule(modules.get(Math.min(1, modules.size() - 1)));
        a2.setTargetFiliere(filiere);
        a2.setPublished(true);
        a2 = assignmentRepository.save(a2);

        Assignment a3 = new Assignment();
        a3.setTitle("Compte rendu de TP reseaux");
        a3.setDescription("Remettre un rapport PDF de 3 pages sur le TP reseaux.");
        a3.setDueDate(now.minusDays(2));
        a3.setTeacher(teacher);
        a3.setModule(modules.get(Math.min(2, modules.size() - 1)));
        a3.setTargetClasse(classe);
        a3.setTargetFiliere(null);
        a3.setPublished(true);
        a3 = assignmentRepository.save(a3);

        List<Student> classStudents = studentRepository.findByClasseId(classe.getId());
        if (!classStudents.isEmpty()) {
            Student s1 = classStudents.get(0);
            AssignmentSubmission sub1 = new AssignmentSubmission();
            sub1.setAssignment(a1);
            sub1.setStudent(s1);
            sub1.setSubmissionText("Travail rendu avec script SQL et captures d'ecran.");
            sub1.setSubmittedAt(now.minusHours(6));
            sub1.setLateSubmission(false);
            sub1.setStatus(SubmissionStatus.REVIEWED);
            sub1.setFeedback("Bon travail. Pensez a optimiser certaines requetes.");
            assignmentSubmissionRepository.save(sub1);

            AssignmentSubmission sub2 = new AssignmentSubmission();
            sub2.setAssignment(a3);
            sub2.setStudent(s1);
            sub2.setSubmissionText("Rapport remis apres la date limite.");
            sub2.setSubmittedAt(now.minusDays(1));
            sub2.setLateSubmission(true);
            sub2.setStatus(SubmissionStatus.GRADED);
            sub2.setScore(13.5);
            sub2.setFeedback("Contenu correct, mais rendu tardif.");
            assignmentSubmissionRepository.save(sub2);
        }

        List<Student> filiereStudents = studentRepository.findByFiliereId(filiere.getId());
        if (filiereStudents.size() > 1) {
            Student s2 = filiereStudents.get(1);
            AssignmentSubmission sub3 = new AssignmentSubmission();
            sub3.setAssignment(a2);
            sub3.setStudent(s2);
            sub3.setSubmissionText("Diagrammes UML fournis en PDF.");
            sub3.setSubmittedAt(now.minusHours(2));
            sub3.setLateSubmission(false);
            sub3.setStatus(SubmissionStatus.SUBMITTED);
            assignmentSubmissionRepository.save(sub3);
        }
    }
}
