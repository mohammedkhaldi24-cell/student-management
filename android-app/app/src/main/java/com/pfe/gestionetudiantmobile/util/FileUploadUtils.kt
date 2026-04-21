package com.pfe.gestionetudiantmobile.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink

object FileUploadUtils {

    fun uriToMultipartPart(
        context: Context,
        uri: Uri,
        partName: String,
        onProgress: ((bytesWritten: Long, contentLength: Long) -> Unit)? = null
    ): MultipartBody.Part {
        val contentResolver = context.contentResolver
        val fileName = resolveFileName(context, uri)
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val contentLength = resolveFileSize(context, uri) ?: -1L

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long = contentLength

            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var uploaded = 0L

                contentResolver.openInputStream(uri)?.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        uploaded += read
                        onProgress?.invoke(uploaded, contentLength)
                    }
                } ?: throw IllegalArgumentException("Impossible de lire le fichier.")
            }
        }
        return MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }

    fun resolveMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    fun resolveFileSize(context: Context, uri: Uri): Long? {
        if (uri.scheme != "content") {
            return null
        }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(index).takeIf { it >= 0 }
            }
        }
        return null
    }

    fun describeUri(context: Context, uri: Uri): String {
        val name = resolveFileName(context, uri)
        val mimeType = resolveMimeType(context, uri)
        val type = mimeType.substringAfter('/')
        val size = resolveFileSize(context, uri)
        val sizeLabel = size?.let { readableSize(it) } ?: "taille inconnue"
        return "${iconForMimeType(mimeType)} $name ($type, $sizeLabel)"
    }

    fun readableSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.ROOT, "%.2f GB", gb)
    }

    fun iconForMimeType(mimeType: String): String {
        return when {
            mimeType.contains("pdf", ignoreCase = true) -> "PDF"
            mimeType.startsWith("image/", ignoreCase = true) -> "IMG"
            mimeType.contains("word", ignoreCase = true) ||
                mimeType.contains("document", ignoreCase = true) -> "DOC"
            mimeType.contains("spreadsheet", ignoreCase = true) ||
                mimeType.contains("excel", ignoreCase = true) -> "XLS"
            mimeType.contains("presentation", ignoreCase = true) ||
                mimeType.contains("powerpoint", ignoreCase = true) -> "PPT"
            mimeType.startsWith("text/", ignoreCase = true) -> "TXT"
            mimeType.contains("zip", ignoreCase = true) ||
                mimeType.contains("rar", ignoreCase = true) -> "ZIP"
            else -> "FILE"
        }
    }

    fun iconForDocument(mimeType: String?, fileName: String?): String {
        val normalizedMime = mimeType?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedMime != null && normalizedMime != "application/octet-stream") {
            return iconForMimeType(normalizedMime)
        }

        return when (fileName.extension()) {
            "pdf" -> "PDF"
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic" -> "IMG"
            "doc", "docx", "odt", "rtf" -> "DOC"
            "xls", "xlsx", "csv", "ods" -> "XLS"
            "ppt", "pptx", "odp" -> "PPT"
            "txt", "md", "json", "xml", "html", "css", "kt", "java" -> "TXT"
            "zip", "rar", "7z", "tar", "gz" -> "ZIP"
            else -> "FILE"
        }
    }

    fun readableDocumentType(mimeType: String?, fileName: String?): String {
        return when (iconForDocument(mimeType, fileName)) {
            "PDF" -> "PDF"
            "IMG" -> "Image"
            "DOC" -> "Document"
            "XLS" -> "Tableur"
            "PPT" -> "Presentation"
            "TXT" -> "Texte"
            "ZIP" -> "Archive"
            else -> fileName.extension()?.uppercase(Locale.ROOT) ?: "Fichier"
        }
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val value = cursor.getString(nameIndex)
                    if (!value.isNullOrBlank()) {
                        return value
                    }
                }
            }
        }

        val path = uri.path
        if (path.isNullOrBlank()) {
            return "document"
        }
        val slash = path.lastIndexOf('/')
        return if (slash >= 0 && slash < path.length - 1) {
            path.substring(slash + 1)
        } else {
            "document"
        }
    }

    private fun String?.extension(): String? {
        val value = this?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase(Locale.ROOT)
        return value?.takeIf { it.isNotBlank() && it.length <= 8 }
    }
}
