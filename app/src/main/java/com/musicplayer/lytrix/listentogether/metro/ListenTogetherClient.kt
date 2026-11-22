package com.musicplayer.lytrix.listentogether.metro

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString.Companion.toByteString
import timber.log.Timber
import java.util.concurrent.TimeUnit

sealed interface MetroEvent {
    data class Connected(val resumed: Boolean) : MetroEvent
    data class Message(val type: String, val payload: Any?) : MetroEvent
    data object ClockReady : MetroEvent
    data class Disconnected(val reason: String?, val willRetry: Boolean) : MetroEvent
    data class Log(val level: String, val message: String) : MetroEvent
}

sealed class PendingMetroAction {
    data class CreateRoom(val username: String) : PendingMetroAction()
    data class JoinRoom(val roomCode: String, val username: String) : PendingMetroAction()
}

class MetroClient(
    private val context: Context? = null,
    private val serverUrlProvider: () -> String = { DEFAULT_SERVER_URL },
    private val userAgent: String = "Lytrix/1.0.1 (Android)"
) : CoroutineScope {

    companion object {
        const val DEFAULT_SERVER_URL = "wss://lytrix-sync.onrender.com/ws"
        private const val PING_INTERVAL_MS = 25_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val MAX_RECONNECT_ATTEMPTS = 15
    }

    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val metroClock = MetroClock()
    private val codec = MetroCodec(compressionEnabled = true)

    private val _events = MutableSharedFlow<MetroEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<MetroEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var connectionJob: Job? = null
    private var pingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var pendingAction: PendingMetroAction? = null
    var sessionToken: String? = null
        private set

    private var pingSequence = 0L
    private var reconnectAttempts = 0
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var isUserInitiatedDisconnect = false

    val isConnected: Boolean
        get() = webSocket != null

    fun connect() {
        isUserInitiatedDisconnect = false
        reconnectAttempts = 0
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        startConnection()
    }

    fun setSessionToken(token: String?) {
        this.sessionToken = token
    }

    fun reconnectWithToken(token: String) {
        this.sessionToken = token
        this.pendingAction = null
        isUserInitiatedDisconnect = false
        reconnectAttempts = 0
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        if (webSocket != null) {
            emitLog("INFO", "Sending RECONNECT with token on existing socket...")
            sendImmediate(MessageTypes.RECONNECT, ReconnectPayload(token))
        } else {
            startConnection()
        }
    }

    fun disconnect() {
        isUserInitiatedDisconnect = true
        releaseWakeLock()
        pingJob?.cancel()
        connectionJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        sessionToken = null
        pendingAction = null
        metroClock.reset()
        emitLog("INFO", "Disconnected from server by user")
        _events.tryEmit(MetroEvent.Disconnected("User disconnected", willRetry = false))
    }

    fun createRoom(username: String) {
        if (isConnected) {
            emitLog("INFO", "Creating room as $username")
            send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username = username))
        } else {
            emitLog("INFO", "Queueing create room as $username while connecting...")
            pendingAction = PendingMetroAction.CreateRoom(username)
            connect()
        }
    }

    fun joinRoom(roomCode: String, username: String) {
        val cleanCode = roomCode.trim().uppercase()
        if (isConnected) {
            emitLog("INFO", "Joining room $cleanCode as $username")
            send(MessageTypes.JOIN_ROOM, JoinRoomPayload(roomCode = cleanCode, username = username))
        } else {
            emitLog("INFO", "Queueing join room $cleanCode while connecting...")
            pendingAction = PendingMetroAction.JoinRoom(cleanCode, username)
            connect()
        }
    }

    private fun executePendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        when (action) {
            is PendingMetroAction.CreateRoom -> {
                emitLog("INFO", "Executing pending create room: ${action.username}")
                send(MessageTypes.CREATE_ROOM, CreateRoomPayload(username = action.username))
            }
            is PendingMetroAction.JoinRoom -> {
                emitLog("INFO", "Executing pending join room: ${action.roomCode} as ${action.username}")
                send(MessageTypes.JOIN_ROOM, JoinRoomPayload(roomCode = action.roomCode, username = action.username))
            }
        }
    }

    private fun startConnection() {
        if (webSocket != null) {
            emitLog("INFO", "Already connected to server")
            return
        }

        connectionJob?.cancel()
        connectionJob = launch {
            try {
                var url = serverUrlProvider().trim()
                if (url.startsWith("http://")) url = "ws://" + url.removePrefix("http://")
                else if (url.startsWith("https://")) url = "wss://" + url.removePrefix("https://")
                if (!url.endsWith("/ws")) {
                    url = if (url.endsWith("/")) "${url}ws" else "$url/ws"
                }

                emitLog("INFO", "Connecting to $url...")

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header(
                        com.musicplayer.lytrix.constants.LytrixSecurityConstants.SYNC_SECRET_HEADER,
                        com.musicplayer.lytrix.constants.LytrixSecurityConstants.SYNC_SECRET_TOKEN
                    )
                    .build()

                webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        emitLog("INFO", "WebSocket connection opened")
                        reconnectAttempts = 0
                        acquireWakeLock()
                        startPingLoop()

                        val resumed = sessionToken != null
                        _events.tryEmit(MetroEvent.Connected(resumed = resumed))

                        if (sessionToken != null) {
                            emitLog("INFO", "Restoring room session with token...")
                            sendImmediate(MessageTypes.RECONNECT, ReconnectPayload(sessionToken!!))
                        } else {
                            executePendingAction()
                        }
                    }

                    override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                        handleIncomingFrame(bytes.toByteArray())
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        emitLog("WARN", "Received text message (expected binary proto): $text")
                    }

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        emitLog("INFO", "Server closing connection: $code / $reason")
                        ws.close(code, reason)
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        emitLog("INFO", "WebSocket closed ($code: $reason)")
                        handleDisconnection(reason)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        emitLog("ERROR", "WebSocket failure: ${t.message}")
                        handleDisconnection(t.message)
                    }
                })

            } catch (e: Throwable) {
                emitLog("ERROR", "Connection error: ${e.message}")
                handleDisconnection(e.message)
            }
        }
    }

    private fun handleIncomingFrame(data: ByteArray) {
        try {
            val (msgType, payloadBytes) = codec.decode(data)
            val payload = codec.decodePayload(msgType, payloadBytes)

            when (msgType) {
                MessageTypes.PONG -> {
                    val pong = payload as? PongPayload
                    if (pong != null) {
                        val firstSample = metroClock.recordPong(
                            clientTime = pong.clientTime,
                            serverReceiveTime = pong.serverReceiveTime,
                            serverSendTime = pong.serverSendTime
                        )
                        if (firstSample) {
                            emitLog("INFO", "Clock calibrated with server")
                            _events.tryEmit(MetroEvent.ClockReady)
                        }
                    }
                }

                MessageTypes.ROOM_CREATED -> {
                    val created = payload as? RoomCreatedPayload
                    if (created != null && created.sessionToken.isNotBlank()) {
                        sessionToken = created.sessionToken
                    }
                    _events.tryEmit(MetroEvent.Message(msgType, payload))
                }

                MessageTypes.JOIN_APPROVED -> {
                    val approved = payload as? JoinApprovedPayload
                    if (approved != null && approved.sessionToken.isNotBlank()) {
                        sessionToken = approved.sessionToken
                    }
                    _events.tryEmit(MetroEvent.Message(msgType, payload))
                }

                MessageTypes.RECONNECTED -> {
                    emitLog("INFO", "Room session successfully reconnected")
                    _events.tryEmit(MetroEvent.Message(msgType, payload))
                }

                MessageTypes.KICKED -> {
                    sessionToken = null
                    _events.tryEmit(MetroEvent.Message(msgType, payload))
                }

                else -> {
                    _events.tryEmit(MetroEvent.Message(msgType, payload))
                }
            }
        } catch (e: Throwable) {
            emitLog("ERROR", "Error parsing frame: ${e.message}")
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                acquireWakeLock()
                val now = SystemClock.elapsedRealtime()
                sendImmediate(MessageTypes.PING, PingPayload(clientTime = now, sequence = ++pingSequence))
            }
        }
    }

    private fun handleDisconnection(reason: String?) {
        pingJob?.cancel()
        webSocket = null

        if (isUserInitiatedDisconnect) {
            return
        }

        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            emitLog("WARN", "Disconnected ($reason). Retrying in ${reconnectDelay / 1000}s (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)...")
            _events.tryEmit(MetroEvent.Disconnected(reason, willRetry = true))

            launch {
                delay(reconnectDelay)
                reconnectDelay = (reconnectDelay * 1.5).toLong().coerceAtMost(MAX_RECONNECT_DELAY_MS)
                startConnection()
            }
        } else {
            emitLog("ERROR", "Disconnected permanently: retry limit reached ($reason)")
            _events.tryEmit(MetroEvent.Disconnected(reason, willRetry = false))
        }
    }

    fun send(msgType: String, payload: Any? = null): Boolean {
        return sendImmediate(msgType, payload)
    }

    private fun sendImmediate(msgType: String, payload: Any? = null): Boolean {
        val ws = webSocket ?: return false
        return try {
            val bytes = codec.encode(msgType, payload)
            ws.send(bytes.toByteString(0, bytes.size))
        } catch (e: Throwable) {
            emitLog("ERROR", "Send failed for $msgType: ${e.message}")
            false
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null && context != null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lytrix:ListenTogether")
            }
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Throwable) {
            // Ignore wake lock issues on background restrictions
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun emitLog(level: String, message: String) {
        Timber.tag("MetroClient").d("[$level] $message")
        _events.tryEmit(MetroEvent.Log(level, message))
    }
}
