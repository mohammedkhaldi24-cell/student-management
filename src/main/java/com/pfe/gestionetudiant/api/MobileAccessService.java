package com.pfe.gestionetudiant.api;

import com.pfe.gestionetudiant.model.Filiere;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.Teacher;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.TeacherRepository;
import com.pfe.gestionetudiant.service.FiliereService;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MobileAccessService {

    private final UserService userService;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final FiliereService filiereService;

    public User currentUser() {
        return userService.getCurrentUser();
    }

    public Student currentStudent() {
        User user = currentUser();
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Profil etudiant introuvable."));
    }

    public Teacher currentTeacher() {
        User user = currentUser();
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Profil enseignant introuvable."));
    }

    public Filiere currentChefFiliere() {
        User user = currentUser();
        return filiereService.findByChefFiliereId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Aucune filiere assignee."));
    }

    public String redirectPath(Role role) {
        return switch (role) {
            case ADMIN -> "/admin/dashboard";
            case CHEF_FILIERE -> "/chef/dashboard";
            case TEACHER -> "/teacher/dashboard";
            case STUDENT -> "/student/dashboard";
        };
    }
}
