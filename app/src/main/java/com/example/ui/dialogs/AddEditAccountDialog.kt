package com.example.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.DecryptedAccount
import com.example.data.security.CryptoManager
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.components.SecurityBadge

private val CATEGORIES = listOf("Logins", "Banking", "Social", "Work", "Personal", "Streaming", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountDialog(
    initialAccount: DecryptedAccount?,
    onSave: (DecryptedAccount) -> Unit,
    onOpenGenerator: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initialAccount != null && initialAccount.id != 0L

    var title by remember { mutableStateOf(initialAccount?.title.orEmpty()) }
    var category by remember { mutableStateOf(initialAccount?.category.takeIf { !it.isNullOrBlank() } ?: "Logins") }
    var username by remember { mutableStateOf(initialAccount?.username.orEmpty()) }
    var password by remember { mutableStateOf(initialAccount?.password.orEmpty()) }
    var url by remember { mutableStateOf(initialAccount?.url.orEmpty()) }
    var notes by remember { mutableStateOf(initialAccount?.notes.orEmpty()) }
    var isFavorite by remember { mutableStateOf(initialAccount?.isFavorite ?: false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    val strength = remember(password) {
        CryptoManager.calculatePasswordStrength(password)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isEdit) "Edit Encrypted Account" else "Add New Account",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { isFavorite = !isFavorite },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecurityBadge(isCompact = true)

                // Service / Account Name
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Account / Service Name *") },
                    placeholder = { Text("e.g. Google, GitHub, Chase Bank") },
                    leadingIcon = {
                        Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Account name is required") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_title_input")
                )

                // Category selector
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Username / Email
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Email") },
                    placeholder = { Text("user@example.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_username_input")
                )

                // Password with Show/Hide & Generator
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter or generate password") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = if (passwordVisible) FontFamily.Monospace else FontFamily.Default
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_password_input")
                )

                if (password.isNotEmpty()) {
                    PasswordStrengthBar(strength = strength)
                }

                // Quick Generate Button
                OutlinedButton(
                    onClick = onOpenGenerator,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Strong Password")
                }

                // Website URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL (Optional)") },
                    placeholder = { Text("https://example.com/login") },
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Secure Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Secure Notes (Encrypted)") },
                    placeholder = { Text("2FA backup codes, PIN, security questions...") },
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    onSave(
                        DecryptedAccount(
                            id = initialAccount?.id ?: 0L,
                            title = title.trim(),
                            category = category,
                            username = username.trim(),
                            password = password,
                            url = url.trim(),
                            notes = notes.trim(),
                            isFavorite = isFavorite,
                            createdAt = initialAccount?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                modifier = Modifier.testTag("save_account_button")
            ) {
                Text(if (isEdit) "Update Account" else "Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
