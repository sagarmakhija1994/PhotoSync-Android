package com.sagar.prosync.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HashCache(context: Context) : SQLiteOpenHelper(context, "hash_cache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // Creates a table linking the file path to its hash and last modified date
        db.execSQL(
            "CREATE TABLE hashes (" +
                    "path TEXT PRIMARY KEY, " +
                    "modified INTEGER, " +
                    "hash TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS hashes")
        onCreate(db)
    }

    fun getHash(path: String, dateModified: Long): String? {
        val db = readableDatabase
        val cursor = db.query(
            "hashes", arrayOf("hash"),
            "path = ? AND modified = ?",
            arrayOf(path, dateModified.toString()),
            null, null, null
        )

        var hash: String? = null
        if (cursor.moveToFirst()) {
            hash = cursor.getString(0)
        }
        cursor.close()
        return hash
    }

    fun putHash(path: String, dateModified: Long, hash: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("path", path)
            put("modified", dateModified)
            put("hash", hash)
        }
        // Replace ensures we update the record if the file was edited
        db.replace("hashes", null, values)
    }
}