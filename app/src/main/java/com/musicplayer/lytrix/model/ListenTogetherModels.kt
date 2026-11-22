package com.musicplayer.lytrix.model

import androidx.annotation.Keep

@Keep
data class ListenRoom(
    var hostId: String = "",
    var roomCode: String = "",
    var roomTitle: String = "", // 🏷️ কাস্টম রুম নাম (max 25 chars)
    var roomVibe: String = "Chill 🎧", // 🎨 রুম ভাইব মোড
    var pinnedNotice: String = "", // 📌 পিন করা নোটিশ
    var isLocked: Boolean = false, // 🔒 রুম প্রাইভেসি লক
    var activePoll: RoomPoll? = null, // 🗳️ লাইভ পোল
    var currentRatings: HashMap<String, Int> = hashMapOf(), // ⭐ ১-৫ স্টার রেটিং
    var playbackState: SyncState = SyncState(),
    var members: HashMap<String, RoomMember> = hashMapOf(),
    var coHosts: HashMap<String, Boolean> = hashMapOf(),
    var messages: HashMap<String, ChatMessage> = hashMapOf(), // 🔥 চ্যাট স্টোরেজ
    var suggestions: HashMap<String, FirebaseSongSuggestion> = hashMapOf() // 🎵 সং সাজেশন
)

@Keep
data class RoomPoll(
    var pollId: String = "",
    var creatorName: String = "",
    var question: String = "",
    var options: List<String> = emptyList(),
    var votes: HashMap<String, Int> = hashMapOf(), // deviceId -> optionIndex
    var active: Boolean = true,
    var createdAt: Long = 0L
)

@Keep
data class FirebaseSongSuggestion(
    var suggestionId: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var songId: String = "",
    var title: String = "",
    var artist: String = "",
    var imageUrl: String = "",
    var streamUrl: String = "",
    var timestamp: Long = 0L
)

@Keep
data class SyncState(
    var songId: String = "",
    var title: String = "",
    var artist: String = "",
    var imageUrl: String = "",
    var streamUrl: String = "", // 🔥 জিওসাভনের গান চালানোর জন্য এটা অ্যাড করা হলো
    var isPlaying: Boolean = false,
    var currentPosition: Long = 0L,
    var updatedAt: Long = 0L,
    var playbackSpeed: Float = 1.0f,
    var mediaCategory: String = "MUSIC"
)

@Keep
data class RoomMember(
    var name: String = "",
    var status: String = "pending",
    var online: Boolean = true
)

@Keep
data class ChatMessage(
    var senderId: String = "",
    var senderName: String = "",
    var message: String = "",
    var timestamp: Long = 0L,
    var role: String = "guest",
    val type: String = "text",       // 🎤 "text" বা "voice"
    val voiceId: String = "",
    val audioDuration: Int = 0,       // সেকেন্ড
    val replyToId: String = "",
    val replyToName: String = "",
    val replyToText: String = ""
)