package com.inventario.propiedades.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object TemplateSeedData {
    fun insert(db: SupportSQLiteDatabase) {
        val living = listOf("Puerta", "Ventana", "Luminaria", "Enchufes", "Piso", "Muros")
        val kitchen = listOf("Lavaplatos", "Grifería", "Encimera", "Campana", "Muebles de cocina")
        val bedroom = listOf("Puerta", "Ventana", "Clóset", "Luminaria", "Enchufes", "Piso")
        val bathroom = listOf("WC", "Lavamanos", "Grifería", "Ducha", "Espejo", "Extractor")
        val bases = listOf(
            "Estudio" to listOf("Ambiente principal" to living, "Cocina" to kitchen, "Baño" to bathroom),
            "1D1B" to listOf("Living comedor" to living, "Cocina" to kitchen, "Dormitorio" to bedroom, "Baño" to bathroom),
            "2D1B" to listOf("Living comedor" to living, "Cocina" to kitchen,
                "Dormitorio 1" to bedroom, "Dormitorio 2" to bedroom, "Baño" to bathroom),
        )
        // Solo se invoca al crear las tablas: instalación nueva o migración 2 → 3, nunca al reabrir.
        db.compileStatement("INSERT INTO templates (name, isBuiltIn) VALUES (?, 1)").use { templateInsert ->
            db.compileStatement("INSERT INTO template_rooms (templateId, name) VALUES (?, ?)").use { roomInsert ->
                db.compileStatement("INSERT INTO template_items (templateRoomId, name) VALUES (?, ?)").use { itemInsert ->
                    for ((name, rooms) in bases) {
                        templateInsert.bindString(1, name)
                        val templateId = templateInsert.executeInsert()
                        for ((roomName, items) in rooms) {
                            roomInsert.bindLong(1, templateId)
                            roomInsert.bindString(2, roomName)
                            val roomId = roomInsert.executeInsert()
                            for (itemName in items) {
                                itemInsert.bindLong(1, roomId)
                                itemInsert.bindString(2, itemName)
                                itemInsert.executeInsert()
                            }
                        }
                    }
                }
            }
        }
    }
}
