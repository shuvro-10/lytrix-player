package com.musicplayer.lytrix.listentogether.metro

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Listen Together wire protocol models for Metrolist / Metroserver.
 * Uses kotlinx.serialization.protobuf for direct Kotlin data class serialization.
 */
object MessageTypes {
    const val CREATE_ROOM = "create_room"
    const val JOIN_ROOM = "join_room"
    const val LEAVE_ROOM = "leave_room"
    const val APPROVE_JOIN = "approve_join"
    const val REJECT_JOIN = "reject_join"
    const val PLAYBACK_ACTION = "playback_action"
    const val BUFFER_READY = "buffer_ready"
    const val KICK_USER = "kick_user"
    const val TRANSFER_HOST = "transfer_host"
    const val PING = "ping"
    const val CHAT = "chat"
    const val REQUEST_SYNC = "request_sync"
    const val RECONNECT = "reconnect"
    const val SUGGEST_TRACK = "suggest_track"
    const val APPROVE_SUGGESTION = "approve_suggestion"
    const val REJECT_SUGGESTION = "reject_suggestion"
    const val CLIENT_CAPABILITIES = "client_capabilities"

    const val ROOM_CREATED = "room_created"
    const val JOIN_REQUEST = "join_request"
    const val JOIN_APPROVED = "join_approved"
    const val JOIN_REJECTED = "join_rejected"
    const val USER_JOINED = "user_joined"
    const val USER_LEFT = "user_left"
    const val SYNC_PLAYBACK = "sync_playback"
    const val BUFFER_WAIT = "buffer_wait"
    const val BUFFER_COMPLETE = "buffer_complete"
    const val ERROR = "error"
    const val PONG = "pong"
    const val HOST_CHANGED = "host_changed"
    const val KICKED = "kicked"
    const val SYNC_STATE = "sync_state"
    const val RECONNECTED = "reconnected"
    const val USER_RECONNECTED = "user_reconnected"
    const val USER_DISCONNECTED = "user_disconnected"
    const val SUGGESTION_RECEIVED = "suggestion_received"
    const val SUGGESTION_APPROVED = "suggestion_approved"
    const val SUGGESTION_REJECTED = "suggestion_rejected"
    const val SERVER_CAPABILITIES = "server_capabilities"
}

object PlaybackActions {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK = "seek"
    const val SKIP_NEXT = "skip_next"
    const val SKIP_PREV = "skip_prev"
    const val CHANGE_TRACK = "change_track"
    const val QUEUE_ADD = "queue_add"
    const val QUEUE_REMOVE = "queue_remove"
    const val QUEUE_CLEAR = "queue_clear"
    const val SYNC_QUEUE = "sync_queue"
    const val SET_VOLUME = "set_volume"
}

@Serializable
data class Envelope(
    @ProtoNumber(1) val type: String = "",
    @ProtoNumber(2) val payload: ByteArray = ByteArray(0),
    @ProtoNumber(3) val compressed: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (
            other is Envelope &&
                type == other.type &&
                compressed == other.compressed &&
                payload.contentEquals(other.payload)
        )

    override fun hashCode(): Int =
        (type.hashCode() * 31 + payload.contentHashCode()) * 31 + compressed.hashCode()
}

@Serializable
data class TrackInfo(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val title: String = "",
    @ProtoNumber(3) val artist: String = "",
    @ProtoNumber(4) val album: String = "",
    @ProtoNumber(5) val duration: Long = 0L,
    @ProtoNumber(6) val thumbnail: String = "",
    @ProtoNumber(7) val suggestedBy: String = "",
)

@Serializable
data class UserInfo(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
    @ProtoNumber(3) val isHost: Boolean = false,
    @ProtoNumber(4) val isConnected: Boolean = false,
)

@Serializable
data class RoomState(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val hostId: String = "",
    @ProtoNumber(3) val users: List<UserInfo> = emptyList(),
    @ProtoNumber(4) val currentTrack: TrackInfo? = null,
    @ProtoNumber(5) val isPlaying: Boolean = false,
    @ProtoNumber(6) val position: Long = 0L,
    @ProtoNumber(7) val lastUpdate: Long = 0L,
    @ProtoNumber(8) val volume: Float = 0f,
    @ProtoNumber(9) val queue: List<TrackInfo> = emptyList(),
    @ProtoNumber(10) val revision: Long = 0L,
)

// Client -> Server Payloads
@Serializable
data class CreateRoomPayload(
    @ProtoNumber(1) val username: String = "",
)

@Serializable
data class JoinRoomPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
class LeaveRoomPayload

@Serializable
data class ApproveJoinPayload(
    @ProtoNumber(1) val userId: String = "",
)

@Serializable
data class RejectJoinPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
data class PlaybackActionPayload(
    @ProtoNumber(1) val action: String = "",
    @ProtoNumber(2) val trackId: String = "",
    @ProtoNumber(3) val position: Long = 0L,
    @ProtoNumber(4) val trackInfo: TrackInfo? = null,
    @ProtoNumber(5) val insertNext: Boolean = false,
    @ProtoNumber(6) val queue: List<TrackInfo> = emptyList(),
    @ProtoNumber(7) val queueTitle: String = "",
    @ProtoNumber(8) val volume: Float = 0f,
    @ProtoNumber(9) val serverTime: Long = 0L,
    @ProtoNumber(10) val revision: Long = 0L,
    @ProtoNumber(11) val capturedAtServerTime: Long = 0L,
)

@Serializable
data class PingPayload(
    @ProtoNumber(1) val clientTime: Long = 0L,
    @ProtoNumber(2) val sequence: Long = 0L,
)

@Serializable
data class BufferReadyPayload(
    @ProtoNumber(1) val trackId: String = "",
    @ProtoNumber(2) val trackRevision: Long = 0L,
)

@Serializable
data class KickUserPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
data class TransferHostPayload(
    @ProtoNumber(1) val newHostId: String = "",
)

@Serializable
data class SuggestTrackPayload(
    @ProtoNumber(1) val trackInfo: TrackInfo? = null,
)

@Serializable
data class ApproveSuggestionPayload(
    @ProtoNumber(1) val suggestionId: String = "",
)

@Serializable
data class RejectSuggestionPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

@Serializable
data class ReconnectPayload(
    @ProtoNumber(1) val sessionToken: String = "",
)

// Server -> Client Payloads
@Serializable
data class RoomCreatedPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val sessionToken: String = "",
)

@Serializable
data class JoinRequestPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
data class JoinApprovedPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val sessionToken: String = "",
    @ProtoNumber(4) val state: RoomState? = null,
)

@Serializable
data class JoinRejectedPayload(
    @ProtoNumber(1) val reason: String = "",
)

@Serializable
data class UserJoinedPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
data class UserLeftPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
data class BufferWaitPayload(
    @ProtoNumber(1) val trackId: String = "",
    @ProtoNumber(2) val waitingFor: List<String> = emptyList(),
)

@Serializable
data class BufferCompletePayload(
    @ProtoNumber(1) val trackId: String = "",
)

@Serializable
data class ErrorPayload(
    @ProtoNumber(1) val code: String = "",
    @ProtoNumber(2) val message: String = "",
)

@Serializable
data class HostChangedPayload(
    @ProtoNumber(1) val newHostId: String = "",
    @ProtoNumber(2) val newHostName: String = "",
)

@Serializable
data class KickedPayload(
    @ProtoNumber(1) val reason: String = "",
)

@Serializable
data class SyncStatePayload(
    @ProtoNumber(1) val currentTrack: TrackInfo? = null,
    @ProtoNumber(2) val isPlaying: Boolean = false,
    @ProtoNumber(3) val position: Long = 0L,
    @ProtoNumber(4) val lastUpdate: Long = 0L,
    @ProtoNumber(5) val queue: List<TrackInfo> = emptyList(),
    @ProtoNumber(6) val volume: Float = 0f,
    @ProtoNumber(7) val revision: Long = 0L,
)

@Serializable
data class PongPayload(
    @ProtoNumber(1) val clientTime: Long = 0L,
    @ProtoNumber(2) val serverReceiveTime: Long = 0L,
    @ProtoNumber(3) val serverSendTime: Long = 0L,
    @ProtoNumber(4) val sequence: Long = 0L,
)

@Serializable
data class ReconnectedPayload(
    @ProtoNumber(1) val roomCode: String = "",
    @ProtoNumber(2) val userId: String = "",
    @ProtoNumber(3) val state: RoomState? = null,
    @ProtoNumber(4) val isHost: Boolean = false,
)

@Serializable
data class UserReconnectedPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
data class UserDisconnectedPayload(
    @ProtoNumber(1) val userId: String = "",
    @ProtoNumber(2) val username: String = "",
)

@Serializable
data class SuggestionReceivedPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val fromUserId: String = "",
    @ProtoNumber(3) val fromUsername: String = "",
    @ProtoNumber(4) val trackInfo: TrackInfo? = null,
)

@Serializable
data class SuggestionApprovedPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val trackInfo: TrackInfo? = null,
)

@Serializable
data class SuggestionRejectedPayload(
    @ProtoNumber(1) val suggestionId: String = "",
    @ProtoNumber(2) val reason: String = "",
)

// Capability Handshake
@Serializable
data class ClientCapabilities(
    @ProtoNumber(1) val supportsProtobuf: Boolean = false,
    @ProtoNumber(2) val supportsCompression: Boolean = false,
    @ProtoNumber(3) val clientVersion: String = "",
)

@Serializable
data class ServerCapabilities(
    @ProtoNumber(1) val supportsProtobuf: Boolean = false,
    @ProtoNumber(2) val supportsCompression: Boolean = false,
    @ProtoNumber(3) val serverVersion: String = "",
)
