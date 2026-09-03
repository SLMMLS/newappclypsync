package com.clipsync.app.network

import android.util.Base64
import com.clipsync.app.crypto.CryptoUtils
import com.clipsync.app.crypto.Identity
import com.clipsync.app.data.PairedPc
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to the PC's embedded server (`server.rs`). Short timeouts on
 * purpose: a paired PC that is asleep or off the network should fail fast,
 * not hang - same "best-effort, never block" philosophy as `sync.rs`.
 */
object SyncClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()
    private val TEXT = "text/plain".toMediaType()

    fun pair(
        payload: PairingQrPayload,
        identity: Identity.KeyPairRaw,
        myDeviceId: String,
        myDeviceName: String,
    ): PairResponse {
        val request = PairRequest(
            token = payload.token,
            deviceId = myDeviceId,
            deviceName = myDeviceName,
            publicKey = Base64.encodeToString(identity.publicKey, Base64.NO_WRAP),
        )
        val httpRequest = Request.Builder()
            .url("http://${payload.addr}/pair")
            .post(request.toJson().toRequestBody(JSON))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val bodyText = response.body?.string()
            if (!response.isSuccessful || bodyText == null) {
                error("ПК отклонил сопряжение (код ${response.code})")
            }
            return PairResponse.fromJson(bodyText)
        }
    }

    /** Throws on any failure - callers decide whether that's fatal or, for
     * a background sync attempt, just something to log and move on from. */
    fun push(pc: PairedPc, identity: Identity.KeyPairRaw, myDeviceId: String, payload: PushPayload) {
        val transportKey = CryptoUtils.deriveTransportKey(Identity.sharedSecret(identity.privateKey, pc.publicKey))
        val encrypted = CryptoUtils.encrypt(transportKey, payload.toJson().toByteArray(Charsets.UTF_8))
        val bodyB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)

        val httpRequest = Request.Builder()
            .url("http://${pc.address}/push")
            .header("X-Device-Id", myDeviceId)
            .post(bodyB64.toRequestBody(TEXT))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) error("ПК не принял пуш (код ${response.code})")
        }
    }
}
