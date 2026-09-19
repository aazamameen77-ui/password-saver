package com.example.data.repository

import android.util.Base64
import com.example.data.local.AccountDao
import com.example.data.local.AccountEntity
import com.example.data.local.VaultConfigEntity
import com.example.data.model.DecryptedAccount
import com.example.data.security.CryptoManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class VaultRepository(
    private val accountDao: AccountDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    @Volatile
    private var activeKey: SecretKey? = null

    private val _isLocked = MutableStateFlow(true)
    val isLocked: Flow<Boolean> = _isLocked.asStateFlow()

    private val _activeKeyVersion = MutableStateFlow(0)

    val vaultConfig: Flow<VaultConfigEntity?> = accountDao.getVaultConfig()

    /**
     * Flow of accounts decrypted using the active Master Key in memory.
     * When locked or key is absent, emits empty list.
     */
    val decryptedAccounts: Flow<List<DecryptedAccount>> = combine(
        accountDao.getAllAccounts(),
        _activeKeyVersion,
        _isLocked
    ) { entities, _, locked ->
        if (locked) {
            emptyList()
        } else {
            val key = activeKey ?: return@combine emptyList()
            entities.map { entity ->
                decryptEntity(entity, key)
            }
        }
    }.flowOn(ioDispatcher)

    private fun decryptEntity(entity: AccountEntity, key: SecretKey): DecryptedAccount {
        val username = try {
            CryptoManager.decrypt(entity.encryptedUsername, entity.usernameIv, key)
        } catch (_: Exception) {
            ""
        }

        val password = try {
            CryptoManager.decrypt(entity.encryptedPassword, entity.passwordIv, key)
        } catch (_: Exception) {
            ""
        }

        val url = try {
            if (entity.encryptedUrl.isNotEmpty()) {
                CryptoManager.decrypt(entity.encryptedUrl, entity.urlIv, key)
            } else ""
        } catch (_: Exception) {
            ""
        }

        val notes = try {
            if (entity.encryptedNotes.isNotEmpty()) {
                CryptoManager.decrypt(entity.encryptedNotes, entity.notesIv, key)
            } else ""
        } catch (_: Exception) {
            ""
        }

        return DecryptedAccount(
            id = entity.id,
            title = entity.title,
            category = entity.category,
            username = username,
            password = password,
            url = url,
            notes = notes,
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Checks whether the vault has been set up with a master password.
     */
    suspend fun isVaultInitialized(): Boolean = withContext(ioDispatcher) {
        val config = accountDao.getVaultConfigSync()
        config != null && config.isInitialized
    }

    /**
     * Retrieves the stored password hint if any.
     */
    suspend fun getPasswordHint(): String = withContext(ioDispatcher) {
        accountDao.getVaultConfigSync()?.passwordHint.orEmpty()
    }

    /**
     * First-time initialization of the Master Password and cryptographic salt.
     */
    suspend fun setupMasterPassword(password: String, hint: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            if (password.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Master password must be at least 4 characters"))
            }

            val salt = CryptoManager.generateSalt()
            val derivedKey = CryptoManager.deriveKey(password.toCharArray(), salt)

            val verifierEncryption = CryptoManager.encrypt(CryptoManager.VERIFIER_PLAINTEXT, derivedKey)

            val config = VaultConfigEntity(
                id = 1,
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                verifierCiphertext = verifierEncryption.ciphertextBase64,
                verifierIv = verifierEncryption.ivBase64,
                passwordHint = hint.trim(),
                isInitialized = true
            )

            accountDao.insertOrUpdateVaultConfig(config)
            activeKey = derivedKey
            _isLocked.value = false
            _activeKeyVersion.value += 1

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unlocks the vault by deriving the key from the entered master password and verifying the integrity token.
     */
    suspend fun unlockVault(password: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val config = accountDao.getVaultConfigSync()
                ?: return@withContext Result.failure(IllegalStateException("Vault is not initialized"))

            val salt = Base64.decode(config.saltBase64, Base64.NO_WRAP)
            val candidateKey = CryptoManager.deriveKey(password.toCharArray(), salt)

            val isValid = CryptoManager.verifyKey(
                verifierCiphertext = config.verifierCiphertext,
                verifierIv = config.verifierIv,
                candidateKey = candidateKey
            )

            if (isValid) {
                activeKey = candidateKey
                _isLocked.value = false
                _activeKeyVersion.value += 1
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Incorrect master password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Locks the vault and purges the active key from memory.
     */
    fun lockVault() {
        activeKey = null
        _isLocked.value = true
        _activeKeyVersion.value += 1
    }

    /**
     * Saves or updates an account with individual AES-256-GCM encryption for all sensitive fields.
     */
    suspend fun saveAccount(account: DecryptedAccount): Result<Long> = withContext(ioDispatcher) {
        val key = activeKey ?: return@withContext Result.failure(IllegalStateException("Vault is locked"))

        try {
            val encryptedUsername = CryptoManager.encrypt(account.username, key)
            val encryptedPassword = CryptoManager.encrypt(account.password, key)
            val encryptedUrl = if (account.url.isNotEmpty()) CryptoManager.encrypt(account.url, key) else null
            val encryptedNotes = if (account.notes.isNotEmpty()) CryptoManager.encrypt(account.notes, key) else null

            val now = System.currentTimeMillis()
            val entity = AccountEntity(
                id = account.id,
                title = account.title.trim().ifEmpty { "Untitled Account" },
                category = account.category,
                encryptedUsername = encryptedUsername.ciphertextBase64,
                usernameIv = encryptedUsername.ivBase64,
                encryptedPassword = encryptedPassword.ciphertextBase64,
                passwordIv = encryptedPassword.ivBase64,
                encryptedUrl = encryptedUrl?.ciphertextBase64 ?: "",
                urlIv = encryptedUrl?.ivBase64 ?: "",
                encryptedNotes = encryptedNotes?.ciphertextBase64 ?: "",
                notesIv = encryptedNotes?.ivBase64 ?: "",
                isFavorite = account.isFavorite,
                createdAt = if (account.id == 0L) now else account.createdAt,
                updatedAt = now
            )

            val rowId = accountDao.insertAccount(entity)
            Result.success(rowId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(ioDispatcher) {
        accountDao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteAccount(id: Long) = withContext(ioDispatcher) {
        accountDao.deleteAccountById(id)
    }

    /**
     * Re-encrypts the entire vault when the master password is changed.
     */
    suspend fun changeMasterPassword(
        currentPassword: String,
        newPassword: String,
        newHint: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val config = accountDao.getVaultConfigSync()
                ?: return@withContext Result.failure(IllegalStateException("Vault not initialized"))

            val currentSalt = Base64.decode(config.saltBase64, Base64.NO_WRAP)
            val currentKey = CryptoManager.deriveKey(currentPassword.toCharArray(), currentSalt)

            if (!CryptoManager.verifyKey(config.verifierCiphertext, config.verifierIv, currentKey)) {
                return@withContext Result.failure(IllegalArgumentException("Current master password is incorrect"))
            }

            // Decrypt all accounts with current key
            val existingEntities = accountDao.getAllAccountsSync()
            val decryptedList = existingEntities.map { decryptEntity(it, currentKey) }

            // Generate new salt and new key
            val newSalt = CryptoManager.generateSalt()
            val newKey = CryptoManager.deriveKey(newPassword.toCharArray(), newSalt)
            val newVerifier = CryptoManager.encrypt(CryptoManager.VERIFIER_PLAINTEXT, newKey)

            // Re-encrypt all accounts with new key
            val reEncryptedEntities = decryptedList.map { item ->
                val encUser = CryptoManager.encrypt(item.username, newKey)
                val encPass = CryptoManager.encrypt(item.password, newKey)
                val encUrl = if (item.url.isNotEmpty()) CryptoManager.encrypt(item.url, newKey) else null
                val encNotes = if (item.notes.isNotEmpty()) CryptoManager.encrypt(item.notes, newKey) else null

                AccountEntity(
                    id = item.id,
                    title = item.title,
                    category = item.category,
                    encryptedUsername = encUser.ciphertextBase64,
                    usernameIv = encUser.ivBase64,
                    encryptedPassword = encPass.ciphertextBase64,
                    passwordIv = encPass.ivBase64,
                    encryptedUrl = encUrl?.ciphertextBase64 ?: "",
                    urlIv = encUrl?.ivBase64 ?: "",
                    encryptedNotes = encNotes?.ciphertextBase64 ?: "",
                    notesIv = encNotes?.ivBase64 ?: "",
                    isFavorite = item.isFavorite,
                    createdAt = item.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            }

            // Save new config and re-encrypted accounts
            val newConfig = VaultConfigEntity(
                id = 1,
                saltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP),
                verifierCiphertext = newVerifier.ciphertextBase64,
                verifierIv = newVerifier.ivBase64,
                passwordHint = newHint.trim(),
                isInitialized = true
            )

            accountDao.insertOrUpdateVaultConfig(newConfig)
            accountDao.insertAccounts(reEncryptedEntities)

            activeKey = newKey
            _activeKeyVersion.value += 1
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports the encrypted vault data as a portable JSON string.
     * All credentials remain strongly encrypted with AES-256-GCM.
     */
    suspend fun exportEncryptedBackup(): Result<String> = withContext(ioDispatcher) {
        try {
            val config = accountDao.getVaultConfigSync()
                ?: return@withContext Result.failure(IllegalStateException("No vault config found"))
            val accounts = accountDao.getAllAccountsSync()

            val root = JSONObject()
            root.put("version", 1)
            root.put("type", "ACCOUNT_SAVER_ENCRYPTED_BACKUP")
            root.put("timestamp", System.currentTimeMillis())

            val configJson = JSONObject().apply {
                put("salt", config.saltBase64)
                put("verifierCiphertext", config.verifierCiphertext)
                put("verifierIv", config.verifierIv)
                put("hint", config.passwordHint)
            }
            root.put("vaultConfig", configJson)

            val accountsArray = JSONArray()
            for (acc in accounts) {
                val item = JSONObject().apply {
                    put("title", acc.title)
                    put("category", acc.category)
                    put("username", acc.encryptedUsername)
                    put("usernameIv", acc.usernameIv)
                    put("password", acc.encryptedPassword)
                    put("passwordIv", acc.passwordIv)
                    put("url", acc.encryptedUrl)
                    put("urlIv", acc.urlIv)
                    put("notes", acc.encryptedNotes)
                    put("notesIv", acc.notesIv)
                    put("isFavorite", acc.isFavorite)
                    put("createdAt", acc.createdAt)
                    put("updatedAt", acc.updatedAt)
                }
                accountsArray.put(item)
            }
            root.put("accounts", accountsArray)

            Result.success(root.toString(2))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports accounts from an encrypted backup JSON file.
     */
    suspend fun importEncryptedBackup(backupJson: String, masterPassword: String): Result<Int> = withContext(ioDispatcher) {
        try {
            val root = JSONObject(backupJson)
            val configObj = root.getJSONObject("vaultConfig")
            val saltBase64 = configObj.getString("salt")
            val verifierCiphertext = configObj.getString("verifierCiphertext")
            val verifierIv = configObj.getString("verifierIv")

            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val backupKey = CryptoManager.deriveKey(masterPassword.toCharArray(), salt)

            if (!CryptoManager.verifyKey(verifierCiphertext, verifierIv, backupKey)) {
                return@withContext Result.failure(IllegalArgumentException("Password does not match this backup"))
            }

            // Current key in this vault
            val currentKey = activeKey ?: return@withContext Result.failure(IllegalStateException("Vault must be unlocked to import"))

            val accountsArray = root.getJSONArray("accounts")
            val newEntities = mutableListOf<AccountEntity>()

            for (i in 0 until accountsArray.length()) {
                val item = accountsArray.getJSONObject(i)
                val title = item.optString("title", "Imported Account")
                val category = item.optString("category", "Logins")
                val isFav = item.optBoolean("isFavorite", false)

                // Decrypt from backup key
                val decUser = CryptoManager.decrypt(item.getString("username"), item.getString("usernameIv"), backupKey)
                val decPass = CryptoManager.decrypt(item.getString("password"), item.getString("passwordIv"), backupKey)
                val decUrl = CryptoManager.decrypt(item.optString("url"), item.optString("urlIv"), backupKey)
                val decNotes = CryptoManager.decrypt(item.optString("notes"), item.optString("notesIv"), backupKey)

                // Encrypt with current vault key
                val encUser = CryptoManager.encrypt(decUser, currentKey)
                val encPass = CryptoManager.encrypt(decPass, currentKey)
                val encUrl = if (decUrl.isNotEmpty()) CryptoManager.encrypt(decUrl, currentKey) else null
                val encNotes = if (decNotes.isNotEmpty()) CryptoManager.encrypt(decNotes, currentKey) else null

                newEntities.add(
                    AccountEntity(
                        title = title,
                        category = category,
                        encryptedUsername = encUser.ciphertextBase64,
                        usernameIv = encUser.ivBase64,
                        encryptedPassword = encPass.ciphertextBase64,
                        passwordIv = encPass.ivBase64,
                        encryptedUrl = encUrl?.ciphertextBase64 ?: "",
                        urlIv = encUrl?.ivBase64 ?: "",
                        encryptedNotes = encNotes?.ciphertextBase64 ?: "",
                        notesIv = encNotes?.ivBase64 ?: "",
                        isFavorite = isFav,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            accountDao.insertAccounts(newEntities)
            Result.success(newEntities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Completely resets the vault and clears all accounts.
     */
    suspend fun resetVault() = withContext(ioDispatcher) {
        accountDao.deleteAllAccounts()
        accountDao.clearVaultConfig()
        lockVault()
    }
}
