package com.example.myapplication

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ServiceRefreshResult(
    val counts: Map<String, Int>,
    val errors: List<String>
) {
    fun summary(): String = buildString {
        if (errors.isEmpty()) append("Atualização concluída.\n\n")
        else append("Atualização parcial; algumas listas não responderam.\n\n")
        append("Canais: ${counts["canais"] ?: "—"}\n")
        append("Filmes: ${counts["filmes"] ?: "—"}\n")
        append("Séries: ${counts["séries"] ?: "—"}")
        if (errors.isNotEmpty()) append("\n\nSem resposta: ${errors.joinToString(", ")}")
        append("\n\nAo abrir TV, Filmes ou Séries, a app carrega as listas atualizadas do serviço.")
    }
}

object ServiceCatalogUpdater {
    private const val BASE_URL = "https://allrevplay.online:443"
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    private val endpoints = listOf(
        "get_live_categories" to "categorias de canais",
        "get_live_streams" to "canais",
        "get_vod_categories" to "categorias de filmes",
        "get_vod_streams" to "filmes",
        "get_series_categories" to "categorias de séries",
        "get_series" to "séries"
    )

    fun refresh(
        username: String,
        password: String,
        onProgress: (String) -> Unit,
        onComplete: (ServiceRefreshResult) -> Unit,
        onItemStart: (String) -> Unit = {},
        onItemComplete: (String, Int?) -> Unit = { _, _ -> }
    ) {
        thread(name = "service-catalog-refresh") {
            val counts = linkedMapOf<String, Int>()
            val errors = mutableListOf<String>()
            endpoints.forEachIndexed { index, (action, label) ->
                val counterKey = when (action) {
                    "get_live_streams" -> "canais"
                    "get_vod_streams" -> "filmes"
                    "get_series" -> "séries"
                    else -> null
                }
                counterKey?.let(onItemStart)
                onProgress("A atualizar $label (${index + 1}/${endpoints.size})…")
                val url = "$BASE_URL/player_api.php?username=${Uri.encode(username)}&password=${Uri.encode(password)}&action=$action"
                var returnedCount: Int? = null
                try {
                    client.newCall(Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build())
                        .execute().use { response ->
                            val body = response.body?.string().orEmpty()
                            if (response.isSuccessful) {
                                val count = org.json.JSONArray(body).length()
                                counts[label] = count
                                returnedCount = count
                            }
                            else errors.add(label)
                        }
                } catch (_: Exception) {
                    errors.add(label)
                }
                if (counterKey != null) onItemComplete(counterKey, returnedCount)
            }
            onComplete(ServiceRefreshResult(counts, errors))
        }
    }
}
