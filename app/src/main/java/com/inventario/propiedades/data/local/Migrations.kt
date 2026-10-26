package com.inventario.propiedades.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val lastItemId = db.query("SELECT COALESCE(MAX(seq), 0) FROM sqlite_sequence WHERE name = 'items'").use {
            it.moveToFirst()
            it.getLong(0)
        }
        // SQLite exige reconstruir la tabla para permitir estados nulos.
        db.execSQL("""
            CREATE TABLE items_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                roomId INTEGER NOT NULL,
                name TEXT NOT NULL,
                status TEXT,
                note TEXT NOT NULL,
                photoPath TEXT,
                FOREIGN KEY(roomId) REFERENCES rooms(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO items_new (id, roomId, name, status, note, photoPath)
            SELECT id, roomId, name, status, note, photoPath FROM items
        """.trimIndent())
        db.execSQL("DROP TABLE items")
        db.execSQL("ALTER TABLE items_new RENAME TO items")
        db.execSQL("CREATE INDEX index_items_roomId ON items(roomId)")
        // Conserva también la secuencia para no reutilizar IDs de artículos borrados.
        db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'items'")
        db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('items', ?)", arrayOf(lastItemId))
    }
}
