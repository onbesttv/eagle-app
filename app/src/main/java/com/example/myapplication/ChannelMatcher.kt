package com.example.myapplication

object ChannelMatcher {

    /**
     * Encontra o melhor canal na lista de streams para o canal alvo requisitado.
     * Regras estritas:
     * 1. Apenas pastas de Portugal (ex: "Portugal", "Portugal FHD", "Desporto", "PT | Desporto").
     * 2. Rejeita totalmente pastas e prefixos estrangeiros (ES, BR, FR, UK, USA, etc.).
     * 3. Prioriza 1080 / FHD antes de qualidade normal; rejeita canais puramente HD (720p).
     * 4. Se não existir versão 1080 nem normal disponível, recorre como último recurso à lista disponível.
     */
    fun encontrarCanalAlvo(
        streams: List<LiveStream>,
        targetChannelName: String,
        categorias: List<LiveCategory>? = null,
        prefer1080: Boolean = true
    ): LiveStream? {
        if (streams.isEmpty() || targetChannelName.isBlank()) return null

        val catMap = try {
            categorias?.associate { (it.categoryId ?: "") to (it.categoryName ?: "") } ?: emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }

        // 1. Desmembrar múltiplos canais (ex: "RTP 1 / Sport TV 1", "SIC, DAZN 1")
        val subCanais = extrairSubCanais(targetChannelName)
        if (subCanais.isEmpty()) return null

        // 2. Tentar primeiro encontrar canal em 1080/FHD dentro de pastas de Portugal
        if (prefer1080) {
            for (subCanal in subCanais) {
                val match1080 = encontrarCandidato(streams, subCanal, catMap, apenas1080 = true, rejeitarHd = true)
                if (match1080 != null) return match1080
            }
        }

        // 3. Fallback: procurar canal em pastas válidas de Portugal sem 1080, mas ainda rejeitando HD
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
        if (cleanSub.isBlank()) return emptyList()
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
        if (variantes.isEmpty()) return null

        val candidatos = mutableListOf<Pair<LiveStream, Int>>()

        for (stream in streams) {
            val sName = stream.name ?: continue
            if (sName.isBlank()) continue

            val catName = stream.categoryId?.let { catMap[it] }

            // 1. Rejeitar canais com prefixo estrangeiro no próprio nome
            if (isStreamEstrangeiro(sName)) continue

            // 2. Rejeitar pastas estrangeiras e pastas não permitidas
            if (!isPastaValidaPortugal(catName, rejeitarHd)) continue

            // 3. Se rejeitarHd estiver ativo, não aceitar streams com qualidade puramente HD (720p)
            if (rejeitarHd && isStreamHdRejeitado(sName, catName)) continue

            // 4. Se apenas1080 estiver ativo, exigir qualidade 1080p / FHD
            val is1080 = is1080Stream(stream, catMap)
            if (apenas1080 && !is1080) continue

            // 5. Verificar correspondência com alguma das variantes do canal
            var matchedVariant = false
            for (variante in variantes) {
                if (streamCorresponde(sName, variante)) {
                    matchedVariant = true
                    break
                }
            }

            if (matchedVariant) {
                val score = pontuarCandidato(sName, catName, is1080)
                candidatos.add(Pair(stream, score))
            }
        }

        // Retorna o candidato com maior pontuação
        return candidatos.maxByOrNull { it.second }?.first
    }

    /**
     * Valida se a pasta/categoria pertence a Portugal, Portugal FHD ou Desporto
     */
    fun isPastaValidaPortugal(categoryName: String?, rejeitarHd: Boolean = true): Boolean {
        if (categoryName.isNullOrBlank()) return true // Sem categoria mapeada, decisão baseada no stream

        val cat = categoryName.trim().lowercase()

        // 1. Excluir pastas explicitamente estrangeiras
        if (isPastaEstrangeira(cat)) return false

        // 2. Se rejeitarHd estiver ativo, excluir pastas que sejam puramente "HD" (sem FHD/1080)
        if (rejeitarHd) {
            val hasFhd = cat.contains("fhd") || cat.contains("full hd") || cat.contains("fullhd") || cat.contains("1080")
            if (!hasFhd && contemPalavraHd(cat)) {
                return false
            }
        }

        // 3. Pastas permitidas de Portugal / Desporto
        val isPortugal = cat.contains("portugal") || cat.contains("nacionais")
        val isDesporto = cat.contains("desporto") || cat.contains("sports") || cat.contains("sport")
        val isPtPrefix = cat.startsWith("pt") || cat.contains("pt |") || cat.contains("pt:") || cat.contains("pt -") || cat.contains("[pt]")
        val isAllowedSpecial = cat.contains("premium") || cat.contains("generalistas") ||
                cat.contains("dazn") || cat.contains("eleven") || cat.contains("benfica") || cat.contains("btv")

        return isPortugal || isDesporto || isPtPrefix || isAllowedSpecial
    }

    /**
     * Deteta se uma pasta é estrangeira (Espanha, Brasil, França, UK, USA, etc.)
     * Verifica prefixos e palavras de fronteira completas, sem falsos positivos em preposições ("de", "es").
     */
    fun isPastaEstrangeira(catLower: String): Boolean {
        val foreignPrefixes = listOf(
            "es|", "es:", "es -", "[es]", "esp|", "esp:", "esp -",
            "br|", "br:", "br -", "[br]", "bra|", "bra:", "bra -",
            "fr|", "fr:", "fr -", "[fr]", "fra|", "fra:",
            "uk|", "uk:", "uk -", "[uk]",
            "us|", "us:", "us -", "[us]", "usa|", "usa:", "usa -",
            "de|", "de:", "de -", "[de]", "ger|", "ger:",
            "it|", "it:", "it -", "[it]", "ita|", "ita:",
            "nl|", "nl:", "[nl]",
            "pl|", "pl:", "[pl]",
            "tr|", "tr:", "[tr]",
            "ar|", "ar:", "[ar]",
            "ro|", "ro:", "[ro]",
            "al|", "al:", "[al]"
        )

        for (prefix in foreignPrefixes) {
            if (catLower.startsWith(prefix) || catLower.contains("|$prefix") || catLower.contains(" $prefix")) {
                return true
            }
        }

        // Palavras de países estrangeiros (isoladas)
        val foreignWords = listOf(
            "españa", "espanha", "spain",
            "brasil", "brazil",
            "france", "frança",
            "united kingdom", "england",
            "united states",
            "deutschland", "germany", "alemanha",
            "italia", "itália", "italy",
            "netherlands", "holanda",
            "poland", "polonia", "polónia",
            "turkey", "turquia",
            "arabic", "arabia",
            "romania", "albania"
        )

        for (w in foreignWords) {
            if (catLower.contains(w)) return true
        }

        return false
    }

    /**
     * Deteta se o canal tem prefixo de país estrangeiro no nome (ex: ES: SPORT TV 1)
     */
    fun isStreamEstrangeiro(streamName: String): Boolean {
        val sLower = streamName.trim().lowercase()
        val foreignPrefixes = listOf(
            "es:", "es|", "es -", "esp:", "esp|", "[es]",
            "br:", "br|", "br -", "bra:", "bra|", "[br]",
            "fr:", "fr|", "fr -", "fra:", "fra|", "[fr]",
            "uk:", "uk|", "uk -", "[uk]",
            "us:", "us|", "us -", "usa:", "usa|", "[us]",
            "de:", "de|", "de -", "ger:", "[de]",
            "it:", "it|", "it -", "ita:", "[it]",
            "nl:", "pl:", "tr:", "ar:"
        )
        return foreignPrefixes.any { sLower.startsWith(it) }
    }

    /**
     * Deteta se um stream ou categoria é puramente HD (720p) e deve ser rejeitado
     */
    fun isStreamHdRejeitado(streamName: String, categoryName: String?): Boolean {
        val nameLower = streamName.lowercase()
        val catLower = (categoryName ?: "").lowercase()

        // Se tem 1080p, FHD ou Full HD, NUNCA é rejeitado como HD
        val isFhd = nameLower.contains("fhd") || nameLower.contains("full hd") || nameLower.contains("fullhd") || nameLower.contains("1080") ||
                catLower.contains("fhd") || catLower.contains("full hd") || catLower.contains("fullhd") || catLower.contains("1080")

        if (isFhd) return false

        val nameHasHd = contemPalavraHd(nameLower)
        val catHasHd = contemPalavraHd(catLower)

        return nameHasHd || catHasHd
    }

    /**
     * Verifica com segurança (sem lookbehind) se o texto contém "hd", "720" ou "720p" como token isolado
     */
    fun contemPalavraHd(text: String): Boolean {
        if (text.isBlank()) return false
        val tokens = text.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        for (t in tokens) {
            if (t == "hd" || t == "720" || t == "720p") {
                return true
            }
        }
        return false
    }

    /**
     * Deteta se o stream é de qualidade 1080 (1080p, FHD, Full HD) pelo nome ou categoria
     */
    fun is1080Stream(stream: LiveStream, catMap: Map<String, String>): Boolean {
        val nameLower = (stream.name ?: "").lowercase()
        val catLower = (stream.categoryId?.let { catMap[it] } ?: "").lowercase()

        return nameLower.contains("1080") ||
                nameLower.contains("fhd") ||
                nameLower.contains("full hd") ||
                nameLower.contains("full-hd") ||
                nameLower.contains("fullhd") ||
                catLower.contains("1080") ||
                catLower.contains("fhd") ||
                catLower.contains("full hd") ||
                catLower.contains("full-hd") ||
                catLower.contains("fullhd")
    }

    /**
     * Verifica de forma rápida, robusta e sem recompilar Regex em loop se o stream corresponde à variante
     */
    fun streamCorresponde(streamName: String, variante: String): Boolean {
        val sLower = streamName.lowercase()
        val cleanVariant = variante.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim().lowercase()
        val variantTokens = cleanVariant.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (variantTokens.isEmpty()) return false

        // Extrai números da variante
        val variantDigits = variantTokens.filter { it.all { c -> c.isDigit() } }
        // Extrai palavras da variante
        val variantWords = variantTokens.filter { !it.all { c -> c.isDigit() } }

        // Extrai todos os números presentes no nome do stream
        val streamTokens = sLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val streamDigits = streamTokens.filter { it.all { c -> c.isDigit() } }

        // 1. Cada número específico da variante deve existir exatamente no stream (exclui conflito 1 com 11, etc.)
        for (d in variantDigits) {
            if (!streamDigits.contains(d)) {
                return false
            }
        }

        // 2. Cada palavra da variante deve existir no nome do stream
        for (w in variantWords) {
            if (!sLower.contains(w)) {
                return false
            }
        }

        // 3. Regras estritas de exclusão para evitar colisões
        if (variantWords.size == 1 && variantWords[0] == "sic") {
            val excluidos = listOf("noticias", "notícias", "mulher", "radical", "caras", "novelas", "esperanca", "esperança", "internacional", "k")
            if (excluidos.any { sLower.contains(it) }) return false
        }
        if (variantWords.size == 1 && variantWords[0] == "tvi") {
            val excluidos = listOf("ficcao", "ficção", "reality", "internacional", "afrika", "áfrica")
            if (excluidos.any { sLower.contains(it) }) return false
        }
        if (variantWords.contains("rtp") && variantDigits.contains("1")) {
            val excluidos = listOf("memoria", "memória", "madeira", "acores", "açores", "africa", "áfrica")
            if (excluidos.any { sLower.contains(it) }) return false
        }
        if (variantWords.contains("sport") && variantWords.contains("tv") && !cleanVariant.contains("+") && !cleanVariant.contains("mais")) {
            if (sLower.contains("sport tv +") || sLower.contains("sporttv +") || sLower.contains("sport tv mais") || sLower.contains("sporttv mais")) {
                return false
            }
        }

        return true
    }

    /**
     * Calcula pontuação de preferência para ordenar candidatos
     */
    private fun pontuarCandidato(streamName: String, categoryName: String?, is1080: Boolean): Int {
        var score = 0
        val nameLower = streamName.lowercase()
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
