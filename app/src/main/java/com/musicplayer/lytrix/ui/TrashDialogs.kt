package com.musicplayer.lytrix.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.musicplayer.lytrix.R

@Composable
fun PremiumTrashConfirmDialog(
    uris: List<android.net.Uri>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onConfirm()
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AndroidView(
            factory = { context ->
                val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_trash_confirm, null)
                
                view.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener {
                    onDismiss()
                }
                
                view.findViewById<android.widget.Button>(R.id.btnConfirm).setOnClickListener {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            val pi = android.provider.MediaStore.createTrashRequest(context.contentResolver, uris, true)
                            launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onConfirm() // fallback to direct deletion if it fails unexpectedly
                            onDismiss()
                        }
                    } else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                        try {
                            var allSuccess = true
                            uris.forEach { uri ->
                                val values = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 1) }
                                if (context.contentResolver.update(uri, values, null, null) <= 0) {
                                    allSuccess = false
                                }
                            }
                            if (allSuccess) {
                                onConfirm()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            if (e is android.app.RecoverableSecurityException) {
                                launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                            } else {
                                onConfirm() // fallback
                                onDismiss()
                            }
                        }
                    } else {
                        // Pre-Q (Android 9 and below), no scopes, we can just do manual file deletion here
                        onConfirm()
                        onDismiss()
                    }
                }
                
                view
            },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}
