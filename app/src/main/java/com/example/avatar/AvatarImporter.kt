package com.example.avatar

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.ImportedAvatarEntity
import com.example.model.AvatarFormat
import com.example.model.AvatarSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AvatarImporter(private val context: Context) {

    suspend fun importFromUri(uri: Uri): Pair<ImportedAvatarEntity, AvatarModel3D> =
        withContext(Dispatchers.IO) {
            val fileName = resolveDisplayName(uri)
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val format = when (ext) {
                "glb" -> AvatarFormat.GLB
                "gltf" -> AvatarFormat.GLTF
                "obj" -> AvatarFormat.OBJ
                else -> throw IllegalArgumentException(
                    "Unsupported 3D format '.$ext'. Please select a .glb, .gltf, or .obj file."
                )
            }

            val id = "custom_${UUID.randomUUID()}"
            val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
            val destFile = File(avatarDir, "$id.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not read selected 3D model file.")

            val cleanName = fileName.substringBeforeLast('.')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifEmpty { "Custom Avatar" }

            val model = loadModelFromSpec(
                AvatarSpec(
                    id = id,
                    name = cleanName,
                    roleTag = "Custom",
                    format = format,
                    filePath = destFile.absolutePath,
                    isBuiltIn = false
                )
            )

            val entity = ImportedAvatarEntity(
                id = id,
                name = cleanName,
                filePath = destFile.absolutePath,
                formatName = format.name,
                vertexCount = model.totalVertices,
                triangleCount = model.totalTriangles,
                morphTargetCount = model.totalMorphTargets,
                importedAtMs = System.currentTimeMillis()
            )
            entity to model
        }

    fun loadModelFromSpec(spec: AvatarSpec): AvatarModel3D {
        if (spec.isBuiltIn || spec.filePath == null) {
            return ProceduralAvatarFactory.buildAvatar(spec.id)
        }
        val file = File(spec.filePath)
        if (!file.exists()) {
            return ProceduralAvatarFactory.buildAvatar("nova")
        }
        return when (spec.format) {
            AvatarFormat.OBJ -> ObjModelParser.parse(file, spec.id, spec.name)
            AvatarFormat.GLB, AvatarFormat.GLTF -> GltfModelParser.parse(file, spec.id, spec.name)
            AvatarFormat.BUILTIN -> ProceduralAvatarFactory.buildAvatar(spec.id)
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "avatar.glb"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "avatar.glb"
    }
}
