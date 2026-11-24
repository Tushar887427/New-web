package com.soloadmin.hub.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.soloadmin.hub.data.backup.BackupCodec
import com.soloadmin.hub.domain.model.BackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Writes backup/export documents to cache and hands out shareable URIs through FileProvider.
 * Nothing here needs storage permissions: files live in app-private space and are shared with
 * temporary read grants.
 */
class BackupFilesService(private val context: Context, private val mediaStore: MediaStore) {

    private val authority: String get() = "${context.packageName}.fileprovider"

    suspend fun writeJsonBackup(snapshot: BackupSnapshot, today: LocalDate = LocalDate.now()): File =
        withContext(Dispatchers.IO) {
            val name = "solo_admin_backup_$today.json"
            val file = File(mediaStore.exportsDir(), name)
            file.writeText(BackupCodec.encode(snapshot, today))
            file
        }

    suspend fun writeCsv(name: String, content: String): File = withContext(Dispatchers.IO) {
        val file = File(mediaStore.exportsDir(), name)
        // UTF-8 BOM so Excel/Numbers render ₹ and Indian text correctly.
        file.writeText("\uFEFF$content")
        file
    }

    suspend fun writePdf(name: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File(mediaStore.exportsDir(), name)
        file.writeBytes(bytes)
        file
    }

    /** Copies an incoming content:// document into cache and returns plain text. */
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("The selected file could not be opened.")
        if (bytes.size > MAX_IMPORT_BYTES) {
            throw IllegalStateException("That file is larger than 32 MB. Choose the JSON backup file.")
        }
        decodeText(bytes)
    }

    /** Some browsers save .json as UTF-16 or with a BOM; normalise before parsing. */
    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0xFF && second == 0xFE) return String(bytes, Charsets.UTF_16LE)
            if (first == 0xFE && second == 0xFF) return String(bytes, Charsets.UTF_16BE)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return utf8.removePrefix("\uFEFF")
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        mediaStore.exportsDir().listFiles()?.forEach { it.delete() }
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    fun shareIntent(file: File, mimeType: String, title: String): Intent {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 32L * 1024 * 1024
    }
}
