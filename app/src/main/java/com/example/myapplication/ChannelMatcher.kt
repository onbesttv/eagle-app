package com.example.myapplication

import java.util.regex.Pattern

object ChannelMatcher {

    /**
     * Encontra o melhor canal na lista de streams para o canal alvo requisitado.
     * Prioriza a qualidade 1080 (1080p, FHD, Full HD) antes de recorrer à qualidade normal (HD, SD, padrão).
     */
    fun encontrarCanalAlvo(
        streams: List<LiveStream>,
        targetChannelName: String,
        categorias: List<LiveCategory>? = null,
        prefer1080: Boolean = true
    ): LiveStream? {
        if (streams.isEmpty() || targetChannelName.isBlank()) return null

        val catMap = categorias?.associate { it.categoryId to it.categoryName } ?: emptyMap()

        // 1. Desmembrar múltiplos canais (ex: "RTP 1 / Sport TV 1", "SIC, DAZN 1")
        val subCanais = extrairSubCanais(targetChannelName)
        if (subCanais.isEmpty()) return null

        // 2. Se preferência por 1080 estiver ativa, tentar primeiro encontrar canal em 1080 para qualquer uma das opções
        if (prefer1080) {
            for (subCanal in subCanais) {
                val match1080 = encontrarCandidato(streams, subCanal, catMap, apenas1080 = true)
                if (match1080 != null) return match1080
            }
        }

        // 3. Fallback: procurar canal normal (sem exigir 1080, escolhendo a melhor versão disponível)
        for (subCanal in subCanais) {
            val matchNormal = encontrarCandidato(streams, subCanal, catMap, apenas1080 = false)
            if (matchNormal != null) return matchNormal
        }

        return null
    }

    /**
     * Extrai sub-canais caso o texto contenha delimitadores como '/', ',', '|'
     */
    fun extrairSubCanais(nome: String): List<String> {
        val trimmed = nome.trim()
        if (trimmed.isBlank() || trimmed.contains("anunciar", ignoreCase = true)) return emptyList()

        return if (trimmed.contains("/") || trimmed.contains(",") || trimmed.contains("|")) {
            trimmed.split('/', ',', '|').map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf(trimmed)
        }
    }

    /**
     * Expande variantes de nome para um canal específico
     */
    fun expandirVariantesCanal(subCanal: String): List<String> {
        val results = mutableListOf<String>()
        val cleanSub = subCanal.trim()
        results.add(cleanSub)

        val lower = cleanSub.lowercase()

        // Benfica TV / BTV
        if (lower.contains("btv") || lower.contains("benfica")) {
            results.add("Benfica TV 1")
            results.add("Benfica TV")
            results.add("BTV 1")
            results.add("BTV")
            results.add("Benfica.Tv")
        }

        // DAZN / Eleven Sports
        if (lower.contains("dazn") || lower.contains("eleven")) {
            val num = Regex("\\d+").find(lower)?.value ?: "1"
            results.add("DAZN $num")
            results.add("DAZN$num")
            results.add("Eleven Sports $num")
            results.add("Eleven $num")
            results.add("ElevenSports $num")
        }

        // Sport TV
        if (lower.contains("sport") && lower.contains("tv")) {
            val num = Regex("\\d+").find(lower)?.value
            if (num != null) {
                results.add("Sport TV $num")
                results.add("SportTV $num")
                results.add("Sport.TV $num")
                results.add("SportTV$num")
                results.add("SPT $num")
                results.add("SPT$num")
            } else if (lower.contains("+") || lower.contains("mais")) {
                results.add("Sport TV +")
                results.add("SportTV +")
                results.add("Sport TV Mais")
                results.add("SportTV Mais")
                results.add("SPT +")
            } else {
                results.add("Sport TV 1")
                results.add("SportTV 1")
                results.add("Sport.TV 1")
                results.add("SPT 1")
                results.add("Sport TV")
                results.add("SportTV")
            }
        }

        // Canal 11
        if (lower.contains("canal 11") || lower.contains("canal11") || lower.contains("c11")) {
            results.add("Canal 11")
            results.add("Canal11")
            results.add("C11")
        }

        // Porto Canal
        if (lower.contains("porto")) {
            results.add("Porto Canal")
            results.add("PortoCanal")
            results.add("FCP Canal")
        }

        // Sporting TV
        if (lower.contains("sporting")) {
            results.add("Sporting TV")
            results.add("SportingTV")
            results.add("SCP TV")
        }

        // RTP
        if (lower.contains("rtp")) {
            val num = Regex("\\d+").find(lower)?.value ?: "1"
            results.add("RTP $num")
            results.add("RTP$num")
        }

        // SIC
        if (lower == "sic" || lower.startsWith("sic ")) {
            results.add("SIC")
        }

        // TVI
        if (lower == "tvi" || lower.startsWith("tvi ")) {
            results.add("TVI")
        }

        // Eurosport
        if (lower.contains("eurosport") || (lower.contains("euro") && lower.contains("sport"))) {
            val num = Regex("\\d+").find(lower)?.value ?: "1"
            results.add("Eurosport $num")
            results.add("Euro Sport $num")
            results.add("Eurosport$num")
        }

        return results.distinct()
    }

    /**
     * Procura o melhor stream candidato para um canal alvo
     */
    private fun encontrarCandidato(
        streams: List<LiveStream>,
        subCanal: String,
        catMap: Map<String, String>,
        apenas1080: Boolean
    ): LiveStream? {
        val variantes = expandirVariantesCanal(subCanal)
        val candidatos = mutableListOf<Pair<LiveStream, Int>>()

        for (stream in streams) {
            val sName = stream.name
            if (sName.isBlank()) continue

            val is1080 = is1080Stream(stream, catMap)
            if (apenas1080 && !is1080) continue

            // Verificar se o stream corresponde a alguma das variantes
            var matchedVariant = false
            for (variante in variantes) {
                if (streamCorresponde(stream, variante)) {
                    matchedVariant = true
                    break
                }
            }

            if (matchedVariant) {
                val score = pontuarCandidato(stream, catMap, is1080)
                candidatos.add(Pair(stream, score))
            }
        }

        // Retorna o candidato com maior pontuação
        return candidatos.maxByOrNull { it.second }?.first
    }

    /**
     * Verifica se o stream corresponde à variante de canal pesquisada
     */
    private fun streamCorresponde(stream: LiveStream, variante: String): Boolean {
        val sName = stream.name.lowercase()
        val cleanVariant = variante.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim().lowercase()
        val tokens = cleanVariant.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        // 1. Validação por tokens com isolamento estrito de dígitos
        val regexTokens = tokens.map { t ->
            if (t.all { it.isDigit() }) "(?<!\\d)$t(?!\\d)" else "\\b${Pattern.quote(t)}\\b"
        }
        val regex = Regex("(?i)" + regexTokens.joinToString(".*"))
        val matchedTokens = regex.containsMatchIn(stream.name)

        if (matchedTokens) {
            // Regras de exclusão para evitar colisões
            // Se procura "SIC" puro, não casar "SIC Notícias", "SIC Mulher", etc.
            if (tokens.size == 1 && tokens[0] == "sic") {
                val excluidos = listOf("noticias", "notícias", "mulher", "radical", "caras", "novelas", "esperanca", "esperança", "internacional", "k")
                if (excluidos.any { sName.contains(it) }) return false
            }
            // Se procura "TVI" puro, não casar "TVI Ficção", "TVI Reality", etc.
            if (tokens.size == 1 && tokens[0] == "tvi") {
                val excluidos = listOf("ficcao", "ficção", "reality", "internacional", "afrika", "áfrica")
                if (excluidos.any { sName.contains(it) }) return false
            }
            // Se procura "RTP 1", não casar "RTP Memória", "RTP Açores", etc.
            if (tokens.contains("rtp") && tokens.contains("1")) {
                val excluidos = listOf("memoria", "memória", "madeira", "acores", "açores", "africa", "áfrica")
                if (excluidos.any { sName.contains(it) }) return false
            }
            // Se procura Sport TV numerada (ex: Sport TV 1), não casar Sport TV +
            if (tokens.contains("sport") && tokens.contains("tv") && !tokens.contains("+") && !tokens.contains("mais")) {
                if (sName.contains("sport tv +") || sName.contains("sporttv +") || sName.contains("sport tv mais") || sName.contains("sporttv mais")) {
                    return false
                }
            }
            return true
        }

        // 2. Correspondência sem espaços (ex: "SportTV1" em "Sport TV 1")
        val noSpaces = cleanVariant.replace(" ", "")
        val streamClean = sName.replace(Regex("[^a-zA-Z0-9]"), "")
        if (streamClean.contains(noSpaces)) {
            val digitsInVariant = Regex("\\d+").findAll(cleanVariant).map { it.value }.toList()
            for (d in digitsInVariant) {
                val dRegex = Regex("(?<!\\d)$d(?!\\d)")
                if (!dRegex.containsMatchIn(stream.name)) return false
            }
            return true
        }

        return false
    }

    /**
     * Deteta se o stream é de qualidade 1080 (1080p, FHD, Full HD) pelo nome ou categoria
     */
    fun is1080Stream(stream: LiveStream, catMap: Map<String, String>): Boolean {
        val nameLower = stream.name.lowercase()
        val catLower = stream.categoryId?.let { catMap[it] }?.lowercase().orEmpty()

        val name1080 = nameLower.contains("1080") ||
                nameLower.contains("fhd") ||
                nameLower.contains("full hd") ||
                nameLower.contains("full-hd") ||
                nameLower.contains("fullhd")

        val cat1080 = catLower.contains("1080") ||
                catLower.contains("fhd") ||
                catLower.contains("full hd") ||
                catLower.contains("full-hd") ||
                catLower.contains("fullhd")

        return name1080 || cat1080
    }

    /**
     * Calcula pontuação de preferência para ordenar candidatos
     */
    private fun pontuarCandidato(stream: LiveStream, catMap: Map<String, String>, is1080: Boolean): Int {
        var score = 0
        val nameLower = stream.name.lowercase()
        val catLower = stream.categoryId?.let { catMap[it] }?.lowercase().orEmpty()

        if (is1080) {
            score += 1000 // Máxima prioridade para 1080
        }

        if (nameLower.contains("1080")) score += 60
        if (nameLower.contains("fhd")) score += 40

        // Prioridade para canais de Portugal / nacionais
        val isPt = catLower.contains("portugal") || catLower.contains("pt") || catLower.contains("desporto") || catLower.contains("nacionais") ||
                nameLower.startsWith("pt") || nameLower.contains("pt:") || nameLower.contains("pt|") || nameLower.contains("[pt]")
        val isForeign = nameLower.startsWith("es:") || nameLower.startsWith("fr:") || nameLower.startsWith("uk:") ||
                nameLower.startsWith("de:") || nameLower.startsWith("it:") || nameLower.startsWith("us:")
        if (isPt && !isForeign) {
            score += 200
        } else if (!isForeign) {
            score += 100
        }

        // Penalizar streams de backup / teste quando existe o canal primário
        if (nameLower.contains("backup") || nameLower.contains("back up") || nameLower.contains("alt") || nameLower.contains("teste")) {
            score -= 80
        }

        // Preferir nomes mais curtos / limpos (sem ruído extra)
        score -= (nameLower.length / 5)

        return score
    }
}
