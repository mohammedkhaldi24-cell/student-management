package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.dto.UserDto;
import com.pfe.gestionetudiant.model.Role;
import com.pfe.gestionetudiant.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    User createUser(UserDto dto);

    User updateUser(Long id, UserDto dto);

    void deleteUser(Long id);

    void toggleUserStatus(Long id);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    List<User> findByRole(Role role);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User getCurrentUser();
}
