package com.autobuy.core.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.autobuy.core.data.db.converter.DateConverter
import com.autobuy.core.data.db.converter.StringListConverter
import com.autobuy.core.data.db.dao.ConfigurationDao
import com.autobuy.core.data.db.dao.ExecutionLogDao
import com.autobuy.core.data.db.dao.ProfileDao
import com.autobuy.core.data.db.dao.SecureRecordDao
import com.autobuy.core.data.db.entity.ConfigurationEntity
import com.autobuy.core.data.db.entity.ExecutionLogEntity
import com.autobuy.core.data.db.entity.ProfileEntity
import com.autobuy.core.data.db.entity.SecureRecordEntity

/**
 * AutoBuy 로컬 데이터베이스.
 *
 * 주의: 이 DB 자체는 SQLite 파일로 저장되지만,
 * 민감 데이터(SecureRecordEntity)의 모든 필드는 AES-256-GCM으로 이미 암호화된 상태로 저장됩니다.
 * 추가 보안을 위해 SQLCipher 연동도 고려할 수 있습니다.
 */
@Database(
    entities = [
        ProfileEntity::class,
        SecureRecordEntity::class,
        ExecutionLogEntity::class,
        ConfigurationEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverter::class, StringListConverter::class)
abstract class AutoBuyDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun secureRecordDao(): SecureRecordDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun configurationDao(): ConfigurationDao

    companion object {
        private const val DB_NAME = "autobuy.db"

        @Volatile
        private var INSTANCE: AutoBuyDatabase? = null

        fun getInstance(context: Context): AutoBuyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoBuyDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
