package com.soloadmin.hub.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns every image and attachment the user adds.
 *
 * User-selected photos are copied into app-private storage (so a gallery deletion cannot break a
 * record) and re-encoded as JPEG with a bounded dimension. Legacy data URLs from the HTML app are
 * written through the same path, which means imported photos behave exactly like native ones.
 */
class MediaStore(private val context: Context) {

    init {
        // Coil needs a plain File for a stored photo name; register the folder once.
        PhotoFiles.init(File(context.filesDir, DIR_PHOTOS))
    }

    private companion object {
        const val TAG = "MediaStore"
        const val MAX_DIMENSION = 1440
        const val JPEG_QUALITY = 88
        const val MAX_BYTES = 12L * 1024L * 1024L
        const val DIR_PHOTOS = "photos"
        const val DIR_ATTACHMENTS = "attachments"
        const val DIR_EXPORTS = "exports"
    }

    private fun photosDir(): File = File(context.filesDir, DIR_PHOTOS).apply { mkdirs() }
    private fun attachmentsDir(): File = File(context.filesDir, DIR_ATTACHMENTS).apply { mkdirs() }
    fun exportsDir(): File = File(context.cacheDir, DIR_EXPORTS).apply { mkdirs() }

    /**
     * Copies an image picked through the system photo picker into private storage.
     * @return relative file name (for Room) or null when the image could not be read.
     */
    suspend fun importImage(uri: Uri, prefix: String = "img"): String? = withContext(Dispatchers.IO) {
        try {
            val dir = photosDir()
            val target = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext null

            val rotation = readRotation(uri)
            val oriented = if (rotation != 0f) rotate(decoded, rotation) else decoded
            val scaled = scaleDown(oriented, MAX_DIMENSION)

            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled !== oriented) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            target.name
        } catch (error: Exception) {
            Log.w(TAG, "Unable to import image", error)
            null
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Image too large to decode", error)
            null
        }
    }

    /** Writes a legacy `data:image/...;base64,...` payload into private storage. */
    suspend fun importDataUrl(dataUrl: String?, prefix: String = "legacy"): String? {
        val bytes = com.soloadmin.hub.domain.logic.BackupMapper.decodeDataUrl(dataUrl) ?: return null
        if (bytes.size > MAX_BYTES) return null
        return withContext(Dispatchers.IO) {
            try {
                val dir = photosDir()
                val target = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
                // Re-encode so every stored image is a consistent JPEG.
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    val scaled = scaleDown(bitmap, MAX_DIMENSION)
                    FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                } else {
                    target.writeBytes(bytes)
                }
                target.name
            } catch (error: Exception) {
                Log.w(TAG, "Unable to store legacy image", error)
                null
            }
        }
    }

    /** Copies a document (e.g. an official-duty order photo) into private storage. */
    suspend fun importAttachment(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val displayName = queryDisplayName(uri) ?: "attachment_${UUID.randomUUID()}"
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
            val target = File(attachmentsDir(), "${UUID.randomUUID()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                if (target.exists()) target.delete()
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (target.length() > MAX_BYTES) {
                target.delete()
                return@withContext null
            }
            target.name
        } catch (error: Exception) {
            Log.w(TAG, "Unable to import attachment", error)
            null
        }
    }

    fun photoFile(relativeName: String?): File? = relativeName?.let {
        if (it.contains('/')) {
            // Legacy entries may hold a bare file name from a desktop download.
            File(it).name.takeIf { name -> name.isNotBlank() }?.let { name -> File(photosDir(), name) }
        } else {
            File(photosDir(), it)
        }
    }

    fun attachmentFile(relativeName: String?): File? = relativeName?.let { File(attachmentsDir(), File(it).name) }

    fun exists(relativeName: String?): Boolean = photoFile(relativeName)?.exists() == true

    suspend fun deleteImage(relativeName: String?) {
        withContext(Dispatchers.IO) {
            relativeName ?: return@withContext
            // Never delete an image another record still points at.
            val file = photoFile(relativeName) ?: return@withContext
            if (file.exists()) file.delete()
        }
    }

    /** Keeps only files referenced by the database (called after imports/deletes). */
    suspend fun pruneOrphans(referencedPhotos: Set<String>, referencedAttachments: Set<String>) {
        withContext(Dispatchers.IO) {
            photosDir().listFiles()?.forEach { file ->
                if (file.isFile && file.name !in referencedPhotos) file.delete()
            }
            attachmentsDir().listFiles()?.forEach { file ->
                if (file.isFile && file.name !in referencedAttachments) file.delete()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun readRotation(uri: Uri): Float = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (_: Exception) {
        0f
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= max) return bitmap
        val ratio = max.toFloat() / largest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}

/**
 * Resolves stored photo names to files for Coil.
 *
 * The app always imports photos through [MediaStore] into one private folder, so a single
 * registered directory is enough and image loading never needs a Context at the call site.
 */
object PhotoFiles {

    @Volatile
    private var directory: File? = null

    fun init(directory: File) {
        this.directory = directory.apply { mkdirs() }
    }

    fun resolve(relativeName: String?): File? {
        val name = relativeName?.trim().orEmpty()
        if (name.isEmpty()) return null
        // Legacy exports sometimes stored a path or a bare downloaded file name.
        val bare = name.substringAfterLast('/')
        val dir = directory ?: return null
        val file = File(dir, bare)
        return file.takeIf { it.exists() }
    }
}

/** Convenience for Coil models: stored name to an existing file, or null. */
fun photoFile(relativeName: String?): File? = PhotoFiles.resolve(relativeName)
