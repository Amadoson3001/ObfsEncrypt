package com.obfs.encrypt.crypto

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Instrumented integration tests for EncryptionHelper.
 * 
 * These tests run on an Android device/emulator and test:
 * - Full encryption/decryption cycles
 * - File format correctness
 * - Integrity verification
 * - Keyfile support
 * 
 * Note: These tests create temporary files and should clean up after themselves.
 */
@RunWith(AndroidJUnit4::class)
class EncryptionHelperIntegrationTest {

    private lateinit var encryptionHelper: EncryptionHelper
    private lateinit var testDir: File

    @Before
    fun setup() {
        encryptionHelper = EncryptionHelper()
        
        // Create temporary test directory
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        testDir = File(context.cacheDir, "encryption_test_${System.currentTimeMillis()}")
        testDir.mkdirs()
    }

    @After
    fun tearDown() {
        // Clean up test files
        testDir.listFiles()?.forEach { it.delete() }
        testDir.delete()
    }

    // region: Basic Encryption/Decryption Tests

    @Test
    fun encryptAndDecrypt_smallFile_success() = runTest {
        // Create test file
        val testContent = "Hello, World! This is a test file for encryption."
        val inputFile = File(testDir, "test_input.txt").apply {
            writeText(testContent)
        }

        val encryptedFile = File(testDir, "test_output.obfs")
        val decryptedFile = File(testDir, "test_decrypted.txt")

        val password = charArrayOf('t', 'e', 's', 't', '1', '2', '3')

        // Encrypt
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = null,
                    enableIntegrityCheck = false
                )
            }
        }

        // Verify encrypted file exists and is larger than original (due to header/overhead)
        assertTrue("Encrypted file should exist", encryptedFile.exists())
        assertTrue("Encrypted file should be larger than original", encryptedFile.length() > inputFile.length())

        // Verify magic header (now using OBFSv4)
        val header = encryptedFile.readBytes().take(6).toByteArray()
        assertEquals("OBFSv4", String(header, Charsets.UTF_8))

        // Decrypt
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = null,
                    verifyIntegrity = false
                )
                assertTrue("Decryption should succeed", result.success)
            }
        }

        // Verify decrypted content matches original
        val decryptedContent = decryptedFile.readText()
        assertEquals("Decrypted content should match original", testContent, decryptedContent)
    }

    @Test
    fun encryptAndDecrypt_withIntegrityCheck_success() = runTest {
        val testContent = "This file has integrity verification enabled."
        val inputFile = File(testDir, "test_integrity_input.txt").apply {
            writeText(testContent)
        }

        val encryptedFile = File(testDir, "test_integrity_output.obfs")
        val decryptedFile = File(testDir, "test_integrity_decrypted.txt")

        val password = charArrayOf('s', 'e', 'c', 'u', 'r', 'e')

        // Encrypt with integrity check
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = null,
                    enableIntegrityCheck = true
                )
            }
        }

        // Decrypt with integrity verification
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = null,
                    verifyIntegrity = true
                )

                assertTrue("Decryption should succeed", result.success)
                assertNotNull("Integrity result should be present", result.integrityResult)
                assertTrue("Integrity should be valid", result.integrityResult?.isValid == true)
            }
        }

        // Verify content
        val decryptedContent = decryptedFile.readText()
        assertEquals(testContent, decryptedContent)
    }

    // endregion

    // region: Encryption Method Tests

    @Test
    fun encryptWithFastMethod_success() = runTest {
        val testContent = "Fast encryption test"
        val inputFile = File(testDir, "test_fast_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_fast_output.obfs")

        val password = charArrayOf('f', 'a', 's', 't')

        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.FAST,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        assertTrue(encryptedFile.exists())
        
        // Verify method byte in header
        val bytes = encryptedFile.readBytes()
        val methodByte = bytes[6 + 16 + 12] // After magic, salt, nonce
        assertEquals("Method should be FAST (0)", 0, methodByte.toInt())
    }

    @Test
    fun encryptWithStrongMethod_success() = runTest {
        val testContent = "Strong encryption test"
        val inputFile = File(testDir, "test_strong_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_strong_output.obfs")

        val password = charArrayOf('s', 't', 'r', 'o', 'n', 'g')

        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STRONG,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        assertTrue(encryptedFile.exists())
        
        // Verify method byte in header
        val bytes = encryptedFile.readBytes()
        val methodByte = bytes[6 + 16 + 12]
        assertEquals("Method should be STRONG (2)", 2, methodByte.toInt())
    }

    // endregion

    // region: Keyfile Tests

    @Test
    fun encryptAndDecrypt_withKeyfile_success() = runTest {
        val testContent = "Keyfile encryption test"
        val inputFile = File(testDir, "test_keyfile_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_keyfile_output.obfs")
        val decryptedFile = File(testDir, "test_keyfile_decrypted.txt")

        val password = charArrayOf('p', 'a', 's', 's')
        val keyfileBytes = encryptionHelper.generateKeyfile(256)

        // Encrypt with keyfile
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = keyfileBytes,
                    enableIntegrityCheck = false
                )
            }
        }

        // Decrypt with keyfile
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = keyfileBytes,
                    verifyIntegrity = false
                )
                assertTrue("Decryption with keyfile should succeed", result.success)
            }
        }

        val decryptedContent = decryptedFile.readText()
        assertEquals(testContent, decryptedContent)
    }

    @Test
    fun decrypt_withWrongKeyfile_fails() = runTest {
        val testContent = "Wrong keyfile test"
        val inputFile = File(testDir, "test_wrong_keyfile_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_wrong_keyfile_output.obfs")
        val decryptedFile = File(testDir, "test_wrong_keyfile_decrypted.txt")

        val password = charArrayOf('p', 'a', 's', 's')
        val correctKeyfile = encryptionHelper.generateKeyfile(256)
        val wrongKeyfile = encryptionHelper.generateKeyfile(256)

        // Encrypt with correct keyfile
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = correctKeyfile,
                    enableIntegrityCheck = false
                )
            }
        }

        // Decrypt with wrong keyfile - should fail
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length(),
                        keyfileBytes = wrongKeyfile,
                        verifyIntegrity = false
                    )
                    // Should not reach here
                    assertTrue("Decryption with wrong keyfile should fail", false)
                } catch (e: Exception) {
                    // Expected - decryption should fail
                    assertTrue("Should throw security exception", e is SecurityException || e.message?.contains("corrupted") == true)
                }
            }
        }
    }

    // endregion

    // region: Error Handling Tests

    @Test
    fun decrypt_withWrongPassword_throwsException() = runTest {
        val testContent = "Wrong password test"
        val inputFile = File(testDir, "test_wrong_pass_input.txt").apply { writeText(testContent) }
        val encryptedFile = File(testDir, "test_wrong_pass_output.obfs")
        val decryptedFile = File(testDir, "test_wrong_pass_decrypted.txt")

        val correctPassword = charArrayOf('c', 'o', 'r', 'r', 'e', 'c', 't')
        val wrongPassword = charArrayOf('w', 'r', 'o', 'n', 'g')

        // Encrypt with correct password
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = correctPassword,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        // Decrypt with wrong password - should fail
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = wrongPassword,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length()
                    )
                    assertTrue("Decryption with wrong password should fail", false)
                } catch (e: Exception) {
                    assertTrue("Should throw security exception", e is SecurityException || e.message?.contains("password") == true)
                }
            }
        }
    }

    @Test
    fun decrypt_invalidFileFormat_throwsException() = runTest {
        val invalidFile = File(testDir, "invalid.obfs").apply {
            writeText("This is not a valid encrypted file")
        }
        val decryptedFile = File(testDir, "invalid_decrypted.txt")

        val password = charArrayOf('t', 'e', 's', 't')

        FileInputStream(invalidFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = invalidFile.length()
                    )
                    assertTrue("Should throw exception for invalid format", false)
                } catch (e: Exception) {
                    assertTrue("Should throw IllegalArgumentException", e is IllegalArgumentException)
                }
            }
        }
    }

    // endregion

    // region: Large File Tests

    @Test
    fun encryptAndDecrypt_largeFile_success() = runTest {
        // Create a 5MB test file
        val inputFile = File(testDir, "test_large_input.bin")
        val fileSize = 5 * 1024 * 1024 // 5MB
        
        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(1024) { i -> (i % 256).toByte() }
            var written = 0
            while (written < fileSize) {
                val toWrite = minOf(buffer.size, fileSize - written)
                output.write(buffer, 0, toWrite)
                written += toWrite
            }
        }

        val encryptedFile = File(testDir, "test_large_output.obfs")
        val decryptedFile = File(testDir, "test_large_decrypted.bin")

        val password = charArrayOf('l', 'a', 'r', 'g', 'e')

        // Encrypt
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length()
                )
            }
        }

        assertTrue(encryptedFile.exists())

        // Decrypt
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length()
                )
                assertTrue("Decryption should succeed", result.success)
            }
        }

        // Verify file sizes match
        assertEquals("File sizes should match", inputFile.length(), decryptedFile.length())

        // Verify content (sample check for performance)
        val originalBytes = inputFile.readBytes()
        val decryptedBytes = decryptedFile.readBytes()
        assertTrue("Content should match", originalBytes.contentEquals(decryptedBytes))
    }

    // endregion

    // region: Streaming Decryption Tests (OOM Prevention) - Large File Tests

    @Test
    fun encryptAndDecrypt_20MBFile_withIntegrityCheck_streamingNoOOM() = runTest {
        // Test streaming decryption with 20MB file and integrity check
        // This verifies the fix for OutOfMemoryError - should use MessageDigest streaming
        val inputFile = File(testDir, "test_20mb_input.bin")
        val fileSize = 20 * 1024 * 1024 // 20MB

        // Create 20MB test file with pattern
        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(8192) { i -> (i % 256).toByte() }
            var written = 0L
            while (written < fileSize) {
                val toWrite = minOf(buffer.size.toLong(), fileSize - written)
                output.write(buffer, 0, toWrite.toInt())
                written += toWrite
            }
        }

        val encryptedFile = File(testDir, "test_20mb_output.obfs")
        val decryptedFile = File(testDir, "test_20mb_decrypted.bin")

        val password = charArrayOf('s', 't', 'r', 'e', 'a', 'm', '2', '0')

        // Encrypt with integrity check
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    enableIntegrityCheck = true
                )
            }
        }

        assertTrue("Encrypted file should exist", encryptedFile.exists())

        // Decrypt with integrity verification - streaming should NOT cause OOM
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    verifyIntegrity = true
                )

                assertTrue("Decryption should succeed without OOM", result.success)
                assertNotNull("Integrity result should be present", result.integrityResult)
                assertTrue("Integrity should be valid", result.integrityResult?.isValid == true)
            }
        }

        // Verify file sizes match
        assertEquals("File sizes should match", inputFile.length(), decryptedFile.length())

        // Verify content by comparing hashes (more efficient than full byte comparison for large files)
        val originalHash = inputFile.readBytes().let { java.security.MessageDigest.getInstance("SHA-256").digest(it) }
        val decryptedHash = decryptedFile.readBytes().let { java.security.MessageDigest.getInstance("SHA-256").digest(it) }
        assertTrue("Content hashes should match", originalHash.contentEquals(decryptedHash))
    }

    @Test
    fun encryptAndDecrypt_30MBFile_withoutIntegrityCheck_streamingNoOOM() = runTest {
        // Test streaming decryption with 30MB file without integrity check
        val inputFile = File(testDir, "test_30mb_input.bin")
        val fileSize = 30 * 1024 * 1024 // 30MB

        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(8192) { i -> (i % 256).toByte() }
            var written = 0L
            while (written < fileSize) {
                val toWrite = minOf(buffer.size.toLong(), fileSize - written)
                output.write(buffer, 0, toWrite.toInt())
                written += toWrite
            }
        }

        val encryptedFile = File(testDir, "test_30mb_output.obfs")
        val decryptedFile = File(testDir, "test_30mb_decrypted.bin")

        val password = charArrayOf('s', 't', 'r', 'e', 'a', 'm', '3', '0')

        // Encrypt without integrity check
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    enableIntegrityCheck = false
                )
            }
        }

        // Decrypt - streaming should work without buffering all data
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    verifyIntegrity = false
                )
                assertTrue("Decryption should succeed without OOM", result.success)
            }
        }

        assertEquals("File sizes should match", inputFile.length(), decryptedFile.length())
    }

    @Test
    fun encryptAndDecrypt_50MBFile_withKeyfile_streamingNoOOM() = runTest {
        // Test streaming decryption with 50MB file and keyfile - stress test for memory
        val inputFile = File(testDir, "test_50mb_input.bin")
        val fileSize = 50 * 1024 * 1024 // 50MB

        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(8192) { i -> (i % 256).toByte() }
            var written = 0L
            while (written < fileSize) {
                val toWrite = minOf(buffer.size.toLong(), fileSize - written)
                output.write(buffer, 0, toWrite.toInt())
                written += toWrite
            }
        }

        val encryptedFile = File(testDir, "test_50mb_output.obfs")
        val decryptedFile = File(testDir, "test_50mb_decrypted.bin")

        val password = charArrayOf('k', 'e', 'y', 'f', 'i', 'l', 'e', '5', '0')
        val keyfileBytes = encryptionHelper.generateKeyfile(256)

        // Encrypt with keyfile and integrity check
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    keyfileBytes = keyfileBytes,
                    enableIntegrityCheck = true
                )
            }
        }

        // Decrypt with keyfile - streaming should NOT cause OOM even with 50MB
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                val result = encryptionHelper.decrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = encryptedFile.length(),
                    keyfileBytes = keyfileBytes,
                    verifyIntegrity = true
                )

                assertTrue("Decryption with keyfile should succeed without OOM", result.success)
                assertTrue("Integrity should be valid", result.integrityResult?.isValid == true)
            }
        }

        assertEquals("File sizes should match", inputFile.length(), decryptedFile.length())
    }

    @Test
    fun encryptAndDecrypt_chunkBoundaryFiles_streamingCorrectness() = runTest {
        // Test files at various chunk boundaries to ensure correct handling
        // Chunk size is 1MB, test at 1MB, 2MB, 1MB+1byte, 1MB-1byte

        val testSizes = listOf(
            1 * 1024 * 1024,       // Exactly 1 chunk
            1 * 1024 * 1024 - 1,   // Just under 1 chunk
            1 * 1024 * 1024 + 1,   // Just over 1 chunk
            2 * 1024 * 1024,       // Exactly 2 chunks
            2 * 1024 * 1024 + 500  // 2 chunks + 500 bytes
        )

        val password = charArrayOf('c', 'h', 'u', 'n', 'k', 'B', 'o', 'u', 'n', 'd')

        testSizes.forEach { size ->
            val inputFile = File(testDir, "test_chunk_$size.bin")
            FileOutputStream(inputFile).use { output ->
                val buffer = ByteArray(8192) { i -> (i % 256).toByte() }
                var written = 0L
                while (written < size) {
                    val toWrite = minOf(buffer.size.toLong(), size - written)
                    output.write(buffer, 0, toWrite.toInt())
                    written += toWrite
                }
            }

            val encryptedFile = File(testDir, "test_chunk_${size}_output.obfs")
            val decryptedFile = File(testDir, "test_chunk_${size}_decrypted.bin")

            // Encrypt
            FileInputStream(inputFile).use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    encryptionHelper.encrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = inputFile.length(),
                        enableIntegrityCheck = true
                    )
                }
            }

            // Decrypt with streaming
            FileInputStream(encryptedFile).use { input ->
                FileOutputStream(decryptedFile).use { output ->
                    val result = encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length(),
                        verifyIntegrity = true
                    )

                    assertTrue("Decryption should succeed for size $size", result.success)
                    assertTrue("Integrity should be valid for size $size", result.integrityResult?.isValid == true)
                }
            }

            assertEquals("File size $size should match", inputFile.length(), decryptedFile.length())
        }
    }

    @Test
    fun encryptAndDecrypt_corruptedLargeFile_throwsException() = runTest {
        // Test that corruption in large files is properly detected
        val inputFile = File(testDir, "test_corrupt_input.bin")
        val fileSize = 10 * 1024 * 1024 // 10MB

        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(8192) { i -> (i % 256).toByte() }
            var written = 0L
            while (written < fileSize) {
                val toWrite = minOf(buffer.size.toLong(), fileSize - written)
                output.write(buffer, 0, toWrite.toInt())
                written += toWrite
            }
        }

        val encryptedFile = File(testDir, "test_corrupt_output.obfs")
        val decryptedFile = File(testDir, "test_corrupt_decrypted.bin")

        val password = charArrayOf('c', 'o', 'r', 'r', 'u', 'p', 't', 'T', 'e', 's', 't')

        // Encrypt
        FileInputStream(inputFile).use { input ->
            FileOutputStream(encryptedFile).use { output ->
                encryptionHelper.encrypt(
                    inputStream = input,
                    outputStream = output,
                    password = password,
                    method = EncryptionMethod.STANDARD,
                    progressCallback = { _, _, _ -> },
                    totalSize = inputFile.length(),
                    enableIntegrityCheck = false
                )
            }
        }

        // Corrupt bytes in the middle of the encrypted file
        val encryptedBytes = encryptedFile.readBytes()
        val corruptPosition = encryptedBytes.size / 2
        encryptedBytes[corruptPosition] = (encryptedBytes[corruptPosition].toInt() xor 0xFF).toByte()
        encryptedFile.writeBytes(encryptedBytes)

        // Decrypt - should fail with AEADBadTagException
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(decryptedFile).use { output ->
                try {
                    encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length(),
                        verifyIntegrity = false
                    )
                    assertTrue("Decryption of corrupted file should fail", false)
                } catch (e: Exception) {
                    assertTrue("Should throw AEADBadTagException or SecurityException",
                        e is javax.crypto.AEADBadTagException || e is SecurityException)
                }
            }
        }
    }

    @Test
    fun encryptAndDecrypt_allEncryptionMethods_largeFile_streamingSuccess() = runTest {
        // Test streaming decryption with all encryption methods on large file
        val inputFile = File(testDir, "test_methods_input.bin")
        val fileSize = 5 * 1024 * 1024 // 5MB

        FileOutputStream(inputFile).use { output ->
            val buffer = ByteArray(8192) { i -> (i % 256).toByte() }
            var written = 0L
            while (written < fileSize) {
                val toWrite = minOf(buffer.size.toLong(), fileSize - written)
                output.write(buffer, 0, toWrite.toInt())
                written += toWrite
            }
        }

        EncryptionMethod.entries.forEach { method ->
            val encryptedFile = File(testDir, "test_methods_${method.name}_output.obfs")
            val decryptedFile = File(testDir, "test_methods_${method.name}_decrypted.bin")
            val password = charArrayOf('m', 'e', 't', 'h', 'o', 'd', 'T', 'e', 's', 't')

            // Encrypt with this method
            FileInputStream(inputFile).use { input ->
                FileOutputStream(encryptedFile).use { output ->
                    encryptionHelper.encrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = method,
                        progressCallback = { _, _, _ -> },
                        totalSize = inputFile.length(),
                        enableIntegrityCheck = true
                    )
                }
            }

            // Decrypt with streaming
            FileInputStream(encryptedFile).use { input ->
                FileOutputStream(decryptedFile).use { output ->
                    val result = encryptionHelper.decrypt(
                        inputStream = input,
                        outputStream = output,
                        password = password,
                        method = method,
                        progressCallback = { _, _, _ -> },
                        totalSize = encryptedFile.length(),
                        verifyIntegrity = true
                    )

                    assertTrue("Decryption should succeed for method ${method.name}", result.success)
                    assertTrue("Integrity should be valid for method ${method.name}", result.integrityResult?.isValid == true)
                }
            }

            assertEquals("File size should match for method ${method.name}", inputFile.length(), decryptedFile.length())
        }
    }

    // endregion
}
