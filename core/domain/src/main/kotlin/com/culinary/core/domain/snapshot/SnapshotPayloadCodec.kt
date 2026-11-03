package com.culinary.core.domain.snapshot

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 快照 payload 编解码；解码失败映射为可预期错误，不抛到 UI。 */
object SnapshotPayloadCodec {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(payload: RequirementSnapshotPayload): String =
        json.encodeToString(RequirementSnapshotPayload.serializer(), payload)

    fun decode(text: String): Outcome<RequirementSnapshotPayload> =
        try {
            Outcome.success(json.decodeFromString(RequirementSnapshotPayload.serializer(), text))
        } catch (e: SerializationException) {
            invalid("snapshot payload is not valid JSON", e)
        } catch (e: IllegalArgumentException) {
            invalid("snapshot payload is malformed", e)
        }

    private fun invalid(
        message: String,
        cause: Throwable,
    ): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Transfer(
                message = message,
                code = ErrorCode.BACKUP_003,
                cause = cause,
            ),
        )
}
