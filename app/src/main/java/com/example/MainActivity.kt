package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.DecryptedAccount
import com.example.ui.VaultLockState
import com.example.ui.VaultViewModel
import com.example.ui.dialogs.AccountDetailDialog
import com.example.ui.dialogs.AddEditAccountDialog
import com.example.ui.dialogs.BackupExportImportDialog
import com.example.ui.dialogs.ChangePasswordDialog
import com.example.ui.dialogs.PasswordGeneratorDialog
import com.example.ui.dialogs.SecurityAuditDialog
import com.example.ui.screens.VaultDashboardScreen
import com.example.ui.screens.VaultLockScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VaultAppRoot()
            }
        }
    }
}

@Composable
fun VaultAppRoot(viewModel: VaultViewModel = viewModel()) {
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val accounts by viewModel.filteredAccounts.collectAsStateWithLifecycle()
    val rawAccounts by viewModel.rawAccounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val securityAudit by viewModel.securityAudit.collectAsStateWithLifecycle()
    val unlockError by viewModel.unlockError.collectAsStateWithLifecycle()
    val passwordHint by viewModel.passwordHint.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    // Dialogs state
    val editingAccount by viewModel.editingAccount.collectAsStateWithLifecycle()
    val viewingAccount by viewModel.viewingAccount.collectAsStateWithLifecycle()
    val showPasswordGenerator by viewModel.showPasswordGenerator.collectAsStateWithLifecycle()
    val showSecurityAudit by viewModel.showSecurityAudit.collectAsStateWithLifecycle()
    val showChangePassword by viewModel.showChangePassword.collectAsStateWithLifecycle()
    val showBackupDialog by viewModel.showBackupDialog.collectAsStateWithLifecycle()
    val generatorConfig by viewModel.generatorConfig.collectAsStateWithLifecycle()

    when (lockState) {
        VaultLockState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        VaultLockState.Uninitialized, VaultLockState.Locked -> {
            VaultLockScreen(
                lockState = lockState,
                passwordHint = passwordHint,
                unlockError = unlockError,
                onSetup = { password, hint ->
                    viewModel.setupMasterPassword(password, hint)
                },
                onUnlock = { password ->
                    viewModel.unlockVault(password)
                },
                onResetVault = {
                    viewModel.resetVault()
                }
            )
        }

        VaultLockState.Unlocked -> {
            VaultDashboardScreen(
                accounts = accounts,
                totalAccountsCount = rawAccounts.size,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchQuery.value = it },
                selectedCategory = selectedCategory,
                onCategoryChange = { viewModel.selectedCategory.value = it },
                favoritesOnly = favoritesOnly,
                onFavoritesOnlyChange = { viewModel.favoritesOnly.value = it },
                securityAudit = securityAudit,
                snackbarMessage = snackbarMessage,
                onClearSnackbar = { viewModel.clearSnackbar() },
                onLockVault = { viewModel.lockVault() },
                onOpenAddAccount = { viewModel.editingAccount.value = DecryptedAccount() },
                onAccountClick = { account -> viewModel.viewingAccount.value = account },
                onToggleFavorite = { id, fav -> viewModel.toggleFavorite(id, fav) },
                onOpenGenerator = { viewModel.showPasswordGenerator.value = true },
                onOpenSecurityAudit = { viewModel.showSecurityAudit.value = true },
                onOpenChangePassword = { viewModel.showChangePassword.value = true },
                onOpenBackup = { viewModel.showBackupDialog.value = true },
                onOpenResetVault = { viewModel.resetVault() }
            )
        }
    }

    // Add / Edit Account Dialog
    if (editingAccount != null) {
        AddEditAccountDialog(
            initialAccount = editingAccount,
            onSave = { account -> viewModel.saveAccount(account) },
            onOpenGenerator = { viewModel.showPasswordGenerator.value = true },
            onDismiss = { viewModel.editingAccount.value = null }
        )
    }

    // View Account Details Dialog
    if (viewingAccount != null) {
        AccountDetailDialog(
            account = viewingAccount!!,
            onEdit = {
                val acc = viewingAccount
                viewModel.viewingAccount.value = null
                viewModel.editingAccount.value = acc
            },
            onDelete = {
                viewingAccount?.id?.let { viewModel.deleteAccount(it) }
            },
            onToggleFavorite = {
                viewingAccount?.let { viewModel.toggleFavorite(it.id, it.isFavorite) }
            },
            onDismiss = { viewModel.viewingAccount.value = null }
        )
    }

    // Standalone Password Generator Dialog
    if (showPasswordGenerator) {
        PasswordGeneratorDialog(
            config = generatorConfig,
            onConfigChange = { len, upper, lower, nums, syms ->
                viewModel.updateGeneratorConfig(len, upper, lower, nums, syms)
            },
            onRegenerate = { viewModel.regeneratePassword() },
            onUsePassword = if (editingAccount != null) {
                { pass ->
                    val current = editingAccount ?: DecryptedAccount()
                    viewModel.editingAccount.value = current.copy(password = pass)
                }
            } else null,
            onDismiss = { viewModel.showPasswordGenerator.value = false }
        )
    }

    // Security Audit Dialog
    if (showSecurityAudit) {
        SecurityAuditDialog(
            audit = securityAudit,
            onEditAccount = { account ->
                viewModel.editingAccount.value = account
            },
            onDismiss = { viewModel.showSecurityAudit.value = false }
        )
    }

    // Change Master Password Dialog
    if (showChangePassword) {
        ChangePasswordDialog(
            currentHint = passwordHint,
            onChangePassword = { curr, new, hint, onSuccess, onError ->
                viewModel.changeMasterPassword(curr, new, hint, onSuccess, onError)
            },
            onDismiss = { viewModel.showChangePassword.value = false }
        )
    }

    // Backup Export / Import Dialog
    if (showBackupDialog) {
        BackupExportImportDialog(
            onExport = { callback ->
                viewModel.exportEncryptedBackup(callback)
            },
            onImport = { json, pass, callback ->
                viewModel.importEncryptedBackup(json, pass, callback)
            },
            onDismiss = { viewModel.showBackupDialog.value = false }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
