package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import android.widget.ImageButton

class SeriesDetailActivity : AppCompatActivity() {

    private val baseUrl = "https://allrevplay.online:443"
    private val client = OkHttpClient()

    private lateinit var rvSeasons: RecyclerView
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var btnToggleFav: Button

    private var seasonsMap = mutableMapOf<String, List<EpisodeItem>>()
    private var seriesId: Int = 0
    private var seriesName: String = ""
    private var user = ""
    private var pass = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_details)

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "Wcjhrr3mzj") ?: "Wcjhrr3mzj"
        pass = prefs.getString("SAVED_PASS", "qww2rsEHnY") ?: "qww2rsEHnY"

        seriesId = intent.getIntExtra("SERIES_ID", 0)
        seriesName = intent.getStringExtra("SERIES_NAME") ?: ""
        val cover = intent.getStringExtra("SERIES_COVER")
        val plot = intent.getStringExtra("SERIES_PLOT") ?: ""

        val ivCover = findViewById<ImageView>(R.id.ivDetailCover)
        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvPlot = findViewById<TextView>(R.id.tvDetailPlot)
        btnToggleFav = findViewById(R.id.btnToggleSeriesFav)

        tvTitle.text = seriesName
        tvPlot.text = if (plot.isNotEmpty()) plot else "Sem descrição disponível."
        if (!cover.isNullOrEmpty()) {
            Glide.with(this).load(cover).into(ivCover)
        }

        atualizarTextoBotaoFav()

        btnToggleFav.setOnClickListener {
            alternarFavoritoSerie()
        }

        rvSeasons = findViewById(R.id.rvSeasons)
        rvEpisodes = findViewById(R.id.rvEpisodes)

        rvSeasons.layoutManager = LinearLayoutManager(this)
        rvEpisodes.layoutManager = LinearLayoutManager(this)

        carregarDetalhesSerie(seriesId)

        findViewById<ImageButton>(R.id.btnBackSeriesDetail)?.setOnClickListener {
            finish()
        }

    }

    private fun alternarFavoritoSerie() {
        val favPrefs = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
        val setAtual = favPrefs.getStringSet("fav_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val idStr = seriesId.toString()

        if (setAtual.contains(idStr)) {
            setAtual.remove(idStr)
            Toast.makeText(this, "$seriesName removida dos favoritos", Toast.LENGTH_SHORT).show()
        } else {
            setAtual.add(idStr)
            Toast.makeText(this, "$seriesName adicionada aos favoritos!", Toast.LENGTH_SHORT).show()
        }

        favPrefs.edit().putStringSet("fav_ids", setAtual).apply()
        atualizarTextoBotaoFav()
    }

    private fun atualizarTextoBotaoFav() {
        val favPrefs = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
        val favIds = favPrefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
        val isFav = favIds.contains(seriesId.toString())

        btnToggleFav.text = if (isFav) "★ Remover Favorito" else "☆ Adicionar Favorito"
    }

    private fun carregarDetalhesSerie(seriesId: Int) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$seriesId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SeriesDetailActivity, "Erro ao carregar episódios", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val corpo = response.body?.string() ?: return
                try {
                    val json = JSONObject(corpo)
                    val episodesObj = json.optJSONObject("episodes") ?: JSONObject()

                    val mapTemporadas = mutableMapOf<String, List<EpisodeItem>>()
                    val keys = episodesObj.keys()

                    while (keys.hasNext()) {
                        val seasonKey = keys.next()
                        val array = episodesObj.getJSONArray(seasonKey)
                        val listaEp = mutableListOf<EpisodeItem>()

                        for (i in 0 until array.length()) {
                            val ep = array.getJSONObject(i)
                            listaEp.add(
                                EpisodeItem(
                                    id = ep.optString("id"),
                                    episodeNum = ep.opt("episode_num"),
                                    title = ep.optString("title", "Episódio ${i + 1}"),
                                    containerExtension = ep.optString("container_extension", "mp4"),
                                    season = ep.optInt("season", seasonKey.toIntOrNull() ?: 1),
                                    plot = ep.optString("plot")
                                )
                            )
                        }
                        mapTemporadas["Temporada $seasonKey"] = listaEp
                    }

                    seasonsMap = mapTemporadas
                    val listaNomesSeasons = mapTemporadas.keys.toList()

                    runOnUiThread {
                        rvSeasons.adapter = SeasonAdapter(listaNomesSeasons) { seasonKey ->
                            val eps = seasonsMap[seasonKey] ?: emptyList()
                            rvEpisodes.adapter = EpisodeAdapter(eps) { ep ->
                                tocarEpisodio(ep, eps)
                            }
                        }

                        if (listaNomesSeasons.isNotEmpty()) {
                            val firstSeason = listaNomesSeasons[0]
                            val eps = seasonsMap[firstSeason] ?: emptyList()
                            rvEpisodes.adapter = EpisodeAdapter(eps) { ep ->
                                tocarEpisodio(ep, eps)
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@SeriesDetailActivity, "Erro ao processar episódios", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun tocarEpisodio(ep: EpisodeItem, listaEpsTemporada: List<EpisodeItem>) {
        val ext = ep.containerExtension ?: "mp4"
        val videoUrl = "$baseUrl/series/$user/$pass/${ep.id}.$ext"

        val listaUrls = ArrayList(listaEpsTemporada.map { item ->
            val eExt = item.containerExtension ?: "mp4"
            "$baseUrl/series/$user/$pass/${item.id}.$eExt"
        })
        val indexAtual = listaEpsTemporada.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)

        val intent = Intent(this, VodPlayerActivity::class.java).apply {
            putExtra("STREAM_URL", videoUrl)
            putExtra("STREAM_TITLE", ep.title)
            putExtra("STREAM_ID", ep.id.toIntOrNull() ?: 0)
            putExtra("IS_SERIES", true)
            putStringArrayListExtra("EPISODE_URLS", listaUrls)
            putExtra("CURRENT_EPISODE_INDEX", indexAtual)
        }
        startActivity(intent)
    }

    inner class SeasonAdapter(
        private val list: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SeasonAdapter.ViewHolder>() {

        private var selectedPosition = 0

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvSeasonTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_season, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tv.text = item
            holder.tv.setBackgroundColor(if (position == selectedPosition) 0xFF1E2836.toInt() else 0x00000000)

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onClick(item)
                }
            }
        }
    }

    inner class EpisodeAdapter(
        private val list: List<EpisodeItem>,
        private val onClick: (EpisodeItem) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvNum: TextView = v.findViewById(R.id.tvEpisodeNumber)
            val tvTitle: TextView = v.findViewById(R.id.tvEpisodeTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ep = list[position]
            holder.tvNum.text = "EP ${ep.episodeNum ?: (position + 1)}"
            holder.tvTitle.text = ep.title
            holder.itemView.setOnClickListener { onClick(ep) }
        }
    }
}