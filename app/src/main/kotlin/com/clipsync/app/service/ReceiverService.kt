package com.clipsync.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.clipsync.app.crypto.CryptoUtils
import com.clipsync.app.crypto.Identity
import com.clipsync.app.data.AppDatabase
import com.clipsync.app.data.ClipEntryRow
import com.clipsync.app.network.PushPayload
import com.clipsync.app.network.SyncServer
import com.clipsync.app.ui.MainActivity
import java.util.UUID

const val SYNC_PORT = 47990
private const val CHANNEL_ID = "clipsync_receiver"
private const val PERSISTENT_NOTIFICATION_ID = 1
private const val NEW_ITEM_NOTIFICATION_ID = 2

/**
 * Keeps the embedded HTTP receiver alive so the PC can push to this phone
 * at any time, not just while the app happens to be open. Declared with
 * foregroundServiceType="connectedDevice" in the manifest (an ongoing
 * network connection to an external device) rather than "dataSync", which
 * Google has been steering apps away from for exactly this kind of
 * always-on network listener and which carries a hard runtime cap on
 * newer Android versions.
 */
class ReceiverService : Service() {
    private var server: SyncServer? = null

    override fun onCreate() {
        super.onCreate()
        val notification = buildPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PERSISTENT_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(PERSISTENT_NOTIFICATION_ID, notification)
        }

        val identity = Identity.loadOrCreate(applicationContext)
        val localKey = Identity.loadOrCreateLocalStorageKey(applicationContext)
        val db = AppDatabase.get(applicationContext)

        server = SyncServer(
            port = SYNC_PORT,
            identity = identity,
            getPeerPublicKey = { deviceId ->
                db.pairedPcDao().getPc()?.takeIf { it.deviceId == deviceId }?.publicKey
            },
            onPushReceived = { _, payload: PushPayload ->
                val contentBytes = if (payload.entryType == "image") {
                    Base64.decode(payload.contentB64, Base64.NO_WRAP)
                } else {
                    payload.contentB64.toByteArray(Charsets.UTF_8)
                }
                db.clipDao().insert(
                    ClipEntryRow(
                        id = UUID.randomUUID().toString(),
                        entryType = payload.entryType,
                        encryptedContent = CryptoUtils.encrypt(localKey, contentBytes),
                        createdAt = payload.createdAt,
                        pinned = false,
                        direction = "incoming",
                    ),
                )
                showNewItemNotification()
            },
        )
        server?.start()
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildPersistentNotification(): Notification {
        createChannelIfNeeded()
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipSync активен")
            .setContentText("Готов принимать буфер с ПК")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun showNewItemNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Новый элемент с ПК")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setAutoCancel(true)
            .build()
        manager.notify(NEW_ITEM_NOTIFICATION_ID, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "ClipSync", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
