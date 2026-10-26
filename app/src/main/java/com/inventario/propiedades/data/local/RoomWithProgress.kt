package com.inventario.propiedades.data.local

import androidx.room.Embedded

data class RoomWithProgress(
    @Embedded val room: RoomEntity,
    val totalItems: Int,
    val reviewedItems: Int,
)
