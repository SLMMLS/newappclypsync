package com.clipsync.app.network

import android.util.Base64
import android.util.Log
import com.clipsync.app.crypto.CryptoUtils
import com.clipsync.app.crypto.Identity
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking

/**
 * Android only ever *receives* /push (it always initiates /pair itself, by
 * scanning the PC's QR - see PROTOCOL.md §2), so this only needs to
 * implement one route.
 */
class SyncServer(
    port: Int,
    private val identity: Identity.KeyPairRaw,
    private val getPeerPublicKey: suspend (deviceId: String) -> ByteArray?,
    private val onPushReceived: suspend (senderDeviceId: String, payload: PushPayload) -> Unit,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/push" || session.method != Method.POST) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }

        return try {
            // NanoHTTPD lowercases header names internally.
            val senderId = session.headers["x-device-id"]
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "missing X-Device-Id")

            val bodyText = readBody(session).trim()
            val raw = Base64.decode(bodyText, Base64.NO_WRAP)

            val theirPublicKey = runBlocking { getPeerPublicKey(senderId) }
                ?: return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "unknown device")

            val transportKey = CryptoUtils.deriveTransportKey(Identity.sharedSecret(identity.privateKey, theirPublicKey))
            val plaintext = CryptoUtils.decrypt(transportKey, raw)
            val payload = PushPayload.fromJson(String(plaintext, Charsets.UTF_8))

            runBlocking { onPushReceived(senderId, payload) }
            newFixedLengthResponse(Response.Status.OK, "application/json", "{}")
        } catch (e: Exception) {
            Log.e("SyncServer", "failed to handle /push", e)
            newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "could not authenticate payload")
        }
    }

    /** `InputStream.read` is not guaranteed to fill the buffer in one call
     * over a socket, so this loops until it actually has everything. */
    private fun readBody(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        val buffer = ByteArray(length)
        var readSoFar = 0
        while (readSoFar < length) {
            val n = session.inputStream.read(buffer, readSoFar, length - readSoFar)
            if (n == -1) break
            readSoFar += n
        }
        return String(buffer, 0, readSoFar, Charsets.UTF_8)
    }
}
