package com.vikra.willard.di

import android.content.Context
import androidx.room.Room
import com.vikra.willard.data.local.AppDatabase
import com.vikra.willard.data.local.DailyProgressDao
import com.vikra.willard.data.local.TaskDao
import com.vikra.willard.data.local.TaskCompletionLogDao
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
            "willard_db"
        ).fallbackToDestructiveMigration() // For dev phase
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
