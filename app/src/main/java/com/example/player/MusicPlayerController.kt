package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.catalog.MusicCatalogProvider
import com.example.data.model.RepeatModeState
import com.example.data.model.StreamUrlResult
import com.example.data.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 24_000L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val volume: Float = 0.85f,
    val activeStreamFormat: String = "PCM 16-bit Stereo • 22.05 kHz",
    val isWaveModeActive: Boolean = false,
    val waveReasonBanner: String? = null,
    val playbackErrorRu: String? = null
)

/**
 * Real Media3 ExoPlayer & MediaSession controller managing queue, transport controls,
 * background service binding, audio stream synthesis/resolution, error recovery, and lifecycle release.
 */
class MusicPlayerController(
    private val appContext: Context?,
    private val catalogProvider: MusicCatalogProvider,
    private val scope: CoroutineScope,
    private var onTrackCompletedOrSkipped: ((Track, Long, Boolean) -> Unit)? = null
) {
    private val _state = MutableStateFlow(
        PlayerUiState(
            currentTrack = catalogProvider.getAllTracks().firstOrNull(),
            queue = catalogProvider.getAllTracks().take(6)
        )
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var player: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playerListener: Player.Listener? = null
    private var progressPollingJob: Job? = null

    init {
        if (appContext != null) {
            initializeMedia3Player(appContext)
        }
    }

    fun setOnTrackCompletedOrSkippedListener(listener: (Track, Long, Boolean) -> Unit) {
        this.onTrackCompletedOrSkipped = listener
    }

    private fun initializeMedia3Player(context: Context) {
        try {
            val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            this.controllerFuture = future
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        attachPlayerInstance(controller)
                    } catch (_: Exception) {
                        createDirectExoPlayerFallback(context)
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (_: Exception) {
            createDirectExoPlayerFallback(context)
        }
    }

    private fun createDirectExoPlayerFallback(context: Context) {
        try {
            val exo = ExoPlayer.Builder(context).build()
            attachPlayerInstance(exo)
        } catch (_: Exception) {
            // Headless unit test environment without Looper/AudioTrack
        }
    }

    private fun attachPlayerInstance(attachedPlayer: Player) {
        this.player = attachedPlayer
        attachedPlayer.volume = _state.value.volume
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                val dur = attachedPlayer.duration.takeIf { it > 0 } ?: (_state.value.currentTrack?.durationMs ?: 24_000L)
                _state.update { it.copy(isBuffering = buffering, durationMs = dur) }
                if (playbackState == Player.STATE_ENDED) {
                    handleTrackNaturalEnd()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        playbackErrorRu = "Ошибка воспроизведения потока: ${error.localizedMessage ?: "недоступный аудиопоток"}"
                    )
                }
            }
        }
        this.playerListener = listener
        attachedPlayer.addListener(listener)
    }

    private fun startPositionPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive && _state.value.isPlaying) {
                val pos = player?.currentPosition?.coerceAtLeast(0L) ?: _state.value.currentPositionMs
                val dur = player?.duration?.takeIf { it > 0 } ?: (_state.value.currentTrack?.durationMs ?: 24_000L)
                _state.update {
                    it.copy(
                        currentPositionMs = pos.coerceAtMost(dur),
                        durationMs = dur
                    )
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling() {
        progressPollingJob?.cancel()
    }

    private fun handleTrackNaturalEnd() {
        val current = _state.value.currentTrack
        val listenedMs = _state.value.durationMs
        if (current != null) {
            onTrackCompletedOrSkipped?.invoke(current, listenedMs, false)
        }
        when (_state.value.repeatMode) {
            RepeatModeState.ONE -> {
                seekTo(0L)
                play()
            }
            RepeatModeState.ALL -> {
                skipToNext(autoTriggered = true)
            }
            RepeatModeState.OFF -> {
                val s = _state.value
                if (s.currentQueueIndex < s.queue.lastIndex) {
                    skipToNext(autoTriggered = true)
                } else {
                    _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                }
            }
        }
    }

    fun playTrack(
        track: Track,
        newQueue: List<Track>? = null,
        isWaveMode: Boolean = _state.value.isWaveModeActive,
        waveReason: String? = null,
        simulateOfflineError: Boolean = false
    ) {
        val updatedQueue = when {
            newQueue != null && newQueue.isNotEmpty() -> newQueue
            _state.value.queue.any { it.id == track.id } -> _state.value.queue
            else -> listOf(track) + _state.value.queue
        }
        val idx = updatedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _state.update {
            it.copy(
                currentTrack = track,
                queue = updatedQueue,
                currentQueueIndex = idx,
                currentPositionMs = 0L,
                durationMs = track.durationMs,
                isWaveModeActive = isWaveMode,
                waveReasonBanner = waveReason,
                playbackErrorRu = null,
                isBuffering = true
            )
        }

        scope.launch {
            val cacheDir: File? = appContext?.cacheDir
            val streamResult = withContext(Dispatchers.IO) {
                catalogProvider.resolveStreamUrl(
                    trackId = track.id,
                    cacheDir = cacheDir,
                    simulateOfflineError = simulateOfflineError
                )
            }
            when (streamResult) {
                is StreamUrlResult.Success -> {
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = true,
                            activeStreamFormat = streamResult.formatLabel,
                            playbackErrorRu = null
                        )
                    }
                    player?.let { p ->
                        val mediaMetadata = MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artistName)
                            .setAlbumTitle(track.albumTitle)
                            .setGenre(track.genre)
                            .build()
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(Uri.parse(streamResult.uriString))
                            .setMediaMetadata(mediaMetadata)
                            .build()
                        p.setMediaItem(mediaItem)
                        p.prepare()
                        p.play()
                    }
                }
                is StreamUrlResult.ExpiredLink -> {
                    _state.update {
                        it.copy(isBuffering = false, isPlaying = false, playbackErrorRu = streamResult.messageRu)
                    }
                }
                is StreamUrlResult.RegionRestricted -> {
                    _state.update {
                        it.copy(isBuffering = false, isPlaying = false, playbackErrorRu = streamResult.messageRu)
                    }
                }
                is StreamUrlResult.NetworkError -> {
                    _state.update {
                        it.copy(isBuffering = false, isPlaying = false, playbackErrorRu = streamResult.messageRu)
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        val currentlyPlaying = _state.value.isPlaying
        if (currentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val current = _state.value.currentTrack ?: return
        val p = player
        if (p != null && p.mediaItemCount == 0) {
            playTrack(current, _state.value.queue, _state.value.isWaveModeActive, _state.value.waveReasonBanner)
            return
        }
        p?.play()
        _state.update { it.copy(isPlaying = true, playbackErrorRu = null) }
    }

    fun pause() {
        player?.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs)
        player?.seekTo(clamped)
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    fun skipToNext(autoTriggered: Boolean = false) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (!autoTriggered && s.currentTrack != null) {
            val wasEarlySkip = s.currentPositionMs < 10_000L
            onTrackCompletedOrSkipped?.invoke(s.currentTrack, s.currentPositionMs, wasEarlySkip)
        }

        val nextIndex = if (s.isShuffleEnabled && s.queue.size > 1) {
            val candidates = s.queue.indices.filter { it != s.currentQueueIndex }
            candidates.random()
        } else {
            val candidate = s.currentQueueIndex + 1
            if (candidate <= s.queue.lastIndex) candidate else 0
        }

        val nextTrack = s.queue[nextIndex]
        playTrack(nextTrack, s.queue, s.isWaveModeActive, s.waveReasonBanner)
    }

    fun skipToPrevious() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (s.currentPositionMs > 4_000L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (s.currentQueueIndex > 0) s.currentQueueIndex - 1 else s.queue.lastIndex
        val prevTrack = s.queue[prevIndex]
        playTrack(prevTrack, s.queue, s.isWaveModeActive, s.waveReasonBanner)
    }

    fun toggleShuffle() {
        _state.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatModeState.OFF -> RepeatModeState.ALL
            RepeatModeState.ALL -> RepeatModeState.ONE
            RepeatModeState.ONE -> RepeatModeState.OFF
        }
        player?.repeatMode = when (nextMode) {
            RepeatModeState.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeState.ALL -> Player.REPEAT_MODE_ALL
            RepeatModeState.ONE -> Player.REPEAT_MODE_ONE
        }
        _state.update { it.copy(repeatMode = nextMode) }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        player?.volume = v
        _state.update { it.copy(volume = v) }
    }

    fun addToQueue(track: Track) {
        _state.update { s ->
            if (s.queue.any { it.id == track.id }) s
            else s.copy(queue = s.queue + track)
        }
    }

    fun removeFromQueue(trackId: String) {
        _state.update { s ->
            if (s.queue.size <= 1) return@update s
            val filtered = s.queue.filterNot { it.id == trackId }
            val newIdx = filtered.indexOfFirst { it.id == s.currentTrack?.id }.coerceAtLeast(0)
            s.copy(queue = filtered, currentQueueIndex = newIdx)
        }
    }

    fun moveTrackInQueue(fromIndex: Int, toIndex: Int) {
        _state.update { s ->
            if (fromIndex !in s.queue.indices || toIndex !in s.queue.indices || fromIndex == toIndex) {
                return@update s
            }
            val mutable = s.queue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            val newCurrentIdx = mutable.indexOfFirst { it.id == s.currentTrack?.id }.coerceAtLeast(0)
            s.copy(queue = mutable, currentQueueIndex = newCurrentIdx)
        }
    }

    fun restoreState(
        trackId: String,
        positionMs: Long,
        queueIds: List<String>,
        shuffle: Boolean,
        repeatMode: RepeatModeState,
        volume: Float
    ) {
        val restoredQueue = queueIds.mapNotNull { catalogProvider.getTrackById(it) }
            .ifEmpty { catalogProvider.getAllTracks().take(6) }
        val restoredTrack = catalogProvider.getTrackById(trackId) ?: restoredQueue.first()
        val idx = restoredQueue.indexOfFirst { it.id == restoredTrack.id }.coerceAtLeast(0)

        player?.volume = volume.coerceIn(0f, 1f)
        _state.update {
            it.copy(
                currentTrack = restoredTrack,
                queue = restoredQueue,
                currentQueueIndex = idx,
                currentPositionMs = positionMs.coerceIn(0L, restoredTrack.durationMs),
                durationMs = restoredTrack.durationMs,
                isShuffleEnabled = shuffle,
                repeatMode = repeatMode,
                volume = volume.coerceIn(0f, 1f)
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(playbackErrorRu = null) }
    }

    fun release() {
        stopPositionPolling()
        runCatching { scope.cancel() }
        playerListener?.let { listener ->
            player?.removeListener(listener)
        }
        playerListener = null
        controllerFuture?.let { future ->
            runCatching { MediaController.releaseFuture(future) }
        }
        controllerFuture = null
        player?.let { p ->
            if (p !is MediaController) {
                runCatching { p.release() }
            }
        }
        player = null
        onTrackCompletedOrSkipped = null
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
    }
}
