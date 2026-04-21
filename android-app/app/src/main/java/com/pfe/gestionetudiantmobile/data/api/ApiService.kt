package com.pfe.gestionetudiantmobile.data.api

import com.pfe.gestionetudiantmobile.data.model.AbsenceCreateRequest
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AbsenceSessionRequest
import com.pfe.gestionetudiantmobile.data.model.AbsenceSessionResponse
import com.pfe.gestionetudiantmobile.data.model.AdminClasseUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminDashboard
import com.pfe.gestionetudiantmobile.data.model.AdminFiliereUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminModuleUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminTimetableUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AdminUserUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.AuthResponse
import com.pfe.gestionetudiantmobile.data.model.ChefDashboard
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.LoginRequest
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.NoteBulkRequest
import com.pfe.gestionetudiantmobile.data.model.NotificationItem
import com.pfe.gestionetudiantmobile.data.model.NoteUpsertRequest
import com.pfe.gestionetudiantmobile.data.model.StudentDashboard
import com.pfe.gestionetudiantmobile.data.model.StudentModuleItem
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.SubmissionReviewRequest
import com.pfe.gestionetudiantmobile.data.model.TeacherDashboard
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TeacherProfile
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.model.TopStudentItem
import com.pfe.gestionetudiantmobile.data.model.UserSummary
import com.pfe.gestionetudiantmobile.util.MobileApiConfig
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {

    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>

    @POST(MobileApiConfig.LOGIN_PATH)
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/mobile/auth/me")
    suspend fun me(): Response<AuthResponse>

    @POST("api/mobile/auth/logout")
    suspend fun logout(): Response<ApiMessage>

    @GET("api/mobile/student/profile")
    suspend fun studentProfile(): Response<StudentProfile>

    @GET("api/mobile/student/dashboard")
    suspend fun studentDashboard(): Response<StudentDashboard>

    @GET("api/mobile/student/modules")
    suspend fun studentModules(): Response<List<StudentModuleItem>>

    @GET("api/mobile/student/notes")
    suspend fun studentNotes(@Query("moduleId") moduleId: Long? = null): Response<List<NoteItem>>

    @GET("api/mobile/student/absences")
    suspend fun studentAbsences(@Query("moduleId") moduleId: Long? = null): Response<List<AbsenceItem>>

    @GET("api/mobile/student/timetable")
    suspend fun studentTimetable(): Response<List<TimetableItem>>

    @GET("api/mobile/student/courses")
    suspend fun studentCourses(@Query("moduleId") moduleId: Long? = null): Response<List<CourseItem>>

    @GET("api/mobile/student/announcements")
    suspend fun studentAnnouncements(): Response<List<AnnouncementItem>>

    @GET("api/mobile/student/notifications")
    suspend fun studentNotifications(): Response<List<NotificationItem>>

    @GET("api/mobile/student/assignments")
    suspend fun studentAssignments(
        @Query("filter") filter: String = "all",
        @Query("moduleId") moduleId: Long? = null
    ): Response<List<AssignmentItem>>

    @GET("api/mobile/student/assignments/{id}")
    suspend fun studentAssignmentDetail(@Path("id") assignmentId: Long): Response<AssignmentItem>

    @GET("api/mobile/student/assignments/{id}/submission")
    suspend fun studentAssignmentSubmission(@Path("id") assignmentId: Long): Response<AssignmentSubmissionItem>

    @Multipart
    @POST("api/mobile/student/assignments/{id}/submit")
    suspend fun submitStudentAssignment(
        @Path("id") assignmentId: Long,
        @Part("submissionText") submissionText: RequestBody?,
        @Part files: List<MultipartBody.Part>?
    ): Response<AssignmentSubmissionItem>

    @DELETE("api/mobile/student/assignments/{id}/submission-files/{fileId}")
    suspend fun deleteStudentSubmissionFile(
        @Path("id") assignmentId: Long,
        @Path("fileId") fileId: Long
    ): Response<AssignmentSubmissionItem>

    @GET("api/mobile/teacher/profile")
    suspend fun teacherProfile(): Response<TeacherProfile>

    @GET("api/mobile/teacher/dashboard")
    suspend fun teacherDashboard(): Response<TeacherDashboard>

    @GET("api/mobile/teacher/modules")
    suspend fun teacherModules(): Response<List<TeacherModuleItem>>

    @GET("api/mobile/teacher/timetable")
    suspend fun teacherTimetable(): Response<List<TimetableItem>>

    @GET("api/mobile/teacher/classes")
    suspend fun teacherClasses(
        @Query("moduleId") moduleId: Long? = null,
        @Query("filiereId") filiereId: Long? = null
    ): Response<List<ClasseItem>>

    @GET("api/mobile/teacher/students")
    suspend fun teacherStudents(
        @Query("moduleId") moduleId: Long? = null,
        @Query("classeId") classeId: Long? = null,
        @Query("filiereId") filiereId: Long? = null,
        @Query("q") query: String? = null
    ): Response<List<StudentProfile>>

    @GET("api/mobile/teacher/notes")
    suspend fun teacherNotes(
        @Query("moduleId") moduleId: Long? = null,
        @Query("classeId") classeId: Long? = null,
        @Query("q") query: String? = null
    ): Response<List<NoteItem>>

    @POST("api/mobile/teacher/notes/upsert")
    suspend fun upsertTeacherNote(@Body request: NoteUpsertRequest): Response<NoteItem>

    @POST("api/mobile/teacher/notes/bulk")
    suspend fun upsertTeacherNotesBulk(@Body request: NoteBulkRequest): Response<List<NoteItem>>

    @DELETE("api/mobile/teacher/notes/{id}")
    suspend fun deleteTeacherNote(@Path("id") noteId: Long): Response<ApiMessage>

    @GET("api/mobile/teacher/absences")
    suspend fun teacherAbsences(
        @Query("moduleId") moduleId: Long? = null,
        @Query("classeId") classeId: Long? = null,
        @Query("q") query: String? = null
    ): Response<List<AbsenceItem>>

    @POST("api/mobile/teacher/absences")
    suspend fun createTeacherAbsence(@Body request: AbsenceCreateRequest): Response<AbsenceItem>

    @POST("api/mobile/teacher/absences/session")
    suspend fun saveTeacherAbsenceSession(@Body request: AbsenceSessionRequest): Response<AbsenceSessionResponse>

    @POST("api/mobile/teacher/absences/{id}/justify")
    suspend fun justifyTeacherAbsence(
        @Path("id") absenceId: Long,
        @Query("motif") motif: String? = null
    ): Response<AbsenceItem>

    @DELETE("api/mobile/teacher/absences/{id}")
    suspend fun deleteTeacherAbsence(@Path("id") absenceId: Long): Response<ApiMessage>

    @GET("api/mobile/teacher/courses")
    suspend fun teacherCourses(@Query("moduleId") moduleId: Long? = null): Response<List<CourseItem>>

    @Multipart
    @POST("api/mobile/teacher/courses")
    suspend fun createTeacherCourse(
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody?,
        @Part("moduleId") moduleId: RequestBody,
        @Part("classeId") classeId: RequestBody?,
        @Part("filiereId") filiereId: RequestBody?,
        @Part files: List<MultipartBody.Part>?,
        @Part file: MultipartBody.Part?
    ): Response<CourseItem>

    @Multipart
    @POST("api/mobile/teacher/courses/{id}/files")
    suspend fun addTeacherCourseFiles(
        @Path("id") courseId: Long,
        @Part files: List<MultipartBody.Part>
    ): Response<CourseItem>

    @DELETE("api/mobile/teacher/courses/{id}")
    suspend fun deleteTeacherCourse(@Path("id") courseId: Long): Response<ApiMessage>

    @Multipart
    @PUT("api/mobile/teacher/courses/{id}/file")
    suspend fun replaceTeacherCourseFile(
        @Path("id") courseId: Long,
        @Part file: MultipartBody.Part
    ): Response<CourseItem>

    @DELETE("api/mobile/teacher/courses/{id}/file")
    suspend fun removeTeacherCourseFile(@Path("id") courseId: Long): Response<CourseItem>

    @GET("api/mobile/teacher/announcements")
    suspend fun teacherAnnouncements(): Response<List<AnnouncementItem>>

    @Multipart
    @POST("api/mobile/teacher/announcements")
    suspend fun createTeacherAnnouncement(
        @Part("title") title: RequestBody,
        @Part("message") message: RequestBody,
        @Part("moduleId") moduleId: RequestBody?,
        @Part("classeId") classeId: RequestBody?,
        @Part("filiereId") filiereId: RequestBody?,
        @Part attachment: MultipartBody.Part?
    ): Response<AnnouncementItem>

    @DELETE("api/mobile/teacher/announcements/{id}")
    suspend fun deleteTeacherAnnouncement(@Path("id") announcementId: Long): Response<ApiMessage>

    @Multipart
    @PUT("api/mobile/teacher/announcements/{id}/attachment")
    suspend fun replaceTeacherAnnouncementAttachment(
        @Path("id") announcementId: Long,
        @Part attachment: MultipartBody.Part
    ): Response<AnnouncementItem>

    @DELETE("api/mobile/teacher/announcements/{id}/attachment")
    suspend fun removeTeacherAnnouncementAttachment(@Path("id") announcementId: Long): Response<AnnouncementItem>

    @GET("api/mobile/teacher/assignments")
    suspend fun teacherAssignments(@Query("moduleId") moduleId: Long? = null): Response<List<AssignmentItem>>

    @Multipart
    @POST("api/mobile/teacher/assignments")
    suspend fun createTeacherAssignment(
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("dueDate") dueDate: RequestBody,
        @Part("moduleId") moduleId: RequestBody?,
        @Part("classeId") classeId: RequestBody?,
        @Part("filiereId") filiereId: RequestBody?,
        @Part("published") published: RequestBody,
        @Part attachment: MultipartBody.Part?
    ): Response<AssignmentItem>

    @Multipart
    @PUT("api/mobile/teacher/assignments/{id}")
    suspend fun updateTeacherAssignment(
        @Path("id") assignmentId: Long,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("dueDate") dueDate: RequestBody,
        @Part("moduleId") moduleId: RequestBody?,
        @Part("classeId") classeId: RequestBody?,
        @Part("filiereId") filiereId: RequestBody?,
        @Part("published") published: RequestBody,
        @Part attachment: MultipartBody.Part?
    ): Response<AssignmentItem>

    @DELETE("api/mobile/teacher/assignments/{id}")
    suspend fun deleteTeacherAssignment(@Path("id") assignmentId: Long): Response<ApiMessage>

    @Multipart
    @PUT("api/mobile/teacher/assignments/{id}/attachment")
    suspend fun replaceTeacherAssignmentAttachment(
        @Path("id") assignmentId: Long,
        @Part attachment: MultipartBody.Part
    ): Response<AssignmentItem>

    @DELETE("api/mobile/teacher/assignments/{id}/attachment")
    suspend fun removeTeacherAssignmentAttachment(@Path("id") assignmentId: Long): Response<AssignmentItem>

    @GET("api/mobile/teacher/assignments/{id}/submissions")
    suspend fun teacherAssignmentSubmissions(@Path("id") assignmentId: Long): Response<List<AssignmentSubmissionItem>>

    @POST("api/mobile/teacher/assignments/{assignmentId}/submissions/{submissionId}/review")
    suspend fun reviewSubmission(
        @Path("assignmentId") assignmentId: Long,
        @Path("submissionId") submissionId: Long,
        @Body request: SubmissionReviewRequest
    ): Response<AssignmentSubmissionItem>

    @GET("api/mobile/chef/dashboard")
    suspend fun chefDashboard(): Response<ChefDashboard>

    @GET("api/mobile/chef/classes")
    suspend fun chefClasses(): Response<List<ClasseItem>>

    @GET("api/mobile/chef/modules")
    suspend fun chefModules(): Response<List<TeacherModuleItem>>

    @GET("api/mobile/chef/students")
    suspend fun chefStudents(@Query("classeId") classeId: Long? = null): Response<List<StudentProfile>>

    @GET("api/mobile/chef/notes")
    suspend fun chefNotes(@Query("classeId") classeId: Long? = null): Response<List<NoteItem>>

    @GET("api/mobile/chef/absences")
    suspend fun chefAbsences(@Query("classeId") classeId: Long? = null): Response<List<AbsenceItem>>

    @GET("api/mobile/chef/courses")
    suspend fun chefCourses(): Response<List<CourseItem>>

    @GET("api/mobile/chef/announcements")
    suspend fun chefAnnouncements(): Response<List<AnnouncementItem>>

    @GET("api/mobile/chef/timetable")
    suspend fun chefTimetable(): Response<List<TimetableItem>>

    @POST("api/mobile/chef/timetable")
    suspend fun createChefTimetable(@Body request: AdminTimetableUpsertRequest): Response<TimetableItem>

    @PUT("api/mobile/chef/timetable/{id}")
    suspend fun updateChefTimetable(
        @Path("id") timetableId: Long,
        @Body request: AdminTimetableUpsertRequest
    ): Response<TimetableItem>

    @DELETE("api/mobile/chef/timetable/{id}")
    suspend fun deleteChefTimetable(@Path("id") timetableId: Long): Response<ApiMessage>

    @GET("api/mobile/admin/dashboard")
    suspend fun adminDashboard(): Response<AdminDashboard>

    @GET("api/mobile/admin/top-students")
    suspend fun adminTopStudents(@Query("limit") limit: Int = 5): Response<List<TopStudentItem>>

    @GET("api/mobile/admin/users")
    suspend fun adminUsers(
        @Query("role") role: String? = null,
        @Query("q") query: String? = null,
        @Query("enabled") enabled: Boolean? = null
    ): Response<List<UserSummary>>

    @POST("api/mobile/admin/users")
    suspend fun createAdminUser(@Body request: AdminUserUpsertRequest): Response<UserSummary>

    @PUT("api/mobile/admin/users/{id}")
    suspend fun updateAdminUser(
        @Path("id") userId: Long,
        @Body request: AdminUserUpsertRequest
    ): Response<UserSummary>

    @DELETE("api/mobile/admin/users/{id}")
    suspend fun deleteAdminUser(@Path("id") userId: Long): Response<ApiMessage>

    @POST("api/mobile/admin/users/{id}/toggle")
    suspend fun toggleAdminUser(@Path("id") userId: Long): Response<UserSummary>

    @GET("api/mobile/admin/filieres")
    suspend fun adminFilieres(@Query("q") query: String? = null): Response<List<Map<String, Any?>>>

    @POST("api/mobile/admin/filieres")
    suspend fun createAdminFiliere(@Body request: AdminFiliereUpsertRequest): Response<Map<String, Any?>>

    @PUT("api/mobile/admin/filieres/{id}")
    suspend fun updateAdminFiliere(
        @Path("id") filiereId: Long,
        @Body request: AdminFiliereUpsertRequest
    ): Response<Map<String, Any?>>

    @DELETE("api/mobile/admin/filieres/{id}")
    suspend fun deleteAdminFiliere(@Path("id") filiereId: Long): Response<ApiMessage>

    @GET("api/mobile/admin/classes")
    suspend fun adminClasses(
        @Query("filiereId") filiereId: Long? = null,
        @Query("q") query: String? = null
    ): Response<List<Map<String, Any?>>>

    @POST("api/mobile/admin/classes")
    suspend fun createAdminClasse(@Body request: AdminClasseUpsertRequest): Response<Map<String, Any?>>

    @PUT("api/mobile/admin/classes/{id}")
    suspend fun updateAdminClasse(
        @Path("id") classeId: Long,
        @Body request: AdminClasseUpsertRequest
    ): Response<Map<String, Any?>>

    @DELETE("api/mobile/admin/classes/{id}")
    suspend fun deleteAdminClasse(@Path("id") classeId: Long): Response<ApiMessage>

    @GET("api/mobile/admin/modules")
    suspend fun adminModules(
        @Query("filiereId") filiereId: Long? = null,
        @Query("teacherId") teacherId: Long? = null,
        @Query("q") query: String? = null
    ): Response<List<Map<String, Any?>>>

    @POST("api/mobile/admin/modules")
    suspend fun createAdminModule(@Body request: AdminModuleUpsertRequest): Response<Map<String, Any?>>

    @PUT("api/mobile/admin/modules/{id}")
    suspend fun updateAdminModule(
        @Path("id") moduleId: Long,
        @Body request: AdminModuleUpsertRequest
    ): Response<Map<String, Any?>>

    @DELETE("api/mobile/admin/modules/{id}")
    suspend fun deleteAdminModule(@Path("id") moduleId: Long): Response<ApiMessage>

    @GET("api/mobile/admin/timetable")
    suspend fun adminTimetable(
        @Query("filiereId") filiereId: Long? = null,
        @Query("classeId") classeId: Long? = null,
        @Query("q") query: String? = null
    ): Response<List<TimetableItem>>

    @POST("api/mobile/admin/timetable")
    suspend fun createAdminTimetable(@Body request: AdminTimetableUpsertRequest): Response<TimetableItem>

    @PUT("api/mobile/admin/timetable/{id}")
    suspend fun updateAdminTimetable(
        @Path("id") timetableId: Long,
        @Body request: AdminTimetableUpsertRequest
    ): Response<TimetableItem>

    @DELETE("api/mobile/admin/timetable/{id}")
    suspend fun deleteAdminTimetable(@Path("id") timetableId: Long): Response<ApiMessage>
}
