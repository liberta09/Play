package com.kaan.play.data.party

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phoenix Channels V2 JSON framing format:
 * `[join_ref, ref, topic, event, payload]`
 */
data class PhoenixFrame(
    val joinRef: String?,
    val ref: String?,
    val topic: String,
    val event: String,
    val payload: JsonElement,
) {
    fun serialize(): String {
        val array = buildJsonArray {
            add(joinRef?.let { JsonPrimitive(it) } ?: JsonNull)
            add(ref?.let { JsonPrimitive(it) } ?: JsonNull)
            add(JsonPrimitive(topic))
            add(JsonPrimitive(event))
            add(payload)
        }
        return array.toString()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): PhoenixFrame? = runCatching {
            val root = json.parseToJsonElement(text).jsonArray
            if (root.size < 5) return null

            val joinRef = root[0].takeIf { it !is JsonNull }?.jsonPrimitive?.content
            val ref = root[1].takeIf { it !is JsonNull }?.jsonPrimitive?.content
            val topic = root[2].jsonPrimitive.content
            val event = root[3].jsonPrimitive.content
            val payload = root[4]

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

        fun join(topic: String, ref: String, joinRef: String = ref): PhoenixFrame = PhoenixFrame(
            joinRef = joinRef,
            ref = ref,
            topic = topic,
            event = "phx_join",
            payload = buildJsonObject {},
        )

        fun leave(topic: String, ref: String, joinRef: String? = null): PhoenixFrame = PhoenixFrame(
            joinRef = joinRef,
            ref = ref,
            topic = topic,
            event = "phx_leave",
            payload = buildJsonObject {},
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
