package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import android.widget.ImageButton

class MovieDetailActivity : AppCompatActivity() {

    private val BASE_URL = "https://allrevplay.online:443"
    private val client = OkHttpClient()
    private val gson = Gson()

    private var streamId = 0
    private var streamTitle = ""
    private var streamIcon = ""
    private var containerExt = "mp4"

    private var user = ""
    private var pass = ""

    private lateinit var btnToggleFav: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_detail)

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "").orEmpty()
        pass = prefs.getString("SAVED_PASS", "").orEmpty()

        streamId = intent.getIntExtra("STREAM_ID", 0)
        streamTitle = intent.getStringExtra("STREAM_TITLE") ?: ""
        streamIcon = intent.getStringExtra("STREAM_ICON") ?: ""
        containerExt = intent.getStringExtra("CONTAINER_EXT") ?: "mp4"

        val ivPoster = findViewById<ImageView>(R.id.ivDetailPoster)
        val tvTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val btnPlay = findViewById<Button>(R.id.btnPlayMovie)
        btnToggleFav = findViewById(R.id.btnToggleFav)

        tvTitle.text = streamTitle
        if (streamIcon.isNotEmpty()) {
            Glide.with(this).load(streamIcon).into(ivPoster)
        }

        atualizarTextoBotaoFav()

        btnToggleFav.setOnClickListener {
            alternarFavorito()
        }

        findViewById<ImageButton>(R.id.btnBackMovieDetail)?.setOnClickListener {
            finish()
        }

        btnPlay.setOnClickListener {
            val videoUrl = "$BASE_URL/movie/$user/$pass/$streamId.$containerExt"
            val intent = Intent(this, VodPlayerActivity::class.java).apply {
                putExtra("STREAM_URL", videoUrl)
                putExtra("STREAM_TITLE", streamTitle)
                putExtra("STREAM_ID", streamId)
                putExtra("IS_SERIES", false)
            }
            startActivity(intent)
        }

        carregarInfoApi()
    }

    private fun carregarInfoApi() {
        val url = "$BASE_URL/player_api.php?username=$user&password=$pass&action=get_vod_info&vod_id=$streamId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val infoResp = gson.fromJson(json, VodInfoResponse::class.java)
                    val info = infoResp.info

                    runOnUiThread {
                        val plot = info?.plot?.ifEmpty { null } ?: info?.description ?: "Sem sinopse disponível."
                        findViewById<TextView>(R.id.tvDetailPlot).text = plot

                        val rating = info?.rating ?: "0.0"
                        findViewById<TextView>(R.id.tvDetailRating).text = "★ $rating"

                        val ano = info?.releaseDate?.take(4) ?: ""
                        val genero = info?.genre ?: ""
                        val duracao = info?.duration ?: ""
                        findViewById<TextView>(R.id.tvDetailMeta).text = listOf(ano, genero, duracao).filter { it.isNotEmpty() }.joinToString(" • ")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun alternarFavorito() {
        val favPrefs = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
        val setAtual = favPrefs.getStringSet("fav_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val idStr = streamId.toString()

        if (setAtual.contains(idStr)) {
            setAtual.remove(idStr)
            Toast.makeText(this, "Removido dos favoritos", Toast.LENGTH_SHORT).show()
        } else {
            setAtual.add(idStr)
            Toast.makeText(this, "Adicionado aos favoritos!", Toast.LENGTH_SHORT).show()
        }

        favPrefs.edit().putStringSet("fav_ids", setAtual).apply()
        atualizarTextoBotaoFav()
    }

    private fun atualizarTextoBotaoFav() {
        val favPrefs = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
        val favIds = favPrefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
        val isFav = favIds.contains(streamId.toString())

        btnToggleFav.text = if (isFav) "★ Remover Favorito" else "☆ Adicionar Favorito"
    }
}