package com.example.data.db

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.LightingPreset
import com.example.model.StageBackdrop
import com.example.model.VideoAspectRatio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "voxsculpt_prefs")

data class StudioSettings(
    val selectedAvatarId: String = "nova",
    val lightingPreset: LightingPreset = LightingPreset.CINEMA_KEY,
    val stageBackdrop: StageBackdrop = StageBackdrop.OBSIDIAN_STUDIO,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.PORTRAIT_9_16
)

class StudioPreferences(private val context: Context) {
    private object Keys {
        val AVATAR_ID = stringPreferencesKey("selected_avatar_id")
        val LIGHTING = stringPreferencesKey("lighting_preset")
        val BACKDROP = stringPreferencesKey("stage_backdrop")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            selectedAvatarId = prefs[Keys.AVATAR_ID] ?: "nova",
            lightingPreset = prefs[Keys.LIGHTING]?.let { name ->
                LightingPreset.entries.firstOrNull { it.name == name }
            } ?: LightingPreset.CINEMA_KEY,
            stageBackdrop = prefs[Keys.BACKDROP]?.let { name ->
                StageBackdrop.entries.firstOrNull { it.name == name }
            } ?: StageBackdrop.OBSIDIAN_STUDIO,
            aspectRatio = prefs[Keys.ASPECT_RATIO]?.let { name ->
                VideoAspectRatio.entries.firstOrNull { it.name == name }
            } ?: VideoAspectRatio.PORTRAIT_9_16
        )
    }

    suspend fun setSelectedAvatar(avatarId: String) {
        context.studioDataStore.edit { it[Keys.AVATAR_ID] = avatarId }
    }

    suspend fun setLightingPreset(preset: LightingPreset) {
        context.studioDataStore.edit { it[Keys.LIGHTING] = preset.name }
    }

    suspend fun setStageBackdrop(backdrop: StageBackdrop) {
        context.studioDataStore.edit { it[Keys.BACKDROP] = backdrop.name }
    }

    suspend fun setAspectRatio(ratio: VideoAspectRatio) {
        context.studioDataStore.edit { it[Keys.ASPECT_RATIO] = ratio.name }
    }
}
