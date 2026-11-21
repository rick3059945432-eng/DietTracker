package com.diettracker.app.data.preferences

import android.content.Context
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 本地安全存储助手：利用设备硬件私有指纹与 AES 对敏感字段（如自接 API Key）进行密文存储保护
 */
object SecureStorageHelper {

    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val CIPHER_PREFIX = "ENC::v1::"

    private fun getDeviceSecretKey(context: Context): SecretKeySpec {
        val rawSeed = "${context.packageName}#${Build.BOARD}#${Build.BRAND}#${Build.DEVICE}#diet_secure_seed_2026"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(rawSeed.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(sha256.copyOf(16), "AES")
    }

    private fun getFixedIv(): IvParameterSpec {
        // 16 字节固定向量，确保确定性解密
        val ivBytes = byteArrayOf(
            0x31, 0x34, 0x31, 0x35, 0x39, 0x32, 0x36, 0x35,
            0x33, 0x35, 0x38, 0x39, 0x37, 0x39, 0x33, 0x32
        )
        return IvParameterSpec(ivBytes)
    }

    /**
     * 将明文字符串加密为本地密文
     */
    fun encrypt(context: Context, plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, getDeviceSecretKey(context), getFixedIv())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            CIPHER_PREFIX + Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    /**
     * 将密文解密为明文（兼容未加密的历史旧数据）
     */
    fun decrypt(context: Context, cipherText: String): String {
        if (cipherText.isBlank()) return ""
        if (!cipherText.startsWith(CIPHER_PREFIX)) {
            // 历史明文数据：直接返回并允许透明读取
            return cipherText
        }
        return try {
            val base64Data = cipherText.removePrefix(CIPHER_PREFIX)
            val encryptedBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, getDeviceSecretKey(context), getFixedIv())
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            cipherText
        }
    }
}
