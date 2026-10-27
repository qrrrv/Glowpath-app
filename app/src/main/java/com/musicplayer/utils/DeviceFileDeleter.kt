package com.musicplayer.utils

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.musicplayer.data.Song
import java.io.File

/**
 * Удаляет аудиофайл, стоящий за [Song], с устройства.
 *
 * Файлы бывают трёх видов, и у каждого свой способ удаления:
 *  - `content://media/...` (результат «Найти всю музыку») — через MediaStore;
 *  - `file://...` (скачанные из онлайн-поиска) — через File, а если не вышло — через MediaStore;
 *  - документы из системного выбора файлов — переводим в MediaStore или в путь к файлу.
 *
 * На Android 11+ чужие файлы можно удалить только с подтверждением пользователя в системном
 * диалоге ([MediaStore.createDeleteRequest]); на Android 10 — через [RecoverableSecurityException].
 * Поэтому [delete] может вернуть [Step.NeedsConfirmation]: экран показывает системный диалог и,
 * если пользователь согласился, вызывает [delete] ещё раз (повторный вызов безопасен — уже
 * удалённые файлы считаются удалёнными).
 */
object DeviceFileDeleter {

    sealed interface Step {
        /** Все возможные файлы удалены. [failed] — сколько файлов удалить не удалось. */
        data class Finished(val failed: Int) : Step

        /** Нужно показать системный диалог подтверждения. */
        data class NeedsConfirmation(val request: IntentSenderRequest) : Step
    }

    private sealed interface One {
        data object Done : One
        data object Failed : One
        class NeedsUser(val mediaUri: Uri, val sender: android.content.IntentSender?) : One
    }

    private const val MEDIA_AUTHORITY = "media"
    private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"

    /** Блокирующий вызов — запускать не на главном потоке. */
    fun delete(context: Context, songs: List<Song>): Step {
        val resolver = context.contentResolver
        val pending = mutableListOf<Uri>()
        var failed = 0

        for (song in songs) {
            when (val result = deleteOne(context, song.uri)) {
                One.Done -> Unit
                One.Failed -> failed++
                is One.NeedsUser -> {
                    val sender = result.sender
                    if (sender != null) {
                        // Android 10: подтверждение запрашивается на каждый файл отдельно
                        return Step.NeedsConfirmation(IntentSenderRequest.Builder(sender).build())
                    }
                    pending += result.mediaUri
                }
            }
        }

        if (pending.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return try {
                    val intent = MediaStore.createDeleteRequest(resolver, pending)
                    Step.NeedsConfirmation(IntentSenderRequest.Builder(intent.intentSender).build())
                } catch (_: Exception) {
                    Step.Finished(failed + pending.size)
                }
            }
            failed += pending.size
        }
        return Step.Finished(failed)
    }

    private fun deleteOne(context: Context, uri: Uri): One = try {
        when (uri.scheme) {
            // Онлайн-трек без файла на устройстве — удалять нечего
            "http", "https" -> One.Done
            "file" -> deleteByPath(context, uri.path)
            "content" -> deleteContent(context, uri)
            else -> One.Failed
        }
    } catch (_: Exception) {
        One.Failed
    }

    private fun deleteContent(context: Context, uri: Uri): One = when (uri.authority) {
        MEDIA_AUTHORITY -> deleteMedia(context, uri)

        MEDIA_DOCUMENTS_AUTHORITY -> {
            // Документ вида audio:1234 → content://media/external/audio/media/1234
            val id = DocumentsContract.getDocumentId(uri).substringAfter(':').toLongOrNull()
            if (id == null) One.Failed
            else deleteMedia(context, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
        }

        EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY -> deleteByPath(context, externalStoragePath(uri))

        else -> {
            // Прочие провайдеры документов: пробуем штатное удаление, права на запись может не быть
            if (DocumentsContract.deleteDocument(context.contentResolver, uri)) One.Done else One.Failed
        }
    }

    /** primary:Music/a.mp3 → /storage/emulated/0/Music/a.mp3, 1A2B-3C4D:Music/a.mp3 → /storage/1A2B-3C4D/Music/a.mp3 */
    private fun externalStoragePath(uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', missingDelimiterValue = "")
        if (relative.isEmpty()) return null
        @Suppress("DEPRECATION")
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    private fun deleteByPath(context: Context, path: String?): One {
        if (path.isNullOrBlank()) return One.Failed
        val file = File(path)
        if (!file.exists()) return One.Done
        if (file.delete()) {
            // Убираем устаревшую запись из MediaStore, чтобы трек не вернулся при следующем сканировании
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            return One.Done
        }
        // Файл принадлежит не нам — удаляем через MediaStore (с подтверждением, если нужно)
        val mediaUri = findMediaUriByPath(context, path) ?: return One.Failed
        return deleteMedia(context, mediaUri)
    }

    private fun deleteMedia(context: Context, mediaUri: Uri): One = try {
        // 0 удалённых строк — запись уже исчезла, это тоже успех
        context.contentResolver.delete(mediaUri, null, null)
        One.Done
    } catch (e: SecurityException) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> One.NeedsUser(mediaUri, null)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                One.NeedsUser(mediaUri, e.userAction.actionIntent.intentSender)
            else -> One.Failed
        }
    } catch (_: Exception) {
        One.Failed
    }

    @Suppress("DEPRECATION")
    private fun findMediaUriByPath(context: Context, path: String): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.DATA}=?",
            arrayOf(path),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0)
                )
            }
        }
        return null
    }
}
