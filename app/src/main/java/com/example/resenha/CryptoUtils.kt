package com.example.resenha

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES"
    // Chave secreta de 16 caracteres (128 bits).
    // Em produção seria dinâmica, mas para o escopo do trabalho, uma chave simétrica fixa é perfeita!
    private const val SECRET_KEY = "ResenhaUfuSecret"

    fun encrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            data // Se algo falhar, retorna o original
        }
    }

    fun decrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKeySpec = SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            val decodedBytes = Base64.decode(data, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            data // Se a mensagem for antiga (sem criptografia), ele apenas exibe ela normalmente!
        }
    }
}