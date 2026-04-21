package com.pfe.gestionetudiantmobile.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ApiMessage(
    val message: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val authenticated: Boolean,
    val user: UserSummary?,
    val message: String
)

data class UserSummary(
    val id: Long,
    val username: String,
    val fullName: String,
    val email: String?,
    val role: String,
    val redirectPath: String
)

data class TopStudentItem(
    val studentId: Long?,
    val name: String,
    val average: Double
)

data class StudentProfile(
    val id: Long,
    val matricule: String,
    val fullName: String,
    val email: String?,
    val classe: String?,
    val filiere: String?,
    val telephone: String?,
    val adresse: String?,
    val dateNaissance: LocalDate?,
    val photoUrl: String?
)

data class TeacherProfile(
    val id: Long,
    val fullName: String,
    val email: String?,
    val grade: String?,
    val specialite: String?,
    val telephone: String?,
    val bureau: String?
)

data class NoteItem(
    val id: Long,
    val studentId: Long?,
    val studentName: String?,
    val matricule: String?,
    val moduleId: Long?,
    val moduleNom: String?,
    val moduleCode: String?,
    val coefficient: Int?,
    val noteCc: Double?,
    val noteExamen: Double?,
    val noteFinal: Double?,
    val semestre: String,
    val anneeAcademique: String,
    val statut: String,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val actorName: String? = null
)

data class AbsenceItem(
    val id: Long,
    val studentId: Long?,
    val studentName: String?,
    val matricule: String?,
    val moduleId: Long?,
    val moduleNom: String?,
    val moduleCode: String?,
    val dateAbsence: LocalDate,
    val nombreHeures: Int,
    val justifiee: Boolean,
    val motif: String?,
    val createdAt: LocalDateTime? = null,
    val actorName: String? = null
)

data class AcademicHistoryEvent(
    val key: String,
    val type: String,
    val title: String,
    val message: String,
    val moduleId: Long?,
    val moduleName: String?,
    val studentId: Long?,
    val studentName: String?,
    val actorName: String,
    val occurredAt: LocalDateTime?,
    val dateLabel: String,
    val badge: String,
    val pending: Boolean = false
)

data class TimetableItem(
    val id: Long,
    val jour: String,
    val heureDebut: LocalTime,
    val heureFin: LocalTime,
    val moduleId: Long?,
    val moduleNom: String?,
    val moduleCode: String?,
    val teacherId: Long?,
    val teacherName: String?,
    val classeId: Long?,
    val classeNom: String?,
    val filiereId: Long?,
    val filiereNom: String?,
    val salle: String,
    val valide: Boolean
)

data class CourseItem(
    val id: Long,
    val title: String,
    val description: String?,
    val filePath: String?,
    val fileName: String,
    val moduleId: Long?,
    val moduleNom: String?,
    val moduleCode: String?,
    val teacherId: Long?,
    val teacherName: String?,
    val classeId: Long?,
    val classeNom: String?,
    val filiereId: Long?,
    val filiereNom: String?,
    val createdAt: LocalDateTime?,
    val files: List<CourseDocumentItem>? = null
)

data class CourseDocumentItem(
    val id: Long? = null,
    val filePath: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val fileSize: Long? = null,
    val uploadedAt: LocalDateTime? = null
)

data class AnnouncementItem(
    val id: Long,
    val title: String,
    val message: String,
    val attachmentPath: String?,
    val attachmentName: String,
    val authorId: Long?,
    val authorName: String?,
    val targetClasseId: Long?,
    val targetClasseNom: String?,
    val targetFiliereId: Long?,
    val targetFiliereNom: String?,
    val createdAt: LocalDateTime?
)

data class AssignmentItem(
    val id: Long,
    val title: String,
    val description: String,
    val createdAt: LocalDateTime?,
    val dueDate: LocalDateTime,
    val attachmentPath: String?,
    val attachmentName: String,
    val teacherId: Long?,
    val teacherName: String?,
    val moduleId: Long?,
    val moduleNom: String?,
    val moduleCode: String?,
    val targetClasseId: Long?,
    val targetClasseNom: String?,
    val targetFiliereId: Long?,
    val targetFiliereNom: String?,
    val published: Boolean,
    val submissionStatus: String,
    val submittedAt: LocalDateTime?,
    val lateSubmission: Boolean,
    val score: Double?,
    val feedback: String?,
    val overdue: Boolean,
    val upcoming: Boolean
)

data class AssignmentSubmissionItem(
    val id: Long,
    val assignmentId: Long?,
    val assignmentTitle: String?,
    val studentId: Long?,
    val studentName: String?,
    val matricule: String?,
    val submissionText: String?,
    val filePath: String?,
    val fileName: String,
    val files: List<SubmissionFileItem> = emptyList(),
    val submittedAt: LocalDateTime?,
    val lateSubmission: Boolean,
    val score: Double?,
    val feedback: String?,
    val status: String
)

data class SubmissionFileItem(
    val id: Long?,
    val filePath: String?,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long?,
    val uploadedAt: LocalDateTime?
)

data class NotificationItem(
    val eventId: String? = null,
    val type: String,
    val title: String,
    val message: String,
    val createdAt: LocalDateTime?,
    val actionPath: String,
    val emailRelated: Boolean = false
) {
    fun stableKey(): String {
        return eventId?.trim()?.takeIf { it.isNotEmpty() }
            ?: listOf(type, title, createdAt?.toString().orEmpty(), actionPath).joinToString("|")
    }
}

data class StudentDashboard(
    val moyenneS1: Double,
    val moyenneS2: Double,
    val moyenneGenerale: Double,
    val totalAbsenceHours: Int,
    val totalNonJustifiedHours: Int,
    val overdueAssignmentsCount: Long,
    val upcomingAssignments: List<AssignmentItem>,
    val recentAnnouncements: List<AnnouncementItem>,
    val recentCourses: List<CourseItem>,
    val upcomingSessions: List<TimetableItem>,
    val notifications: List<NotificationItem>
)

data class TeacherDashboard(
    val totalModules: Long,
    val totalStudents: Long,
    val totalCourses: Long,
    val totalAnnouncements: Long,
    val totalAssignments: Long,
    val pendingSubmissions: Long,
    val recentAssignments: List<AssignmentItem>,
    val recentCourses: List<CourseItem>,
    val recentAnnouncements: List<AnnouncementItem>
)

data class AdminDashboard(
    val totalUsers: Long,
    val totalStudents: Long,
    val totalTeachers: Long,
    val totalChefs: Long,
    val totalFilieres: Long,
    val totalClasses: Long,
    val totalModules: Long,
    val totalNotes: Long,
    val totalAbsences: Long
)

data class ChefDashboard(
    val filiereNom: String,
    val totalClasses: Long,
    val totalStudents: Long,
    val totalCourses: Long,
    val totalAnnouncements: Long,
    val totalAbsences: Long
)

data class TeacherModuleItem(
    val id: Long,
    val nom: String,
    val code: String,
    val coefficient: Int,
    val volumeHoraire: Int?,
    val semestre: String,
    val filiereId: Long?,
    val filiereNom: String?,
    val teacherId: Long?,
    val teacherName: String?
)

data class StudentModuleItem(
    val id: Long,
    val nom: String,
    val code: String,
    val semestre: String?,
    val volumeHoraire: Int? = null,
    val teacherId: Long?,
    val teacherName: String?,
    val classeId: Long?,
    val classeNom: String?,
    val filiereId: Long?,
    val filiereNom: String?
)

data class ClasseItem(
    val id: Long,
    val nom: String,
    val filiereId: Long?,
    val filiereNom: String?
)

data class NoteUpsertRequest(
    val studentId: Long,
    val moduleId: Long,
    val semestre: String,
    val anneeAcademique: String,
    val noteCc: Double?,
    val noteExamen: Double?
)

data class NoteBulkItem(
    val studentId: Long,
    val noteCc: Double?,
    val noteExamen: Double?
)

data class NoteBulkRequest(
    val moduleId: Long,
    val semestre: String,
    val anneeAcademique: String,
    val notes: List<NoteBulkItem>
)

data class AbsenceCreateRequest(
    val studentId: Long,
    val moduleId: Long,
    val dateAbsence: String?,
    val nombreHeures: Int?
)

data class AbsenceSessionRequest(
    val moduleId: Long,
    val classeId: Long?,
    val dateAbsence: String,
    val nombreHeures: Int,
    val absentStudentIds: List<Long>
)

data class AbsenceSessionResponse(
    val message: String,
    val absences: List<AbsenceItem>
)

data class AnnouncementCreateRequest(
    val title: String,
    val message: String,
    val classeId: Long?,
    val filiereId: Long?,
    val moduleId: Long? = null
)

data class SubmissionReviewRequest(
    val score: Double?,
    val feedback: String?,
    val status: String?
)

data class AdminUserUpsertRequest(
    val username: String,
    val password: String? = null,
    val email: String? = null,
    val firstName: String,
    val lastName: String,
    val role: String,
    val enabled: Boolean = true,
    val matricule: String? = null,
    val classeId: Long? = null,
    val specialite: String? = null,
    val grade: String? = null,
    val filiereId: Long? = null
)

data class AdminFiliereUpsertRequest(
    val nom: String,
    val code: String,
    val description: String? = null,
    val chefFiliereId: Long? = null
)

data class AdminClasseUpsertRequest(
    val nom: String,
    val niveau: String,
    val anneeAcademique: String,
    val filiereId: Long
)

data class AdminModuleUpsertRequest(
    val nom: String,
    val code: String,
    val coefficient: Int = 1,
    val volumeHoraire: Int? = 30,
    val semestre: String,
    val filiereId: Long,
    val teacherId: Long? = null
)

data class AdminTimetableUpsertRequest(
    val jour: String,
    val heureDebut: String,
    val heureFin: String,
    val moduleId: Long,
    val classeId: Long,
    val filiereId: Long,
    val teacherId: Long? = null,
    val salle: String,
    val valide: Boolean = true
)
