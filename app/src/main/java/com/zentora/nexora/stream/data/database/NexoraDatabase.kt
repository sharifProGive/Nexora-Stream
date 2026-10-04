/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zentora.nexora.stream.data.database.dao.NexoraDao
import com.zentora.nexora.stream.data.database.entities.*

@Database(
    entities = [
        VideoEntity::class,
        InteractionEntity::class,
        CommentEntity::class,
        ChannelEntity::class,
        HistoryEntity::class,
        NotesEntity::class,
        CommunityPostEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun nexoraDao(): NexoraDao

    companion object {
        @Volatile
        private var INSTANCE: NexoraDatabase? = null

        fun getInstance(context: Context): NexoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    "nexora_stream_core.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
