package com.pfe.gestionetudiantmobile.data.repository

import android.content.Context
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import com.pfe.gestionetudiantmobile.data.model.AbsenceItem
import com.pfe.gestionetudiantmobile.data.model.AbsenceSessionRequest
import com.pfe.gestionetudiantmobile.data.model.AbsenceSessionResponse
import com.pfe.gestionetudiantmobile.data.model.AcademicHistoryEvent
import com.pfe.gestionetudiantmobile.data.model.AcademicStatPoint
import com.pfe.gestionetudiantmobile.data.model.AcademicStatistics
import com.pfe.gestionetudiantmobile.data.model.AnnouncementItem
import com.pfe.gestionetudiantmobile.data.model.ApiMessage
import com.pfe.gestionetudiantmobile.data.model.AssignmentItem
import com.pfe.gestionetudiantmobile.data.model.AssignmentSubmissionItem
import com.pfe.gestionetudiantmobile.data.model.ClasseItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.data.model.NoteItem
import com.pfe.gestionetudiantmobile.data.model.NoteBulkItem
import com.pfe.gestionetudiantmobile.data.model.NoteBulkRequest
import com.pfe.gestionetudiantmobile.data.model.StudentProfile
import com.pfe.gestionetudiantmobile.data.model.SubmissionReviewRequest
import com.pfe.gestionetudiantmobile.data.model.TeacherDashboard
import com.pfe.gestionetudiantmobile.data.model.TeacherModuleItem
import com.pfe.gestionetudiantmobile.data.model.TeacherProfile
import com.pfe.gestionetudiantmobile.data.model.TimetableItem
import com.pfe.gestionetudiantmobile.data.offline.ConnectivityMonitor
import com.pfe.gestionetudiantmobile.data.offline.OfflineStore
import com.pfe.gestionetudiantmobile.data.offline.OfflineSyncManager
import com.pfe.gestionetudiantmobile.data.offline.PendingOfflineAction
import com.pfe.gestionetudiantmobile.util.ApiResult
import java.text.DecimalFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class TeacherRepository(context: Context? = null) {

    private val api get() = RetrofitClient.api
    private val appContext = context?.applicationContext

    init {
        appContext?.let { OfflineSyncManager.register(it) }
    }

    suspend fun profile(): ApiResult<TeacherProfile> = call { api.teacherProfile() }

    suspend fun dashboard(): ApiResult<TeacherDashboard> = call { api.teacherDashboard() }

    suspend fun modules(): ApiResult<List<TeacherModuleItem>> = call { api.teacherModules() }

    suspend fun timetable(): ApiResult<List<TimetableItem>> = call { api.teacherTimetable() }

    suspend fun classes(moduleId: Long? = null, filiereId: Long? = null): ApiResult<List<ClasseItem>> =
        call { api.teacherClasses(moduleId, filiereId) }

    suspend fun students(
        moduleId: Long? = null,
        classeId: Long? = null,
        filiereId: Long? = null,
        query: String? = null
    ): ApiResult<List<StudentProfile>> = call { api.teacherStudents(moduleId, classeId, filiereId, query) }

    suspend fun notes(
        moduleId: Long? = null,
        classeId: Long? = null,
        query: String? = null
    ): ApiResult<List<NoteItem>> {
        val scope = teacherNotesScope(moduleId, classeId, query)
        val result = call { api.teacherNotes(moduleId, classeId, query) }
        return when (result) {
            is ApiResult.Success -> {
                appContext?.let { OfflineStore(it).cacheNotes(scope, result.data) }
                result
            }
            is ApiResult.Error -> {
                val cached = appContext?.let { OfflineStore(it).cachedNotes(scope) }
                if (cached != null) ApiResult.Success(cached) else result
            }
        }
    }

    suspend fun absences(
        moduleId: Long? = null,
        classeId: Long? = null,
        query: String? = null
    ): ApiResult<List<AbsenceItem>> {
        val scope = teacherAbsencesScope(moduleId, classeId, query)
        val result = call { api.teacherAbsences(moduleId, classeId, query) }
        return when (result) {
            is ApiResult.Success -> {
                appContext?.let { OfflineStore(it).cacheAbsences(scope, result.data) }
                result
            }
            is ApiResult.Error -> {
                val cached = appContext?.let { OfflineStore(it).cachedAbsences(scope) }
                if (cached != null) ApiResult.Success(cached) else result
            }
        }
    }

    suspend fun history(
        moduleId: Long? = null,
        classeId: Long? = null,
        query: String? = null,
        actorName: String? = null
    ): ApiResult<List<AcademicHistoryEvent>> {
        val noteResult = notes(moduleId, classeId, query)
        val absenceResult = absences(moduleId, classeId, query)
        val events = mutableListOf<AcademicHistoryEvent>()
        val errors = mutableListOf<String>()

        when (noteResult) {
            is ApiResult.Success -> events += AcademicHistoryFactory.fromNotes(
                noteResult.data.filterNot { it.statut.contains("attente", ignoreCase = true) },
                fallbackActorName = actorName
            )
            is ApiResult.Error -> errors += noteResult.message
        }
        when (absenceResult) {
            is ApiResult.Success -> events += AcademicHistoryFactory.fromAbsences(
                absenceResult.data.filterNot { it.motif?.contains("attente", ignoreCase = true) == true },
                fallbackActorName = actorName
            )
            is ApiResult.Error -> errors += absenceResult.message
        }

        val store = appContext?.let { OfflineStore(it) }
        if (store != null) {
            events += AcademicHistoryFactory.fromPendingActions(
                actions = store.pendingActions(),
                moduleId = moduleId,
                fallbackActorName = actorName,
                cachedNoteResolver = { action ->
                    action.cacheScope
                        ?.let { store.cachedNotes(it) }
                        .orEmpty()
                        .firstOrNull { it.studentId == action.studentId && it.moduleId == action.moduleId }
                },
                cachedAbsenceResolver = { action ->
                    action.cacheScope
                        ?.let { store.cachedAbsences(it) }
                        .orEmpty()
                        .firstOrNull { it.localMatch(action) }
                }
            )
        }

        return if (events.isNotEmpty() || errors.isEmpty()) {
            ApiResult.Success(AcademicHistoryFactory.sorted(events))
        } else {
            ApiResult.Error(errors.first())
        }
    }

    suspend fun statistics(preferredModuleId: Long? = null): ApiResult<AcademicStatistics> {
        val modulesResult = modules()
        if (modulesResult is ApiResult.Error) {
            return ApiResult.Error(modulesResult.message)
        }
        val modules = (modulesResult as ApiResult.Success).data.sortedBy { it.nom.lowercase(Locale.ROOT) }
        val noteResult = notes()
        val absenceResult = absences()
        val assignmentResult = assignments()
        val notes = (noteResult as? ApiResult.Success)?.data.orEmpty()
        val absences = (absenceResult as? ApiResult.Success)?.data.orEmpty()
        val assignments = (assignmentResult as? ApiResult.Success)?.data.orEmpty()

        val averagePoints = teacherAveragePoints(modules, notes, preferredModuleId)
        val absencePoints = teacherAbsenceRatePoints(modules, notes, absences, preferredModuleId)
        val completionPoints = teacherAssignmentCompletionPoints(modules, assignments, preferredModuleId)

        val warnings = listOfNotNull(
            (noteResult as? ApiResult.Error)?.message?.let { "notes indisponibles" },
            (absenceResult as? ApiResult.Error)?.message?.let { "absences indisponibles" },
            (assignmentResult as? ApiResult.Error)?.message?.let { "devoirs indisponibles" }
        )
        return ApiResult.Success(
            AcademicStatistics(
                averageByModule = averagePoints,
                absenceRateByModule = absencePoints,
                assignmentCompletionByModule = completionPoints,
                highlightedModuleId = preferredModuleId,
                summary = if (warnings.isEmpty()) {
                    "Statistiques calculees depuis les donnees mobiles disponibles."
                } else {
                    "Vue partielle: ${warnings.distinct().joinToString(", ")}."
                }
            )
        )
    }

    suspend fun courses(moduleId: Long? = null): ApiResult<List<CourseItem>> =
        call { api.teacherCourses(moduleId) }

    suspend fun announcements(): ApiResult<List<AnnouncementItem>> = call { api.teacherAnnouncements() }

    suspend fun assignments(moduleId: Long? = null): ApiResult<List<AssignmentItem>> =
        call { api.teacherAssignments(moduleId) }

    suspend fun submissions(assignmentId: Long): ApiResult<List<AssignmentSubmissionItem>> =
        call { api.teacherAssignmentSubmissions(assignmentId) }

    suspend fun createCourse(
        title: String,
        description: String?,
        moduleId: Long,
        classeId: Long?,
        filiereId: Long?,
        fileParts: List<MultipartBody.Part>? = null,
        filePart: MultipartBody.Part? = null
    ): ApiResult<CourseItem> = call {
        api.createTeacherCourse(
            title = title.trim().toRequestBody(TEXT_MEDIA),
            description = description?.takeIf { it.isNotBlank() }?.trim()?.toRequestBody(TEXT_MEDIA),
            moduleId = moduleId.toString().toRequestBody(TEXT_MEDIA),
            classeId = classeId?.toString()?.toRequestBody(TEXT_MEDIA),
            filiereId = filiereId?.toString()?.toRequestBody(TEXT_MEDIA),
            files = fileParts?.takeIf { it.isNotEmpty() },
            file = if (fileParts.isNullOrEmpty()) filePart else null
        )
    }

    suspend fun addCourseFiles(
        courseId: Long,
        fileParts: List<MultipartBody.Part>
    ): ApiResult<CourseItem> = call {
        api.addTeacherCourseFiles(courseId, fileParts)
    }

    suspend fun replaceCourseFile(
        courseId: Long,
        filePart: MultipartBody.Part
    ): ApiResult<CourseItem> = call {
        api.replaceTeacherCourseFile(courseId, filePart)
    }

    suspend fun removeCourseFile(courseId: Long): ApiResult<CourseItem> = call {
        api.removeTeacherCourseFile(courseId)
    }

    suspend fun deleteCourse(courseId: Long): ApiResult<ApiMessage> = call {
        api.deleteTeacherCourse(courseId)
    }

    suspend fun createAnnouncement(
        title: String,
        message: String,
        moduleId: Long?,
        classeId: Long?,
        filiereId: Long?,
        attachment: MultipartBody.Part?
    ): ApiResult<AnnouncementItem> = call {
        api.createTeacherAnnouncement(
            title = title.trim().toRequestBody(TEXT_MEDIA),
            message = message.trim().toRequestBody(TEXT_MEDIA),
            moduleId = moduleId?.toString()?.toRequestBody(TEXT_MEDIA),
            classeId = classeId?.toString()?.toRequestBody(TEXT_MEDIA),
            filiereId = filiereId?.toString()?.toRequestBody(TEXT_MEDIA),
            attachment = attachment
        )
    }

    suspend fun replaceAnnouncementAttachment(
        announcementId: Long,
        attachment: MultipartBody.Part
    ): ApiResult<AnnouncementItem> = call {
        api.replaceTeacherAnnouncementAttachment(announcementId, attachment)
    }

    suspend fun removeAnnouncementAttachment(announcementId: Long): ApiResult<AnnouncementItem> = call {
        api.removeTeacherAnnouncementAttachment(announcementId)
    }

    suspend fun deleteAnnouncement(announcementId: Long): ApiResult<ApiMessage> = call {
        api.deleteTeacherAnnouncement(announcementId)
    }

    suspend fun createAssignment(
        title: String,
        description: String,
        dueDate: String,
        moduleId: Long?,
        classeId: Long?,
        filiereId: Long?,
        published: Boolean,
        attachment: MultipartBody.Part?
    ): ApiResult<AssignmentItem> = call {
        api.createTeacherAssignment(
            title = title.trim().toRequestBody(TEXT_MEDIA),
            description = description.trim().toRequestBody(TEXT_MEDIA),
            dueDate = dueDate.trim().toRequestBody(TEXT_MEDIA),
            moduleId = moduleId?.toString()?.toRequestBody(TEXT_MEDIA),
            classeId = classeId?.toString()?.toRequestBody(TEXT_MEDIA),
            filiereId = filiereId?.toString()?.toRequestBody(TEXT_MEDIA),
            published = published.toString().toRequestBody(TEXT_MEDIA),
            attachment = attachment
        )
    }

    suspend fun replaceAssignmentAttachment(
        assignmentId: Long,
        attachment: MultipartBody.Part
    ): ApiResult<AssignmentItem> = call {
        api.replaceTeacherAssignmentAttachment(assignmentId, attachment)
    }

    suspend fun removeAssignmentAttachment(assignmentId: Long): ApiResult<AssignmentItem> = call {
        api.removeTeacherAssignmentAttachment(assignmentId)
    }

    suspend fun deleteAssignment(assignmentId: Long): ApiResult<ApiMessage> = call {
        api.deleteTeacherAssignment(assignmentId)
    }

    suspend fun reviewSubmission(
        assignmentId: Long,
        submissionId: Long,
        score: Double?,
        feedback: String?,
        status: String?
    ): ApiResult<AssignmentSubmissionItem> = call {
        api.reviewSubmission(
            assignmentId,
            submissionId,
            SubmissionReviewRequest(score = score, feedback = feedback, status = status)
        )
    }

    suspend fun upsertNote(
        studentId: Long,
        moduleId: Long,
        semestre: String,
        anneeAcademique: String,
        noteCc: Double?,
        noteExamen: Double?,
        cacheClasseId: Long? = null
    ): ApiResult<NoteItem> = call {
        api.upsertTeacherNote(noteRequest(studentId, moduleId, semestre, anneeAcademique, noteCc, noteExamen))
    }.let { result ->
        if (result is ApiResult.Error && appContext != null && !ConnectivityMonitor.isOnline(appContext)) {
            queueNote(
                studentId = studentId,
                moduleId = moduleId,
                semestre = semestre,
                anneeAcademique = anneeAcademique,
                noteCc = noteCc,
                noteExamen = noteExamen,
                cacheClasseId = cacheClasseId
            )
        } else {
            result
        }
    }

    private fun noteRequest(
        studentId: Long,
        moduleId: Long,
        semestre: String,
        anneeAcademique: String,
        noteCc: Double?,
        noteExamen: Double?
    ) = com.pfe.gestionetudiantmobile.data.model.NoteUpsertRequest(
        studentId = studentId,
        moduleId = moduleId,
        semestre = semestre,
        anneeAcademique = anneeAcademique,
        noteCc = noteCc,
        noteExamen = noteExamen
    )

    private fun queueNote(
        studentId: Long,
        moduleId: Long,
        semestre: String,
        anneeAcademique: String,
        noteCc: Double?,
        noteExamen: Double?,
        cacheClasseId: Long?
    ): ApiResult<NoteItem> {
        val context = appContext ?: return ApiResult.Error("Connexion indisponible.")
        val store = OfflineStore(context)
        val scope = teacherNotesScope(moduleId, cacheClasseId, null)
        val existing = store.cachedNotes(scope).orEmpty().firstOrNull {
            it.studentId == studentId && it.moduleId == moduleId && it.semestre == semestre
        }
        val final = if (noteCc != null && noteExamen != null) (noteCc + noteExamen) / 2.0 else existing?.noteFinal
        val queuedNote = existing?.copy(
            noteCc = noteCc,
            noteExamen = noteExamen,
            noteFinal = final,
            statut = "En attente sync"
        ) ?: NoteItem(
            id = localId(),
            studentId = studentId,
            studentName = null,
            matricule = null,
            moduleId = moduleId,
            moduleNom = null,
            moduleCode = null,
            coefficient = null,
            noteCc = noteCc,
            noteExamen = noteExamen,
            noteFinal = final,
            semestre = semestre,
            anneeAcademique = anneeAcademique,
            statut = "En attente sync"
        )

        store.upsertCachedNote(scope, queuedNote)
        store.enqueue(
            PendingOfflineAction(
                id = UUID.randomUUID().toString(),
                type = PendingOfflineAction.TYPE_UPSERT_NOTE,
                createdAtMillis = System.currentTimeMillis(),
                replacementKey = "note:$studentId:$moduleId:$semestre:$anneeAcademique",
                cacheScope = scope,
                studentId = studentId,
                moduleId = moduleId,
                semestre = semestre,
                anneeAcademique = anneeAcademique,
                noteCc = noteCc,
                noteExamen = noteExamen
            )
        )
        return ApiResult.Success(queuedNote)
    }

    suspend fun upsertNotesBulk(
        moduleId: Long,
        semestre: String,
        anneeAcademique: String,
        notes: List<NoteBulkItem>
    ): ApiResult<List<NoteItem>> = call {
        api.upsertTeacherNotesBulk(
            NoteBulkRequest(
                moduleId = moduleId,
                semestre = semestre,
                anneeAcademique = anneeAcademique,
                notes = notes
            )
        )
    }

    suspend fun deleteNote(noteId: Long): ApiResult<ApiMessage> = call { api.deleteTeacherNote(noteId) }

    suspend fun createAbsence(
        studentId: Long,
        moduleId: Long,
        dateAbsence: String?,
        nombreHeures: Int?,
        cacheClasseId: Long? = null
    ): ApiResult<AbsenceItem> = call {
        api.createTeacherAbsence(
            com.pfe.gestionetudiantmobile.data.model.AbsenceCreateRequest(
                studentId = studentId,
                moduleId = moduleId,
                dateAbsence = dateAbsence,
                nombreHeures = nombreHeures
            )
        )
    }.let { result ->
        if (result is ApiResult.Error && appContext != null && !ConnectivityMonitor.isOnline(appContext)) {
            queueCreateAbsence(studentId, moduleId, dateAbsence, nombreHeures, cacheClasseId)
        } else {
            result
        }
    }

    private fun queueCreateAbsence(
        studentId: Long,
        moduleId: Long,
        dateAbsence: String?,
        nombreHeures: Int?,
        cacheClasseId: Long?
    ): ApiResult<AbsenceItem> {
        val context = appContext ?: return ApiResult.Error("Connexion indisponible.")
        val store = OfflineStore(context)
        val scope = teacherAbsencesScope(moduleId, cacheClasseId, null)
        val date = runCatching { LocalDate.parse(dateAbsence.orEmpty()) }.getOrElse { LocalDate.now() }
        val localId = localId()
        val queuedAbsence = AbsenceItem(
            id = localId,
            studentId = studentId,
            studentName = null,
            matricule = null,
            moduleId = moduleId,
            moduleNom = null,
            moduleCode = null,
            dateAbsence = date,
            nombreHeures = nombreHeures ?: 1,
            justifiee = false,
            motif = "En attente sync"
        )
        store.upsertCachedAbsence(scope, queuedAbsence)
        store.enqueue(
            PendingOfflineAction(
                id = UUID.randomUUID().toString(),
                type = PendingOfflineAction.TYPE_CREATE_ABSENCE,
                createdAtMillis = System.currentTimeMillis(),
                replacementKey = "absence:create:$studentId:$moduleId:$date",
                cacheScope = scope,
                localEntityId = localId,
                studentId = studentId,
                moduleId = moduleId,
                dateAbsence = date.toString(),
                nombreHeures = nombreHeures
            )
        )
        return ApiResult.Success(queuedAbsence)
    }

    suspend fun saveAbsenceSession(
        moduleId: Long,
        classeId: Long?,
        dateAbsence: String,
        nombreHeures: Int,
        absentStudentIds: List<Long>
    ): ApiResult<AbsenceSessionResponse> = call {
        api.saveTeacherAbsenceSession(
            AbsenceSessionRequest(
                moduleId = moduleId,
                classeId = classeId,
                dateAbsence = dateAbsence,
                nombreHeures = nombreHeures,
                absentStudentIds = absentStudentIds
            )
        )
    }

    suspend fun justifyAbsence(absenceId: Long, motif: String?): ApiResult<AbsenceItem> =
        call { api.justifyTeacherAbsence(absenceId, motif) }

    suspend fun deleteAbsence(absenceId: Long): ApiResult<ApiMessage> {
        if (appContext != null && !ConnectivityMonitor.isOnline(appContext)) {
            val store = OfflineStore(appContext)
            if (absenceId < 0 && store.removeQueuedCreateAbsence(absenceId)) {
                return ApiResult.Success(ApiMessage("Absence locale retiree."))
            }
            store.removeCachedAbsenceById(absenceId)
            store.enqueue(
                PendingOfflineAction(
                    id = UUID.randomUUID().toString(),
                    type = PendingOfflineAction.TYPE_DELETE_ABSENCE,
                    createdAtMillis = System.currentTimeMillis(),
                    replacementKey = "absence:delete:$absenceId",
                    absenceId = absenceId
                )
            )
            return ApiResult.Success(ApiMessage("Suppression mise en attente de synchronisation."))
        }
        return call { api.deleteTeacherAbsence(absenceId) }
    }

    private suspend fun <T> call(block: suspend () -> retrofit2.Response<T>): ApiResult<T> {
        return runCatching {
            val response = block()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(RepositoryUtils.parseError(response))
            }
        }.getOrElse {
            ApiResult.Error(RepositoryUtils.networkError(it))
        }
    }

    companion object {
        private val TEXT_MEDIA = "text/plain".toMediaType()
        private const val DEFAULT_MODULE_HOURS = 30
    }

    private fun teacherNotesScope(moduleId: Long?, classeId: Long?, query: String?): String {
        return OfflineStore.scope(
            "teacher.notes",
            "moduleId" to moduleId,
            "classeId" to classeId,
            "query" to query
        )
    }

    private fun teacherAbsencesScope(moduleId: Long?, classeId: Long?, query: String?): String {
        return OfflineStore.scope(
            "teacher.absences",
            "moduleId" to moduleId,
            "classeId" to classeId,
            "query" to query
        )
    }

    private fun AbsenceItem.localMatch(action: PendingOfflineAction): Boolean {
        return id == action.localEntityId ||
            (studentId == action.studentId && moduleId == action.moduleId && dateAbsence.toString() == action.dateAbsence)
    }

    private fun localId(): Long = -(System.currentTimeMillis())

    private fun teacherAveragePoints(
        modules: List<TeacherModuleItem>,
        notes: List<NoteItem>,
        preferredModuleId: Long?
    ): List<AcademicStatPoint> {
        val scoresByModule = notes
            .mapNotNull { note -> note.moduleId?.let { id -> note.noteFinal?.let { score -> id to score } } }
            .groupBy({ it.first }, { it.second })
        return modules.map { module ->
            val scores = scoresByModule[module.id].orEmpty()
            val average = scores.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            AcademicStatPoint(
                moduleId = module.id,
                label = moduleLabel(module.nom, module.code),
                value = average,
                valueLabel = if (scores.isEmpty()) "--" else "${formatStatDecimal(average)}/20",
                detail = if (scores.isEmpty()) "Aucune note finale" else "${scores.size} note(s) finale(s)"
            )
        }.prioritize(preferredModuleId)
    }

    private suspend fun teacherAbsenceRatePoints(
        modules: List<TeacherModuleItem>,
        notes: List<NoteItem>,
        absences: List<AbsenceItem>,
        preferredModuleId: Long?
    ): List<AcademicStatPoint> {
        val absencesByModule = absences.filter { it.moduleId != null }.groupBy { it.moduleId!! }
        val fallbackStudentsByModule = buildFallbackStudentCounts(notes, absences)
        val points = mutableListOf<AcademicStatPoint>()
        for (module in modules) {
            val studentCount = when (val result = students(moduleId = module.id)) {
                is ApiResult.Success -> result.data.size
                is ApiResult.Error -> fallbackStudentsByModule[module.id] ?: 0
            }
            val moduleAbsences = absencesByModule[module.id].orEmpty()
            val absenceHours = moduleAbsences.sumOf { it.nombreHeures }
            val expectedHours = (module.volumeHoraire ?: DEFAULT_MODULE_HOURS).coerceAtLeast(1)
            val possibleHours = studentCount * expectedHours
            val rate = if (possibleHours > 0) {
                absenceHours.toDouble() * 100.0 / possibleHours.toDouble()
            } else {
                0.0
            }
            points += AcademicStatPoint(
                moduleId = module.id,
                label = moduleLabel(module.nom, module.code),
                value = rate.coerceIn(0.0, 100.0),
                valueLabel = "${formatStatDecimal(rate.coerceIn(0.0, 100.0))}%",
                detail = if (studentCount == 0) {
                    "$absenceHours h d'absence | aucun etudiant charge"
                } else {
                    "$absenceHours h / $studentCount etudiant(s)"
                }
            )
        }
        return points.prioritize(preferredModuleId)
    }

    private suspend fun teacherAssignmentCompletionPoints(
        modules: List<TeacherModuleItem>,
        assignments: List<AssignmentItem>,
        preferredModuleId: Long?
    ): List<AcademicStatPoint> {
        val publishedByModule = assignments
            .filter { it.published && it.moduleId != null }
            .groupBy { it.moduleId!! }
        val targetCountCache = mutableMapOf<AssignmentTargetKey, Int>()
        val points = mutableListOf<AcademicStatPoint>()

        for (module in modules) {
            val moduleAssignments = publishedByModule[module.id].orEmpty()
            var completed = 0
            var expected = 0

            for (assignment in moduleAssignments) {
                val targetKey = AssignmentTargetKey(
                    moduleId = assignment.moduleId,
                    classeId = assignment.targetClasseId,
                    filiereId = assignment.targetFiliereId
                )
                val targetCount = targetCountCache.getOrPut(targetKey) {
                    when (val result = students(
                        moduleId = targetKey.moduleId,
                        classeId = targetKey.classeId,
                        filiereId = targetKey.filiereId
                    )) {
                        is ApiResult.Success -> result.data.size
                        is ApiResult.Error -> 0
                    }
                }
                val submittedCount = when (val result = submissions(assignment.id)) {
                    is ApiResult.Success -> result.data
                        .filter { it.submittedAt != null || it.status.isSubmittedStatus() }
                        .map { submission -> submission.studentId ?: submission.id }
                        .distinct()
                        .size
                    is ApiResult.Error -> 0
                }
                val denominator = targetCount.takeIf { it > 0 } ?: submittedCount.coerceAtLeast(1)
                expected += denominator
                completed += submittedCount.coerceAtMost(denominator)
            }

            val rate = if (expected > 0) completed.toDouble() * 100.0 / expected.toDouble() else 0.0
            points += AcademicStatPoint(
                moduleId = module.id,
                label = moduleLabel(module.nom, module.code),
                value = rate,
                valueLabel = "${formatStatDecimal(rate)}%",
                detail = if (moduleAssignments.isEmpty()) {
                    "Aucun devoir publie"
                } else {
                    "$completed/$expected soumission(s)"
                }
            )
        }
        return points.prioritize(preferredModuleId)
    }

    private fun buildFallbackStudentCounts(
        notes: List<NoteItem>,
        absences: List<AbsenceItem>
    ): Map<Long, Int> {
        val idsByModule = mutableMapOf<Long, MutableSet<String>>()
        notes.forEach { note ->
            val moduleId = note.moduleId ?: return@forEach
            val studentKey = note.studentId?.toString() ?: note.matricule?.takeIf { it.isNotBlank() } ?: return@forEach
            idsByModule.getOrPut(moduleId) { mutableSetOf() } += studentKey
        }
        absences.forEach { absence ->
            val moduleId = absence.moduleId ?: return@forEach
            val studentKey = absence.studentId?.toString() ?: absence.matricule?.takeIf { it.isNotBlank() } ?: return@forEach
            idsByModule.getOrPut(moduleId) { mutableSetOf() } += studentKey
        }
        return idsByModule.mapValues { it.value.size }
    }

    private fun List<AcademicStatPoint>.prioritize(moduleId: Long?): List<AcademicStatPoint> {
        return sortedWith(compareBy<AcademicStatPoint> { if (it.moduleId == moduleId) 0 else 1 }
            .thenBy { it.label.lowercase(Locale.ROOT) })
    }

    private fun moduleLabel(name: String?, code: String?): String {
        return listOfNotNull(name?.takeIf { it.isNotBlank() }, code?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "Module" }
    }

    private fun formatStatDecimal(value: Double): String {
        return DecimalFormat("0.#").format(value)
    }

    private fun String.isSubmittedStatus(): Boolean {
        val normalized = trim().uppercase(Locale.ROOT)
        return normalized in setOf("SUBMITTED", "TURNED_IN", "SENT", "REVIEWED", "GRADED", "LATE")
    }

    private data class AssignmentTargetKey(
        val moduleId: Long?,
        val classeId: Long?,
        val filiereId: Long?
    )

}
