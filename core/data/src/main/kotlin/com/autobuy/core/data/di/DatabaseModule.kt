package com.autobuy.core.data.di

import android.content.Context
import androidx.room.Room
import com.autobuy.core.data.db.AutoBuyDatabase
import com.autobuy.core.data.db.dao.ConfigurationDao
import com.autobuy.core.data.db.dao.ExecutionLogDao
import com.autobuy.core.data.db.dao.ProfileDao
import com.autobuy.core.data.db.dao.SecureRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoBuyDatabase {
        return AutoBuyDatabase.getInstance(context)
    }

    @Provides
    fun provideProfileDao(db: AutoBuyDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideSecureRecordDao(db: AutoBuyDatabase): SecureRecordDao = db.secureRecordDao()

    @Provides
    fun provideExecutionLogDao(db: AutoBuyDatabase): ExecutionLogDao = db.executionLogDao()

    @Provides
    fun provideConfigurationDao(db: AutoBuyDatabase): ConfigurationDao = db.configurationDao()
}
