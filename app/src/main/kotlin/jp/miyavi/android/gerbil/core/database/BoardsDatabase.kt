package jp.miyavi.android.gerbil.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NgEntity::class, SiteEntity::class, BoardEntity::class, MenuEntryEntity::class, ThreadSummaryEntity::class, ThreadLogEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 4, to = 5)],
)
abstract class BoardsDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun threadDao(): ThreadDao

    companion object {
        fun create(context: Context): BoardsDatabase =
            Room.databaseBuilder(context, BoardsDatabase::class.java, "boards.db")
                // 版 4（汎用化。板の ID を掲示板ごとにした）より前のデータは移さず、作り直す（公開前のため。spec 003）
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3)
                .build()
    }
}
