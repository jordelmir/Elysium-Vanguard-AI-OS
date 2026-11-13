package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persists the temporary Supabase identity only as Android Keystore AES-GCM ciphertext. */
internal class NexusAuthSessionManager(context: Context) : SessionManager {
    private val file = AtomicFile(File(context.noBackupFilesDir, "nexus-auth-session-v1.bin"))
    private val mutex = Mutex()

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val plaintext = Json.encodeToString(UserSession.serializer(), session).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            require(iv.size == GCM_IV_BYTES)

            val stream = file.startWrite()
            try {
                stream.write(iv.size)
                stream.write(iv)
                stream.write(ciphertext)
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.baseFile.exists()) return@withLock null
            runCatching {
                val bytes = file.openRead().use { it.readBytes() }
                require(bytes.size in (1 + GCM_IV_BYTES + GCM_TAG_BYTES)..MAX_SESSION_FILE_BYTES)
                val ivLength = bytes[0].toInt() and 0xff
                require(ivLength == GCM_IV_BYTES && bytes.size > 1 + ivLength)
                val iv = bytes.copyOfRange(1, 1 + ivLength)
                val ciphertext = bytes.copyOfRange(1 + ivLength, bytes.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BYTES * 8, iv))
                val json = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
                Json.decodeFromString(UserSession.serializer(), json)
            }.getOrElse {
                // Corrupt or invalidated local credentials are discarded; the client can sign in again.
                file.delete()
                null
            }
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "elysium-nexus-cloud-auth-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val MAX_SESSION_FILE_BYTES = 32 * 1024
    }
}
