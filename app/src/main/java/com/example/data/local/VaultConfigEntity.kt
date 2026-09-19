package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_config")
data class VaultConfigEntity(
    @PrimaryKey val id: Int = 1,
    val saltBase64: String,
    val verifierCiphertext: String,
    val verifierIv: String,
    val passwordHint: String = "",
    val isInitialized: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
