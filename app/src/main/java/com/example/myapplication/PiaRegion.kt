package com.example.myapplication

data class PiaRegion(
    val id: String,
    val name: String,
    val countryCode: String,
    val flagEmoji: String,
    var serverIp: String,
    var hostname: String,
    val isStreamingOptimized: Boolean = false
) {
    companion object {
        fun defaultRegions(): List<PiaRegion> = listOf(
            PiaRegion("pt", "Portugal", "PT", "🇵🇹", "158.173.244.214", "portugal.pvt.site"),
            PiaRegion("spain", "Espanha", "ES", "🇪🇸", "194.146.92.74", "es-madrid.pvt.site"),
            PiaRegion("france", "França", "FR", "🇫🇷", "45.157.112.23", "france.pvt.site"),
            PiaRegion("uk", "Reino Unido", "GB", "🇬🇧", "85.203.46.102", "uk-london.pvt.site"),
            PiaRegion("nl_amsterdam", "Holanda", "NL", "🇳🇱", "158.173.21.183", "nl-amsterdam.privacy.network"),
            PiaRegion("de-frankfurt", "Alemanha", "DE", "🇩🇪", "217.19.30.158", "de-frankfurt.pvt.site"),
            PiaRegion("swiss", "Suíça", "CH", "🇨🇭", "195.177.93.15", "switzerland.pvt.site"),
            PiaRegion("italy", "Itália", "IT", "🇮🇹", "158.173.77.90", "it-milano.pvt.site")
        )

        fun allAvailableRegions(): List<PiaRegion> = defaultRegions() + listOf(
            PiaRegion("belgium", "Bélgica", "BE", "🇧🇪", "158.173.67.62", "belgium.pvt.site"),
            PiaRegion("lu", "Luxemburgo", "LU", "🇱🇺", "45.132.195.88", "luxembourg.pvt.site"),
            PiaRegion("us_new_york_city", "Estados Unidos", "US", "🇺🇸", "191.96.150.155", "us-newyorkcity.privacy.network"),
            PiaRegion("br", "Brasil", "BR", "🇧🇷", "107.149.152.156", "brazil.pvt.site"),
            PiaRegion("ca_toronto", "Canadá", "CA", "🇨🇦", "45.89.249.48", "ca-toronto.pvt.site"),
            PiaRegion("poland", "Polónia", "PL", "🇵🇱", "85.237.212.227", "poland.pvt.site")
        )

        fun flagForCountry(countryCode: String): String = when (countryCode.uppercase()) {
            "PT" -> "🇵🇹"
            "ES" -> "🇪🇸"
            "FR" -> "🇫🇷"
            "GB", "UK" -> "🇬🇧"
            "NL" -> "🇳🇱"
            "DE" -> "🇩🇪"
            "CH" -> "🇨🇭"
            "IT" -> "🇮🇹"
            "BE" -> "🇧🇪"
            "LU" -> "🇱🇺"
            "US" -> "🇺🇸"
            "BR" -> "🇧🇷"
            "CA" -> "🇨🇦"
            "PL" -> "🇵🇱"
            else -> "🌐"
        }
    }
}
