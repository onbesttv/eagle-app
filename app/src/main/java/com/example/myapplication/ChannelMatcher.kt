package com.example.myapplication

import java.util.regex.Pattern

object ChannelMatcher {

    /**
     * Encontra o melhor canal na lista de streams para o canal alvo requisitado.
     * Regras estritas:
     * - Apenas pastas de Portugal (ex: "Portugal", "Portugal FHD", "Desporto", "PT | Desporto").
     * - Rejeita totalmente pastas estrangeiras (ES, BR, FR, UK, USA, etc.).
     * - Rejeita qualidade HD (720p) - prioriza 1080 / FHD / Full HD.
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

        // 2. Tentar primeiro encontrar canal em 1080/FHD para qualquer um dos sub-canais dentro das pastas de Portugal
        if (prefer1080) {
            for (subCanal in subCanais) {
                val match1080 = encontrarCandidato(streams, subCanal, catMap, apenas1080 = true, rejeitarHd = true)
                if (match1080 != null) return match1080
            }
        }

        // 3. Fallback: procurar canal em pastas válidas de Portugal, ainda rejeitando HD
        for (subCanal in subCanais) {
            val matchNormal = encontrarCandidato(streams, subCanal, catMap, apenas1080 = false, rejeitarHd = true)
            if (matchNormal != null) return matchNormal
        }

        // 4. Último recurso se absolutamente não houver versão não-HD disponível na lista
        for (subCanal in subCanais) {
            val matchFallback = encontrarCandidato(streams, subCanal, catMap, apenas1080 = false, rejeitarHd = false)
            if (matchFallback != null) return matchFallback
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
        apenas1080: Boolean,
        rejeitarHd: Boolean
    ): LiveStream? {
        val variantes = expandirVariantesCanal(subCanal)
        val candidatos = mutableListOf<Pair<LiveStream, Int>>()

        for (stream in streams) {
            val sName = stream.name
            if (sName.isBlank()) continue

            val catName = stream.categoryId?.let { catMap[it] }

            // 1. Rejeitar canais com prefixo estrangeiro
            if (isStreamEstrangeiro(sName)) continue

            // 2. Rejeitar pastas estrangeiras e pastas que não sejam de Portugal / Desporto
            if (!isPastaValidaPortugal(catName)) continue

            // 3. Se rejeitarHd estiver ativo, não aceitar streams nem pastas com qualidade puramente HD (720p)
            if (rejeitarHd && isStreamHdRejeitado(sName, catName)) continue

            // 4. Se apenas1080 estiver ativo, exigir qualidade 1080p / FHD
            val is1080 = is1080Stream(stream, catMap)
            if (apenas1080 && !is1080) continue

            // 5. Verificar correspondência com alguma das variantes do canal
            var matchedVariant = false
            for (variante in variantes) {
                if (streamCorresponde(stream, variante)) {
                    matchedVariant = true
                    break
                }
            }

            if (matchedVariant) {
                val score = pontuarCandidato(stream, catName, is1080)
                candidatos.add(Pair(stream, score))
            }
        }

        // Retorna o candidato com maior pontuação
        return candidatos.maxByOrNull { it.second }?.first
    }

    /**
     * Valida se a pasta/categoria pertence a Portugal, Portugal FHD ou Desporto português
     */
    fun isPastaValidaPortugal(categoryName: String?): Boolean {
        if (categoryName.isNullOrBlank()) return true // Sem pasta mapeada, validação fica no stream

        val cat = categoryName.trim().lowercase()

        // 1. Excluir pastas explicitamente estrangeiras
        if (isPastaEstrangeira(cat)) return false

        // 2. Excluir pastas puramente "HD" se não forem FHD (ex: "Portugal HD", "PT | Desporto HD")
        val hasFhd = cat.contains("fhd") || cat.contains("full hd") || cat.contains("fullhd") || cat.contains("1080")
        val hasPureHd = Regex("(?<!f|full\\s*)hd\\b").containsMatchIn(cat)
        if (hasPureHd && !hasFhd) {
            return false
        }

        // 3. Pastas permitidas: Portugal, Portugal FHD, Desporto, PT
        val isPortugal = cat.contains("portugal") || cat.contains("nacionais")
        val isDesporto = cat.contains("desporto")
        val isPtPrefix = cat.startsWith("pt") || cat.contains("pt |") || cat.contains("pt:") || cat.contains("pt -") || cat.contains("[pt]")

        return isPortugal || isDesporto || isPtPrefix
    }

    /**
     * Deteta se uma pasta é estrangeira (Espanha, Brasil, França, UK, USA, etc.)
     */
    fun isPastaEstrangeira(catLower: String): Boolean {
        val foreignPrefixes = listOf(
            "es|", "es:", "es -", "es ", "esp|", "esp:", "esp ", "españa", "espanha", "spain",
            "br|", "br:", "br -", "br ", "bra|", "bra:", "bra ", "brasil", "brazil",
            "fr|", "fr:", "fr -", "fr ", "fra|", "fra:", "france", "frança",
            "uk|", "uk:", "uk -", "uk ", "england", "united kingdom",
            "de|", "de:", "de -", "de ", "germany", "deutschland", "alemanha",
            "it|", "it:", "it -", "it ", "ita|", "ita:", "italia", "itália", "italy",
            "us|", "us:", "us -", "us ", "usa|", "usa:", "usa ", "united states",
            "nl|", "nl:", "netherlands", "holanda",
            "pl|", "poland", "polonia", "polónia",
            "tr|", "turkey", "turquia",
            "ar|", "arabic", "arabia",
            "ro|", "romania",
            "al|", "albania"
        )
        for (prefix in foreignPrefixes) {
            if (catLower.startsWith(prefix) || catLower.contains("|$prefix") || catLower.contains(" $prefix")) {
                return true
            }
        }
        return false
    }

    /**
     * Deteta se o canal tem prefixo de país estrangeiro no nome (ex: ES: SPORT TV 1)
     */
    fun isStreamEstrangeiro(streamName: String): Boolean {
        val sLower = streamName.trim().lowercase()
        val foreignPrefixes = listOf(
            "es:", "es|", "es -", "esp:", "[es]",
            "br:", "br|", "br -", "bra:", "[br]",
            "fr:", "fr|", "fr -", "fra:", "[fr]",
            "uk:", "uk|", "uk -", "[uk]",
            "de:", "de|", "de -", "ger:", "[de]",
            "it:", "it|", "it -", "ita:", "[it]",
            "us:", "us|", "us -", "usa:", "[us]"
        )
        return foreignPrefixes.any { sLower.startsWith(it) }
    }

    /**
     * Deteta se um stream é de qualidade puramente HD (720p) e deve ser rejeitado
     */
    fun isStreamHdRejeitado(streamName: String, categoryName: String?): Boolean {
        val nameLower = streamName.lowercase()
        val catLower = (categoryName ?: "").lowercase()

        val isFhd = nameLower.contains("fhd") || nameLower.contains("full hd") || nameLower.contains("fullhd") || nameLower.contains("1080") ||
                catLower.contains("fhd") || catLower.contains("full hd") || catLower.contains("fullhd") || catLower.contains("1080")

        if (isFhd) return false

        val nameHasHd = Regex("(?<!f|full\\s*)hd\\b").containsMatchIn(nameLower)
        val catHasHd = Regex("(?<!f|full\\s*)hd\\b").containsMatchIn(catLower)

        return nameHasHd || catHasHd
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
            if (tokens.size == 1 && tokens[0] == "sic") {
                val excluidos = listOf("noticias", "notícias", "mulher", "radical", "caras", "novelas", "esperanca", "esperança", "internacional", "k")
                if (excluidos.any { sName.contains(it) }) return false
            }
            if (tokens.size == 1 && tokens[0] == "tvi") {
                val excluidos = listOf("ficcao", "ficção", "reality", "internacional", "afrika", "áfrica")
                if (excluidos.any { sName.contains(it) }) return false
            }
            if (tokens.contains("rtp") && tokens.contains("1")) {
                val excluidos = listOf("memoria", "memória", "madeira", "acores", "açores", "africa", "áfrica")
                if (excluidos.any { sName.contains(it) }) return false
            }
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
    private fun pontuarCandidato(stream: LiveStream, categoryName: String?, is1080: Boolean): Int {
        var score = 0
        val nameLower = stream.name.lowercase()
        val catLower = (categoryName ?: "").lowercase()

        if (is1080) {
            score += 1000 // Prioridade máxima absoluta para 1080/FHD
        }

        // Bónus especial para pastas "Portugal FHD", "Portugal 1080p", "Desporto FHD"
        if (catLower.contains("portugal") && (catLower.contains("fhd") || catLower.contains("1080"))) {
            score += 300
        } else if (catLower.contains("desporto") && (catLower.contains("fhd") || catLower.contains("1080"))) {
            score += 250
        } else if (catLower.contains("portugal")) {
            score += 200
        } else if (catLower.contains("desporto")) {
            score += 180
        }

        if (nameLower.contains("1080")) score += 80
        if (nameLower.contains("fhd")) score += 60

        // Prioridade para canais explicitamente portugueses
        if (nameLower.startsWith("pt") || nameLower.contains("pt:") || nameLower.contains("pt|") || nameLower.contains("[pt]")) {
            score += 100
        }

        // Penalizar streams de backup / teste quando existe o canal primário
        if (nameLower.contains("backup") || nameLower.contains("back up") || nameLower.contains("alt") || nameLower.contains("teste")) {
            score -= 100
        }

        // Penalizar ligeiramente nomes com ruído longo
        score -= (nameLower.length / 5)

        return score
    }
}
