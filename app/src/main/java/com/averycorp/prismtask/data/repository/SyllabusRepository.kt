package com.averycorp.prismtask.data.repository

import android.content.Context
import android.net.Uri
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.data.remote.api.SyllabusConfirmRequest
import com.averycorp.prismtask.data.remote.api.SyllabusConfirmResponse
import com.averycorp.prismtask.data.remote.api.SyllabusParseResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyllabusRepository
@Inject
constructor(
    private val api: PrismTaskApi
) {
    suspend fun parseSyllabus(uri: Uri, context: Context): SyllabusParseResponse {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open file")
        val bytes = inputStream.use { it.readBytes() }

        if (bytes.size > MAX_FILE_SIZE) {
            throw FileTooLargeException()
        }

        val requestBody = bytes.toRequestBody("application/pdf".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "syllabus.pdf", requestBody)
        return api.parseSyllabus(part)
    }

    suspend fun confirmSyllabus(request: SyllabusConfirmRequest): SyllabusConfirmResponse {
        return api.confirmSyllabus(request)
    }

    companion object {
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 MB
    }
}

class FileTooLargeException : Exception("PDF must be under 10MB")
