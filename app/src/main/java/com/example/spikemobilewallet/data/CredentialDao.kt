package com.example.spikemobilewallet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials")
    fun getAll(): Flow<List<StoredCredential>>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getById(id: String): StoredCredential?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: StoredCredential)

    @Delete
    suspend fun delete(credential: StoredCredential)

    @Query("DELETE FROM credentials")
    suspend fun deleteAll()
}
