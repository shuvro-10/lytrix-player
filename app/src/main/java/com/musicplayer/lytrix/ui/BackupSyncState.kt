package com.musicplayer.lytrix.ui

object BackupSyncState {
    @Volatile var isSyncing = false
    @Volatile var isRestoring = false
    @Volatile var lastSyncTime = 0L
}