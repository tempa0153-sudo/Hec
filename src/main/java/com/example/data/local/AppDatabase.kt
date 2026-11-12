package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppDatabase private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val changeTrigger = MutableStateFlow(System.currentTimeMillis())

    private val daoImpl = object : SavedMediaDao {
        override fun getAllByCustomOrder(): Flow<List<SavedMediaEntity>> {
            return changeTrigger.asStateFlow().map {
                queryList("SELECT * FROM saved_media ORDER BY customOrderIndex ASC")
            }
        }

        override fun getAllByDateDesc(): Flow<List<SavedMediaEntity>> {
            return changeTrigger.asStateFlow().map {
                queryList("SELECT * FROM saved_media ORDER BY addedTimestamp DESC")
            }
        }

        override fun getAllByDateAsc(): Flow<List<SavedMediaEntity>> {
            return changeTrigger.asStateFlow().map {
                queryList("SELECT * FROM saved_media ORDER BY addedTimestamp ASC")
            }
        }

        override fun getAllByTitleAsc(): Flow<List<SavedMediaEntity>> {
            return changeTrigger.asStateFlow().map {
                queryList("SELECT * FROM saved_media ORDER BY title ASC")
            }
        }

        override fun getAllByRatingDesc(): Flow<List<SavedMediaEntity>> {
            return changeTrigger.asStateFlow().map {
                queryList("SELECT * FROM saved_media ORDER BY CAST(imdbRating AS REAL) DESC")
            }
        }

        override fun getAllByRatingAsc(): Flow<List<SavedMediaEntity>> {
            return changeTrigger.asStateFlow().map {
                queryList("SELECT * FROM saved_media ORDER BY CAST(imdbRating AS REAL) ASC")
            }
        }

        override fun isSavedFlow(id: String): Flow<Boolean> {
            return changeTrigger.asStateFlow().map {
                exists(id)
            }
        }

        override fun isSavedFlow(id: String, displayImdbId: String, title: String): Flow<Boolean> {
            return changeTrigger.asStateFlow().map {
                exists(id, displayImdbId, title)
            }
        }

        override suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) {
            val db = readableDatabase
            db.rawQuery("SELECT 1 FROM saved_media WHERE id = ? LIMIT 1", arrayOf(id)).use { cursor ->
                cursor.moveToFirst()
            }
        }

        override suspend fun exists(id: String, displayImdbId: String, title: String): Boolean = withContext(Dispatchers.IO) {
            val db = readableDatabase
            val hasValidId = id.isNotBlank() || displayImdbId.isNotBlank()
            if (hasValidId) {
                db.rawQuery("SELECT 1 FROM saved_media WHERE id = ? OR id = ? LIMIT 1", arrayOf(id, displayImdbId)).use { cursor ->
                    if (cursor.moveToFirst()) return@withContext true
                }
                return@withContext false
            }
            val cleanTitle = title.trim()
            if (cleanTitle.isNotBlank()) {
                db.rawQuery("SELECT 1 FROM saved_media WHERE LOWER(title) = LOWER(?) LIMIT 1", arrayOf(cleanTitle)).use { cursor ->
                    return@withContext cursor.moveToFirst()
                }
            }
            false
        }

        override suspend fun deleteByIdOrTitle(id: String, displayImdbId: String, title: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val hasValidId = id.isNotBlank() || displayImdbId.isNotBlank()
            if (hasValidId) {
                db.delete("saved_media", "id = ? OR id = ?", arrayOf(id, displayImdbId))
            } else {
                val cleanTitle = title.trim()
                if (cleanTitle.isNotBlank()) {
                    db.delete("saved_media", "LOWER(title) = LOWER(?)", arrayOf(cleanTitle))
                }
            }
            notifyDataChanged()
        }

        override suspend fun updateImageByIdOrTitle(id: String, displayImdbId: String, title: String, newImageUrl: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("poster", newImageUrl)
                put("backdrop", newImageUrl)
            }
            val hasValidId = id.isNotBlank() || displayImdbId.isNotBlank()
            if (hasValidId) {
                db.update("saved_media", values, "id = ? OR id = ?", arrayOf(id, displayImdbId))
            } else {
                val cleanTitle = title.trim()
                if (cleanTitle.isNotBlank()) {
                    db.update("saved_media", values, "LOWER(title) = LOWER(?)", arrayOf(cleanTitle))
                }
            }
            notifyDataChanged()
        }

        override suspend fun shiftCustomOrderIndices() = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.execSQL("UPDATE saved_media SET customOrderIndex = customOrderIndex + 1")
            notifyDataChanged()
        }

        override suspend fun getCount(): Int = withContext(Dispatchers.IO) {
            val db = readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM saved_media", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }

        override suspend fun insert(item: SavedMediaEntity) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type)
                put("poster", item.poster)
                put("backdrop", item.backdrop)
                put("year", item.year)
                put("imdbRating", item.imdbRating)
                put("genres", item.genres)
                put("description", item.description)
                put("customOrderIndex", item.customOrderIndex)
                put("addedTimestamp", item.addedTimestamp)
            }
            db.insertWithOnConflict("saved_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyDataChanged()
        }

        override suspend fun updateAll(items: List<SavedMediaEntity>) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (item in items) {
                    val values = ContentValues().apply {
                        put("title", item.title)
                        put("type", item.type)
                        put("poster", item.poster)
                        put("backdrop", item.backdrop)
                        put("year", item.year)
                        put("imdbRating", item.imdbRating)
                        put("genres", item.genres)
                        put("description", item.description)
                        put("customOrderIndex", item.customOrderIndex)
                        put("addedTimestamp", item.addedTimestamp)
                    }
                    db.update("saved_media", values, "id = ?", arrayOf(item.id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyDataChanged()
        }

        override suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.delete("saved_media", "id = ?", arrayOf(id))
            notifyDataChanged()
        }
    }

    fun savedMediaDao(): SavedMediaDao = daoImpl

    private fun notifyDataChanged() {
        changeTrigger.value = System.currentTimeMillis()
    }

    private fun queryList(sql: String): List<SavedMediaEntity> {
        val list = mutableListOf<SavedMediaEntity>()
        val db = readableDatabase
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToEntity(cursor))
            }
        }
        return list
    }

    private fun cursorToEntity(cursor: Cursor): SavedMediaEntity {
        return SavedMediaEntity(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            poster = cursor.getStringOrNull("poster"),
            backdrop = cursor.getStringOrNull("backdrop"),
            year = cursor.getStringOrNull("year"),
            imdbRating = cursor.getStringOrNull("imdbRating"),
            genres = cursor.getStringOrNull("genres"),
            description = cursor.getStringOrNull("description"),
            customOrderIndex = cursor.getInt(cursor.getColumnIndexOrThrow("customOrderIndex")),
            addedTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("addedTimestamp"))
        )
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val idx = getColumnIndex(columnName)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_media (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                poster TEXT,
                backdrop TEXT,
                year TEXT,
                imdbRating TEXT,
                genres TEXT,
                description TEXT,
                customOrderIndex INTEGER NOT NULL DEFAULT 0,
                addedTimestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Upgrade migrations if needed
    }

    companion object {
        private const val DATABASE_NAME = "cinestream_database.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
