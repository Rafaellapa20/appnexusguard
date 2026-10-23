package com.nexusguard.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.local.NexusGuardDatabase
import com.nexusguard.android.domain.AllUnlockedEntitlements
import com.nexusguard.android.domain.EntitlementsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("nexus_guard_prefs") }
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): NexusGuardDatabase {
        return Room.databaseBuilder(
            context,
            NexusGuardDatabase::class.java,
            "nexus_guard_history.db"
        ).addMigrations(
            NexusGuardDatabase.MIGRATION_1_2,
            NexusGuardDatabase.MIGRATION_2_3,
            NexusGuardDatabase.MIGRATION_3_4
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDao(database: NexusGuardDatabase): NexusGuardDao {
        return database.dao()
    }

    @Provides
    @Singleton
    fun provideEntitlementsRepository(
        impl: AllUnlockedEntitlements
    ): EntitlementsRepository {
        return impl
    }
}
