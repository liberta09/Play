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
    val topic: String,
    val event: String,
    val payload: JsonElement,
    val ref: String?,
    val joinRef: String? = null,
) {
    fun serialize(): String {
        val obj = buildJsonObject {
            put("topic", JsonPrimitive(topic))
            put("event", JsonPrimitive(event))
            put("payload", payload)
            ref?.let { put("ref", JsonPrimitive(it)) } ?: put("ref", JsonNull)
            joinRef?.let { put("join_ref", JsonPrimitive(it)) }
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
                topic = topic,
                event = event,
                payload = payload,
                ref = ref,
                joinRef = joinRef,
            )
        }.getOrNull()

        fun heartbeat(ref: String): PhoenixFrame = PhoenixFrame(
            topic = "phoenix",
            event = "heartbeat",
            payload = buildJsonObject {},
            ref = ref,
            joinRef = null,
        )

        fun join(
            topic: String,
            ref: String,
            jwt: String,
            userId: String,
            joinRef: String = ref,
        ): PhoenixFrame = PhoenixFrame(
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
            ref = ref,
            joinRef = joinRef,
        )

        fun leave(topic: String, ref: String, joinRef: String? = null): PhoenixFrame = PhoenixFrame(
            topic = topic,
            event = "phx_leave",
            payload = buildJsonObject {},
            ref = ref,
            joinRef = joinRef,
        )

        fun accessToken(topic: String, ref: String, jwt: String): PhoenixFrame = PhoenixFrame(
            topic = topic,
            event = "access_token",
            payload = buildJsonObject {
                put("access_token", JsonPrimitive(jwt))
            },
            ref = ref,
        )

        fun presenceTrack(
            topic: String,
            ref: String,
            userId: String,
            displayName: String,
            role: String,
        ): PhoenixFrame = PhoenixFrame(
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
            ref = ref,
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
                topic = topic,
                event = "broadcast",
                payload = broadcastPayload,
                ref = ref,
                joinRef = joinRef,
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
