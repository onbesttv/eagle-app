package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LiveTvActivity : AppCompatActivity() {

    private val baseUrl = "https://allrevplay.online:443"
    private val client = OkHttpClient()
    private val gson = Gson()

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var etSearch: EditText

    // Contentores de layout para alternar Fullscreen
    private lateinit var layoutLeftPanels: View
    private lateinit var layoutBottomEpg: View
    private lateinit var viewPlayerClickTarget: View
    private lateinit var btnToggleChannelOverlay: ImageView

    // Gaveta flutuante em modo fullscreen
    private lateinit var layoutChannelOverlay: View
    private lateinit var viewOverlayDismissArea: View
    private lateinit var rvOverlayChannels: RecyclerView

    private var isFullScreen = false

    // Elementos EPG
    private lateinit var ivEpgChannelLogo: ImageView
    private lateinit var tvEpgChannelName: TextView
    private lateinit var tvEpgBadge: TextView
    private lateinit var tvEpgCurrentTitle: TextView
    private lateinit var pbEpgProgram: ProgressBar
    private lateinit var tvEpgCurrentTime: TextView
    private lateinit var tvEpgCurrentDesc: TextView
    private lateinit var tvEpgNextTitle: TextView

    private var currentStreamUrl: String? = null
    private var allChannelsInCat: List<LiveStream> = emptyList()
    private var channelAdapter: ChannelAdapter? = null
    private var overlayAdapter: OverlayChannelAdapter? = null

    private var user = ""
    private var pass = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "Wcjhrr3mzj") ?: "Wcjhrr3mzj"
        pass = prefs.getString("SAVED_PASS", "qww2rsEHnY") ?: "qww2rsEHnY"

        // 1. Botão de voltar ao Dashboard principal (canto superior esquerdo)
        findViewById<ImageButton>(R.id.btnBackLiveTv)?.setOnClickListener {
            finish()
        }

        // 2. Botão para fechar a lista flutuante em Fullscreen
        findViewById<ImageButton>(R.id.btnBackOverlay)?.setOnClickListener {
            fecharGavetaFullscreen()
        }

        layoutLeftPanels = findViewById(R.id.layoutLeftPanels)
        layoutBottomEpg = findViewById(R.id.layoutBottomEpg)
        playerView = findViewById(R.id.miniPlayerView)
        viewPlayerClickTarget = findViewById(R.id.viewPlayerClickTarget)
        btnToggleChannelOverlay = findViewById(R.id.btnToggleChannelOverlay)

        layoutChannelOverlay = findViewById(R.id.layoutChannelOverlay)
        viewOverlayDismissArea = findViewById(R.id.viewOverlayDismissArea)
        rvOverlayChannels = findViewById(R.id.rvOverlayChannels)

        rvCategories = findViewById(R.id.rvCategories)
        rvChannels = findViewById(R.id.rvChannels)
        etSearch = findViewById(R.id.etSearchChannel)

        // Inicializar Views EPG
        ivEpgChannelLogo = findViewById(R.id.ivEpgChannelLogo)
        tvEpgChannelName = findViewById(R.id.tvEpgChannelName)
        tvEpgBadge = findViewById(R.id.tvEpgBadge)
        tvEpgCurrentTitle = findViewById(R.id.tvEpgCurrentTitle)
        pbEpgProgram = findViewById(R.id.pbEpgProgram)
        tvEpgCurrentTime = findViewById(R.id.tvEpgCurrentTime)
        tvEpgCurrentDesc = findViewById(R.id.tvEpgCurrentDesc)
        tvEpgNextTitle = findViewById(R.id.tvEpgNextTitle)

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvChannels.layoutManager = LinearLayoutManager(this)
        rvOverlayChannels.layoutManager = LinearLayoutManager(this)

        configurarPlayer()
        carregarCategorias()

        // Toque na TV alterna entre tela cheia e modo normal
        viewPlayerClickTarget.setOnClickListener {
            if (layoutChannelOverlay.visibility == View.VISIBLE) {
                fecharGavetaFullscreen()
            } else {
                alternarFullscreen()
            }
        }

        // Abrir/Fechar gaveta quando em ecrã inteiro
        btnToggleChannelOverlay.setOnClickListener {
            if (layoutChannelOverlay.visibility == View.VISIBLE) {
                fecharGavetaFullscreen()
            } else {
                abrirGavetaFullscreen()
            }
        }

        viewOverlayDismissArea.setOnClickListener {
            fecharGavetaFullscreen()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                val filtrados = if (query.isEmpty()) {
                    allChannelsInCat
                } else {
                    allChannelsInCat.filter { it.name.lowercase().contains(query) }
                }
                channelAdapter?.atualizar(filtrados)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Botão físico/gesto de Voltar
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutChannelOverlay.visibility == View.VISIBLE) {
                    fecharGavetaFullscreen()
                } else if (isFullScreen) {
                    alternarFullscreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun alternarFullscreen() {
        isFullScreen = !isFullScreen
        if (isFullScreen) {
            layoutLeftPanels.visibility = View.GONE
            layoutBottomEpg.visibility = View.GONE
            btnToggleChannelOverlay.visibility = View.VISIBLE
        } else {
            fecharGavetaFullscreen()
            layoutLeftPanels.visibility = View.VISIBLE
            layoutBottomEpg.visibility = View.VISIBLE
            btnToggleChannelOverlay.visibility = View.GONE
        }
    }

    private fun abrirGavetaFullscreen() {
        layoutChannelOverlay.visibility = View.VISIBLE
        viewOverlayDismissArea.visibility = View.VISIBLE
    }

    private fun fecharGavetaFullscreen() {
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
        playerView.player = player
    }

    private fun tocarNoMiniPlayer(canal: LiveStream) {
        val url = "$baseUrl/live/$user/$pass/${canal.streamId}.ts"
        currentStreamUrl = url
        player?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }

        val posPrincipal = channelAdapter?.selecionarCanalPorId(canal.streamId) ?: -1
        if (posPrincipal != -1) {
            rvChannels.smoothScrollToPosition(posPrincipal)
        }

        val posOverlay = overlayAdapter?.selecionarCanalPorId(canal.streamId) ?: -1
        if (posOverlay != -1) {
            rvOverlayChannels.smoothScrollToPosition(posOverlay)
        }

        tvEpgChannelName.text = canal.name
        if (!canal.streamIcon.isNullOrEmpty()) {
            Glide.with(this).load(canal.streamIcon).into(ivEpgChannelLogo)
        } else {
            ivEpgChannelLogo.setImageResource(R.mipmap.ic_launcher)
        }

        carregarEpgCanal(canal.streamId)
    }

    private fun carregarEpgCanal(streamId: Int) {
        tvEpgCurrentTitle.text = "A procurar guia de programação..."
        tvEpgCurrentDesc.text = ""
        tvEpgCurrentTime.text = ""
        tvEpgNextTitle.text = ""
        pbEpgProgram.progress = 0
        tvEpgBadge.visibility = View.GONE

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$streamId&limit=4"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvEpgCurrentTitle.text = "Sem informação de programação disponível"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonStr = response.body?.string() ?: return
                try {
                    val jsonObj = JSONObject(jsonStr)
                    val listings = jsonObj.optJSONArray("epg_listings")

                    runOnUiThread {
                        if (listings != null && listings.length() > 0) {
                            val currentProg = listings.getJSONObject(0)
                            val title = decodeSafe(currentProg.optString("title"))
                            val desc = decodeSafe(currentProg.optString("description"))
                            val start = currentProg.optString("start")
                            val end = currentProg.optString("end")

                            tvEpgCurrentTitle.text = if (title.isNotEmpty()) title else "Programa sem título"
                            tvEpgCurrentDesc.text = if (desc.isNotEmpty()) desc else "Sem descrição adicional."
                            tvEpgBadge.visibility = View.VISIBLE

                            if (start.isNotEmpty() && end.isNotEmpty()) {
                                tvEpgCurrentTime.text = "$start  ➔  $end"
                                calcularProgresso(start, end)
                            }

                            if (listings.length() > 1) {
                                val nextProg = listings.getJSONObject(1)
                                val nextTitle = decodeSafe(nextProg.optString("title"))
                                val nextStart = nextProg.optString("start")
                                tvEpgNextTitle.text = "A Seguir: $nextTitle ($nextStart)"
                            } else {
                                tvEpgNextTitle.text = ""
                            }
                        } else {
                            tvEpgCurrentTitle.text = "Sem emissão agendada no guia"
                            tvEpgBadge.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvEpgCurrentTitle.text = "Programação indisponível"
                    }
                }
            }
        })
    }

    private fun calcularProgresso(startStr: String, endStr: String) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val startDate = sdf.parse(startStr)
            val endDate = sdf.parse(endStr)
            val now = Date()

            if (startDate != null && endDate != null) {
                val total = endDate.time - startDate.time
                val decorrido = now.time - startDate.time

                if (total > 0) {
                    val percent = ((decorrido.toFloat() / total.toFloat()) * 100).toInt()
                    pbEpgProgram.progress = percent.coerceIn(0, 100)
                }
            }
        } catch (e: Exception) {
            pbEpgProgram.progress = 0
        }
    }

    private fun decodeSafe(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(str, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            str
        }
    }

    private fun carregarCategorias() {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LiveTvActivity, "Erro ao carregar categorias", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveCategory>>() {}.type
                    val categorias: List<LiveCategory> = gson.fromJson(json, type)

                    runOnUiThread {
                        rvCategories.adapter = CategoryAdapter(categorias) { categoria ->
                            carregarCanaisDaCategoria(categoria.categoryId)
                        }
                        if (categorias.isNotEmpty()) {
                            carregarCanaisDaCategoria(categorias[0].categoryId)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@LiveTvActivity, "Erro no formato de categorias", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun carregarCanaisDaCategoria(catId: String) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams&category_id=$catId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@LiveTvActivity, "Erro ao carregar canais", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val canais: List<LiveStream> = gson.fromJson(json, type)
                    allChannelsInCat = canais

                    runOnUiThread {
                        channelAdapter = ChannelAdapter(canais) { canal ->
                            tocarNoMiniPlayer(canal)
                        }
                        rvChannels.adapter = channelAdapter

                        overlayAdapter = OverlayChannelAdapter(canais) { canal ->
                            tocarNoMiniPlayer(canal)
                            fecharGavetaFullscreen()
                        }
                        rvOverlayChannels.adapter = overlayAdapter

                        if (canais.isNotEmpty()) {
                            tocarNoMiniPlayer(canais[0])
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@LiveTvActivity, "Erro no formato de canais", Toast.LENGTH_SHORT).show()
                    }
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

    // --- ADAPTER CATEGORIAS ---
    inner class CategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private var selectedPosition = 0

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvCategoryTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cat = list[position]
            holder.tv.text = cat.categoryName
            holder.tv.setBackgroundColor(if (position == selectedPosition) 0xFF1E2836.toInt() else 0x00000000)

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onClick(cat)
                }
            }
        }
    }

    // --- ADAPTER CANAIS PRINCIPAIS ---
    inner class ChannelAdapter(
        private var list: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

        private var selectedPosition = 0

        fun selecionarCanalPorId(streamId: Int): Int {
            val index = list.indexOfFirst { it.streamId == streamId }
            if (index != -1 && index != selectedPosition) {
                val prev = selectedPosition
                selectedPosition = index
                notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
            }
            return index
        }

        fun atualizar(novaLista: List<LiveStream>) {
            list = novaLista
            notifyDataSetChanged()
        }

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
            holder.itemView.setBackgroundColor(if (position == selectedPosition) 0xFF1A2634.toInt() else 0x00000000)

            if (!canal.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(canal.streamIcon)
                    .into(holder.iv)
            } else {
                holder.iv.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                }
                onClick(canal)
            }
        }
    }

    // --- ADAPTER CANAIS GAVETA FULLSCREEN ---
    inner class OverlayChannelAdapter(
        private val list: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<OverlayChannelAdapter.ViewHolder>() {

        private var selectedPosition = 0

        fun selecionarCanalPorId(streamId: Int): Int {
            val index = list.indexOfFirst { it.streamId == streamId }
            if (index != -1 && index != selectedPosition) {
                val prev = selectedPosition
                selectedPosition = index
                notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
            }
            return index
        }

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
            holder.itemView.setBackgroundColor(if (position == selectedPosition) 0xFF1A2634.toInt() else 0x00000000)

            if (!canal.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.streamIcon).into(holder.iv)
            } else {
                holder.iv.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                }
                onClick(canal)
            }
        }
    }
}