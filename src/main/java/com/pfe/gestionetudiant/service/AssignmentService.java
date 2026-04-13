package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.Assignment;
import com.pfe.gestionetudiant.model.Student;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AssignmentService {

    Assignment createAssignment(String title,
                                String description,
                                LocalDateTime dueDate,
                                MultipartFile attachment,
                                Long teacherId,
                                Long moduleId,
                                Long classeId,
                                Long filiereId,
                                boolean published);

    Assignment updateAssignment(Long assignmentId,
                                String title,
                                String description,
                                LocalDateTime dueDate,
                                MultipartFile attachment,
                                Long teacherId,
                                Long moduleId,
                                Long classeId,
                                Long filiereId,
                                boolean published);

    void deleteAssignment(Long assignmentId, Long teacherId);

    Assignment replaceAssignmentAttachment(Long assignmentId, Long teacherId, MultipartFile attachment);

    Assignment removeAssignmentAttachment(Long assignmentId, Long teacherId);

    Optional<Assignment> findById(Long assignmentId);

    Optional<Assignment> findByIdAndTeacher(Long assignmentId, Long teacherId);

    List<Assignment> findByTeacher(Long teacherId);

    List<Assignment> findVisibleForStudent(Long classeId, Long filiereId);

    Optional<Assignment> findVisibleByIdForStudent(Long assignmentId, Long classeId, Long filiereId);

    List<Student> getTargetStudents(Assignment assignment);

    Resource loadAssignmentAttachment(Assignment assignment);
}
