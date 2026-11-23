package com.example.myapplication

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Html
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object RssNewsManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val categoryCache = ConcurrentHashMap<String, List<Newspaper>>()

    data class FeedConfig(
        val url: String,
        val sourceName: String
    )

    // Configuração dos canais e jornais por categoria
    private val categoryFeeds = mapOf(
        "⚡ Ao Minuto" to listOf(
            FeedConfig("https://www.noticiasaominuto.com/rss/ultima-hora", "Ao Minuto"),
            FeedConfig("https://observador.pt/feed/", "Observador")
        ),
        "📺 TV Notícias" to listOf(
            FeedConfig("https://www.rtp.pt/noticias/rss", "RTP"),
            FeedConfig("https://cnnportugal.iol.pt/rss", "CNN Portugal"),
            FeedConfig("https://www.cmjornal.pt/rss", "CMTV / CM")
        ),
        "⚽ Desporto em Direto" to listOf(
            FeedConfig("https://www.record.pt/rss", "Record"),
            FeedConfig("https://maisfutebol.iol.pt/rss", "MaisFutebol"),
            FeedConfig("https://www.noticiasaominuto.com/rss/desporto", "Desporto"),
            FeedConfig("https://sportinforma.sapo.pt/rss", "SAPO Desporto")
        ),
        "📰 Grandes Jornais" to listOf(
            FeedConfig("https://www.cmjornal.pt/rss", "Correio da Manhã"),
            FeedConfig("https://feeds.feedburner.com/jn-ultimas", "Jornal de Notícias"),
            FeedConfig("https://feeds.feedburner.com/PublicoRSS", "Público"),
            FeedConfig("https://feeds.feedburner.com/tsf-ultimas", "TSF")
        ),
        "💶 Economia" to listOf(
            FeedConfig("https://eco.sapo.pt/feed/", "ECO"),
            FeedConfig("https://www.jornaldenegocios.pt/rss", "Negócios")
        )
    )

    fun fetchFeedsForCategory(category: String, forceRefresh: Boolean = false, onResult: (List<Newspaper>) -> Unit) {
        if (!forceRefresh) {
            val cached = categoryCache[category]
            if (cached != null && cached.isNotEmpty()) {
                mainHandler.post { onResult(cached) }
                return
            }
        }

        val feeds = categoryFeeds[category]
        if (feeds.isNullOrEmpty()) {
            mainHandler.post { onResult(emptyList()) }
            return
        }

        val collectedItems = mutableListOf<Newspaper>()
        val pendingCount = AtomicInteger(feeds.size)

        for (cfg in feeds) {
            val req = Request.Builder()
                .url(cfg.url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    checkFinished()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            val parsed = parseXml(body, cfg.sourceName, category)
                            synchronized(collectedItems) {
                                collectedItems.addAll(parsed)
                            }
                        }
                    }
                    checkFinished()
                }

                private fun checkFinished() {
                    if (pendingCount.decrementAndGet() == 0) {
                        val seenTitles = mutableSetOf<String>()
                        val unique = mutableListOf<Newspaper>()
                        synchronized(collectedItems) {
                            for (item in collectedItems) {
                                val norm = item.nome.trim().lowercase().take(50)
                                if (norm.length > 5 && seenTitles.add(norm)) {
                                    unique.add(item)
                                }
                            }
                        }
                        categoryCache[category] = unique
                        mainHandler.post {
                            onResult(unique)
                        }
                    }
                }
            })
        }
    }

    private fun parseXml(xml: String, sourceName: String, category: String): List<Newspaper> {
        val items = mutableListOf<Newspaper>()
        if (xml.isBlank()) return items

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inItem = false
            var currentTitle = ""
            var currentLink = ""
            var currentDesc = ""
            var currentPubDate = ""
            var currentImage = ""
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                        if (currentTag == "item") {
                            inItem = true
                            currentTitle = ""
                            currentLink = ""
                            currentDesc = ""
                            currentPubDate = ""
                            currentImage = ""
                        } else if (inItem) {
                            if (currentTag == "enclosure" || currentTag == "media:content" || currentTag == "media:thumbnail") {
                                val url = parser.getAttributeValue(null, "url")
                                if (!url.isNullOrBlank() && currentImage.isBlank()) {
                                    currentImage = url
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val text = parser.text.orEmpty()
                            when (currentTag) {
                                "title" -> currentTitle += text
                                "link" -> currentLink += text
                                "description", "content:encoded" -> currentDesc += text
                                "pubdate" -> currentPubDate += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name.lowercase()
                        if (endTag == "item") {
                            inItem = false
                            val cleanTitle = cleanHtml(currentTitle)
                            if (cleanTitle.isNotBlank()) {
                                if (currentImage.isBlank()) {
                                    currentImage = extractImageFromHtml(currentDesc)
                                }
                                val cleanDesc = cleanHtml(currentDesc)
                                val formattedDate = formatPubDate(currentPubDate)

                                items.add(
                                    Newspaper(
                                        id = "rss_${System.currentTimeMillis()}_${items.size}",
                                        nome = cleanTitle,
                                        categoria = category,
                                        data = formattedDate,
                                        pais = "PT",
                                        paginas = 0,
                                        capaUrl = currentImage,
                                        pdfUrl = currentLink.trim(),
                                        description = cleanDesc,
                                        isRss = true,
                                        sourceName = sourceName
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val unCdata = raw.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
        val decoded = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(unCdata, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(unCdata).toString()
            }
        } catch (_: Exception) {
            unCdata.replace(Regex("<[^>]*>"), " ")
        }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractImageFromHtml(html: String): String {
        val matcher = Regex("<img[^>]+src=[\"'](https?://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val match = matcher.find(html)
        if (match != null) {
            return match.groupValues[1]
        }
        val encodedMatcher = Regex("&lt;img[^&]+src=[\"'](https?://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val encodedMatch = encodedMatcher.find(html)
        return encodedMatch?.groupValues?.get(1).orEmpty()
    }

    private fun formatPubDate(rawDate: String): String {
        if (rawDate.isBlank()) return "Em Direto"
        val timeMatcher = Regex("\\b(\\d{1,2}:\\d{2})\\b")
        val timeMatch = timeMatcher.find(rawDate)
        return if (timeMatch != null) {
            "Hoje, ${timeMatch.groupValues[1]}"
        } else {
            "Hoje"
        }
    }
}
