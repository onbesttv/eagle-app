package com.example.myapplication

import android.content.Context

/**
 * Gestor centralizado de contadores de canais, filmes e séries por categoria.
 * Permite apresentar instantaneamente a quantidade de conteúdos em cada pasta
 * (Live TV, VOD e Séries) mesmo em arranque a frio, sincronizando em segundo plano.
 */
object CategoryCountHelper {
    private const val PREFS_LIVE = "cat_counts_live"
    private const val PREFS_VOD = "cat_counts_vod"
    private const val PREFS_SERIES = "cat_counts_series"

    fun getLiveCount(context: Context, categoryId: String): Int {
        return context.getSharedPreferences(PREFS_LIVE, Context.MODE_PRIVATE).getInt(categoryId, 0)
    }

    fun saveLiveCounts(context: Context, counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        val editor = context.getSharedPreferences(PREFS_LIVE, Context.MODE_PRIVATE).edit()
        counts.forEach { (catId, count) -> editor.putInt(catId, count) }
        editor.apply()
    }

    fun getVodCount(context: Context, categoryId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_VOD, Context.MODE_PRIVATE)
        val cleanId = categoryId.trim().let { if (it.endsWith(".0") && it.toDoubleOrNull() != null) it.substringBefore(".0") else it }
        val direct = prefs.getInt(cleanId, 0)
        val result = if (direct > 0) direct else prefs.getInt("$cleanId.0", 0)
        // Descarta valores anómalos ou somatórios acidentais em ALL_MOVIES
        if (cleanId == "ALL_MOVIES" && result > 6000) {
            prefs.edit().remove("ALL_MOVIES").apply()
            return 0
        }
        return result
    }

    fun saveVodCounts(context: Context, counts: Map<String, Int>, clearOld: Boolean = false) {
        if (counts.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_VOD, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (clearOld) editor.clear()
        counts.forEach { (catId, count) ->
            val cleanId = catId.trim().let { if (it.endsWith(".0") && it.toDoubleOrNull() != null) it.substringBefore(".0") else it }
            if (cleanId == "ALL_MOVIES" && count > 6000) {
                // Descarta soma inflacionada acidental
            } else {
                editor.putInt(cleanId, count)
            }
        }
        editor.apply()
    }

    fun getSeriesCount(context: Context, categoryId: String): Int {
        return context.getSharedPreferences(PREFS_SERIES, Context.MODE_PRIVATE).getInt(categoryId, 0)
    }

    fun saveSeriesCounts(context: Context, counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        val editor = context.getSharedPreferences(PREFS_SERIES, Context.MODE_PRIVATE).edit()
        counts.forEach { (catId, count) -> editor.putInt(catId, count) }
        editor.apply()
    }
}
