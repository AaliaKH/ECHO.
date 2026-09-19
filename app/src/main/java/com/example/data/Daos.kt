package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SosDao {
    @Query("SELECT * FROM sos_history ORDER BY timestamp DESC")
    fun getAllSos(): Flow<List<SosEntity>>

    @Query("SELECT * FROM sos_history WHERE isMySos = 1 ORDER BY timestamp DESC")
    fun getMySos(): Flow<List<SosEntity>>

    @Query("SELECT * FROM sos_history WHERE messageId = :messageId LIMIT 1")
    suspend fun getSosByMessageId(messageId: String): SosEntity?

    @Query("SELECT * FROM sos_history WHERE isSyncedToBackend = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedSos(): List<SosEntity>

    @Query("SELECT * FROM sos_history WHERE ttl > 0 ORDER BY timestamp DESC")
    suspend fun getActivePacketsForRelay(): List<SosEntity>

    @Query("UPDATE sos_history SET isSyncedToBackend = 1 WHERE messageId = :messageId")
    suspend fun markSynced(messageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSos(sos: SosEntity): Long

    @Update
    suspend fun updateSos(sos: SosEntity)

    @Query("DELETE FROM sos_history WHERE id = :id")
    suspend fun deleteSosById(id: Int)

    @Query("DELETE FROM sos_history")
    suspend fun clearAll()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)
}

@Dao
interface MeshMessageDao {
    @Query("SELECT * FROM mesh_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MeshMessageEntity>>

    @Query("SELECT * FROM mesh_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<MeshMessageEntity>>

    @Query("SELECT * FROM mesh_messages WHERE messageType = :type ORDER BY timestamp DESC")
    fun getMessagesByType(type: String): Flow<List<MeshMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MeshMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MeshMessageEntity>)

    @Query("DELETE FROM mesh_messages")
    suspend fun clearMessages()
}

@Dao
interface OfflineSosLogDao {
    @Query("SELECT * FROM offline_sos_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<OfflineSosLogEntity>>

    @Query("SELECT * FROM offline_sos_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<OfflineSosLogEntity>>

    @Query("SELECT * FROM offline_sos_logs WHERE messageId = :messageId ORDER BY timestamp ASC")
    fun getLogsForMessage(messageId: String): Flow<List<OfflineSosLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OfflineSosLogEntity): Long

    @Query("DELETE FROM offline_sos_logs")
    suspend fun clearLogs()
}
