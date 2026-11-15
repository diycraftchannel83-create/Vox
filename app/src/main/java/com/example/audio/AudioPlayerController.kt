package com.example.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private var currentPath: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    fun load(wavPath: String) {
        if (currentPath == wavPath && mediaPlayer != null) return
        release()
        currentPath = wavPath
        val player = MediaPlayer().apply {
            setDataSource(wavPath)
            prepare()
        }
        mediaPlayer = player
        _positionMs.value = 0L
        _isPlaying.value = false
    }

    fun play(scope: CoroutineScope, onPositionUpdate: (Long) -> Unit, onCompleted: () -> Unit) {
        val player = mediaPlayer ?: return
        if (player.isPlaying) return
        player.setOnCompletionListener {
            _isPlaying.value = false
            tickerJob?.cancel()
            _positionMs.value = 0L
            onPositionUpdate(0L)
            onCompleted()
        }
        player.start()
        _isPlaying.value = true
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val p = try {
                    player.currentPosition.toLong()
                } catch (_: Exception) {
                    0L
                }
                _positionMs.value = p
                onPositionUpdate(p)
                delay(16L)
            }
        }
    }

    fun pause() {
        tickerJob?.cancel()
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        }
        _isPlaying.value = false
    }

    fun seekTo(ms: Long, onPositionUpdate: (Long) -> Unit) {
        val player = mediaPlayer ?: return
        val clamped = ms.coerceIn(0L, player.duration.toLong().coerceAtLeast(0L))
        player.seekTo(clamped.toInt())
        _positionMs.value = clamped
        onPositionUpdate(clamped)
    }

    fun release() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        currentPath = null
        _isPlaying.value = false
        _positionMs.value = 0L
    }
}
