package com.pfe.gestionetudiant.service;

import com.pfe.gestionetudiant.model.CourseContent;
import com.pfe.gestionetudiant.model.CourseDocument;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CourseContentService {

    CourseContent createCourse(String title,
                               String description,
                               MultipartFile file,
                               Long moduleId,
                               Long teacherId,
                               Long classeId,
                               Long filiereId);

    CourseContent createCourse(String title,
                               String description,
                               MultipartFile[] files,
                               Long moduleId,
                               Long teacherId,
                               Long classeId,
                               Long filiereId);

    void deleteCourse(Long courseId, Long teacherId);

    CourseContent replaceCourseFile(Long courseId, Long teacherId, MultipartFile file);

    CourseContent removeCourseFile(Long courseId, Long teacherId);

    CourseContent addCourseFiles(Long courseId, Long teacherId, MultipartFile[] files);

    CourseContent removeCourseFile(Long courseId, Long teacherId, Long fileId);

    Optional<CourseContent> findById(Long id);

    List<CourseContent> findByTeacherId(Long teacherId);

    List<CourseContent> findByFiliereId(Long filiereId);

    List<CourseContent> findForStudent(Long classeId, Long filiereId);

    Resource loadFileAsResource(CourseContent courseContent);

    Resource loadFileAsResource(CourseDocument courseDocument);

    List<CourseDocument> findFilesForCourse(Long courseId);

    CourseDocument findFileForCourse(Long courseId, Long fileId);
}
