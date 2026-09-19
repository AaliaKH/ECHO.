package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SosEntity::class,
        UserProfileEntity::class,
        MeshMessageEntity::class,
        OfflineSosLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun sosDao(): SosDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun meshMessageDao(): MeshMessageDao
    abstract fun offlineSosLogDao(): OfflineSosLogDao

    companion object {
        @Volatile
        private var INSTANCE: EchoDatabase? = null

        fun getDatabase(context: Context): EchoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EchoDatabase::class.java,
                    "echo_mesh_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
