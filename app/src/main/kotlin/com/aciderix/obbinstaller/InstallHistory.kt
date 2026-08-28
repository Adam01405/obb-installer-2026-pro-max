package com.aciderix.obbinstaller

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class InstallRecord(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val hasObb: Boolean,
    val hasPatch: Boolean,
    val timestamp: Long
)

object InstallHistory {
    private const val PREFS = "install_history"
    private const val KEY = "records"
    private const val MAX = 20

    fun load(context: Context): List<InstallRecord> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            InstallRecord(
                packageName = o.getString("packageName"),
                versionName = o.getString("versionName"),
                versionCode = o.getLong("versionCode"),
                apkName = o.getString("apkName"),
                hasObb = o.getBoolean("hasObb"),
                hasPatch = o.getBoolean("hasPatch"),
                timestamp = o.getLong("timestamp")
            )
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, record: InstallRecord) {
        val current = load(context).toMutableList()
        current.removeAll { it.packageName == record.packageName }
        current.add(0, record)
        save(context, current.take(MAX))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun save(context: Context, records: List<InstallRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("packageName", r.packageName)
                put("versionName", r.versionName)
                put("versionCode", r.versionCode)
                put("apkName", r.apkName)
                put("hasObb", r.hasObb)
                put("hasPatch", r.hasPatch)
                put("timestamp", r.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
