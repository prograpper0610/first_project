package com.autobuy.core.data.db.dao

import androidx.room.*
import com.autobuy.core.data.db.entity.ConfigurationEntity
import com.autobuy.core.data.db.entity.ExecutionLogEntity
import com.autobuy.core.data.db.entity.ProfileEntity
import com.autobuy.core.data.db.entity.SecureRecordEntity
import kotlinx.coroutines.flow.Flow

// ==================== Profile DAO ====================
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE is_active = 1 ORDER BY is_builtin DESC, updated_at DESC")
    fun observeAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE shop_id = :shopId AND is_active = 1 LIMIT 1")
    suspend fun getProfileByShopId(shopId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("UPDATE profiles SET is_active = 0 WHERE id = :id")
    suspend fun softDeleteProfile(id: String)
}

// ==================== SecureRecord DAO ====================
@Dao
interface SecureRecordDao {
    @Query("SELECT * FROM secure_records WHERE record_type = :type ORDER BY is_default DESC, created_at DESC")
    fun observeByType(type: String): Flow<List<SecureRecordEntity>>

    @Query("SELECT * FROM secure_records WHERE id = :id")
    suspend fun getById(id: String): SecureRecordEntity?

    @Query("SELECT * FROM secure_records WHERE record_type = :type AND is_default = 1 LIMIT 1")
    suspend fun getDefault(type: String): SecureRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SecureRecordEntity)

    @Query("UPDATE secure_records SET is_default = 0 WHERE record_type = :type")
    suspend fun clearDefaults(type: String)

    @Query("UPDATE secure_records SET is_default = 1 WHERE id = :id")
    suspend fun setDefault(id: String)

    @Transaction
    suspend fun setDefaultRecord(id: String, type: String) {
        clearDefaults(type)
        setDefault(id)
    }

    @Delete
    suspend fun delete(record: SecureRecordEntity)
}

// ==================== ExecutionLog DAO ====================
@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY started_at DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 50): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE id = :id")
    suspend fun getById(id: String): ExecutionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ExecutionLogEntity)

    @Query("DELETE FROM execution_logs WHERE started_at < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: Long)

    @Query("SELECT COUNT(*) FROM execution_logs WHERE status = 'SUCCESS'")
    suspend fun getSuccessCount(): Int
}

// ==================== Configuration DAO ====================
@Dao
interface ConfigurationDao {
    @Query("SELECT * FROM configurations WHERE `key` = :key")
    suspend fun get(key: String): ConfigurationEntity?

    @Query("SELECT value FROM configurations WHERE `key` = :key")
    fun observeValue(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(config: ConfigurationEntity)

    @Query("DELETE FROM configurations WHERE `key` = :key")
    suspend fun delete(key: String)
}
