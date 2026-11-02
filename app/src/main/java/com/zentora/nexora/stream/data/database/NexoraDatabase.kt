/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        CommunityPostEntity::class,
        PrivateFriendProfileEntity::class,
        PrivateFriendEntity::class,
        PrivateChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun nexoraDao(): NexoraDao

    companion object {
        /**
         * 2 -> 3 (Vault 35 chat): friends get their server id; messages get the peer's id, the server's event number,
         * a send state and the indexes the chat queries use. The old messages were written by the previous,
         * phone-only chat and never reached anyone, so they are removed instead of being shown as delivered.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `private_friends` ADD COLUMN `nexoraId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("DELETE FROM `private_chat_messages`")
                db.execSQL("ALTER TABLE `private_chat_messages` ADD COLUMN `peerId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `private_chat_messages` ADD COLUMN `serverSeq` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `private_chat_messages` ADD COLUMN `sendState` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_chat_messages_conversationHandle_timestamp` ON `private_chat_messages` (`conversationHandle`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_chat_messages_conversationHandle_isFromMe_isRead` ON `private_chat_messages` (`conversationHandle`, `isFromMe`, `isRead`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_chat_messages_peerId_serverSeq` ON `private_chat_messages` (`peerId`, `serverSeq`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_chat_messages_serverSeq` ON `private_chat_messages` (`serverSeq`)")
            }
        }

        @Volatile
        private var INSTANCE: NexoraDatabase? = null

        fun getInstance(context: Context): NexoraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    "nexora_stream_core.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
