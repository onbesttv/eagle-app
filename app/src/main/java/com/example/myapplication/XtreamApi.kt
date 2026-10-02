package com.example.myapplication

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import android.util.Base64

// --- MODELOS DE DADOS ---
// Modelo para a lista de listagens EPG
data class EpgResponse(
    @SerializedName("epg_listings") val epgListings: List<EpgProgram>?
)

// Modelo para as Categorias de Live TV
data class LiveCategory(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String
)

// Modelo para os Canais (compatível com LiveStream e Canal)
data class LiveStream(
    @SerializedName("num") val num: Any?,
    @SerializedName("name") val name: String,
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

// Modelo para Categorias de Filmes (VOD)
data class VodCategory(
    @com.google.gson.annotations.SerializedName("category_id")
    val categoryId: String,
    @com.google.gson.annotations.SerializedName("category_name")
    val categoryName: String,
    @com.google.gson.annotations.SerializedName("parent_id")
    val parentId: Int = 0
)

// Modelo para cada Filme
data class VodStream(
    val num: Int? = null,
    val name: String,
    val stream_type: String? = null,
    @com.google.gson.annotations.SerializedName("stream_id")
    val streamId: Int,
    @com.google.gson.annotations.SerializedName("stream_icon")
    val streamIcon: String? = null,
    val rating: String? = null,
    val rating_5based: Double? = null,
    val added: String? = null,
    @com.google.gson.annotations.SerializedName("container_extension")
    val containerExtension: String? = null
)

// Categoria de Séries
data class SeriesCategory(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String
)

// Item da Série na grelha
data class SeriesItem(
    @SerializedName("num") val num: Any?,
    @SerializedName("name") val name: String,
    @SerializedName("series_id") val seriesId: Int,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("category_id") val categoryId: String?
)

// Episódio individual
data class EpisodeItem(
    @SerializedName("id") val id: String,
    @SerializedName("episode_num") val episodeNum: Any?,
    @SerializedName("title") val title: String,
    @SerializedName("container_extension") val containerExtension: String? = "mp4",
    @SerializedName("season") val season: Int,
    @SerializedName("plot") val plot: String?
)

data class VodInfoResponse(
    val info: VodInfoData?,
    @com.google.gson.annotations.SerializedName("movie_data")
    val movieData: VodStream?
)

data class VodInfoData(
    val name: String?,
    val plot: String?,
    val description: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @com.google.gson.annotations.SerializedName("release_date")
    val releaseDate: String?,
    val duration: String?,
    val rating: String?,
    @com.google.gson.annotations.SerializedName("movie_image")
    val movieImage: String?,
    @com.google.gson.annotations.SerializedName("backdrop_path")
    val backdropPath: List<String>?
)

// Modelo para cada programa
data class EpgProgram(
    @SerializedName("id") val id: String?,
    @SerializedName("epg_id") val epgId: String?,
    @SerializedName("title") val rawTitle: String?,
    @SerializedName("lang") val lang: String?,
    @SerializedName("start") val start: String?,
    @SerializedName("end") val end: String?,
    @SerializedName("description") val rawDescription: String?,
    @SerializedName("channel_id") val channelId: String?,
    @SerializedName("start_timestamp") val startTimestamp: String?,
    @SerializedName("stop_timestamp") val stopTimestamp: String?,
    @SerializedName("now_playing") val nowPlaying: Int?
) {
    // Decodifica Base64 se a API enviar títulos/descrições codificados
    val title: String
        get() = decodeSafe(rawTitle)

    val description: String
        get() = decodeSafe(rawDescription)

    private fun decodeSafe(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(text, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            text
        }
    }
}


// Mantemos o data class Canal por retrocompatibilidade com o teu código anterior
typealias Canal = LiveStream

// --- SERVIÇO RETROFIT ---

interface XtreamService {

    // 1. Obter todas as categorias de canais
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_categories"
    ): List<LiveCategory>

    // 2. Obter canais filtrados por categoria (ou todos se categoryId for omitido)
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<LiveStream>

    companion object {
        fun create(baseUrl: String): XtreamService {
            val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return Retrofit.Builder()
                .baseUrl(cleanUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(XtreamService::class.java)
        }
    }
}