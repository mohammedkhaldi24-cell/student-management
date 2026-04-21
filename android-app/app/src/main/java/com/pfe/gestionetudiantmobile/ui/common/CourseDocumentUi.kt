package com.pfe.gestionetudiantmobile.ui.common

import com.pfe.gestionetudiantmobile.data.model.CourseDocumentItem
import com.pfe.gestionetudiantmobile.data.model.CourseItem
import com.pfe.gestionetudiantmobile.util.FileUploadUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object CourseDocumentUi {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.FRENCH)

    fun rowsByModule(courses: List<CourseItem>): List<UiRow> {
        if (courses.isEmpty()) {
            return emptyList()
        }

        val sortedCourses = courses.sortedWith(
            compareBy<CourseItem> { moduleLabel(it).lowercase(Locale.ROOT) }
                .thenByDescending { it.createdAt ?: LocalDateTime.MIN }
                .thenBy { title(it).lowercase(Locale.ROOT) }
        )
        val grouped = sortedCourses.groupBy { moduleLabel(it) }
        val rows = mutableListOf<UiRow>()

        grouped.forEach { (module, moduleCourses) ->
            val documentCount = moduleCourses.sumOf { documentsFor(it).size }
            rows += UiRow(
                title = module,
                subtitle = "${moduleCourses.size} cours | $documentCount fichier(s)",
                badge = "Module",
                icon = "MOD"
            )
            rows += moduleCourses.map { courseRow(it) }
        }

        return rows
    }

    fun courseRow(course: CourseItem): UiRow {
        val documents = documentsFor(course)
        return UiRow(
            id = course.id,
            title = title(course),
            subtitle = listOf(
                "Prof: ${teacher(course)}",
                "Date: ${formatDate(course.createdAt)}",
                shortDescription(course),
                documentsLine(documents)
            ).joinToString("\n"),
            badge = documentBadge(documents.size),
            icon = documents.firstOrNull()?.let { FileUploadUtils.iconForDocument(it.contentType, it.fileName) } ?: "CRS"
        )
    }

    fun documentsFor(course: CourseItem): List<CourseDocumentUiItem> {
        val backendFiles = course.files.orEmpty().mapNotNull { it.toUiItem() }
        if (backendFiles.isNotEmpty()) {
            return backendFiles.distinctBy { it.filePath }
        }

        val legacyPath = course.filePath.clean() ?: return emptyList()
        val legacyName = course.fileName.clean() ?: legacyPath.substringAfterLast('/').ifBlank { "document" }
        return listOf(
            CourseDocumentUiItem(
                id = null,
                filePath = legacyPath,
                fileName = legacyName,
                contentType = null,
                fileSize = null,
                uploadedAt = course.createdAt
            )
        )
    }

    fun detailsText(course: CourseItem): String {
        return buildString {
            appendLine("Module: ${moduleLabel(course)}")
            appendLine("Prof: ${teacher(course)}")
            appendLine("Date: ${formatDate(course.createdAt)}")
            appendLine("Documents: ${documentsSummary(documentsFor(course))}")
            appendLine()
            append(course.description.clean() ?: "Aucune description disponible.")
        }
    }

    fun documentLabel(document: CourseDocumentUiItem): String {
        val type = FileUploadUtils.readableDocumentType(document.contentType, document.fileName)
        val size = document.fileSize?.let { FileUploadUtils.readableSize(it) }
        val date = document.uploadedAt?.let { formatDate(it) }
        return listOfNotNull(
            "${FileUploadUtils.iconForDocument(document.contentType, document.fileName)}  ${document.fileName}",
            listOfNotNull(type, size, date).joinToString(" | ").takeIf { it.isNotBlank() }
        ).joinToString("\n")
    }

    fun moduleLabel(course: CourseItem): String {
        val name = course.moduleNom.clean() ?: "Module non defini"
        val code = course.moduleCode.clean()
        return if (code == null) name else "$name ($code)"
    }

    private fun CourseDocumentItem.toUiItem(): CourseDocumentUiItem? {
        val path = filePath.clean() ?: return null
        val name = fileName.clean() ?: path.substringAfterLast('/').ifBlank { "document" }
        return CourseDocumentUiItem(
            id = id,
            filePath = path,
            fileName = name,
            contentType = contentType.clean(),
            fileSize = fileSize,
            uploadedAt = uploadedAt
        )
    }

    private fun title(course: CourseItem): String {
        return course.title.clean() ?: "Cours sans titre"
    }

    private fun teacher(course: CourseItem): String {
        return course.teacherName.clean() ?: "Enseignant non renseigne"
    }

    private fun shortDescription(course: CourseItem): String {
        val description = course.description.clean() ?: return "Aucune description courte."
        return if (description.length <= 140) {
            description
        } else {
            "${description.take(137).trimEnd()}..."
        }
    }

    private fun documentsLine(documents: List<CourseDocumentUiItem>): String {
        if (documents.isEmpty()) {
            return "Fichiers: aucun document joint"
        }

        val preview = documents.take(2).joinToString(", ") {
            "${FileUploadUtils.iconForDocument(it.contentType, it.fileName)} ${it.fileName}"
        }
        val remaining = documents.size - 2
        return if (remaining > 0) {
            "Fichiers: $preview (+$remaining)"
        } else {
            "Fichiers: $preview"
        }
    }

    private fun documentsSummary(documents: List<CourseDocumentUiItem>): String {
        return when (documents.size) {
            0 -> "Aucun document joint"
            1 -> documents.first().fileName
            else -> "${documents.size} fichiers"
        }
    }

    private fun documentBadge(count: Int): String {
        return when (count) {
            0 -> "Sans fichier"
            1 -> "Ouvrir"
            else -> "$count fichiers"
        }
    }

    private fun formatDate(value: LocalDateTime?): String {
        return value?.format(dateFormatter) ?: "Date non renseignee"
    }

    private fun String?.clean(): String? {
        return this
            ?.replace("\\s+".toRegex(), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

data class CourseDocumentUiItem(
    val id: Long?,
    val filePath: String,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long?,
    val uploadedAt: LocalDateTime?
)
