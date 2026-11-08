package com.example.myapplication

import com.google.gson.annotations.SerializedName

data class NewspaperFeed(
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("jornais") val jornais: List<Newspaper> = emptyList()
)

data class Newspaper(
    @SerializedName("id") val id: String = "",
    @SerializedName("nome") val nome: String = "",
    @SerializedName("categoria") val categoria: String = "Desporto",
    @SerializedName("data") val data: String = "Hoje",
    @SerializedName("pais") val pais: String = "PT",
    @SerializedName("paginas") val paginas: Int = 0,
    @SerializedName("capa_url") val capaUrl: String = "",
    @SerializedName("pdf_url") val pdfUrl: String = ""
)
