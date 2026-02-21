package com.flow.di

import android.content.Context
import androidx.room.Room
import com.flow.data.local.AppDatabase
import com.flow.data.local.DailyProgressDao
import com.flow.data.local.TaskDao
import com.flow.data.local.TaskCompletionLogDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "flow_db"
        ).addMigrations(AppDatabase.MIGRATION_4_5)
         .build()
    }

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase): TaskDao {
        return db.taskDao()
    }

    @Provides
    @Singleton
    fun provideDailyProgressDao(db: AppDatabase): DailyProgressDao {
        return db.dailyProgressDao()
    }

    @Provides
    @Singleton
    fun provideTaskCompletionLogDao(db: AppDatabase): TaskCompletionLogDao {
        return db.taskCompletionLogDao()
    }
}
