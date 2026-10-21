package app.sitara.music.core.data.library

import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONObject

/** Writes a database exactly as schema [version] left it, built from the committed export, then lets [fill] add rows. */
internal fun createDatabaseAt(version: Int, file: File, fill: (SQLiteDatabase) -> Unit) {
    val schema = JSONObject(File("schemas/app.sitara.music.core.data.library.SitaraDatabase/$version.json").readText()).getJSONObject("database")
    file.parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        fill(db)
        db.version = version
    }
}

/** The time heard of every kept play, in the order they were kept; null for plays kept before heard time was. */
internal fun SitaraDatabase.listenedMs(): List<Long?> = openHelper.readableDatabase.query("SELECT listened_ms FROM play_history ORDER BY id").use { rows ->
    List(rows.count) { rows.moveToNext(); if (rows.isNull(0)) null else rows.getLong(0) }
}
