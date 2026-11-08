package com.example.myapplication

import com.google.gson.annotations.SerializedName

data class SportsAgendaFeed(
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("jogos") val jogos: List<SportsMatch> = emptyList()
)

data class SportsMatch(
    @SerializedName("id") val id: String = "",
    @SerializedName("data") val data: String = "Hoje",
    @SerializedName("hora") val hora: String = "",
    @SerializedName("liga") val liga: String = "",
    @SerializedName("equipa_casa") val equipaCasa: String = "",
    @SerializedName("equipa_fora") val equipaFora: String = "",
    @SerializedName("canal") val canal: String = "",
    @SerializedName("destaque") val destaque: Boolean = false
)
