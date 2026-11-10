package com.example.myapplication

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SportsAgendaManager {
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val CACHE_FILE_NAME = "sports_agenda_cache.json"
    private const val REMOTE_FALLBACK_URL = "https://raw.githubusercontent.com/onbesttv/eagle-app/main/data/agenda_jogos.json"

    /**
     * Carrega a agenda atualizada.
     * Retorna de imediato a lista local/em cache (filtrando jogos que já terminaram)
     * e, em background, busca a grelha em direto online para atualizar.
     */
    fun loadOrRefresh(context: Context, forceOnline: Boolean = false, onResult: (List<SportsMatch>) -> Unit) {
        val appContext = context.applicationContext

        // 1. Carrega dados rápidos da cache local ou assets
        if (!forceOnline) {
            val cached = loadCachedMatches(appContext)
            val filteredCached = filterActiveMatches(cached)
            if (filteredCached.isNotEmpty()) {
                onResult(filteredCached)
            }
        }

        // 2. Procura em background a versão mais recente em direto
        Thread {
            try {
                var freshMatches = fetchFromOndebola()
                if (freshMatches.isEmpty()) {
                    freshMatches = fetchFromGithubFallback()
                }

                if (freshMatches.isNotEmpty()) {
                    val activeMatches = filterActiveMatches(freshMatches)
                    saveToCache(appContext, activeMatches)
                    mainHandler.post {
                        onResult(activeMatches)
                    }
                }
            } catch (e: Exception) {
                // Silencioso se estiver offline, mantém dados em cache
            }
        }.start()
    }

    /**
     * Filtra diariamente os jogos que já terminaram hoje ou em dias passados.
     * Mantém os jogos a decorrer (até ~2h15m após início) e todos os jogos futuros.
     */
    fun filterActiveMatches(matches: List<SportsMatch>): List<SportsMatch> {
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val todayDay = calendar.get(Calendar.DAY_OF_MONTH)

        return matches.filter { match ->
            val day = match.data.trim().lowercase()

            val timeParts = match.hora.split(":")
            val gameHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
            val gameMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            val gameMinutes = gameHour * 60 + gameMinute

            // Verifica se a data corresponde a hoje (ex: "10 nov", "11 nov", ou contém "hoje")
            val dayNumber = day.split(" ").firstOrNull()?.toIntOrNull()
            val isToday = day.contains("hoje") || (dayNumber != null && dayNumber == todayDay)

            if (isToday) {
                // Jogo dura ~135 minutos (90m + intervalo + descontos)
                // Se o jogo começou há mais de 135 minutos, já acabou
                if (currentMinutes > gameMinutes + 135) {
                    return@filter false
                }
                return@filter true
            }

            // Ignora dias anteriores do mês
            if (dayNumber != null && dayNumber < todayDay) {
                return@filter false
            }

            if (day.contains("ontem")) {
                return@filter false
            }

            // Jogos futuros (amanhã, próximos dias do calendário)
            true
        }
    }

    private fun loadCachedMatches(context: Context): List<SportsMatch> {
        return try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists() && cacheFile.length() > 50) {
                val feed = gson.fromJson(cacheFile.readText(), SportsAgendaFeed::class.java)
                if (feed != null && feed.jogos.isNotEmpty()) {
                    return feed.jogos
                }
            }

            context.assets.open("agenda_jogos.json").bufferedReader().use { reader ->
                val feed = gson.fromJson(reader, SportsAgendaFeed::class.java)
                feed?.jogos ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToCache(context: Context, matches: List<SportsMatch>) {
        try {
            val feed = SportsAgendaFeed(
                updatedAt = System.currentTimeMillis().toString(),
                jogos = matches
            )
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            cacheFile.writeText(gson.toJson(feed))
        } catch (_: Exception) {}
    }

    private fun fetchFromOndebola(): List<SportsMatch> {
        return try {
            val request = Request.Builder()
                .url("https://ondebola.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body?.string().orEmpty()
            parseOndebolaHtml(html)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchFromGithubFallback(): List<SportsMatch> {
        return try {
            val request = Request.Builder()
                .url("$REMOTE_FALLBACK_URL?v=${System.currentTimeMillis()}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string().orEmpty()
            val feed = gson.fromJson(body, SportsAgendaFeed::class.java)
            feed?.jogos ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseOndebolaHtml(html: String): List<SportsMatch> {
        val results = mutableListOf<SportsMatch>()
        val rowPattern = Pattern.compile("(?si)<tr data-game-id=[\"']?(\\d+)[\"']?[^>]*>(.*?)</tr>")
        val teamPattern = Pattern.compile("(?si)<span class=[\"']?team[\"']?[^>]*>(.*?)</span>")
        val extraPattern = Pattern.compile("(?si)<span class=[\"']?extra[\"']?[^>]*>(.*?)</span>")
        val datePattern = Pattern.compile("(?si)<td class=[\"']?col-date[\"']?[^>]*>(.*?)</td>")
        val channelLinkPattern = Pattern.compile("(?si)<a[^>]*title=[\"']?([^\"']+)[\"']?[^>]*>(.*?)</a>")

        val matcher = rowPattern.matcher(html)
        var count = 0

        while (matcher.find()) {
            val rowContent = matcher.group(2) ?: continue

            // Data e Hora
            val dateMatcher = datePattern.matcher(rowContent)
            val dateRaw = if (dateMatcher.find()) dateMatcher.group(1).orEmpty() else ""
            val cleanDate = stripHtml(dateRaw)

            val timeMatcher = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b").matcher(cleanDate)
            val hora = if (timeMatcher.find()) timeMatcher.group(1).orEmpty() else ""

            val isToday = dateRaw.contains("gametoday", ignoreCase = true) || cleanDate.contains("hoje", ignoreCase = true)
            val dateMatcherNum = Pattern.compile("\\b(\\d{1,2}\\s+[a-zA-Záéíóúçãõ]+)\\b").matcher(cleanDate)
            val dia = if (dateMatcherNum.find()) {
                dateMatcherNum.group(1).orEmpty()
            } else if (isToday) {
                val cal = Calendar.getInstance()
                "${cal.get(Calendar.DAY_OF_MONTH)} Nov"
            } else {
                cleanDate.split(" ").firstOrNull()?.trim() ?: "Hoje"
            }

            // Equipas
            val teamMatcher = teamPattern.matcher(rowContent)
            val teams = mutableListOf<String>()
            while (teamMatcher.find()) {
                val t = decodeHtmlEntities(stripHtml(teamMatcher.group(1).orEmpty())).trim()
                if (t.isNotBlank()) teams.add(t)
            }
            if (teams.size < 2) continue
            val homeTeam = teams[0]
            val awayTeam = teams[1]

            // Liga / Competição
            val extraMatcher = extraPattern.matcher(rowContent)
            var liga = if (extraMatcher.find()) decodeHtmlEntities(stripHtml(extraMatcher.group(1).orEmpty())).trim() else "Futebol"
            liga = formatLeagueName(liga)

            // Canal
            val channelMatcher = channelLinkPattern.matcher(rowContent)
            var canal = if (channelMatcher.find()) {
                decodeHtmlEntities(stripHtml(channelMatcher.group(2).orEmpty())).trim()
            } else ""

            canal = formatChannelName(canal)

            count++
            val destaque = liga.contains("Portugal", ignoreCase = true) ||
                    canal.contains("Sport TV 1", ignoreCase = true) ||
                    canal.contains("DAZN 1", ignoreCase = true) ||
                    canal.contains("BTV", ignoreCase = true)

            results.add(
                SportsMatch(
                    id = count.toString(),
                    data = dia,
                    hora = hora,
                    liga = liga,
                    equipaCasa = homeTeam,
                    equipaFora = awayTeam,
                    canal = canal,
                    destaque = destaque
                )
            )
        }

        return results
    }

    private fun formatChannelName(raw: String): String {
        var c = raw.trim()
            .replace(Regex("(?i)sport\\.tv\\s*1"), "Sport TV 1")
            .replace(Regex("(?i)sport\\.tv\\s*2"), "Sport TV 2")
            .replace(Regex("(?i)sport\\.tv\\s*3"), "Sport TV 3")
            .replace(Regex("(?i)sport\\.tv\\s*4"), "Sport TV 4")
            .replace(Regex("(?i)sport\\.tv\\s*5"), "Sport TV 5")
            .replace(Regex("(?i)sport\\.tv\\s*6"), "Sport TV 6")
            .replace(Regex("(?i)dazn\\s*1"), "DAZN 1")
            .replace(Regex("(?i)dazn\\s*2"), "DAZN 2")
            .replace(Regex("(?i)dazn\\s*3"), "DAZN 3")
            .replace(Regex("(?i)dazn\\s*4"), "DAZN 4")
            .replace(Regex("(?i)dazn\\s*5"), "DAZN 5")
            .replace(Regex("(?i)dazn\\s*6"), "DAZN 6")
            .replace(Regex("(?i)canal\\s*11"), "Canal 11")
            .replace(Regex("(?i)benfica\\s*tv|btv"), "BTV 1")
            .replace(Regex("(?i)porto\\s*canal"), "Porto Canal")
            .replace(Regex("(?i)rtp\\s*1"), "RTP 1")
            .replace(Regex("(?i)rtp\\s*2"), "RTP 2")
            .replace(Regex("(?i)\\bsic\\b"), "SIC")
            .replace(Regex("(?i)\\btvi\\b"), "TVI")

        if (c.isBlank() || c.contains("betano", ignoreCase = true) || c.contains("livestream", ignoreCase = true)) {
            c = "A anunciar"
        }
        return c
    }

    private fun formatLeagueName(raw: String): String {
        val l = raw.trim()
        val lower = l.lowercase()
        return when {
            lower.contains("liga portugal") -> "🇵🇹 Liga Portugal"
            lower.contains("taça de portugal") || lower.contains("taca de portugal") -> "🇵🇹 Taça de Portugal"
            lower.contains("champions") -> "⭐ Champions League"
            lower.contains("europa") -> "🏆 Liga Europa"
            lower.contains("confer") -> "🏆 Liga Conferência"
            lower.contains("premier") || lower.contains("inglaterra") -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League"
            lower.contains("la liga") || lower.contains("espanha") -> "🇪🇸 La Liga"
            lower.contains("serie a") || lower.contains("itália") -> "🇮🇹 Serie A"
            lower.contains("bundesliga") || lower.contains("alemanha") -> "🇩🇪 Bundesliga"
            lower.contains("nations") || lower.contains("nações") -> "🇪🇺 Liga das Nações"
            lower.contains("futsal") -> "⚽ Futsal"
            else -> l
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        var res = text
        // Entidades numéricas &#233; -> é
        val numPattern = Pattern.compile("&#(\\d+);")
        val matcher = numPattern.matcher(res)
        val sb = StringBuffer()
        while (matcher.find()) {
            val code = matcher.group(1)?.toIntOrNull() ?: continue
            matcher.appendReplacement(sb, code.toChar().toString())
        }
        matcher.appendTail(sb)
        res = sb.toString()

        return res
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#233;", "é")
            .replace("&#225;", "á")
            .replace("&#227;", "ã")
            .replace("&#243;", "ó")
            .replace("&#237;", "í")
            .replace("&#250;", "ú")
            .replace("&#234;", "ê")
            .replace("&#226;", "â")
            .replace("&#231;", "ç")
    }
}
