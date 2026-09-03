package com.clipsync.app.network

import org.json.JSONObject

/** What the PC's pairing QR / numeric code encodes - see PROTOCOL.md §2. */
data class PairingQrPayload(val v: Int, val name: String, val addr: String, val token: String) {
    companion object {
        fun fromJson(json: String): PairingQrPayload {
            val o = JSONObject(json)
            return PairingQrPayload(o.getInt("v"), o.getString("name"), o.getString("addr"), o.getString("token"))
        }
    }
}

data class PairRequest(val token: String, val deviceId: String, val deviceName: String, val publicKey: String) {
    fun toJson(): String = JSONObject()
        .put("token", token)
        .put("device_id", deviceId)
        .put("device_name", deviceName)
        .put("public_key", publicKey)
        .toString()
}

data class PairResponse(val deviceId: String, val deviceName: String, val publicKey: String) {
    companion object {
        fun fromJson(json: String): PairResponse {
            val o = JSONObject(json)
            return PairResponse(o.getString("device_id"), o.getString("device_name"), o.getString("public_key"))
        }
    }
}

/** The plaintext shape sealed inside every /push body - see PROTOCOL.md §3. */
data class PushPayload(
    val entryType: String,
    val contentB64: String,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val createdAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("entry_type", entryType)
        .put("content_b64", contentB64)
        .put("image_width", imageWidth ?: JSONObject.NULL)
        .put("image_height", imageHeight ?: JSONObject.NULL)
        .put("created_at", createdAt)
        .toString()

    companion object {
        fun fromJson(json: String): PushPayload {
            val o = JSONObject(json)
            return PushPayload(
                entryType = o.getString("entry_type"),
                contentB64 = o.getString("content_b64"),
                imageWidth = if (o.isNull("image_width")) null else o.getInt("image_width"),
                imageHeight = if (o.isNull("image_height")) null else o.getInt("image_height"),
                createdAt = o.getLong("created_at"),
            )
        }
    }
}
