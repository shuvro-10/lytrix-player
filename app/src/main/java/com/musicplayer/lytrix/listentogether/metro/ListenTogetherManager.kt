package com.musicplayer.lytrix.listentogether.metro

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

enum class MetroConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class MetroRoomRole {
    NONE,
    HOST,
    GUEST
}

data class MetroLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String,
    val message: String
)

class MetroManager(
    private val context: Context
) : CoroutineScope {

    companion object {
        private const val PREFS_NAME = "metro_listen_together_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTO_APPROVE_JOINS = "auto_approve_joins"
        private const val KEY_AUTO_APPROVE_SUGGESTIONS = "auto_approve_suggestions"
        private const val KEY_SYNC_VOLUME = "sync_volume"
        private const val KEY_BLOCKED_USERS = "blocked_users"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_STORED_ROOM_CODE = "session_room_code"
        private const val KEY_IS_HOST = "session_is_host"
        private const val KEY_SESSION_TIMESTAMP = "session_timestamp"
        private const val SESSION_GRACE_PERIOD_MS = 5 * 60 * 1000L // 5 minutes for members
        private const val HOST_SESSION_GRACE_PERIOD_MS = 15 * 60 * 1000L // 15 minutes for host reclaiming room
        private const val MAX_LOGS = 200
    }

    override val coroutineContext = SupervisorJob() + Dispatchers.Main

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Settings
    private val _serverUrl = MutableStateFlow(
        prefs.getString(KEY_SERVER_URL, MetroClient.DEFAULT_SERVER_URL) ?: MetroClient.DEFAULT_SERVER_URL
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow(prefs.getString(KEY_USERNAME, "") ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _autoApproveJoins = MutableStateFlow(prefs.getBoolean(KEY_AUTO_APPROVE_JOINS, false))
    val autoApproveJoins: StateFlow<Boolean> = _autoApproveJoins.asStateFlow()

    private val _autoApproveSuggestions = MutableStateFlow(prefs.getBoolean(KEY_AUTO_APPROVE_SUGGESTIONS, false))
    val autoApproveSuggestions: StateFlow<Boolean> = _autoApproveSuggestions.asStateFlow()

    private val _syncHostVolume = MutableStateFlow(prefs.getBoolean(KEY_SYNC_VOLUME, true))
    val syncHostVolume: StateFlow<Boolean> = _syncHostVolume.asStateFlow()

    private val _blockedUsers = MutableStateFlow(
        prefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
    )
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    // State
    val client = MetroClient(
        context = context,
        serverUrlProvider = { _serverUrl.value }
    )

    private val _connectionState = MutableStateFlow(MetroConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MetroConnectionState> = _connectionState.asStateFlow()

    private val _roomRole = MutableStateFlow(MetroRoomRole.NONE)
    val roomRole: StateFlow<MetroRoomRole> = _roomRole.asStateFlow()

    private val _selfUserId = MutableStateFlow("")
    val selfUserId: StateFlow<String> = _selfUserId.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _pendingJoinCode = MutableStateFlow<String?>(null)
    val pendingJoinCode: StateFlow<String?> = _pendingJoinCode.asStateFlow()

    private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = _pendingJoinRequests.asStateFlow()

    private val _suggestions = MutableStateFlow<List<SuggestionReceivedPayload>>(emptyList())
    val suggestions: StateFlow<List<SuggestionReceivedPayload>> = _suggestions.asStateFlow()

    private val _waitingForNames = MutableStateFlow<List<String>>(emptyList())
    val waitingForNames: StateFlow<List<String>> = _waitingForNames.asStateFlow()

    private val _logs = MutableStateFlow<List<MetroLogEntry>>(emptyList())
    val logs: StateFlow<List<MetroLogEntry>> = _logs.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    // Callbacks for ExoPlayer integration
    var onRemotePlaybackAction: ((action: String, trackId: String, position: Long, track: TrackInfo?) -> Unit)? = null
    var onRemoteVolumeChange: ((volume: Float) -> Unit)? = null
    var onHostNeedsToAnnouncePlayback: (() -> Unit)? = null
    var onSuggestionApprovedPlay: ((track: TrackInfo) -> Unit)? = null

    // Track sync state
    var bufferingTrackId: String? = null
        private set
    private var pendingPlaybackAction: PlaybackActionPayload? = null
    var lastSyncedTrackId: String? = null
        private set
    var lastSyncedIsPlaying: Boolean? = null
        private set

    // Host volume observation
    private var volumeObserver: ContentObserver? = null
    private var lastObservedVolume: Float? = null

    // Session Persistence Helpers
    fun saveSession(token: String, roomCode: String, isHost: Boolean) {
        if (token.isBlank() || roomCode.isBlank()) return
        prefs.edit()
            .putString(KEY_SESSION_TOKEN, token)
            .putString(KEY_STORED_ROOM_CODE, roomCode)
            .putBoolean(KEY_IS_HOST, isHost)
            .putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun clearSavedSession() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_STORED_ROOM_CODE)
            .remove(KEY_IS_HOST)
            .remove(KEY_SESSION_TIMESTAMP)
            .apply()
        client.setSessionToken(null)
    }

    fun getSavedSessionToken(): String? {
        val token = prefs.getString(KEY_SESSION_TOKEN, null)
        val time = prefs.getLong(KEY_SESSION_TIMESTAMP, 0L)
        val isHost = prefs.getBoolean(KEY_IS_HOST, false)
        val grace = if (isHost) HOST_SESSION_GRACE_PERIOD_MS else SESSION_GRACE_PERIOD_MS
        return if (!token.isNullOrBlank() && (System.currentTimeMillis() - time < grace)) token else null
    }

    init {
        // Restore session if within grace period
        val savedToken = getSavedSessionToken()
        val savedCode = prefs.getString(KEY_STORED_ROOM_CODE, null)
        val savedIsHost = prefs.getBoolean(KEY_IS_HOST, false)
        if (!savedToken.isNullOrBlank() && !savedCode.isNullOrBlank()) {
            _roomRole.value = if (savedIsHost) MetroRoomRole.HOST else MetroRoomRole.GUEST
            _roomState.value = RoomState(
                roomCode = savedCode,
                hostId = if (savedIsHost) "self" else "",
                users = emptyList()
            )
            _connectionState.value = MetroConnectionState.DISCONNECTED
            client.setSessionToken(savedToken)
            addLog("INFO", "Restored session for room $savedCode")
        }

        launch {
            client.events.collect { event ->
                handleMetroEvent(event)
            }
        }
    }

    private fun handleMetroEvent(event: MetroEvent) {
        when (event) {
            is MetroEvent.Connected -> {
                _connectionState.value = MetroConnectionState.CONNECTED
                addLog("INFO", "Connected to server")
                if (event.resumed && _roomRole.value == MetroRoomRole.GUEST) {
                    requestSync()
                }
            }

            is MetroEvent.Disconnected -> {
                _connectionState.value = MetroConnectionState.DISCONNECTED
                addLog("WARN", "Disconnected from server: ${event.reason}")
                // Do not wipe room state immediately; keep roomState so user can view Disconnected banner and Reconnect button
            }

            is MetroEvent.ClockReady -> {
                addLog("INFO", "Server clock calibrated")
                if (_roomRole.value == MetroRoomRole.GUEST) {
                    requestSync()
                }
            }

            is MetroEvent.Log -> {
                addLog(event.level, event.message)
            }

            is MetroEvent.Message -> {
                handleServerMessage(event.type, event.payload)
            }
        }
    }

    private fun handleServerMessage(type: String, payload: Any?) {
        when (type) {
            MessageTypes.ROOM_CREATED -> {
                val p = payload as? RoomCreatedPayload ?: return
                _selfUserId.value = p.userId
                _roomRole.value = MetroRoomRole.HOST
                _roomState.value = RoomState(
                    roomCode = p.roomCode,
                    hostId = p.userId,
                    users = listOf(UserInfo(userId = p.userId, username = _username.value, isHost = true, isConnected = true))
                )
                _pendingJoinCode.value = null
                saveSession(p.sessionToken, p.roomCode, isHost = true)
                _toastEvents.tryEmit("Room created: ${p.roomCode}")
                addLog("INFO", "Room created successfully: ${p.roomCode}")
                startHostVolumeObserver()
                onHostNeedsToAnnouncePlayback?.invoke()
            }

            MessageTypes.JOIN_REQUEST -> {
                val p = payload as? JoinRequestPayload ?: return
                val blocked = _blockedUsers.value.contains(p.username.trim().lowercase())
                if (blocked) {
                    client.send(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId = p.userId, reason = "Blocked by host"))
                    addLog("INFO", "Auto-rejected blocked user: ${p.username}")
                    return
                }

                if (_autoApproveJoins.value) {
                    approveJoin(p.userId)
                    addLog("INFO", "Auto-approved join request from ${p.username}")
                } else {
                    val current = _pendingJoinRequests.value.toMutableList()
                    if (current.none { it.userId == p.userId }) {
                        current.add(p)
                        _pendingJoinRequests.value = current
                        _toastEvents.tryEmit("${p.username} wants to join")
                    }
                }
            }

            MessageTypes.JOIN_APPROVED -> {
                val p = payload as? JoinApprovedPayload ?: return
                _selfUserId.value = p.userId
                _roomRole.value = MetroRoomRole.GUEST
                _roomState.value = p.state
                _pendingJoinCode.value = null
                saveSession(p.sessionToken, p.roomCode, isHost = false)
                _toastEvents.tryEmit("Joined room: ${p.roomCode}")
                addLog("INFO", "Joined room: ${p.roomCode}")

                stopHostVolumeObserver()

                // Request live state from server immediately so we don't rely on stale join snapshot
                requestSync()

                // If host is already playing a track, prepare to play it without forcing an unneeded buffer barrier
                p.state?.currentTrack?.let { track ->
                    val state = p.state
                    val correctedPos = client.metroClock.positionAt(state.position, state.lastUpdate, state.isPlaying)
                    pendingPlaybackAction = PlaybackActionPayload(
                        action = if (state.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                        trackId = track.id,
                        position = correctedPos,
                        trackInfo = track,
                        volume = state.volume
                    )
                    // Request guest to load track and tune in
                    onRemotePlaybackAction?.invoke(
                        if (state.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                        track.id,
                        correctedPos,
                        track
                    )
                }
            }

            MessageTypes.JOIN_REJECTED -> {
                val p = payload as? JoinRejectedPayload
                resetRoom()
                _toastEvents.tryEmit("Join rejected: ${p?.reason ?: "Declined by host"}")
                addLog("WARN", "Join request was declined")
            }

            MessageTypes.USER_JOINED -> {
                val p = payload as? UserJoinedPayload ?: return
                _roomState.value?.let { state ->
                    val updated = state.users.toMutableList()
                    if (updated.none { it.userId == p.userId }) {
                        updated.add(UserInfo(userId = p.userId, username = p.username, isHost = false, isConnected = true))
                        _roomState.value = state.copy(users = updated)
                    }
                }
                _toastEvents.tryEmit("${p.username} joined")
                addLog("INFO", "${p.username} joined room")

                // Host announces current track and playback state to the new member
                if (_roomRole.value == MetroRoomRole.HOST) {
                    onHostNeedsToAnnouncePlayback?.invoke()
                }
            }

            MessageTypes.USER_LEFT -> {
                val p = payload as? UserLeftPayload ?: return
                _roomState.value?.let { state ->
                    _roomState.value = state.copy(users = state.users.filter { it.userId != p.userId })
                }
                _toastEvents.tryEmit("${p.username} left")
                addLog("INFO", "${p.username} left room")
            }

            MessageTypes.SYNC_PLAYBACK, MessageTypes.PLAYBACK_ACTION -> {
                val p = payload as? PlaybackActionPayload ?: return
                val correctedPos = client.metroClock.positionAt(p.position, p.serverTime, p.action == PlaybackActions.PLAY)

                // Update local room state
                _roomState.value?.let { state ->
                    _roomState.value = state.copy(
                        currentTrack = p.trackInfo ?: state.currentTrack,
                        isPlaying = p.action == PlaybackActions.PLAY,
                        position = correctedPos,
                        lastUpdate = p.serverTime,
                        volume = p.volume
                    )
                }

                // Volume sync if enabled and host sent set_volume
                if (p.action == PlaybackActions.SET_VOLUME && _syncHostVolume.value && _roomRole.value == MetroRoomRole.GUEST) {
                    onRemoteVolumeChange?.invoke(p.volume)
                    return
                }

                if (_roomRole.value == MetroRoomRole.GUEST) {
                    val effectiveTrack = p.trackInfo ?: _roomState.value?.currentTrack
                    val effectiveTrackId = if (p.trackId.isNotBlank()) p.trackId else (effectiveTrack?.id ?: "")

                    when (p.action) {
                        PlaybackActions.CHANGE_TRACK -> {
                            if (effectiveTrack != null) {
                                bufferingTrackId = effectiveTrack.id
                                pendingPlaybackAction = p.copy(position = correctedPos, trackId = effectiveTrack.id, trackInfo = effectiveTrack)
                                onRemotePlaybackAction?.invoke(PlaybackActions.CHANGE_TRACK, effectiveTrack.id, correctedPos, effectiveTrack)
                            }
                        }

                        PlaybackActions.PLAY, PlaybackActions.PAUSE, PlaybackActions.SEEK -> {
                            if (bufferingTrackId != null) {
                                pendingPlaybackAction = p.copy(position = correctedPos, trackId = effectiveTrackId, trackInfo = effectiveTrack)
                                launch {
                                    delay(1200)
                                    if (bufferingTrackId != null) {
                                        bufferingTrackId = null
                                        pendingPlaybackAction?.let { pending ->
                                            onRemotePlaybackAction?.invoke(pending.action, pending.trackId, pending.position, pending.trackInfo)
                                            pendingPlaybackAction = null
                                        }
                                    }
                                }
                            } else {
                                onRemotePlaybackAction?.invoke(p.action, effectiveTrackId, correctedPos, effectiveTrack)
                            }
                        }

                        else -> {
                            onRemotePlaybackAction?.invoke(p.action, effectiveTrackId, correctedPos, effectiveTrack)
                        }
                    }
                }
            }

            MessageTypes.BUFFER_WAIT -> {
                val p = payload as? BufferWaitPayload ?: return
                val names = _roomState.value?.users
                    ?.filter { p.waitingFor.contains(it.userId) }
                    ?.map { it.username } ?: emptyList()
                _waitingForNames.value = names
                addLog("INFO", "Buffering... waiting for: ${names.joinToString()}")

                // If WE are in waitingFor, answer the buffer barrier so server can unblock everyone
                if (_selfUserId.value in p.waitingFor) {
                    val trackId = p.trackId.ifBlank { _roomState.value?.currentTrack?.id ?: "" }
                    if (trackId.isNotBlank()) {
                        reportBufferReady(trackId)
                    }
                }
            }

            MessageTypes.BUFFER_COMPLETE -> {
                val p = payload as? BufferCompletePayload
                _waitingForNames.value = emptyList()
                addLog("INFO", "All devices ready, playback starting")

                if (_roomRole.value == MetroRoomRole.GUEST) {
                    val trackId = p?.trackId
                    if (bufferingTrackId == trackId || bufferingTrackId == null) {
                        bufferingTrackId = null
                        pendingPlaybackAction?.let { pending ->
                            onRemotePlaybackAction?.invoke(pending.action, pending.trackId, pending.position, pending.trackInfo)
                            pendingPlaybackAction = null
                        }
                    }
                }
            }

            MessageTypes.HOST_CHANGED -> {
                val p = payload as? HostChangedPayload ?: return
                _roomState.value?.let { state ->
                    val updatedUsers = state.users.map {
                        it.copy(isHost = it.userId == p.newHostId)
                    }
                    _roomState.value = state.copy(hostId = p.newHostId, users = updatedUsers)
                }
                val isMe = p.newHostId == _selfUserId.value
                if (isMe) {
                    _roomRole.value = MetroRoomRole.HOST
                    startHostVolumeObserver()
                    _toastEvents.tryEmit("You are now the Host!")
                } else {
                    _roomRole.value = MetroRoomRole.GUEST
                    stopHostVolumeObserver()
                    _toastEvents.tryEmit("${p.newHostName} is now the Host")
                }
                _roomState.value?.let { state ->
                    val token = client.sessionToken ?: getSavedSessionToken()
                    if (!token.isNullOrBlank()) {
                        saveSession(token, state.roomCode, isHost = isMe)
                    }
                }
                addLog("INFO", "Host changed to ${p.newHostName}")
            }

            MessageTypes.RECONNECTED -> {
                val p = payload as? ReconnectedPayload ?: return
                _selfUserId.value = p.userId
                _roomRole.value = if (p.isHost) MetroRoomRole.HOST else MetroRoomRole.GUEST
                _roomState.value = p.state
                _pendingJoinCode.value = null
                val token = client.sessionToken ?: getSavedSessionToken() ?: ""
                saveSession(token, p.roomCode, isHost = p.isHost)
                _toastEvents.tryEmit("Reconnected to room: ${p.roomCode}")
                addLog("INFO", "Reconnected to room: ${p.roomCode} (Host=${p.isHost})")

                if (p.isHost) {
                    startHostVolumeObserver()
                    onHostNeedsToAnnouncePlayback?.invoke()
                } else {
                    stopHostVolumeObserver()
                    p.state?.currentTrack?.let { track ->
                        val state = p.state
                        val correctedPos = client.metroClock.positionAt(state.position, state.lastUpdate, state.isPlaying)
                        pendingPlaybackAction = PlaybackActionPayload(
                            action = if (state.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                            trackId = track.id,
                            position = correctedPos,
                            trackInfo = track,
                            volume = state.volume
                        )
                        onRemotePlaybackAction?.invoke(
                            if (state.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                            track.id,
                            correctedPos,
                            track
                        )
                    }
                }
            }

            MessageTypes.USER_DISCONNECTED -> {
                val p = payload as? UserDisconnectedPayload ?: return
                _roomState.value?.let { state ->
                    val updated = state.users.map {
                        if (it.userId == p.userId) it.copy(isConnected = false) else it
                    }
                    _roomState.value = state.copy(users = updated)
                }
                _toastEvents.tryEmit("${p.username} went offline")
                addLog("WARN", "${p.username} disconnected (offline)")
            }

            MessageTypes.USER_RECONNECTED -> {
                val p = payload as? UserReconnectedPayload ?: return
                _roomState.value?.let { state ->
                    val updated = state.users.map {
                        if (it.userId == p.userId) it.copy(isConnected = true) else it
                    }
                    _roomState.value = state.copy(users = updated)
                }
                _toastEvents.tryEmit("${p.username} reconnected")
                addLog("INFO", "${p.username} reconnected")
            }

            MessageTypes.SYNC_STATE -> {
                val p = payload as? SyncStatePayload ?: return
                val correctedPos = client.metroClock.positionAt(p.position, p.lastUpdate, p.isPlaying)
                _roomState.value?.let { state ->
                    _roomState.value = state.copy(
                        currentTrack = p.currentTrack ?: state.currentTrack,
                        isPlaying = p.isPlaying,
                        position = correctedPos,
                        lastUpdate = p.lastUpdate,
                        volume = p.volume,
                        queue = if (p.queue.isNotEmpty()) p.queue else state.queue,
                        revision = p.revision
                    )
                }

                if (_roomRole.value == MetroRoomRole.GUEST) {
                    val track = p.currentTrack ?: _roomState.value?.currentTrack
                    if (track != null) {
                        val action = if (p.isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE
                        onRemotePlaybackAction?.invoke(action, track.id, correctedPos, track)
                    }
                }
            }

            MessageTypes.KICKED -> {
                val p = payload as? KickedPayload
                clearSavedSession()
                resetRoom()
                _toastEvents.tryEmit("Kicked: ${p?.reason ?: "Removed by host"}")
                addLog("WARN", "You were kicked from the room: ${p?.reason}")
            }

            MessageTypes.SUGGESTION_RECEIVED -> {
                val p = payload as? SuggestionReceivedPayload ?: return
                if (_roomRole.value == MetroRoomRole.HOST) {
                    if (_autoApproveSuggestions.value) {
                        addLog("INFO", "Auto-approved song suggestion: ${p.trackInfo?.title}")
                        client.send(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(suggestionId = p.suggestionId))
                        _toastEvents.tryEmit("Auto-approved suggestion: ${p.trackInfo?.title}")
                        p.trackInfo?.let { track ->
                            onSuggestionApprovedPlay?.invoke(track)
                        }
                    } else {
                        val current = _suggestions.value.toMutableList()
                        current.add(p)
                        _suggestions.value = current
                        _toastEvents.tryEmit("New song suggestion: ${p.trackInfo?.title}")
                    }
                }
            }

            MessageTypes.SUGGESTION_APPROVED -> {
                val p = payload as? SuggestionApprovedPayload ?: return
                _suggestions.value = _suggestions.value.filter { it.suggestionId != p.suggestionId }
                _toastEvents.tryEmit("Suggestion accepted: ${p.trackInfo?.title}")
            }

            MessageTypes.SUGGESTION_REJECTED -> {
                val p = payload as? SuggestionRejectedPayload ?: return
                _suggestions.value = _suggestions.value.filter { it.suggestionId != p.suggestionId }
                _toastEvents.tryEmit("Suggestion declined: ${p.reason}")
            }

            MessageTypes.ERROR -> {
                val p = payload as? ErrorPayload
                if (p?.code != "stale_track") {
                    _toastEvents.tryEmit("Error: ${p?.message ?: "Unknown error"}")
                    addLog("ERROR", "Server error (${p?.code}): ${p?.message}")
                } else {
                    addLog("INFO", "Server sync notice: Track already active (stale_track)")
                }
                if (p?.code == "invalid_session" || p?.code == "session_expired" || p?.code == "room_not_found") {
                    clearSavedSession()
                    resetRoom()
                }
            }
        }
    }

    // Actions
    private var lastReconnectClickTime = 0L

    fun forceReconnect() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastReconnectClickTime < 3000L) {
            _toastEvents.tryEmit("Please wait a moment before reconnecting...")
            return
        }
        lastReconnectClickTime = now

        if (_connectionState.value == MetroConnectionState.CONNECTED) {
            _toastEvents.tryEmit("Already connected")
            return
        }

        val token = client.sessionToken ?: getSavedSessionToken()
        if (token.isNullOrBlank()) {
            _toastEvents.tryEmit("No session found to reconnect")
            return
        }

        _connectionState.value = MetroConnectionState.CONNECTING
        addLog("INFO", "Reconnecting to room...")
        client.reconnectWithToken(token)
    }

    fun createRoom(username: String) {
        if (client.isConnected) {
            client.send(MessageTypes.LEAVE_ROOM, LeaveRoomPayload())
            client.disconnect()
        }
        clearSavedSession()
        resetRoom()
        setUsername(username)
        _connectionState.value = MetroConnectionState.CONNECTING
        client.createRoom(username)
    }

    fun joinRoom(roomCode: String, username: String) {
        if (client.isConnected) {
            client.send(MessageTypes.LEAVE_ROOM, LeaveRoomPayload())
            client.disconnect()
        }
        clearSavedSession()
        resetRoom()
        setUsername(username)
        val cleanCode = roomCode.trim().uppercase()
        _pendingJoinCode.value = cleanCode
        _connectionState.value = MetroConnectionState.CONNECTING
        client.joinRoom(cleanCode, username)
    }

    fun cancelJoin() {
        _pendingJoinCode.value = null
        client.disconnect()
        _connectionState.value = MetroConnectionState.DISCONNECTED
    }

    fun leaveRoom() {
        stopHostVolumeObserver()
        client.send(MessageTypes.LEAVE_ROOM, LeaveRoomPayload())
        client.disconnect()
        clearSavedSession()
        resetRoom()
        addLog("INFO", "Left room")
    }

    fun approveJoin(userId: String) {
        client.send(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId = userId))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != userId }
    }

    fun rejectJoin(userId: String, reason: String = "Declined by host") {
        client.send(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId = userId, reason = reason))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != userId }
    }

    fun kickUser(userId: String, reason: String = "Removed by host") {
        client.send(MessageTypes.KICK_USER, KickUserPayload(userId = userId, reason = reason))
    }

    fun blockAndKick(userId: String, usernameToBlock: String) {
        val cleanName = usernameToBlock.trim().lowercase()
        val set = _blockedUsers.value.toMutableSet()
        set.add(cleanName)
        _blockedUsers.value = set
        prefs.edit().putStringSet(KEY_BLOCKED_USERS, set).apply()

        kickUser(userId, "Blocked by host")
        addLog("INFO", "Blocked and kicked user: $usernameToBlock")
    }

    fun unblockUser(usernameToUnblock: String) {
        val cleanName = usernameToUnblock.trim().lowercase()
        val set = _blockedUsers.value.toMutableSet()
        set.remove(cleanName)
        _blockedUsers.value = set
        prefs.edit().putStringSet(KEY_BLOCKED_USERS, set).apply()
        addLog("INFO", "Unblocked user: $usernameToUnblock")
    }

    fun transferHost(newHostId: String) {
        client.send(MessageTypes.TRANSFER_HOST, TransferHostPayload(newHostId = newHostId))
    }

    fun suggestTrack(track: TrackInfo) {
        client.send(MessageTypes.SUGGEST_TRACK, SuggestTrackPayload(trackInfo = track))
        _toastEvents.tryEmit("Suggested: ${track.title}")
    }

    fun approveSuggestion(suggestionId: String) {
        val sugg = _suggestions.value.find { it.suggestionId == suggestionId }
        client.send(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(suggestionId = suggestionId))
        _suggestions.value = _suggestions.value.filter { it.suggestionId != suggestionId }
        sugg?.trackInfo?.let { track ->
            onSuggestionApprovedPlay?.invoke(track)
        }
    }

    fun rejectSuggestion(suggestionId: String, reason: String = "Declined by host") {
        client.send(MessageTypes.REJECT_SUGGESTION, RejectSuggestionPayload(suggestionId = suggestionId, reason = reason))
        _suggestions.value = _suggestions.value.filter { it.suggestionId != suggestionId }
    }

    /**
     * Send buffer readiness to server. Single-flight per track change to avoid stale_track error.
     */
    fun reportBufferReady(trackId: String, revision: Long = 0L) {
        if (bufferingTrackId == trackId) {
            bufferingTrackId = null
            client.send(MessageTypes.BUFFER_READY, BufferReadyPayload(trackId = trackId, trackRevision = revision))
            addLog("INFO", "Sent buffer ready for track $trackId (rev=$revision)")
        }
    }

    /**
     * Guest requests fresh state from server/host (useful on reconnect or network resume).
     */
    fun requestSync() {
        if (_roomRole.value == MetroRoomRole.GUEST && _connectionState.value == MetroConnectionState.CONNECTED) {
            client.send(MessageTypes.REQUEST_SYNC, null)
            addLog("INFO", "Requested sync from server")
        }
    }

    /**
     * Host publishes current state snapshot to the room (upon creation, reconnect, or when new member arrives).
     * Sends CHANGE_TRACK to register track and queue on the server, immediately followed by PLAY/PAUSE with trackId=""
     * to set live isPlaying status on the server without stale_track rejection.
     */
    fun publishSnapshot(track: TrackInfo, isPlaying: Boolean, position: Long, volume: Float = 1.0f, queue: List<TrackInfo> = emptyList()) {
        if (_roomRole.value != MetroRoomRole.HOST) return
        lastSyncedTrackId = track.id
        lastSyncedIsPlaying = isPlaying

        // Command 1: Register track and queue on the server
        sendPlaybackAction(
            action = PlaybackActions.CHANGE_TRACK,
            trackId = track.id,
            position = position,
            trackInfo = track,
            volume = volume,
            queue = queue
        )

        // Command 2: Set live isPlaying state with empty trackId so server fills in current track and avoids stale_track rejection
        sendPlaybackAction(
            action = if (isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE,
            trackId = "",
            position = position,
            trackInfo = null,
            volume = volume
        )
    }

    /**
     * Host changes song. Sends CHANGE_TRACK and upcoming queue.
     * PLAY will be broadcast once ExoPlayer actually transitions to STATE_READY / playing.
     */
    fun sendTrackChange(track: TrackInfo, isPlaying: Boolean, position: Long, queue: List<TrackInfo> = emptyList()) {
        if (_roomRole.value != MetroRoomRole.HOST) return
        lastSyncedTrackId = track.id
        lastSyncedIsPlaying = false

        sendPlaybackAction(
            action = PlaybackActions.CHANGE_TRACK,
            trackId = track.id,
            position = position,
            trackInfo = track,
            queue = queue
        )
    }

    fun sendPlaybackAction(
        action: String,
        trackId: String,
        position: Long,
        trackInfo: TrackInfo? = null,
        volume: Float = 1.0f,
        queue: List<TrackInfo> = emptyList()
    ) {
        if (_roomRole.value != MetroRoomRole.HOST) return
        val serverNow = client.metroClock.now() ?: System.currentTimeMillis()
        val payload = PlaybackActionPayload(
            action = action,
            trackId = trackId,
            position = position.coerceAtLeast(1L), // Ensure > 0 for protobuf wire non-default value
            trackInfo = trackInfo,
            serverTime = serverNow,
            capturedAtServerTime = serverNow,
            volume = volume,
            queue = queue
        )
        client.send(MessageTypes.PLAYBACK_ACTION, payload)
    }

    // Host Volume Synchronization Observer
    private fun startHostVolumeObserver() {
        stopHostVolumeObserver()
        if (audioManager == null) return

        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                if (_roomRole.value != MetroRoomRole.HOST || !_syncHostVolume.value) return

                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max <= 0) return
                val ratio = current.toFloat() / max.toFloat()

                val last = lastObservedVolume
                if (last == null || kotlin.math.abs(last - ratio) >= 0.02f) {
                    lastObservedVolume = ratio
                    client.send(
                        MessageTypes.PLAYBACK_ACTION,
                        PlaybackActionPayload(
                            action = PlaybackActions.SET_VOLUME,
                            volume = ratio,
                            serverTime = client.metroClock.now() ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
        } catch (e: Throwable) {
            Timber.tag("MetroManager").w(e, "Failed to register volume ContentObserver")
        }
    }

    private fun stopHostVolumeObserver() {
        volumeObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: Throwable) {
                // Ignore
            }
        }
        volumeObserver = null
        lastObservedVolume = null
    }

    // Settings modifiers
    fun setServerUrl(url: String) {
        val clean = url.trim()
        _serverUrl.value = clean
        prefs.edit().putString(KEY_SERVER_URL, clean).apply()
    }

    fun resetServerUrl() {
        setServerUrl(MetroClient.DEFAULT_SERVER_URL)
    }

    fun setUsername(name: String) {
        val clean = name.trim()
        _username.value = clean
        prefs.edit().putString(KEY_USERNAME, clean).apply()
    }

    fun setAutoApproveJoins(enabled: Boolean) {
        _autoApproveJoins.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_APPROVE_JOINS, enabled).apply()
    }

    fun setAutoApproveSuggestions(enabled: Boolean) {
        _autoApproveSuggestions.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_APPROVE_SUGGESTIONS, enabled).apply()
    }

    fun setSyncHostVolume(enabled: Boolean) {
        _syncHostVolume.value = enabled
        prefs.edit().putBoolean(KEY_SYNC_VOLUME, enabled).apply()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(level: String, message: String) {
        val entry = MetroLogEntry(level = level, message = message)
        val list = _logs.value.toMutableList()
        if (list.size >= MAX_LOGS) list.removeAt(0)
        list.add(entry)
        _logs.value = list
    }

    private fun resetRoom() {
        stopHostVolumeObserver()
        bufferingTrackId = null
        pendingPlaybackAction = null
        lastSyncedTrackId = null
        lastSyncedIsPlaying = null
        _roomRole.value = MetroRoomRole.NONE
        _roomState.value = null
        _pendingJoinCode.value = null
        _pendingJoinRequests.value = emptyList()
        _suggestions.value = emptyList()
        _waitingForNames.value = emptyList()
    }
}
