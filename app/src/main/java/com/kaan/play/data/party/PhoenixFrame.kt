package com.kaan.play.data.party

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phoenix Channels V1 JSON framing format (vsn=1.0.0):
 * `{"topic": topic, "event": event, "payload": payload, "ref": ref, "join_ref": joinRef}`
 *
 * vsn=1.0.0 seçilme nedeni:
 * Standart Phoenix JSON nesne formatıdır; alan adları açıkça belirtildiği için hata ayıklama
 * ve loglama çok daha güvenlidir. Dizi tabanlı (vsn=2.0.0) formattaki sıra kayması riskini ortadan kaldırır.
 */
data class PhoenixFrame(
    val joinRef: String? = null,
    val ref: String?,
    val topic: String,
    val event: String,
    val payload: JsonElement,
) {
    fun serialize(): String {
        val currentRef = this.ref
        val currentJoinRef = this.joinRef
        val obj = buildJsonObject {
            put("topic", JsonPrimitive(topic))
            put("event", JsonPrimitive(event))
            put("payload", payload)
            if (currentRef != null) {
                put("ref", JsonPrimitive(currentRef))
            } else {
                put("ref", JsonNull)
            }
            if (currentJoinRef != null) {
                put("join_ref", JsonPrimitive(currentJoinRef))
            }
        }
        return obj.toString()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PhoenixFrame? = runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val topic = root["topic"]?.jsonPrimitive?.content ?: return null
            val event = root["event"]?.jsonPrimitive?.content ?: return null
            val payload = root["payload"] ?: buildJsonObject {}
            val ref = root["ref"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
            val joinRef = root["join_ref"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

            PhoenixFrame(
                joinRef = joinRef,
                ref = ref,
                topic = topic,
                event = event,
                payload = payload,
            )
        }.getOrNull()

        fun heartbeat(ref: String): PhoenixFrame = PhoenixFrame(
            joinRef = null,
            ref = ref,
            topic = "phoenix",
            event = "heartbeat",
            payload = buildJsonObject {},
        )

        fun join(
            topic: String,
            ref: String,
            jwt: String,
            userId: String,
            joinRef: String = ref,
        ): PhoenixFrame = PhoenixFrame(
            joinRef = joinRef,
            ref = ref,
            topic = topic,
            event = "phx_join",
            payload = buildJsonObject {
                put("config", buildJsonObject {
                    put("broadcast", buildJsonObject {
                        put("self", JsonPrimitive(false))
                        put("ack", JsonPrimitive(false))
                    })
                    put("presence", buildJsonObject {
                        put("key", JsonPrimitive(userId))
                    })
                    put("private", JsonPrimitive(true))
                })
                put("access_token", JsonPrimitive(jwt))
            },
        )

        fun leave(topic: String, ref: String, joinRef: String? = null): PhoenixFrame = PhoenixFrame(
            joinRef = joinRef,
            ref = ref,
            topic = topic,
            event = "phx_leave",
            payload = buildJsonObject {},
        )

        fun accessToken(topic: String, ref: String, jwt: String): PhoenixFrame = PhoenixFrame(
            joinRef = null,
            ref = ref,
            topic = topic,
            event = "access_token",
            payload = buildJsonObject {
                put("access_token", JsonPrimitive(jwt))
            },
        )

        fun presenceTrack(
            topic: String,
            ref: String,
            userId: String,
            displayName: String,
            role: String,
        ): PhoenixFrame = PhoenixFrame(
            joinRef = null,
            ref = ref,
            topic = topic,
            event = "presence",
            payload = buildJsonObject {
                put("type", JsonPrimitive("presence"))
                put("event", JsonPrimitive("track"))
                put("payload", buildJsonObject {
                    put("user_id", JsonPrimitive(userId))
                    put("display_name", JsonPrimitive(displayName))
                    put("role", JsonPrimitive(role))
                })
            },
        )

        fun broadcast(
            topic: String,
            ref: String,
            event: String,
            data: JsonElement,
            joinRef: String? = null,
        ): PhoenixFrame {
            val broadcastPayload = buildJsonObject {
                put("type", JsonPrimitive("broadcast"))
                put("event", JsonPrimitive(event))
                put("payload", data)
            }
            return PhoenixFrame(
                joinRef = joinRef,
                ref = ref,
                topic = topic,
                event = "broadcast",
                payload = broadcastPayload,
            )
        }

        fun extractBroadcast(frame: PhoenixFrame): Pair<String, JsonElement>? = runCatching {
            if (frame.event != "broadcast") return null
            val obj = frame.payload.jsonObject
            val event = obj["event"]?.jsonPrimitive?.content ?: return null
            val innerPayload = obj["payload"] ?: return null
            event to innerPayload
        }.getOrNull()
    }
}
