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
            PiaRegion("spain", "Espanha", "ES", "🇪🇸", "194.146.92.188", "spain.privacy.network"),
            PiaRegion("france", "França", "FR", "🇫🇷", "45.157.112.164", "france.privacy.network"),
            PiaRegion("uk", "Reino Unido", "GB", "🇬🇧", "85.203.46.72", "uk-london.privacy.network"),
            PiaRegion("nl_amsterdam", "Holanda", "NL", "🇳🇱", "212.56.48.134", "nl-amsterdam.privacy.network"),
            PiaRegion("de-frankfurt", "Alemanha", "DE", "🇩🇪", "217.19.30.55", "de-frankfurt.privacy.network"),
            PiaRegion("swiss", "Suíça", "CH", "🇨🇭", "195.177.93.15", "swiss.privacy.network"),
            PiaRegion("italy", "Itália", "IT", "🇮🇹", "158.173.77.90", "italy.privacy.network")
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
            else -> "🌐"
        }
    }
}
