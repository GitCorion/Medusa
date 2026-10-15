package com.example.tujelly.data.local.db

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Tabla virtual FTS4 (contenido externo) para búsqueda de texto completo sobre el título y el
 * título original. Se mantiene sincronizada automáticamente con [JellyfinMediaEntity] mediante
 * los triggers que genera Room para `contentEntity`.
 */
@Fts4(contentEntity = JellyfinMediaEntity::class)
@Entity(tableName = "jellyfin_media_fts")
data class JellyfinMediaFts(
    val title: String,
    val originalTitle: String?
)
