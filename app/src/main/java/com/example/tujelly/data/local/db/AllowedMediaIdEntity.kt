package com.example.tujelly.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabla de IDs de media permitidos para el filtrado de restricciones parentales.
 *
 * Cuando hay restricciones activas, las consultas de catálogo filtran con
 * `id IN (SELECT mediaId FROM allowed_media_ids)` en vez de cargar todos los
 * resultados en memoria y filtrar con Kotlin.
 */
@Entity(tableName = "allowed_media_ids")
data class AllowedMediaIdEntity(
    @PrimaryKey
    val mediaId: String
)
