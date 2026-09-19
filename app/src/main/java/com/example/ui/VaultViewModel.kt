package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.DecryptedAccount
import com.example.data.repository.VaultRepository
import com.example.data.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VaultLockState {
    object Loading : VaultLockState
    object Uninitialized : VaultLockState
    object Locked : VaultLockState
    object Unlocked : VaultLockState
}

data class SecurityAudit(
    val totalAccounts: Int = 0,
    val weakPasswordsCount: Int = 0,
    val reusedPasswordsCount: Int = 0,
    val strongPasswordsCount: Int = 0,
    val overallSecurityScore: Int = 100,
    val weakAccounts: List<DecryptedAccount> = emptyList(),
    val reusedAccounts: List<DecryptedAccount> = emptyList()
)

data class PasswordGeneratorConfig(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val currentPassword: String = ""
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VaultRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = VaultRepository(db.accountDao())
    }

    private val _lockState = MutableStateFlow<VaultLockState>(VaultLockState.Loading)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    val rawAccounts: StateFlow<List<DecryptedAccount>> = repository.decryptedAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")
    val favoritesOnly = MutableStateFlow(false)

    val unlockError = MutableStateFlow<String?>(null)
    val snackbarMessage = MutableStateFlow<String?>(null)
    val passwordHint = MutableStateFlow("")

    // Active Dialogs
    val editingAccount = MutableStateFlow<DecryptedAccount?>(null)
    val viewingAccount = MutableStateFlow<DecryptedAccount?>(null)
    val showPasswordGenerator = MutableStateFlow(false)
    val showSecurityAudit = MutableStateFlow(false)
    val showChangePassword = MutableStateFlow(false)
    val showBackupDialog = MutableStateFlow(false)
    val showResetConfirm = MutableStateFlow(false)
    val showHintDialog = MutableStateFlow(false)

    // Generator state
    val generatorConfig = MutableStateFlow(
        PasswordGeneratorConfig(
            currentPassword = CryptoManager.generateSecurePassword()
        )
    )

    // Filtered accounts based on user search & category
    val filteredAccounts: StateFlow<List<DecryptedAccount>> = combine(
        rawAccounts,
        searchQuery,
        selectedCategory,
        favoritesOnly
    ) { list, query, category, favOnly ->
        list.filter { account ->
            val matchesCategory = (category == "All") || account.category.equals(category, ignoreCase = true)
            val matchesFav = !favOnly || account.isFavorite
            val matchesQuery = query.isBlank() ||
                    account.title.contains(query, ignoreCase = true) ||
                    account.username.contains(query, ignoreCase = true) ||
                    account.url.contains(query, ignoreCase = true) ||
                    account.category.contains(query, ignoreCase = true)

            matchesCategory && matchesFav && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Security Audit computed reactively
    val securityAudit: StateFlow<SecurityAudit> = rawAccounts.combine(MutableStateFlow(Unit)) { accounts, _ ->
        computeSecurityAudit(accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityAudit())

    init {
        checkInitialVaultStatus()
    }

    private fun checkInitialVaultStatus() {
        viewModelScope.launch {
            val initialized = repository.isVaultInitialized()
            if (!initialized) {
                _lockState.value = VaultLockState.Uninitialized
            } else {
                _lockState.value = VaultLockState.Locked
                passwordHint.value = repository.getPasswordHint()
            }
        }
    }

    fun setupMasterPassword(password: String, hint: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.setupMasterPassword(password, hint)
            result.onSuccess {
                _lockState.value = VaultLockState.Unlocked
                passwordHint.value = hint
                showSnackbar("Vault initialized with End-to-End Encryption")
                onComplete()
            }.onFailure { error ->
                unlockError.value = error.message ?: "Failed to set up vault"
            }
        }
    }

    fun unlockVault(password: String) {
        viewModelScope.launch {
            unlockError.value = null
            val result = repository.unlockVault(password)
            result.onSuccess {
                _lockState.value = VaultLockState.Unlocked
                unlockError.value = null
            }.onFailure { error ->
                unlockError.value = error.message ?: "Invalid Master Password"
            }
        }
    }

    fun lockVault() {
        repository.lockVault()
        _lockState.value = VaultLockState.Locked
        searchQuery.value = ""
        editingAccount.value = null
        viewingAccount.value = null
        showSnackbar("Vault locked")
    }

    fun saveAccount(account: DecryptedAccount) {
        viewModelScope.launch {
            val result = repository.saveAccount(account)
            result.onSuccess {
                editingAccount.value = null
                // If viewing this account, update viewed instance
                if (viewingAccount.value?.id == account.id) {
                    viewingAccount.value = account
                }
                showSnackbar(if (account.id == 0L) "Account securely encrypted & saved" else "Account updated")
            }.onFailure { error ->
                showSnackbar("Failed to save: ${error.message}")
            }
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            if (viewingAccount.value?.id == id) viewingAccount.value = null
            if (editingAccount.value?.id == id) editingAccount.value = null
            showSnackbar("Account deleted from encrypted vault")
        }
    }

    fun toggleFavorite(id: Long, currentFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, !currentFav)
        }
    }

    fun changeMasterPassword(current: String, new: String, hint: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.changeMasterPassword(current, new, hint)
            result.onSuccess {
                passwordHint.value = hint
                showChangePassword.value = false
                showSnackbar("Master password changed. All records re-encrypted!")
                onSuccess()
            }.onFailure { error ->
                onError(error.message ?: "Failed to change master password")
            }
        }
    }

    fun exportEncryptedBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.exportEncryptedBackup()
            result.onSuccess { json ->
                onResult(json)
            }.onFailure { err ->
                showSnackbar("Export failed: ${err.message}")
            }
        }
    }

    fun importEncryptedBackup(backupJson: String, masterPassword: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.importEncryptedBackup(backupJson, masterPassword)
            result.onSuccess { count ->
                showSnackbar("Successfully imported $count accounts")
                onComplete(true, "Imported $count accounts successfully")
            }.onFailure { err ->
                onComplete(false, err.message ?: "Failed to import backup")
            }
        }
    }

    fun resetVault() {
        viewModelScope.launch {
            repository.resetVault()
            _lockState.value = VaultLockState.Uninitialized
            passwordHint.value = ""
            showResetConfirm.value = false
            showSnackbar("Vault reset completely")
        }
    }

    fun regeneratePassword() {
        val cfg = generatorConfig.value
        val pass = CryptoManager.generateSecurePassword(
            length = cfg.length,
            includeUppercase = cfg.includeUppercase,
            includeLowercase = cfg.includeLowercase,
            includeNumbers = cfg.includeNumbers,
            includeSymbols = cfg.includeSymbols
        )
        generatorConfig.value = cfg.copy(currentPassword = pass)
    }

    fun updateGeneratorConfig(
        length: Int? = null,
        includeUpper: Boolean? = null,
        includeLower: Boolean? = null,
        includeNums: Boolean? = null,
        includeSyms: Boolean? = null
    ) {
        val current = generatorConfig.value
        val newCfg = current.copy(
            length = length ?: current.length,
            includeUppercase = includeUpper ?: current.includeUppercase,
            includeLowercase = includeLower ?: current.includeLowercase,
            includeNumbers = includeNums ?: current.includeNumbers,
            includeSymbols = includeSyms ?: current.includeSymbols
        )
        val pass = CryptoManager.generateSecurePassword(
            length = newCfg.length,
            includeUppercase = newCfg.includeUppercase,
            includeLowercase = newCfg.includeLowercase,
            includeNumbers = newCfg.includeNumbers,
            includeSymbols = newCfg.includeSymbols
        )
        generatorConfig.value = newCfg.copy(currentPassword = pass)
    }

    fun showSnackbar(message: String) {
        snackbarMessage.value = message
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    private fun computeSecurityAudit(accounts: List<DecryptedAccount>): SecurityAudit {
        if (accounts.isEmpty()) {
            return SecurityAudit(
                totalAccounts = 0,
                weakPasswordsCount = 0,
                reusedPasswordsCount = 0,
                strongPasswordsCount = 0,
                overallSecurityScore = 100
            )
        }

        val weak = mutableListOf<DecryptedAccount>()
        val passwordCounts = mutableMapOf<String, Int>()

        for (acc in accounts) {
            val p = acc.password
            if (p.isNotEmpty()) {
                passwordCounts[p] = (passwordCounts[p] ?: 0) + 1
            }
            val strength = CryptoManager.calculatePasswordStrength(p)
            if (strength.score < 60 || p.length < 10) {
                weak.add(acc)
            }
        }

        val reused = accounts.filter { acc ->
            val count = passwordCounts[acc.password] ?: 0
            count > 1 && acc.password.isNotEmpty()
        }

        val strong = accounts.filter { acc ->
            val str = CryptoManager.calculatePasswordStrength(acc.password)
            str.score >= 80 && (passwordCounts[acc.password] ?: 0) == 1
        }

        val weakPenalty = (weak.size.toFloat() / accounts.size.toFloat()) * 40f
        val reusedPenalty = (reused.size.toFloat() / accounts.size.toFloat()) * 40f
        val score = (100f - weakPenalty - reusedPenalty).toInt().coerceIn(10, 100)

        return SecurityAudit(
            totalAccounts = accounts.size,
            weakPasswordsCount = weak.size,
            reusedPasswordsCount = reused.size,
            strongPasswordsCount = strong.size,
            overallSecurityScore = score,
            weakAccounts = weak,
            reusedAccounts = reused
        )
    }
}
