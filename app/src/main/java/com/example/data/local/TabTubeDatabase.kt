package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, SavedVideoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TabTubeDatabase : RoomDatabase() {
    abstract fun dao(): TabTubeDao

    companion object {
        @Volatile
        private var INSTANCE: TabTubeDatabase? = null

        val DEFAULT_CHANNELS = listOf(
            ChannelEntity("UCKHhA5hN2UohhFDfNXB_cvQ", "Manual do Mundo", "Ciência"),
            ChannelEntity("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt", "Ciência"),
            ChannelEntity("UCsooa4yRKGN_zEE8iknghZA", "TED-Ed", "Educação"),
            ChannelEntity("UC9-y-6csu5WGm29I7JiwpnA", "Computerphile", "Tecnologia"),
            ChannelEntity("UCLA_DiR1FfKNvjuUpBHmylQ", "NASA", "Espaço"),
            ChannelEntity("UC_x5XG1OV2P6uZZ5FSM9Ttw", "Android Developers", "Tecnologia")
        )

        fun getInstance(context: Context): TabTubeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TabTubeDatabase::class.java,
                    "tabtube_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
