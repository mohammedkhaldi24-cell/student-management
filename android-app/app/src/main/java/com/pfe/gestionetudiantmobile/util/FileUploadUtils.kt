package com.pfe.gestionetudiantmobile.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

object FileUploadUtils {

    fun uriToMultipartPart(context: Context, uri: Uri, partName: String): MultipartBody.Part {
        val contentResolver = context.contentResolver
        val fileName = resolveFileName(context, uri)
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Impossible de lire le fichier.")

        val requestBody = bytes.toRequestBody(mimeType.toMediaType())
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
        val type = resolveMimeType(context, uri).substringAfter('/')
        val size = resolveFileSize(context, uri)
        val sizeLabel = size?.let { readableSize(it) } ?: "taille inconnue"
        return "$name ($type, $sizeLabel)"
    }

    private fun readableSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.ROOT, "%.2f GB", gb)
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
}
