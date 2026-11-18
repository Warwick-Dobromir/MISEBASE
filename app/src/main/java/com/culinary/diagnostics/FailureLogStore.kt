package com.culinary.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 本机失败记录：只保存错误时间、来源和错误文本，不采集业务数据。 */
data class FailureLogEntry(
    val timestamp: Long,
    val source: String,
    val message: String,
)

object FailureLogStore {
    private const val PREFS_NAME = "misebase_failure_logs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 100

    fun record(context: Context, source: String, message: String) {
        synchronized(this) {
            val entries = readArray(context)
            entries.put(
                JSONObject()
                    .put("timestamp", System.currentTimeMillis())
                    .put("source", source)
                    .put("message", message),
            )
            val trimmed = JSONArray()
            val start = (entries.length() - MAX_ENTRIES).coerceAtLeast(0)
            for (index in start until entries.length()) {
                trimmed.put(entries.getJSONObject(index))
            }
            preferences(context).edit().putString(KEY_ENTRIES, trimmed.toString()).apply()
        }
    }

    fun read(context: Context): List<FailureLogEntry> =
        synchronized(this) {
            val entries = readArray(context)
            buildList {
                for (index in entries.length() - 1 downTo 0) {
                    val item = entries.optJSONObject(index) ?: continue
                    add(
                        FailureLogEntry(
                            timestamp = item.optLong("timestamp"),
                            source = item.optString("source"),
                            message = item.optString("message"),
                        ),
                    )
                }
            }
        }

    private fun readArray(context: Context): JSONArray {
        val raw = preferences(context).getString(KEY_ENTRIES, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
