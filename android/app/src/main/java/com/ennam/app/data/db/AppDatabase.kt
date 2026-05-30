package com.ennam.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ennam.app.data.model.Category
import com.ennam.app.data.model.Entry
import com.ennam.app.data.model.EntryFts

@Database(
    entities = [Entry::class, EntryFts::class, Category::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ennam.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(SeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Seed default categories
            for (cat in Category.DEFAULTS) {
                db.execSQL(
                    """INSERT OR IGNORE INTO categories (slug, label, emoji, layoutType, isDefault, sortOrder)
                       VALUES (?, ?, ?, ?, 1, ?)""",
                    arrayOf(cat.slug, cat.label, cat.emoji, cat.layoutType, cat.sortOrder)
                )
            }
        }
    }
}
