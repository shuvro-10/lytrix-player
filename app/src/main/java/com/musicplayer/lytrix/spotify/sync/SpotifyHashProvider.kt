package com.musicplayer.lytrix.spotify.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe provider for Spotify GQL persisted-query hashes.
 * Initialized with verified defaults from Meld.
 * Can be dynamically updated from remote JSON registry at runtime.
 */
object SpotifyHashProvider {

    enum class HashSource { HARDCODED, CACHED, REMOTE }

    data class GqlHashEntry(
        val hash: String,
        val previousHash: String? = null,
        val source: HashSource = HashSource.HARDCODED,
    )

    private val hashes = ConcurrentHashMap<String, GqlHashEntry>()

    init {
        loadHardcodedDefaults()
    }

    private fun loadHardcodedDefaults() {
        val defaults = mapOf(
            "profileAttributes" to "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
            "libraryV3" to "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3",
            "fetchPlaylist" to "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763",
            "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
            "searchDesktop" to "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49",
            "searchTracks" to "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428",
            "getAlbum" to "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10",
            "addToLibrary" to "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d",
            "removeFromLibrary" to "7c5a69420e2bfae3da5cc4e14cbc8bb3f6090f80afc00ffc179177f19be3f33d",
            "home" to "23e37f2e58d82d567f27080101d36609009d8c3676457b1086cb0acc55b72a5d",
        )
        defaults.forEach { (op, hash) ->
            hashes[op] = GqlHashEntry(hash = hash, source = HashSource.HARDCODED)
        }
    }

    fun getHash(operationName: String): String =
        hashes[operationName]?.hash
            ?: error("No hash registered for GQL operation: $operationName")

    fun getPreviousHash(operationName: String): String? =
        hashes[operationName]?.previousHash

    fun updateHashes(
        remoteHashes: Map<String, GqlHashEntry>,
        source: HashSource,
    ) {
        remoteHashes.forEach { (op, remote) ->
            hashes[op] = GqlHashEntry(
                hash = remote.hash,
                previousHash = remote.previousHash,
                source = source,
            )
        }
    }

    fun getAll(): Map<String, GqlHashEntry> = hashes.toMap()
}
