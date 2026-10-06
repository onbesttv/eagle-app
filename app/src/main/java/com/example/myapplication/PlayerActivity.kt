package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class PlayerActivity : AppCompatActivity() {

    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val client = OkHttpClient()
    private val gson = Gson()

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    // Elementos da Gaveta de Canais e Área de Dismiss
    private lateinit var btnToggleChannelOverlay: ImageView
    private lateinit var layoutChannelOverlay: View
    private lateinit var viewOverlayDismissArea: View
    private lateinit var rvOverlayChannels: RecyclerView

    private var user = ""
    private var pass = ""
    private var categoryId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "").orEmpty()
        pass = prefs.getString("SAVED_PASS", "").orEmpty()

        val initialStreamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        categoryId = intent.getStringExtra("CATEGORY_ID") ?: ""

        playerView = findViewById(R.id.playerView)
        btnToggleChannelOverlay = findViewById(R.id.btnToggleChannelOverlay)
        layoutChannelOverlay = findViewById(R.id.layoutChannelOverlay)
        viewOverlayDismissArea = findViewById(R.id.viewOverlayDismissArea)
        rvOverlayChannels = findViewById(R.id.rvOverlayChannels)

        rvOverlayChannels.layoutManager = LinearLayoutManager(this)

        configurarPlayer()

        if (initialStreamUrl.isNotEmpty()) {
            tocarStream(initialStreamUrl)
        }

        // Alternar abertura da gaveta
        btnToggleChannelOverlay.setOnClickListener {
            if (layoutChannelOverlay.visibility == View.VISIBLE) {
                fecharGrelha()
            } else {
                abrirGrelha()
            }
        }

        // Tocar em qualquer ponto fora da grelha fecha a grelha
        viewOverlayDismissArea.setOnClickListener {
            fecharGrelha()
        }

        if (categoryId.isNotEmpty()) {
            carregarCanaisDaCategoria(categoryId)
        }
    }

    private fun abrirGrelha() {
        layoutChannelOverlay.visibility = View.VISIBLE
        viewOverlayDismissArea.visibility = View.VISIBLE
    }

    private fun fecharGrelha() {
        layoutChannelOverlay.visibility = View.GONE
        viewOverlayDismissArea.visibility = View.GONE
    }

    private fun configurarPlayer() {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "IPTVSmartersPro/3.1.5")
                    .build()
                chain.proceed(req)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player?.volume = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getInt("player_volume", 100).coerceIn(0, 100) / 100f

        playerView.player = player
    }

    private fun tocarStream(url: String) {
        player?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    private fun carregarCanaisDaCategoria(catId: String) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams&category_id=$catId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val canais: List<LiveStream> = gson.fromJson(json, type)

                    runOnUiThread {
                        rvOverlayChannels.adapter = OverlayChannelAdapter(canais) { canal ->
                            val extension = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                    .getString("stream_type", "MPEGTS") == "HLS") "m3u8" else "ts"
                            val novoUrl = "$baseUrl/live/$user/$pass/${canal.streamId}.$extension"
                            tocarStream(novoUrl)
                            fecharGrelha()
                        }
                    }
                } catch (e: Exception) {
                    // Erro de parse
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player = null
        player?.release()
        player = null
    }

    inner class OverlayChannelAdapter(
        private val list: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<OverlayChannelAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvChannelName)
            val iv: ImageView = v.findViewById(R.id.ivChannelLogo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val canal = list[position]
            holder.tv.text = canal.name

            if (!canal.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.streamIcon).into(holder.iv)
            } else {
                holder.iv.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                onClick(canal)
            }
        }
    }
}
