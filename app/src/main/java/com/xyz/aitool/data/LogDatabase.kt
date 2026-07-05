package com.xyz.aitool.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

private const val DATABASE_NAME = "ai_tool_logs.db"
private const val DATABASE_VERSION = 1
private const val RETENTION_DAYS = 7L
private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

class LogDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE risk_hits (
                id INTEGER PRIMARY KEY,
                time_millis INTEGER NOT NULL,
                text TEXT NOT NULL,
                matched_rules TEXT NOT NULL,
                score INTEGER NOT NULL,
                source TEXT NOT NULL,
                fingerprint TEXT NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_risk_hits_time ON risk_hits(time_millis DESC)")
        db.execSQL(
            """
            CREATE TABLE video_logs (
                id INTEGER PRIMARY KEY,
                time_millis INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                app_label TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                tags TEXT NOT NULL,
                raw_text TEXT NOT NULL,
                source TEXT NOT NULL,
                fingerprint TEXT NOT NULL UNIQUE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_video_logs_time ON video_logs(time_millis DESC)")
        db.execSQL(
            """
            CREATE TABLE operation_logs (
                id INTEGER PRIMARY KEY,
                time_millis INTEGER NOT NULL,
                action TEXT NOT NULL,
                message TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_operation_logs_time ON operation_logs(time_millis DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getHits(limit: Int): List<RiskHit> {
        cleanupExpired()
        return readableDatabase.query(
            "risk_hits",
            null,
            null,
            null,
            null,
            null,
            "time_millis DESC",
            limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toRiskHit())
                }
            }
        }
    }

    fun insertHit(hit: RiskHit, maxRows: Int) {
        cleanupExpired()
        writableDatabase.insertWithOnConflict(
            "risk_hits",
            null,
            hit.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        trimTable("risk_hits", maxRows)
    }

    fun clearHits() {
        writableDatabase.delete("risk_hits", null, null)
    }

    fun getVideoLogs(limit: Int): List<ParsedVideoLog> {
        cleanupExpired()
        return readableDatabase.query(
            "video_logs",
            null,
            null,
            null,
            null,
            null,
            "time_millis DESC",
            limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toParsedVideoLog())
                }
            }
        }
    }

    fun insertVideoLog(log: ParsedVideoLog, maxRows: Int) {
        cleanupExpired()
        writableDatabase.insertWithOnConflict(
            "video_logs",
            null,
            log.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        trimTable("video_logs", maxRows)
    }

    fun clearVideoLogs() {
        writableDatabase.delete("video_logs", null, null)
    }

    fun getOperationLogs(limit: Int): List<OperationLog> {
        cleanupExpired()
        return readableDatabase.query(
            "operation_logs",
            null,
            null,
            null,
            null,
            null,
            "time_millis DESC",
            limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toOperationLog())
                }
            }
        }
    }

    fun insertOperationLog(log: OperationLog, maxRows: Int) {
        cleanupExpired()
        writableDatabase.insertWithOnConflict(
            "operation_logs",
            null,
            log.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        trimTable("operation_logs", maxRows)
    }

    fun clearOperationLogs() {
        writableDatabase.delete("operation_logs", null, null)
    }

    fun clearAllRecordLogs() {
        val db = writableDatabase
        db.delete("risk_hits", null, null)
        db.delete("video_logs", null, null)
        db.delete("operation_logs", null, null)
    }

    fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        val db = writableDatabase
        db.delete("risk_hits", "time_millis < ?", arrayOf(cutoff.toString()))
        db.delete("video_logs", "time_millis < ?", arrayOf(cutoff.toString()))
        db.delete("operation_logs", "time_millis < ?", arrayOf(cutoff.toString()))
    }

    private fun trimTable(tableName: String, maxRows: Int) {
        writableDatabase.execSQL(
            """
            DELETE FROM $tableName
            WHERE id NOT IN (
                SELECT id FROM $tableName
                ORDER BY time_millis DESC
                LIMIT ?
            )
            """.trimIndent(),
            arrayOf(maxRows),
        )
    }

    private fun RiskHit.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("time_millis", timeMillis)
            put("text", text)
            put("matched_rules", matchedRules.toJsonArrayString())
            put("score", score)
            put("source", source)
            put("fingerprint", text.take(80))
        }
    }

    private fun ParsedVideoLog.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("time_millis", timeMillis)
            put("package_name", packageName)
            put("app_label", appLabel)
            put("title", title)
            put("content", content)
            put("tags", tags.toJsonArrayString())
            put("raw_text", rawText)
            put("source", source)
            put("fingerprint", (packageName + title + content + tags.joinToString()).take(160))
        }
    }

    private fun OperationLog.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("time_millis", timeMillis)
            put("action", action)
            put("message", message)
        }
    }

    private fun Cursor.toRiskHit(): RiskHit {
        return RiskHit(
            id = getLong(column("id")),
            timeMillis = getLong(column("time_millis")),
            text = getString(column("text")).orEmpty(),
            matchedRules = getString(column("matched_rules")).orEmpty().toStringList(),
            score = getInt(column("score")),
            source = getString(column("source")).orEmpty(),
        )
    }

    private fun Cursor.toParsedVideoLog(): ParsedVideoLog {
        return ParsedVideoLog(
            id = getLong(column("id")),
            timeMillis = getLong(column("time_millis")),
            packageName = getString(column("package_name")).orEmpty(),
            appLabel = getString(column("app_label")).orEmpty(),
            title = getString(column("title")).orEmpty(),
            content = getString(column("content")).orEmpty(),
            tags = getString(column("tags")).orEmpty().toStringList(),
            rawText = getString(column("raw_text")).orEmpty(),
            source = getString(column("source")).orEmpty(),
        )
    }

    private fun Cursor.toOperationLog(): OperationLog {
        return OperationLog(
            id = getLong(column("id")),
            timeMillis = getLong(column("time_millis")),
            action = getString(column("action")).orEmpty(),
            message = getString(column("message")).orEmpty(),
        )
    }

    private fun Cursor.column(name: String): Int = getColumnIndexOrThrow(name)

    private fun List<String>.toJsonArrayString(): String {
        val array = JSONArray()
        forEach { array.put(it) }
        return array.toString()
    }

    private fun String.toStringList(): List<String> {
        return runCatching {
            val array = JSONArray(this)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.optString(index))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        @Volatile
        private var instance: LogDatabase? = null

        fun get(context: Context): LogDatabase {
            return instance ?: synchronized(this) {
                instance ?: LogDatabase(context).also { instance = it }
            }
        }
    }
}
