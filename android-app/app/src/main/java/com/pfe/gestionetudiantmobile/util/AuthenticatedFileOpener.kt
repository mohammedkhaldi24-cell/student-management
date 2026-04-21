package com.pfe.gestionetudiantmobile.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.pfe.gestionetudiantmobile.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.Locale

object AuthenticatedFileOpener {

    suspend fun downloadAndOpen(
        context: Context,
        rawUrl: String,
        suggestedFileName: String? = null
    ): ApiResult<Unit> {
        val downloaded = withContext(Dispatchers.IO) {
            downloadToCache(context, rawUrl, suggestedFileName)
        }

        if (downloaded is ApiResult.Error) {

            return downloaded
        }

        val file = (downloaded as ApiResult.Success).data.file
        val mimeType = downloaded.data.mimeType
        return openLocalFile(context, file, mimeType)
    }

    private suspend fun downloadToCache(
        context: Context,
        rawUrl: String,
        suggestedFileName: String?
    ): ApiResult<DownloadedFile> {
        val absoluteUrl = AppUrlUtils.toAbsolute(rawUrl)

        return try {
            val response = RetrofitClient.api.downloadFile(absoluteUrl)
            if (!response.isSuccessful) {
                return ApiResult.Error(downloadErrorMessage(response.code()))
            }

            val body = response.body() ?: return ApiResult.Error("Document introuvable.")
            val mimeType = body.contentType()?.toString()
            val fileName = resolveFileName(
                contentDisposition = response.headers()["Content-Disposition"],
                url = absoluteUrl,
                suggestedFileName = suggestedFileName,
                mimeType = mimeType
            )

            val downloadsDir = File(context.cacheDir, "secure_downloads").apply { mkdirs() }
            val outputFile = uniqueFile(downloadsDir, fileName)

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            ApiResult.Success(DownloadedFile(outputFile, mimeType ?: mimeFromFileName(outputFile.name)))
        } catch (exception: Exception) {
            ApiResult.Error(exception.message ?: "Impossible de telecharger le document.")
        }
    }

    private fun openLocalFile(context: Context, file: File, mimeType: String?): ApiResult<Unit> {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Ouvrir le document"))
            ApiResult.Success(Unit)
        } catch (exception: ActivityNotFoundException) {
            ApiResult.Error("Document telecharge, mais aucune application ne peut l'ouvrir.")
        } catch (exception: Exception) {
            ApiResult.Error(exception.message ?: "Impossible d'ouvrir le document.")
        }
    }

    private fun resolveFileName(
        contentDisposition: String?,
        url: String,
        suggestedFileName: String?,
        mimeType: String?
    ): String {
        val fromHeader = contentDisposition
            ?.let { disposition ->
                Regex("filename\\*=UTF-8''([^;]+)").find(disposition)?.groupValues?.getOrNull(1)
                    ?: Regex("filename=\"?([^\";]+)\"?").find(disposition)?.groupValues?.getOrNull(1)
            }
            ?.let { decode(it) }

        val fromUrl = Uri.parse(url).lastPathSegment?.let { decode(it) }
        val baseName = listOf(fromHeader, suggestedFileName, fromUrl)
            .firstOrNull { !it.isNullOrBlank() }
            ?: "document"

        val safeName = sanitizeFileName(baseName)
        return if (safeName.contains(".")) safeName else "$safeName.${extensionFromMime(mimeType) ?: "bin"}"
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val baseName = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isBlank()) "_$index" else "_$index.$extension"
            candidate = File(directory, "$baseName$suffix")
            index++
        }
        return candidate
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace("\\s+".toRegex(), "_")
            .replace("[^A-Za-z0-9._-]".toRegex(), "_")
            .trim('_')
            .ifBlank { "document" }
    }

    private fun mimeFromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun extensionFromMime(mimeType: String?): String? {
        return mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    }

    private fun decode(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    private fun downloadErrorMessage(code: Int): String {
        return when (code) {
            401, 403 -> "Authentification requise. Reconnectez-vous dans l'application puis reessayez."
            404 -> "Document introuvable sur le serveur."
            else -> "Impossible de telecharger le document. Code: $code"
        }
    }

    private data class DownloadedFile(
        val file: File,
        val mimeType: String?
    )
}
