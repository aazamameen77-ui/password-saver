package com.example.data.model

data class DecryptedAccount(
    val id: Long = 0,
    val title: String = "",
    val category: String = "Logins",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
