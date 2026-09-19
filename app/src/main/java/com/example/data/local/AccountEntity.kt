package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val encryptedUsername: String,
    val usernameIv: String,
    val encryptedPassword: String,
    val passwordIv: String,
    val encryptedUrl: String = "",
    val urlIv: String = "",
    val encryptedNotes: String = "",
    val notesIv: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
