package com.example.myapplication

import android.content.Context
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object EagleTvConfig {
    private const val LIVE_FAV_PREFS = "eagle_tv_live_favs"
    private const val KEY_FAV_IDS = "fav_stream_ids"

    private const val LIVE_HISTORY_PREFS = "eagle_tv_live_history"
    private const val KEY_RECENT_JSON = "recent_channels_json"
    private const val MAX_RECENT_CHANNELS = 25

    private const val SETTINGS_PREFS = "app_settings"
    private const val KEY_ASPECT_MODE = "eagle_tv_aspect_mode"

    const val ASPECT_FIT = 0
    const val ASPECT_FILL = 1
    const val ASPECT_ZOOM = 2

    private val gson = Gson()

    fun isEagleTvEdition(context: Context): Boolean {
        if (context.packageName.equals("com.eagle.tv", ignoreCase = true)) return true
        if (context.packageName.equals("com.redon.tv", ignoreCase = true)) return true
        return try {
            val rid = context.getString(R.string.reseller_id).trim().lowercase()
            rid == "eagle_tv" || rid == "dream"
        } catch (_: Exception) {
            false
        }
    }

    // --- FAVORITOS DE CANAIS AO VIVO ---
    fun getFavoriteChannelIds(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(LIVE_FAV_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_FAV_IDS, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun isFavoriteChannel(context: Context, streamId: Int): Boolean {
        val prefs = context.getSharedPreferences(LIVE_FAV_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_FAV_IDS, emptySet()) ?: emptySet()
        return raw.contains(streamId.toString())
    }

    fun toggleFavoriteChannel(context: Context, streamId: Int): Boolean {
        val prefs = context.getSharedPreferences(LIVE_FAV_PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_FAV_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = streamId.toString()
        val added = if (current.contains(key)) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        prefs.edit().putStringSet(KEY_FAV_IDS, current).apply()
        return added
    }

    // --- HISTÓRICO DE ÚLTIMOS CANAIS VISTOS (ESTILO FORMULER / TIVIMATE) ---
    fun recordRecentChannel(context: Context, channel: LiveStream) {
        val current = getRecentChannels(context).toMutableList()
        current.removeAll { it.streamId == channel.streamId }
        current.add(0, channel)
        val trimmed = current.take(MAX_RECENT_CHANNELS)
        context.getSharedPreferences(LIVE_HISTORY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_JSON, gson.toJson(trimmed))
            .apply()
    }

    fun getRecentChannels(context: Context): List<LiveStream> {
        val prefs = context.getSharedPreferences(LIVE_HISTORY_PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LiveStream>>() {}.type
            gson.fromJson<List<LiveStream>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- FORMATO DE ECRÃ (ASPECT RATIO) ---
    fun getAspectMode(context: Context): Int {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ASPECT_MODE, ASPECT_FIT)
            .coerceIn(ASPECT_FIT, ASPECT_ZOOM)
    }

    fun cycleAspectMode(context: Context): Int {
        val next = (getAspectMode(context) + 1) % 3
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ASPECT_MODE, next)
            .apply()
        return next
    }

    fun toPlayerResizeMode(mode: Int): Int {
        return when (mode) {
            ASPECT_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            ASPECT_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun aspectModeLabel(mode: Int): String {
        return when (mode) {
            ASPECT_FILL -> "16:9 Ecrã Completo"
            ASPECT_ZOOM -> "Zoom Cinema"
            else -> "Original (Ajustar)"
        }
    }
}
