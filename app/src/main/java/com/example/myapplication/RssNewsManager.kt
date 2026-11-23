package com.example.myapplication

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object RssNewsManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val feedSources = listOf(
        FeedConfig(
            url = "https://www.noticiasaominuto.com/rss/ultima-hora",
            sourceName = "Ao Minuto",
            category = "Ao Minuto"
        ),
        FeedConfig(
            url = "https://www.noticiasaominuto.com/rss/desporto",
            sourceName = "Desporto",
            category = "Desporto"
        ),
        FeedConfig(
            url = "https://www.record.pt/rss",
            sourceName = "Record",
            category = "Desporto"
        ),
        FeedConfig(
            url = "https://maisfutebol.iol.pt/rss",
            sourceName = "MaisFutebol",
            category = "Desporto"
        ),
        FeedConfig(
            url = "https://www.rtp.pt/noticias/rss",
            sourceName = "RTP",
            category = "Nacional"
        )
    )

    data class FeedConfig(
        val url: String,
        val sourceName: String,
        val category: String
    )

    fun fetchAllFeeds(onResult: (List<Newspaper>) -> Unit) {
        val collectedItems = mutableListOf<Newspaper>()
        val pendingCount = AtomicInteger(feedSources.size)

        for (cfg in feedSources) {
            val req = Request.Builder()
                .url(cfg.url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    checkFinished()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            val parsed = parseXml(body, cfg.sourceName, cfg.category)
                            synchronized(collectedItems) {
                                collectedItems.addAll(parsed)
                            }
                        }
                    }
                    checkFinished()
                }

                private fun checkFinished() {
                    if (pendingCount.decrementAndGet() == 0) {
                        // Deduplicar e ordenar
                        val seenTitles = mutableSetOf<String>()
                        val unique = mutableListOf<Newspaper>()
                        synchronized(collectedItems) {
                            for (item in collectedItems) {
                                val norm = item.nome.trim().lowercase()
                                if (norm.length > 5 && seenTitles.add(norm)) {
                                    unique.add(item)
                                }
                            }
                        }
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
        var s = raw.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
        s = s.replace(Regex("<[^>]*>"), " ")
        s = s.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        return s.replace(Regex("\\s+"), " ").trim()
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
        // Tentar encontrar HH:mm
        val timeMatcher = Regex("\\b(\\d{1,2}:\\d{2})\\b")
        val timeMatch = timeMatcher.find(rawDate)
        return if (timeMatch != null) {
            "Hoje, ${timeMatch.groupValues[1]}"
        } else {
            "Hoje"
        }
    }
}
