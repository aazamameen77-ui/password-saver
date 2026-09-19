package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM vault_accounts ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM vault_accounts")
    suspend fun getAllAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM vault_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM vault_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("DELETE FROM vault_accounts")
    suspend fun deleteAllAccounts()

    @Query("UPDATE vault_accounts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    // Vault Configuration
    @Query("SELECT * FROM vault_config WHERE id = 1 LIMIT 1")
    fun getVaultConfig(): Flow<VaultConfigEntity?>

    @Query("SELECT * FROM vault_config WHERE id = 1 LIMIT 1")
    suspend fun getVaultConfigSync(): VaultConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateVaultConfig(config: VaultConfigEntity)

    @Query("DELETE FROM vault_config")
    suspend fun clearVaultConfig()
}
