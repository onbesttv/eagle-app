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
            PiaRegion("pt", "Portugal", "PT", "🇵🇹", "158.173.244.214", "portugal.privacy.network"),
            PiaRegion("spain", "Espanha (Madrid)", "ES", "🇪🇸", "194.146.92.188", "spain.privacy.network"),
            PiaRegion("es-valencia", "Espanha (Valência)", "ES", "🇪🇸", "196.245.54.145", "es-valencia.privacy.network"),
            PiaRegion("france", "França", "FR", "🇫🇷", "45.157.112.164", "france.privacy.network"),
            PiaRegion("uk", "Reino Unido (Londres)", "GB", "🇬🇧", "85.203.46.72", "uk-london.privacy.network"),
            PiaRegion("uk_manchester", "Reino Unido (Manchester)", "GB", "🇬🇧", "135.136.23.230", "uk-manchester.privacy.network"),
            PiaRegion("nl_netherlands-so", "Países Baixos (Streaming)", "NL", "🇳🇱", "158.173.21.22", "nl-netherlands-so.privacy.network", isStreamingOptimized = true),
            PiaRegion("nl_amsterdam", "Países Baixos (Amesterdão)", "NL", "🇳🇱", "212.56.48.134", "nl-amsterdam.privacy.network"),
            PiaRegion("de-frankfurt", "Alemanha (Frankfurt)", "DE", "🇩🇪", "217.19.30.55", "de-frankfurt.privacy.network"),
            PiaRegion("de_berlin", "Alemanha (Berlim)", "DE", "🇩🇪", "194.5.49.102", "de-berlin.privacy.network"),
            PiaRegion("swiss", "Suíça", "CH", "🇨🇭", "195.177.93.15", "swiss.privacy.network"),
            PiaRegion("italy", "Itália (Milão)", "IT", "🇮🇹", "158.173.77.90", "italy.privacy.network"),
            PiaRegion("us-newjersey", "Estados Unidos (Leste)", "US", "🇺🇸", "151.241.119.245", "us-newjersey.privacy.network"),
            PiaRegion("us_california", "Estados Unidos (Oeste)", "US", "🇺🇸", "191.96.106.215", "us-california.privacy.network"),
            PiaRegion("br", "Brasil", "BR", "🇧🇷", "107.149.152.235", "brazil.privacy.network")
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
            "US" -> "🇺🇸"
            "BR" -> "🇧🇷"
            "CA" -> "🇨🇦"
            "IE" -> "🇮🇪"
            "BE" -> "🇧🇪"
            "SE" -> "🇸🇪"
            "NO" -> "🇳🇴"
            "DK" -> "🇩🇰"
            "FI" -> "🇫🇮"
            "AT" -> "🇦🇹"
            "LU" -> "🇱🇺"
            else -> "🌐"
        }
    }
}
