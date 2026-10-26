package com.inventario.propiedades.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object SeedData {
    fun insert(db: SupportSQLiteDatabase) {
        // Se ejecuta solo al crear la base; la muestra se confirma completa o se revierte.
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO properties (id, name, address, status) VALUES (?, ?, ?, ?)",
                arrayOf(1L, "Departamento de prueba", "Av. Providencia 1234, Santiago", "PENDIENTE"),
            )
            db.execSQL(
                "INSERT INTO rooms (id, propertyId, name) VALUES (1, 1, ?), (2, 1, ?), (3, 1, ?)",
                arrayOf("Living", "Cocina", "Dormitorio"),
            )
            db.execSQL(
                """
                INSERT INTO items (id, roomId, name, status, note, photoPath) VALUES
                (1, 1, ?, ?, ?, NULL),
                (2, 1, ?, ?, ?, NULL),
                (3, 2, ?, ?, ?, NULL),
                (4, 2, ?, ?, ?, NULL),
                (5, 3, ?, ?, ?, NULL)
                """.trimIndent(),
                arrayOf(
                    "Sofá", ItemStatus.NUEVO.name, "Sin uso",
                    "Mesa", ItemStatus.BUENO.name, "",
                    "Refrigerador", ItemStatus.DESGASTADO.name, "Marcas de uso en la puerta",
                    "Horno", ItemStatus.DAÑADO.name, "No enciende",
                    "Lámpara", ItemStatus.FALTANTE.name, "No estaba al realizar el inventario",
                ),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
