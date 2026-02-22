package com.flow.di

import android.content.Context
import androidx.room.Room
import com.flow.data.local.AchievementDao
import com.flow.data.local.AppDatabase
import com.flow.data.local.DailyProgressDao
import com.flow.data.local.TaskDao
import com.flow.data.local.TaskCompletionLogDao
import com.flow.data.local.TaskStreakDao
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
        )
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()
    }

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideDailyProgressDao(db: AppDatabase): DailyProgressDao = db.dailyProgressDao()

    @Provides
    @Singleton
    fun provideTaskCompletionLogDao(db: AppDatabase): TaskCompletionLogDao = db.taskCompletionLogDao()

    @Provides
    @Singleton
    fun provideTaskStreakDao(db: AppDatabase): TaskStreakDao = db.taskStreakDao()

    @Provides
    @Singleton
    fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()
}
