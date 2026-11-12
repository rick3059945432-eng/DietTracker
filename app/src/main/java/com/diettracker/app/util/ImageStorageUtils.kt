package com.diettracker.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object ImageStorageUtils {
    /**
     * 将外部 content:// URI 复制到 App 私有目录 (filesDir)，避免重启后临时权限失效导致图片变空白。
     */
    suspend fun copyUriToInternalStorage(
        context: Context,
        sourceUri: Uri,
        prefix: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val destFile = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 清理不再使用的旧版本内部图片文件，节省空间。
     */
    fun cleanupOldImages(context: Context, prefix: String, activeUriString: String?) {
        val activeFileName = activeUriString?.let { Uri.parse(it).lastPathSegment }
        context.filesDir.listFiles { file ->
            file.name.startsWith("${prefix}_") && file.name != activeFileName
        }?.forEach { it.delete() }
    }

    /**
     * 校验已保存的 URI 是否仍然有效可用（过滤掉历史遗留的失效 content:// 或已删除文件）。
     */
    fun validateSavedUri(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path
                if (path != null && File(path).exists()) uriString else null
            }
            // 旧版本残留的 content:// 在重启后无持久权限，安全回退为 null
            "content" -> null
            else -> uriString
        }
    }

    fun createTempCameraUri(context: Context, prefix: String = "camera_temp"): Uri {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDimension = 1024
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > maxDimension || height > maxDimension) {
            maxDimension.toFloat() / maxOf(width, height)
        } else 1.0f

        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else bitmap

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 千焦 (kJ) 转千卡 (kcal)
     */
    fun kjToKcal(kj: Float): Int = (kj / 4.184f).roundToInt()

    /**
     * 格式化数量或营养素数值（保留最多 1 位小数，整数不带 .0）
     */
    fun formatOneDecimal(value: Float): String {
        val rounded = (value * 10f).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }
}
