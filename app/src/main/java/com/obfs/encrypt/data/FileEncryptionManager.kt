package com.obfs.encrypt.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.obfs.encrypt.crypto.DecryptionResult
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.crypto.EncryptionMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager for file encryption/decryption operations.
 * Consolidates boilerplate for URI handling, stream management, and output file creation.
 */
@Singleton
class FileEncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val encryptionHelper: EncryptionHelper,
    private val appDirectoryManager: AppDirectoryManager
) {

    /**
     * Encrypt a file from a Uri to an output Uri.
     */
    suspend fun encryptUri(
        sourceUri: Uri,
        outputUri: Uri,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        enableIntegrityCheck: Boolean = false,
        keyfileBytes: ByteArray? = null,
        isPaused: StateFlow<Boolean> = MutableStateFlow(false),
        progressCallback: suspend (current: Long, total: Long, startTime: Long) -> Unit
    ) {
        val cr = context.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)
            ?: throw IllegalArgumentException("Could not read source file")
        val fileSize = sourceFile.length()

        cr.openInputStream(sourceUri)?.use { inputStream ->
            cr.openOutputStream(outputUri)?.use { outputStream ->
                encryptionHelper.encrypt(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    password = password,
                    method = method,
                    progressCallback = progressCallback,
                    totalSize = fileSize,
                    keyfileBytes = keyfileBytes,
                    enableIntegrityCheck = enableIntegrityCheck,
                    isPaused = isPaused
                )
            } ?: throw IllegalStateException("Could not open output stream")
        } ?: throw IllegalStateException("Could not open input stream")
    }

    /**
     * Decrypt a file from a Uri to an output Uri.
     */
    suspend fun decryptUri(
        sourceUri: Uri,
        outputUri: Uri,
        password: CharArray,
        verifyIntegrity: Boolean = true,
        keyfileBytes: ByteArray? = null,
        isPaused: StateFlow<Boolean> = MutableStateFlow(false),
        progressCallback: suspend (current: Long, total: Long, startTime: Long) -> Unit
    ): DecryptionResult {
        val cr = context.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(context, sourceUri)
            ?: throw IllegalArgumentException("Could not read source file")
        val fileSize = sourceFile.length()

        return cr.openInputStream(sourceUri)?.use { inputStream ->
            cr.openOutputStream(outputUri)?.use { outputStream ->
                encryptionHelper.decrypt(
                    inputStream = inputStream,
                    outputStream = outputStream,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = progressCallback,
                    totalSize = fileSize,
                    keyfileBytes = keyfileBytes,
                    verifyIntegrity = verifyIntegrity,
                    isPaused = isPaused
                )
            } ?: throw IllegalStateException("Could not open output stream")
        } ?: throw IllegalStateException("Could not open input stream")
    }

    /**
     * Create an appropriate output file for encryption/decryption.
     */
    fun createOutputFile(
        inputUri: Uri, 
        encrypt: Boolean, 
        customOutputDirUri: Uri? = null
    ): Uri {
        val cr = context.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(context, inputUri)
            ?: throw IllegalArgumentException("Could not access source file")
        
        val outputName = if (encrypt) {
            "${sourceFile.name}.obfs"
        } else {
            sourceFile.name?.removeSuffix(".obfs") ?: "decrypted_${System.currentTimeMillis()}"
        }

        val mimeType = if (encrypt) "application/octet-stream" else "*/*"

        // Priority 1: Custom Output Directory (SAF)
        if (customOutputDirUri != null) {
            val dir = DocumentFile.fromTreeUri(context, customOutputDirUri)
                ?: throw IllegalStateException("Output directory not found or not writable")
            val target = createUniqueFile(dir, outputName, mimeType)
            return target.uri
        }

        // Priority 2: Same directory as source (if writable SAF)
        val parent = sourceFile.parentFile
        if (parent != null && parent.canWrite()) {
            val target = createUniqueFile(parent, outputName, mimeType)
            return target.uri
        }

        // Priority 3: Default App Directory
        val fallbackDir = appDirectoryManager.getOutputDirectory()
            ?: throw IllegalStateException("Could not access default output directory")
        val fallbackDocFile = DocumentFile.fromFile(fallbackDir)
        // Note: DocumentFile.fromFile might not be fully functional for creation in some contexts,
        // but for public Documents/ObfsEncrypt it should work or we use raw File API.
        
        val uniqueFile = uniqueFileRaw(fallbackDir, outputName)
        return Uri.fromFile(uniqueFile)
    }

    private fun createUniqueFile(parent: DocumentFile, name: String, mimeType: String): DocumentFile {
        var finalName = name
        var counter = 1
        while (parent.findFile(finalName) != null) {
            val base = if (name.contains(".")) name.substringBeforeLast(".") else name
            val ext = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
            finalName = "$base ($counter)$ext"
            counter++
        }
        return parent.createFile(mimeType, finalName) 
            ?: throw IllegalStateException("Failed to create output file")
    }

    private fun uniqueFileRaw(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast(".")
        val ext = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
        var counter = 1
        while (f.exists()) {
            f = File(dir, "$base ($counter)$ext")
            counter++
        }
        return f
    }
}
