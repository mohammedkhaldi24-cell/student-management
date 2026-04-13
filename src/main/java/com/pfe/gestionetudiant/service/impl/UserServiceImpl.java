package com.pfe.gestionetudiant.service.impl;

import com.pfe.gestionetudiant.dto.UserDto;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.Student;
import com.pfe.gestionetudiant.model.Teacher;
import com.pfe.gestionetudiant.model.User;
import com.pfe.gestionetudiant.repository.ClasseRepository;
import com.pfe.gestionetudiant.repository.FiliereRepository;
import com.pfe.gestionetudiant.repository.StudentRepository;
import com.pfe.gestionetudiant.repository.TeacherRepository;
import com.pfe.gestionetudiant.repository.UserRepository;
import com.pfe.gestionetudiant.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ClasseRepository classeRepository;
    private final FiliereRepository filiereRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User createUser(UserDto dto) {
        String username = dto.getUsername() != null ? dto.getUsername().trim() : null;
        String email = normalizeEmail(dto.getEmail());

        validateUniqueUsername(null, username);
        validateUniqueEmail(null, email);

        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire.");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(email);
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setRole(dto.getRole());
        user.setEnabled(dto.isEnabled());

        user = userRepository.save(user);
        createOrUpdateProfileForRole(user, dto);

        return user;
    }

    @Override
    public User updateUser(Long id, UserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        String username = dto.getUsername() != null ? dto.getUsername().trim() : null;
        String email = normalizeEmail(dto.getEmail());

        validateUniqueUsername(id, username);
        validateUniqueEmail(id, email);

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(email);
        user.setUsername(username);
        user.setEnabled(dto.isEnabled());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        user = userRepository.save(user);
        createOrUpdateProfileForRole(user, dto);

        return user;
    }

    private void createOrUpdateProfileForRole(User user, UserDto dto) {
        switch (user.getRole()) {
            case STUDENT -> {
                Student student = studentRepository.findByUserId(user.getId()).orElseGet(() -> {
                    Student s = new Student();
                    s.setUser(user);
                    return s;
                });

                if (dto.getMatricule() != null && !dto.getMatricule().isBlank()) {
                    student.setMatricule(dto.getMatricule().trim());
                } else if (student.getMatricule() == null || student.getMatricule().isBlank()) {
                    student.setMatricule(generateMatricule());
                }

                if (dto.getClasseId() != null) {
                    classeRepository.findById(dto.getClasseId()).ifPresent(student::setClasse);
                } else {
                    student.setClasse(null);
                }

                studentRepository.save(student);
            }
            case TEACHER -> {
                Teacher teacher = teacherRepository.findByUserId(user.getId()).orElseGet(() -> {
                    Teacher t = new Teacher();
                    t.setUser(user);
                    return t;
                });
                teacher.setSpecialite(dto.getSpecialite());
                teacher.setGrade(dto.getGrade());
                teacherRepository.save(teacher);
            }
            case CHEF_FILIERE -> assignChefFiliere(user, dto.getFiliereId());
            default -> clearChefAssignments(user.getId());
        }
    }

    private void validateUniqueUsername(Long currentUserId, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        }

        userRepository.findByUsername(username).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new IllegalArgumentException("Le nom d'utilisateur '" + username + "' est deja utilise.");
            }
        });
    }

    private void validateUniqueEmail(Long currentUserId, String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        userRepository.findByEmail(email).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new IllegalArgumentException("L'email '" + email + "' est deja utilise.");
            }
        });
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void assignChefFiliere(User user, Long filiereId) {
        clearChefAssignments(user.getId());

        if (filiereId == null) {
            return;
        }

        var filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new IllegalArgumentException("Filiere introuvable."));
        filiere.setChefFiliere(user);
        filiereRepository.save(filiere);
    }

    private void clearChefAssignments(Long userId) {
        for (var filiere : filiereRepository.findAllByChefFiliereId(userId)) {
            filiere.setChefFiliere(null);
            filiereRepository.save(filiere);
        }
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        userRepository.delete(user);
    }

    @Override
    public void toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouve"));
    }

    private String generateMatricule() {
        int year = java.time.LocalDate.now().getYear();
        long count = studentRepository.count() + 1;
        return String.format("%dETU%04d", year, count);
    }
}
