package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioDao {
    @Query("SELECT * FROM imported_avatars ORDER BY importedAtMs DESC")
    fun observeImportedAvatars(): Flow<List<ImportedAvatarEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedAvatar(avatar: ImportedAvatarEntity)

    @Query("DELETE FROM imported_avatars WHERE id = :id")
    suspend fun deleteImportedAvatar(id: String)

    @Query("SELECT * FROM exported_clips ORDER BY createdAtMs DESC")
    fun observeExportedClips(): Flow<List<ExportedClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedClip(clip: ExportedClipEntity)

    @Query("DELETE FROM exported_clips WHERE id = :id")
    suspend fun deleteExportedClip(id: String)
}
