package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AvatarFormat
import com.example.model.AvatarSpec
import com.example.model.ExportedClip

@Entity(tableName = "imported_avatars")
data class ImportedAvatarEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val formatName: String,
    val vertexCount: Int,
    val triangleCount: Int,
    val morphTargetCount: Int,
    val importedAtMs: Long
) {
    fun toDomain(): AvatarSpec = AvatarSpec(
        id = id,
        name = name,
        roleTag = if (morphTargetCount > 0) "Custom · $morphTargetCount Morphs" else "Custom · Auto-Rig",
        format = AvatarFormat.entries.firstOrNull { it.name == formatName } ?: AvatarFormat.GLB,
        filePath = filePath,
        vertexCount = vertexCount,
        triangleCount = triangleCount,
        morphTargetCount = morphTargetCount,
        accentHex = 0xFF818CF8,
        isBuiltIn = false
    )
}

@Entity(tableName = "exported_clips")
data class ExportedClipEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videoPath: String,
    val galleryUri: String?,
    val avatarName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val fileSizeBytes: Long,
    val createdAtMs: Long
) {
    fun toDomain(): ExportedClip = ExportedClip(
        id = id,
        title = title,
        videoPath = videoPath,
        galleryUri = galleryUri,
        avatarName = avatarName,
        durationMs = durationMs,
        width = width,
        height = height,
        fps = fps,
        fileSizeBytes = fileSizeBytes,
        createdAtMs = createdAtMs
    )
}
