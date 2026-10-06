package com.example.myapplication

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val client = OkHttpClient()
    private val gson = Gson()

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var rvChannels: RecyclerView

    // Contentores de layout para alternar Fullscreen
    private lateinit var layoutLeftPanels: View
    private lateinit var layoutBottomEpg: View
    private lateinit var viewPlayerClickTarget: View

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
    private var currentStreamId: Int = -1
    private var channelAdapter: ChannelAdapter? = null
    private var overlayAdapter: OverlayChannelAdapter? = null

    private var user = ""
    private var pass = ""

    @Suppress("DEPRECATION")
    private fun isTvMode(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("amazon.hardware.fire_tv") ||
            pm.hasSystemFeature("android.hardware.type.television") ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            return true
        }
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            return true
        }
        return false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "").orEmpty()
        pass = prefs.getString("SAVED_PASS", "").orEmpty()

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

        layoutChannelOverlay = findViewById(R.id.layoutChannelOverlay)
        viewOverlayDismissArea = findViewById(R.id.viewOverlayDismissArea)
        rvOverlayChannels = findViewById(R.id.rvOverlayChannels)

        rvCategories = findViewById(R.id.rvCategories)
        rvChannels = findViewById(R.id.rvChannels)

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

        // Toque na área do player alterna entre ecrã inteiro e modo normal
        viewPlayerClickTarget.setOnClickListener {
            if (layoutChannelOverlay.visibility == View.VISIBLE) {
                fecharGavetaFullscreen()
            } else {
                alternarFullscreen()
            }
        }

        viewOverlayDismissArea.setOnClickListener {
            fecharGavetaFullscreen()
        }

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOkKey = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                      event.keyCode == KeyEvent.KEYCODE_ENTER ||
                      event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

        // Ao estar em ecrã completo na Box, carregar "OK" sai do ecrã completo
        if (isOkKey && isFullScreen && layoutChannelOverlay.visibility != View.VISIBLE) {
            if (event.action == KeyEvent.ACTION_UP) {
                alternarFullscreen()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun alternarFullscreen() {
        isFullScreen = !isFullScreen
        if (isFullScreen) {
            layoutLeftPanels.visibility = View.GONE
            layoutBottomEpg.visibility = View.GONE
        } else {
            fecharGavetaFullscreen()
            layoutLeftPanels.visibility = View.VISIBLE
            layoutBottomEpg.visibility = View.VISIBLE
            // Ao sair do canal / ecrã completo, focar de imediato o canal atual na lista
            focarCanalAtual()
        }
    }

    private fun focarCanalAtual() {
        if (currentStreamId == -1) return
        val pos = channelAdapter?.selecionarCanalPorId(currentStreamId) ?: -1
        if (pos >= 0) {
            rvChannels.post {
                rvChannels.scrollToPosition(pos)
                rvChannels.postDelayed({
                    val vh = rvChannels.findViewHolderForAdapterPosition(pos)
                    vh?.itemView?.requestFocus()
                }, 80)
            }
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
        player?.volume = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getInt("player_volume", 100).coerceIn(0, 100) / 100f
        playerView.player = player
    }

    private fun tocarNoMiniPlayer(canal: LiveStream) {
        currentStreamId = canal.streamId
        getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
            .putInt("last_live_stream_id", canal.streamId).apply()
        val extension = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString("stream_type", "MPEGTS") == "HLS") "m3u8" else "ts"
        val url = "$baseUrl/live/$user/$pass/${canal.streamId}.$extension"
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
                                tvEpgCurrentTime.text = "${formatProgramTime(start)}  ➔  ${formatProgramTime(end)}"
                                calcularProgresso(start, end)
                            }

                            if (listings.length() > 1) {
                                val nextProg = listings.getJSONObject(1)
                                val nextTitle = decodeSafe(nextProg.optString("title"))
                                val nextStart = nextProg.optString("start")
                                tvEpgNextTitle.text = "A Seguir: $nextTitle (${formatProgramTime(nextStart)})"
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

    private fun formatProgramTime(value: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = input.parse(value) ?: return value
            val pattern = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("time_format", "12 Hr") == "24 Hr") "HH:mm" else "hh:mm a"
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (_: Exception) {
            value
        }
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
                            rvCategories.postDelayed({
                                val vh = rvCategories.findViewHolderForAdapterPosition(0)
                                if (vh != null) {
                                    vh.itemView.requestFocus()
                                } else {
                                    rvCategories.scrollToPosition(0)
                                    rvCategories.postDelayed({
                                        rvCategories.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                    }, 80)
                                }
                            }, 100)
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
                            val appSettings = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                            val lastId = appSettings.getInt("last_live_stream_id", -1)
                            val resumeLast = appSettings.getBoolean("last_live", false)
                            val selected = if (resumeLast) canais.firstOrNull { it.streamId == lastId } else null
                            val canalInicial = selected ?: canais[0]
                            channelAdapter?.selecionarCanalPorId(canalInicial.streamId)
                            tocarNoMiniPlayer(canalInicial)
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

            val updateCatBg: (Boolean) -> Unit = { hasFocus ->
                val currentSelected = holder.bindingAdapterPosition == selectedPosition
                holder.tv.background = GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else if (currentSelected) {
                        setColor(0xFF23324A.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else {
                        setColor(0x00000000)
                    }
                }
            }
            updateCatBg(holder.itemView.isFocused)

            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                updateCatBg(hasFocus)
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                }
                onClick(cat)
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

            val updateChannelBg: (Boolean) -> Unit = { hasFocus ->
                val currentSelected = holder.bindingAdapterPosition == selectedPosition
                holder.itemView.background = GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else if (currentSelected) {
                        setColor(0xFF172338.toInt())
                        setStroke(dp(1), 0xFF354B68.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else {
                        setColor(0x00000000)
                    }
                }
            }
            updateChannelBg(holder.itemView.isFocused)

            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                updateChannelBg(hasFocus)
            }

            if (!canal.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(canal.streamIcon)
                    .into(holder.iv)
            } else {
                holder.iv.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                val clickedPosition = holder.bindingAdapterPosition
                if (clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                // Se o canal clicado já é o que está aberto / a tocar atualmente:
                // Expande para ecrã inteiro sem dar "F5" (sem recarregar o stream)
                if (currentStreamId == canal.streamId) {
                    if (!isFullScreen) {
                        alternarFullscreen()
                    }
                    return@setOnClickListener
                }

                // Se for um canal diferente: seleciona e começa a reproduzir no mini-player
                val prev = selectedPosition
                selectedPosition = clickedPosition
                if (prev != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                }
                notifyItemChanged(selectedPosition)
                onClick(canal)
            }

            holder.itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    holder.itemView.performClick()
                    true
                } else {
                    false
                }
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

            val updateOverlayBg: (Boolean) -> Unit = { hasFocus ->
                val currentSelected = holder.bindingAdapterPosition == selectedPosition
                holder.itemView.background = GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else if (currentSelected) {
                        setColor(0xFF172338.toInt())
                        setStroke(dp(1), 0xFF354B68.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else {
                        setColor(0x00000000)
                    }
                }
            }
            updateOverlayBg(holder.itemView.isFocused)

            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                updateOverlayBg(hasFocus)
            }

            if (!canal.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.streamIcon).into(holder.iv)
            } else {
                holder.iv.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                if (currentStreamId == canal.streamId) {
                    fecharGavetaFullscreen()
                    return@setOnClickListener
                }
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
