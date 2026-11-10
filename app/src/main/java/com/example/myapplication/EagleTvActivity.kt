package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class EagleTvActivity : BaseActivity() {

    enum class WorkspaceSection {
        LIVE_TV,
        MOVIES,
        SERIES,
        SPORTS,
        NEWSPAPERS,
        CATCHUP,
        VPN,
        SETTINGS
    }

    data class InlineEpgSummary(
        val currentTitle: String,
        val currentDesc: String,
        val startStr: String,
        val endStr: String,
        val nextTitle: String,
        val nextStartStr: String,
        val progressPercent: Int,
        val fetchedAtMillis: Long = System.currentTimeMillis()
    )

    sealed class CatalogPosterItem {
        data class Movie(val vod: VodStream) : CatalogPosterItem()
        data class Series(val series: SeriesItem) : CatalogPosterItem()
        data class Channel(val channel: LiveStream) : CatalogPosterItem()
    }

    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val client = OkHttpClient()
    private val gson = Gson()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var framePlayerWrapper: FrameLayout
    private lateinit var layoutMiniPlayerBadge: View
    private lateinit var tvMiniPlayerHint: TextView

    // Contentores principais
    private lateinit var layoutLeftRail: View
    private lateinit var viewRailDivider: View
    private lateinit var layoutTopPreviewRow: LinearLayout
    private lateinit var layoutTopProgramDetails: View
    private lateinit var viewTopHorizontalDivider: View
    private lateinit var layoutLiveTvColumns: View
    private lateinit var layoutCatalogSection: View

    // Menu Lateral (Left Rail)
    private lateinit var tvRailClock: TextView
    private lateinit var tvRailActiveServer: TextView
    private lateinit var navLiveTv: TextView
    private lateinit var navMovies: TextView
    private lateinit var navSeries: TextView
    private lateinit var navEpgGuide: TextView
    private lateinit var navCatchUp: TextView
    private lateinit var navSports: TextView
    private lateinit var navNewspapers: TextView
    private lateinit var navVpn: TextView
    private lateinit var navRefresh: TextView
    private lateinit var navSettings: TextView

    // Topo: Painel de Informação EPG / Hero Preview
    private lateinit var ivTopHeaderLogo: ImageView
    private lateinit var tvTopHeaderTitle: TextView
    private lateinit var tvTopHeaderSubtitle: TextView
    private lateinit var tvTopFavBadge: TextView
    private lateinit var tvTopCatchupBadge: TextView
    private lateinit var tvTopLiveBadge: TextView
    private lateinit var layoutTopProgressRow: View
    private lateinit var tvTopProgramTime: TextView
    private lateinit var pbTopProgram: ProgressBar
    private lateinit var tvTopProgramPercent: TextView
    private lateinit var tvTopProgramDesc: TextView
    private lateinit var tvTopNextProgram: TextView
    private lateinit var tvTopRemoteHints: TextView

    // Secção Live TV (3 Colunas)
    private lateinit var tvLiveGroupsCount: TextView
    private lateinit var rvLiveGroups: RecyclerView
    private lateinit var tvLiveChannelListHeader: TextView
    private lateinit var tvLiveChannelsCount: TextView
    private lateinit var rvLiveChannels: RecyclerView
    private lateinit var tvChannelScheduleHeader: TextView
    private lateinit var tvEmptyChannelSchedule: TextView
    private lateinit var rvChannelSchedule: RecyclerView

    // Secção Catálogo (Filmes / Séries / Pesquisa Global)
    private lateinit var tvCatalogGroupsHeader: TextView
    private lateinit var rvCatalogGroups: RecyclerView
    private lateinit var etCatalogSearch: EditText
    private lateinit var btnCatalogSort: Button
    private lateinit var tvCatalogEmpty: TextView
    private lateinit var rvCatalogGrid: RecyclerView

    // Secção VPN PIA Integrada
    private lateinit var layoutVpnSection: View
    private lateinit var ivVpnShield: ImageView
    private lateinit var tvVpnStatus: TextView
    private lateinit var tvVpnStatusSub: TextView
    private lateinit var tvVpnSelectedLocation: TextView
    private lateinit var tvVpnIp: TextView
    private lateinit var tvVpnDuration: TextView
    private lateinit var btnToggleVpn: Button
    private lateinit var pbVpnLoading: ProgressBar
    private lateinit var rvVpnRegions: RecyclerView

    // Secção Definições Integrada
    private lateinit var layoutSettingsSection: View
    private lateinit var rvSettingsGrid: RecyclerView

    // Overlays em Fullscreen (Estilo Formuler MYTVOnline / TiviMate)
    private lateinit var layoutFullscreenOverlayGuide: View
    private lateinit var rvOverlayGroups: RecyclerView
    private lateinit var tvOverlayGroupTitle: TextView
    private lateinit var rvOverlayChannels: RecyclerView
    private lateinit var viewOverlayDismissTarget: View

    private lateinit var layoutRecentChannelsBar: View
    private lateinit var rvRecentChannels: RecyclerView

    private lateinit var layoutFullscreenOsd: View
    private lateinit var tvOsdChannelNumber: TextView
    private lateinit var ivOsdChannelLogo: ImageView
    private lateinit var tvOsdChannelName: TextView
    private lateinit var tvOsdCurrentProgram: TextView
    private lateinit var tvOsdFavBadge: TextView
    private lateinit var tvOsdClock: TextView
    private lateinit var tvOsdProgramTime: TextView
    private lateinit var pbOsdProgram: ProgressBar
    private lateinit var tvOsdNextProgram: TextView
    private lateinit var btnOsdChannelList: TextView
    private lateinit var btnOsdRecentChannels: TextView
    private lateinit var btnOsdToggleFav: TextView
    private lateinit var btnOsdAspectRatio: TextView
    private lateinit var btnOsdExitFullscreen: TextView

    private lateinit var tvNumericZapOsd: TextView

    // Estado da Aplicação
    private var activeSection: WorkspaceSection = WorkspaceSection.LIVE_TV
    private var isFullScreen = false
    private var user = ""
    private var pass = ""

    private var allLiveCategories = listOf<LiveCategory>()
    private val allLiveStreamsCache = mutableListOf<LiveStream>()
    private var currentGroupChannels = listOf<LiveStream>()
    private var currentLiveGroupId = ""
    private var currentLiveGroupName = "CANAIS"
    private var currentPlayingChannel: LiveStream? = null
    private var currentPlayingNumber: Int = 1
    private var currentStreamId: Int = -1
    private var previewedScheduleStreamId: Int = -1

    private val inlineEpgCache = ConcurrentHashMap<Int, InlineEpgSummary>()
    private val pendingInlineEpgRequests = ConcurrentHashMap.newKeySet<Int>()
    private val vodPlotCache = ConcurrentHashMap<Int, VodInfoData>()

    // Catálogo VOD & Séries
    private var allVodCategories = listOf<VodCategory>()
    private val allMoviesCache = mutableListOf<VodStream>()
    private var currentVodCategoryMovies = listOf<VodStream>()
    private var currentVodCategoryId = ""
    private var currentVodSortMode = 0 // 0=Padrão, 1=A-Z, 2=Recentes, 3=Rating

    private var allSeriesCategories = listOf<SeriesCategory>()
    private val allSeriesCache = mutableListOf<SeriesItem>()
    private var currentSeriesCategoryItems = listOf<SeriesItem>()
    private var currentSeriesCategoryId = ""

    private var liveGroupAdapter: GroupListAdapter? = null
    private var overlayGroupAdapter: GroupListAdapter? = null
    private var liveChannelAdapter: TiviChannelAdapter? = null
    private var overlayChannelAdapter: TiviChannelAdapter? = null
    private var catalogGroupAdapter: GroupListAdapter? = null
    private var catalogPosterAdapter: CatalogPosterAdapter? = null

    // Controlo de Retenção e Restauração de Foco (D-Pad / TV Box)
    enum class LastFocusArea {
        NAV_RAIL,
        LIVE_GROUPS,
        LIVE_CHANNELS,
        CHANNEL_SCHEDULE,
        CATALOG_GROUPS,
        CATALOG_GRID
    }

    private var lastFocusArea = LastFocusArea.LIVE_CHANNELS
    private var lastLiveChannelPos = 0
    private var lastLiveGroupPos = 0
    private var lastSchedulePos = 0
    private var lastCatalogGroupPos = 0
    private var lastCatalogGridPos = 0
    private var lastRailFocusId = R.id.navLiveTv
    private var hasSavedFocus = false

    private fun focarRecyclerViewPosicao(rv: RecyclerView, targetPos: Int, retries: Int = 6) {
        if (targetPos < 0) return
        val count = rv.adapter?.itemCount ?: 0
        if (count == 0) return
        val safePos = targetPos.coerceIn(0, count - 1)
        rv.scrollToPosition(safePos)
        rv.post {
            val vh = rv.findViewHolderForAdapterPosition(safePos)
            if (vh != null) {
                vh.itemView.requestFocus()
            } else if (retries > 0) {
                rv.postDelayed({ focarRecyclerViewPosicao(rv, safePos, retries - 1) }, 50)
            }
        }
    }

    private fun restaurarFocoSalvo() {
        when (lastFocusArea) {
            LastFocusArea.CHANNEL_SCHEDULE -> {
                if (activeSection == WorkspaceSection.LIVE_TV && (rvChannelSchedule.adapter?.itemCount ?: 0) > 0) {
                    focarRecyclerViewPosicao(rvChannelSchedule, lastSchedulePos)
                } else {
                    focarRecyclerViewPosicao(rvLiveChannels, lastLiveChannelPos)
                }
            }
            LastFocusArea.LIVE_CHANNELS -> {
                val streamPos = if (currentStreamId != -1) {
                    val idx = currentGroupChannels.indexOfFirst { it.streamId == currentStreamId }
                    if (idx >= 0) idx else lastLiveChannelPos
                } else {
                    lastLiveChannelPos
                }
                liveChannelAdapter?.selecionarCanalPorId(currentStreamId)
                focarRecyclerViewPosicao(rvLiveChannels, streamPos)
            }
            LastFocusArea.LIVE_GROUPS -> {
                focarRecyclerViewPosicao(rvLiveGroups, lastLiveGroupPos)
            }
            LastFocusArea.CATALOG_GRID -> {
                focarRecyclerViewPosicao(rvCatalogGrid, lastCatalogGridPos)
            }
            LastFocusArea.CATALOG_GROUPS -> {
                focarRecyclerViewPosicao(rvCatalogGroups, lastCatalogGroupPos)
            }
            LastFocusArea.NAV_RAIL -> {
                findViewById<View>(lastRailFocusId)?.requestFocus()
            }
        }
    }

    // Zapping numérico (0-9)
    private var numericBuffer = StringBuilder()
    private val commitNumericZapRunnable = Runnable {
        val number = numericBuffer.toString().toIntOrNull()
        numericBuffer.clear()
        tvNumericZapOsd.visibility = View.GONE
        if (number != null && number >= 1 && currentGroupChannels.isNotEmpty()) {
            val index = (number - 1).coerceIn(0, currentGroupChannels.size - 1)
            val target = currentGroupChannels[index]
            sintonizarCanal(target, index + 1)
            if (isFullScreen) {
                mostrarOsdFullscreen()
            } else {
                focarCanalNaListaPrincipal(target.streamId)
            }
        }
    }

    private val hideOsdRunnable = Runnable {
        if (isFullScreen && ! osdHasFocus()) {
            layoutFullscreenOsd.visibility = View.GONE
        }
    }

    private val clockTickRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                atualizarRelogio()
                uiHandler.postDelayed(this, 15_000L)
            }
        }
    }

    private var scheduleFocusRunnable: Runnable? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(android.graphics.PixelFormat.RGBA_8888)
        setContentView(R.layout.activity_eagle_tv)

        IptvServiceConfig.initialize(this)
        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "").orEmpty()
        pass = prefs.getString("SAVED_PASS", "").orEmpty()

        if (user.isBlank() || pass.isBlank()) {
            startActivity(Intent(this, ServiceSelectionActivity::class.java))
            finish()
            return
        }

        inicializarViews()
        configurarMenuLateral()
        configurarPlayer()
        configurarEventosFullscreen()
        configurarPesquisaEOrdenacao()

        atualizarRelogio()
        atualizarEstadoServidorEVpn()
        AccountExpirationAlert.checkAndShow(this)
        AppUpdateManager.checkForUpdates(this, manual = false)

        carregarTudoLiveTv()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    layoutFullscreenOverlayGuide.visibility == View.VISIBLE -> {
                        fecharGavetaFullscreen()
                    }
                    layoutRecentChannelsBar.visibility == View.VISIBLE -> {
                        layoutRecentChannelsBar.visibility = View.GONE
                    }
                    isFullScreen && layoutFullscreenOsd.visibility == View.VISIBLE -> {
                        layoutFullscreenOsd.visibility = View.GONE
                    }
                    isFullScreen -> {
                        sairDeFullscreen()
                    }
                    catchupViewingPrograms && activeSection == WorkspaceSection.CATCHUP -> {
                        voltarParaListaCanaisCatchUp()
                    }
                    activeSection != WorkspaceSection.LIVE_TV -> {
                        selecionarSecao(WorkspaceSection.LIVE_TV)
                    }
                    !layoutLeftRail.hasFocus() -> {
                        navLiveTv.requestFocus()
                    }
                    else -> {
                        mostrarDialogoSaida()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(clockTickRunnable)
        uiHandler.post(clockTickRunnable)
        atualizarEstadoServidorEVpn()
        if (player != null && currentStreamId != -1) {
            player?.playWhenReady = true
        }
        if (activeSection == WorkspaceSection.MOVIES || activeSection == WorkspaceSection.SERIES) {
            catalogPosterAdapter?.notifyDataSetChanged()
        }
        if (!isFullScreen && hasSavedFocus) {
            uiHandler.postDelayed({
                restaurarFocoSalvo()
            }, 120)
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        PiaVpnManager.removeListener(vpnStateListener)
        vpnDurationHandler.removeCallbacks(vpnDurationRunnable)
        super.onDestroy()
        playerView.player = null
        player?.release()
        player = null
    }

    private fun inicializarViews() {
        playerView = findViewById(R.id.eaglePlayerView)
        framePlayerWrapper = findViewById(R.id.framePlayerWrapper)
        layoutMiniPlayerBadge = findViewById(R.id.layoutMiniPlayerBadge)
        tvMiniPlayerHint = findViewById(R.id.tvMiniPlayerHint)

        layoutLeftRail = findViewById(R.id.layoutLeftRail)
        viewRailDivider = findViewById(R.id.viewRailDivider)
        layoutTopPreviewRow = findViewById(R.id.layoutTopPreviewRow)
        layoutTopProgramDetails = findViewById(R.id.layoutTopProgramDetails)
        viewTopHorizontalDivider = findViewById(R.id.viewTopHorizontalDivider)
        layoutLiveTvColumns = findViewById(R.id.layoutLiveTvColumns)
        layoutCatalogSection = findViewById(R.id.layoutCatalogSection)

        tvRailClock = findViewById(R.id.tvRailClock)
        tvRailActiveServer = findViewById(R.id.tvRailActiveServer)
        navLiveTv = findViewById(R.id.navLiveTv)
        navMovies = findViewById(R.id.navMovies)
        navSeries = findViewById(R.id.navSeries)
        navEpgGuide = findViewById(R.id.navEpgGuide)
        navCatchUp = findViewById(R.id.navCatchUp)
        navSports = findViewById(R.id.navSports)
        navNewspapers = findViewById(R.id.navNewspapers)
        navVpn = findViewById(R.id.navVpn)
        navRefresh = findViewById(R.id.navRefresh)
        navSettings = findViewById(R.id.navSettings)

        ivTopHeaderLogo = findViewById(R.id.ivTopHeaderLogo)
        tvTopHeaderTitle = findViewById(R.id.tvTopHeaderTitle)
        tvTopHeaderSubtitle = findViewById(R.id.tvTopHeaderSubtitle)
        tvTopFavBadge = findViewById(R.id.tvTopFavBadge)
        tvTopCatchupBadge = findViewById(R.id.tvTopCatchupBadge)
        tvTopLiveBadge = findViewById(R.id.tvTopLiveBadge)
        layoutTopProgressRow = findViewById(R.id.layoutTopProgressRow)
        tvTopProgramTime = findViewById(R.id.tvTopProgramTime)
        pbTopProgram = findViewById(R.id.pbTopProgram)
        tvTopProgramPercent = findViewById(R.id.tvTopProgramPercent)
        tvTopProgramDesc = findViewById(R.id.tvTopProgramDesc)
        tvTopNextProgram = findViewById(R.id.tvTopNextProgram)
        tvTopRemoteHints = findViewById(R.id.tvTopRemoteHints)

        tvLiveGroupsCount = findViewById(R.id.tvLiveGroupsCount)
        rvLiveGroups = findViewById(R.id.rvLiveGroups)
        tvLiveChannelListHeader = findViewById(R.id.tvLiveChannelListHeader)
        tvLiveChannelsCount = findViewById(R.id.tvLiveChannelsCount)
        rvLiveChannels = findViewById(R.id.rvLiveChannels)
        tvChannelScheduleHeader = findViewById(R.id.tvChannelScheduleHeader)
        tvEmptyChannelSchedule = findViewById(R.id.tvEmptyChannelSchedule)
        rvChannelSchedule = findViewById(R.id.rvChannelSchedule)

        tvCatalogGroupsHeader = findViewById(R.id.tvCatalogGroupsHeader)
        rvCatalogGroups = findViewById(R.id.rvCatalogGroups)
        etCatalogSearch = findViewById(R.id.etCatalogSearch)
        btnCatalogSort = findViewById(R.id.btnCatalogSort)
        tvCatalogEmpty = findViewById(R.id.tvCatalogEmpty)
        rvCatalogGrid = findViewById(R.id.rvCatalogGrid)

        layoutFullscreenOverlayGuide = findViewById(R.id.layoutFullscreenOverlayGuide)
        rvOverlayGroups = findViewById(R.id.rvOverlayGroups)
        tvOverlayGroupTitle = findViewById(R.id.tvOverlayGroupTitle)
        rvOverlayChannels = findViewById(R.id.rvOverlayChannels)
        viewOverlayDismissTarget = findViewById(R.id.viewOverlayDismissTarget)

        layoutRecentChannelsBar = findViewById(R.id.layoutRecentChannelsBar)
        rvRecentChannels = findViewById(R.id.rvRecentChannels)

        layoutFullscreenOsd = findViewById(R.id.layoutFullscreenOsd)
        tvOsdChannelNumber = findViewById(R.id.tvOsdChannelNumber)
        ivOsdChannelLogo = findViewById(R.id.ivOsdChannelLogo)
        tvOsdChannelName = findViewById(R.id.tvOsdChannelName)
        tvOsdCurrentProgram = findViewById(R.id.tvOsdCurrentProgram)
        tvOsdFavBadge = findViewById(R.id.tvOsdFavBadge)
        tvOsdClock = findViewById(R.id.tvOsdClock)
        tvOsdProgramTime = findViewById(R.id.tvOsdProgramTime)
        pbOsdProgram = findViewById(R.id.pbOsdProgram)
        tvOsdNextProgram = findViewById(R.id.tvOsdNextProgram)
        btnOsdChannelList = findViewById(R.id.btnOsdChannelList)
        btnOsdRecentChannels = findViewById(R.id.btnOsdRecentChannels)
        btnOsdToggleFav = findViewById(R.id.btnOsdToggleFav)
        btnOsdAspectRatio = findViewById(R.id.btnOsdAspectRatio)
        btnOsdExitFullscreen = findViewById(R.id.btnOsdExitFullscreen)

        tvNumericZapOsd = findViewById(R.id.tvNumericZapOsd)

        rvLiveGroups.layoutManager = LinearLayoutManager(this)
        rvLiveGroups.itemAnimator = null
        rvLiveChannels.layoutManager = LinearLayoutManager(this)
        rvLiveChannels.itemAnimator = null
        rvChannelSchedule.layoutManager = LinearLayoutManager(this)
        rvChannelSchedule.itemAnimator = null

        rvOverlayGroups.layoutManager = LinearLayoutManager(this)
        rvOverlayGroups.itemAnimator = null
        rvOverlayChannels.layoutManager = LinearLayoutManager(this)
        rvOverlayChannels.itemAnimator = null

        rvRecentChannels.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRecentChannels.itemAnimator = null

        rvCatalogGroups.layoutManager = LinearLayoutManager(this)
        rvCatalogGroups.itemAnimator = null
        rvCatalogGrid.layoutManager = GridLayoutManager(this, 5)
        rvCatalogGrid.itemAnimator = null

        // Secções Integradas: VPN e Definições
        layoutVpnSection = findViewById(R.id.layoutVpnSection)
        ivVpnShield = findViewById(R.id.ivVpnShield)
        tvVpnStatus = findViewById(R.id.tvVpnStatus)
        tvVpnStatusSub = findViewById(R.id.tvVpnStatusSub)
        tvVpnSelectedLocation = findViewById(R.id.tvVpnSelectedLocation)
        tvVpnIp = findViewById(R.id.tvVpnIp)
        tvVpnDuration = findViewById(R.id.tvVpnDuration)
        btnToggleVpn = findViewById(R.id.btnToggleVpn)
        pbVpnLoading = findViewById(R.id.pbVpnLoading)
        rvVpnRegions = findViewById(R.id.rvVpnRegions)
        rvVpnRegions.layoutManager = GridLayoutManager(this, 2)
        rvVpnRegions.itemAnimator = null

        layoutSettingsSection = findViewById(R.id.layoutSettingsSection)
        rvSettingsGrid = findViewById(R.id.rvSettingsGrid)
        rvSettingsGrid.layoutManager = GridLayoutManager(this, 2)
        rvSettingsGrid.itemAnimator = null

        // Estilizar painel OSD e barra de recentes com cantos arredondados e borda subtil ciano
        layoutFullscreenOsd.background = GradientDrawable().apply {
            setColor(0xEB060D1A.toInt())
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x4D00E5FF)
        }
        layoutRecentChannelsBar.background = GradientDrawable().apply {
            setColor(0xEB060D1A.toInt())
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x4D00E5FF)
        }
        tvNumericZapOsd.background = GradientDrawable().apply {
            setColor(0xEB050B16.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(2), 0xFF00E5FF.toInt())
        }
    }

    private fun configurarMenuLateral() {
        val navButtons = listOf(
            navLiveTv, navMovies, navSeries, navEpgGuide, navCatchUp,
            navSports, navNewspapers, navVpn, navRefresh, navSettings
        )

        navButtons.forEach { btn ->
            btn.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    lastFocusArea = LastFocusArea.NAV_RAIL
                    lastRailFocusId = btn.id
                }
                atualizarEstilosMenuLateral()
            }
        }

        navLiveTv.setOnClickListener { selecionarSecao(WorkspaceSection.LIVE_TV) }
        navMovies.setOnClickListener { selecionarSecao(WorkspaceSection.MOVIES) }
        navSeries.setOnClickListener { selecionarSecao(WorkspaceSection.SERIES) }

        navEpgGuide.setOnClickListener {
            // O Guia TV é a própria vista de TV em Direto: foca diretamente o programa atual no guia EPG
            lastFocusArea = LastFocusArea.CHANNEL_SCHEDULE
            selecionarSecao(WorkspaceSection.LIVE_TV)
            rvChannelSchedule.postDelayed({
                val target = if ((rvChannelSchedule.adapter?.itemCount ?: 0) > 0) rvChannelSchedule else rvLiveChannels
                val targetPos = if (target == rvChannelSchedule) {
                    (rvChannelSchedule.adapter as? ChannelScheduleAdapter)?.posicaoProgramaAtual() ?: 0
                } else {
                    liveChannelAdapter?.posicaoSelecionada()?.coerceAtLeast(0) ?: 0
                }
                focarRecyclerViewPosicao(target, targetPos)
            }, 200)
        }
        navCatchUp.setOnClickListener { selecionarSecao(WorkspaceSection.CATCHUP) }
        navSports.setOnClickListener { selecionarSecao(WorkspaceSection.SPORTS) }
        navNewspapers.setOnClickListener { selecionarSecao(WorkspaceSection.NEWSPAPERS) }
        navVpn.setOnClickListener {
            if (PiaVpnConfig.isVpnAllowedForCurrentUser(this)) {
                selecionarSecao(WorkspaceSection.VPN)
            } else {
                Toast.makeText(this, "VPN não ativa para esta conta no painel.", Toast.LENGTH_SHORT).show()
            }
        }
        navRefresh.setOnClickListener {
            CatalogRefreshDialog.show(this, user, pass) {
                inlineEpgCache.clear()
                allLiveStreamsCache.clear()
                allMoviesCache.clear()
                allSeriesCache.clear()
                carregarTudoLiveTv()
                Toast.makeText(this, "Lista e guia atualizados!", Toast.LENGTH_SHORT).show()
            }
        }
        navSettings.setOnClickListener {
            selecionarSecao(WorkspaceSection.SETTINGS)
        }

        atualizarEstilosMenuLateral()
    }

    private fun atualizarEstilosMenuLateral() {
        val sectionMap = mapOf(
            navLiveTv to (activeSection == WorkspaceSection.LIVE_TV),
            navMovies to (activeSection == WorkspaceSection.MOVIES),
            navSeries to (activeSection == WorkspaceSection.SERIES),
            navEpgGuide to false,
            navCatchUp to (activeSection == WorkspaceSection.CATCHUP),
            navSports to (activeSection == WorkspaceSection.SPORTS),
            navNewspapers to (activeSection == WorkspaceSection.NEWSPAPERS),
            navVpn to (activeSection == WorkspaceSection.VPN),
            navRefresh to false,
            navSettings to (activeSection == WorkspaceSection.SETTINGS)
        )

        sectionMap.forEach { (view, isActive) ->
            val hasFocus = view.hasFocus()
            view.background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                when {
                    hasFocus -> {
                        setColor(0xFF163456.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    }
                    isActive -> {
                        setColor(0xFF10253F.toInt())
                        setStroke(dp(1), 0x6638BDF8)
                    }
                    else -> {
                        setColor(Color.TRANSPARENT)
                    }
                }
            }
            view.setTextColor(
                when {
                    hasFocus -> Color.WHITE
                    isActive -> 0xFF00E5FF.toInt()
                    else -> 0xFFCBD5E1.toInt()
                }
            )
        }
    }

    private fun selecionarSecao(sec: WorkspaceSection) {
        activeSection = sec
        atualizarEstilosMenuLateral()
        configurarCatalogoParaSecao(grelha = true, pesquisa = true)

        layoutLiveTvColumns.visibility = View.GONE
        layoutCatalogSection.visibility = View.GONE
        layoutVpnSection.visibility = View.GONE
        layoutSettingsSection.visibility = View.GONE

        when (sec) {
            WorkspaceSection.SPORTS -> {
                layoutCatalogSection.visibility = View.VISIBLE
                configurarCatalogoParaSecao(grelha = false, pesquisa = false)
                tvCatalogGroupsHeader.text = "COMPETIÇÕES"
                definirTopoInformativo("⚽ Jogos do Dia", "Agenda desportiva com o canal de cada jogo", "Escolhe um jogo e carrega OK para sintonizar o canal que o transmite.")
                iniciarSecaoJogos()
            }
            WorkspaceSection.NEWSPAPERS -> {
                layoutCatalogSection.visibility = View.VISIBLE
                configurarCatalogoParaSecao(grelha = true, pesquisa = false)
                tvCatalogGroupsHeader.text = "CATEGORIAS"
                definirTopoInformativo("📰 Jornais", "Edições do dia em PDF", "Escolhe um jornal e carrega OK para ler a edição.")
                iniciarSecaoJornais()
            }
            WorkspaceSection.CATCHUP -> {
                layoutCatalogSection.visibility = View.VISIBLE
                configurarCatalogoParaSecao(grelha = false, pesquisa = true)
                tvCatalogGroupsHeader.text = "ESCOLHER DATA"
                definirTopoInformativo("⏪ Gravações", "Revê programas dos últimos 8 dias", "Escolhe a data à esquerda, depois o canal e o programa gravado.")
                iniciarSecaoCatchUp()
            }
            WorkspaceSection.LIVE_TV -> {
                layoutLiveTvColumns.visibility = View.VISIBLE
                currentPlayingChannel?.let { atualizarPainelTopoCanal(it) }
                rvLiveChannels.post {
                    if (currentStreamId != -1) {
                        focarCanalNaListaPrincipal(currentStreamId)
                    } else {
                        rvLiveGroups.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            }
            WorkspaceSection.MOVIES -> {
                layoutCatalogSection.visibility = View.VISIBLE
                btnCatalogSort.visibility = View.VISIBLE
                tvCatalogGroupsHeader.text = "PASTAS DE FILMES"
                etCatalogSearch.hint = "🔍 Pesquisar filmes nesta pasta..."
                iniciarSecaoFilmes()
            }
            WorkspaceSection.SERIES -> {
                layoutCatalogSection.visibility = View.VISIBLE
                btnCatalogSort.visibility = View.GONE
                tvCatalogGroupsHeader.text = "PASTAS DE SÉRIES"
                etCatalogSearch.hint = "🔍 Pesquisar séries nesta pasta..."
                iniciarSecaoSeries()
            }
            WorkspaceSection.VPN -> {
                layoutVpnSection.visibility = View.VISIBLE
                definirTopoInformativo("🛡️ PIA VPN", "Segurança e Privacidade WireGuard", "Protege a tua ligação e escolhe a localização pretendida.")
                iniciarSecaoVpn()
            }
            WorkspaceSection.SETTINGS -> {
                layoutSettingsSection.visibility = View.VISIBLE
                definirTopoInformativo("⚙️ Definições", "Configurações da aplicação", "Gere a tua conta, atualizações, formato de stream e idioma.")
                iniciarSecaoDefinicoes()
            }
        }
    }

    fun atualizarEstadoServidorEVpn() {
        val activeService = IptvServiceConfig.activeServiceId(this)
        val serverName = IptvServiceConfig.displayName(activeService).uppercase(Locale.getDefault())
        tvRailActiveServer.text = "● $serverName"
        tvRailActiveServer.background = GradientDrawable().apply {
            setColor(0xFF0F1E33.toInt())
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), 0x4038BDF8)
        }

        val vpnAllowed = PiaVpnConfig.isVpnAllowedForCurrentUser(this)
        val vpnConnected = PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTED
        if (!vpnAllowed) {
            navVpn.text = "🛡️  VPN: Bloqueada"
            navVpn.alpha = 0.45f
        } else if (vpnConnected) {
            navVpn.text = "🛡️  VPN: LIGADA"
            navVpn.alpha = 1.0f
            navVpn.setTextColor(0xFF10B981.toInt())
        } else {
            navVpn.text = "🛡️  VPN: OFF"
            navVpn.alpha = 1.0f
        }
    }

    private fun atualizarRelogio() {
        val pattern = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString("time_format", "24 Hr") == "12 Hr") "hh:mm a" else "HH:mm"
        val nowStr = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        tvRailClock.text = nowStr
        tvOsdClock.text = nowStr
    }

    private fun configurarPlayer() {
        val (p, _) = ExoPlayerHelper.buildOptimizedPlayer(this, playerView)
        player = p
        player?.volume = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getInt("player_volume", 100).coerceIn(0, 100) / 100f
        aplicarAspectMode(EagleTvConfig.getAspectMode(this))

        framePlayerWrapper.setOnClickListener {
            if (!isFullScreen) {
                entrarEmFullscreen()
            } else if (layoutFullscreenOverlayGuide.visibility == View.VISIBLE) {
                fecharGavetaFullscreen()
            } else if (layoutFullscreenOsd.visibility == View.VISIBLE) {
                layoutFullscreenOsd.visibility = View.GONE
            } else {
                mostrarOsdFullscreen()
            }
        }

        framePlayerWrapper.setOnFocusChangeListener { v, hasFocus ->
            if (!isFullScreen) {
                v.background = GradientDrawable().apply {
                    setColor(Color.BLACK)
                    setStroke(dp(if (hasFocus) 2 else 1), if (hasFocus) 0xFF00E5FF.toInt() else 0xFF1E293B.toInt())
                    cornerRadius = dp(8).toFloat()
                }
            }
        }
    }

    private fun aplicarAspectMode(mode: Int) {
        playerView.resizeMode = EagleTvConfig.toPlayerResizeMode(mode)
        btnOsdAspectRatio.text = "📐 ${EagleTvConfig.aspectModeLabel(mode)}"
    }

    private fun configurarEventosFullscreen() {
        viewOverlayDismissTarget.setOnClickListener {
            fecharGavetaFullscreen()
        }

        val osdButtons = listOf(
            btnOsdChannelList, btnOsdRecentChannels, btnOsdToggleFav, btnOsdAspectRatio, btnOsdExitFullscreen
        )
        osdButtons.forEach { btn ->
            styleOsdButton(btn, false)
            btn.setOnFocusChangeListener { v, hasFocus ->
                styleOsdButton(v as TextView, hasFocus)
                if (hasFocus) {
                    uiHandler.removeCallbacks(hideOsdRunnable)
                    uiHandler.postDelayed(hideOsdRunnable, 6000L)
                }
            }
        }

        btnOsdChannelList.setOnClickListener {
            layoutFullscreenOsd.visibility = View.GONE
            abrirGavetaFullscreen()
        }
        btnOsdRecentChannels.setOnClickListener {
            layoutFullscreenOsd.visibility = View.GONE
            abrirBarraCanaisRecentes()
        }
        btnOsdToggleFav.setOnClickListener {
            currentPlayingChannel?.let { canal ->
                alternarFavoritoCanal(canal)
                atualizarDadosOsdFullscreen(canal)
            }
        }
        btnOsdAspectRatio.setOnClickListener {
            val nextMode = EagleTvConfig.cycleAspectMode(this)
            aplicarAspectMode(nextMode)
            Toast.makeText(this, "Formato: ${EagleTvConfig.aspectModeLabel(nextMode)}", Toast.LENGTH_SHORT).show()
        }
        btnOsdExitFullscreen.setOnClickListener {
            sairDeFullscreen()
        }
    }

    private fun styleOsdButton(btn: TextView, hasFocus: Boolean) {
        btn.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            if (hasFocus) {
                setColor(0xFF1D4ED8.toInt())
                setStroke(dp(2), 0xFF00E5FF.toInt())
            } else {
                setColor(0xFF12233B.toInt())
                setStroke(dp(1), 0xFF284266.toInt())
            }
        }
    }

    private fun osdHasFocus(): Boolean {
        return btnOsdChannelList.hasFocus() ||
            btnOsdRecentChannels.hasFocus() ||
            btnOsdToggleFav.hasFocus() ||
            btnOsdAspectRatio.hasFocus() ||
            btnOsdExitFullscreen.hasFocus()
    }

    private fun entrarEmFullscreen() {
        isFullScreen = true
        layoutLeftRail.visibility = View.GONE
        viewRailDivider.visibility = View.GONE
        layoutTopProgramDetails.visibility = View.GONE
        viewTopHorizontalDivider.visibility = View.GONE
        layoutLiveTvColumns.visibility = View.GONE
        layoutCatalogSection.visibility = View.GONE
        layoutVpnSection.visibility = View.GONE
        layoutSettingsSection.visibility = View.GONE
        layoutMiniPlayerBadge.visibility = View.GONE

        layoutTopPreviewRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutTopPreviewRow.setPadding(0, 0, 0, 0)

        framePlayerWrapper.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        framePlayerWrapper.background = null

        mostrarOsdFullscreen()
    }

    private fun sairDeFullscreen() {
        isFullScreen = false
        fecharGavetaFullscreen()
        layoutRecentChannelsBar.visibility = View.GONE
        layoutFullscreenOsd.visibility = View.GONE
        uiHandler.removeCallbacks(hideOsdRunnable)

        layoutLeftRail.visibility = View.VISIBLE
        viewRailDivider.visibility = View.VISIBLE
        layoutTopProgramDetails.visibility = View.VISIBLE
        viewTopHorizontalDivider.visibility = View.VISIBLE
        layoutMiniPlayerBadge.visibility = View.VISIBLE

        layoutLiveTvColumns.visibility = if (activeSection == WorkspaceSection.LIVE_TV) View.VISIBLE else View.GONE
        layoutCatalogSection.visibility = if (activeSection in listOf(WorkspaceSection.MOVIES, WorkspaceSection.SERIES, WorkspaceSection.CATCHUP, WorkspaceSection.SPORTS, WorkspaceSection.NEWSPAPERS)) View.VISIBLE else View.GONE
        layoutVpnSection.visibility = if (activeSection == WorkspaceSection.VPN) View.VISIBLE else View.GONE
        layoutSettingsSection.visibility = if (activeSection == WorkspaceSection.SETTINGS) View.VISIBLE else View.GONE

        layoutTopPreviewRow.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(196)
        )
        val pad = dp(10)
        layoutTopPreviewRow.setPadding(pad, pad, pad, pad)

        framePlayerWrapper.layoutParams = LinearLayout.LayoutParams(
            dp(310),
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        restaurarFocoSalvo()
    }

    private fun mostrarOsdFullscreen(focusQuickActions: Boolean = false) {
        if (!isFullScreen) return
        currentPlayingChannel?.let { atualizarDadosOsdFullscreen(it) }
        layoutFullscreenOsd.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideOsdRunnable)
        uiHandler.postDelayed(hideOsdRunnable, 5000L)
        if (focusQuickActions) {
            btnOsdChannelList.post { btnOsdChannelList.requestFocus() }
        }
    }

    private fun atualizarDadosOsdFullscreen(canal: LiveStream) {
        tvOsdChannelNumber.text = String.format(Locale.US, "%03d", currentPlayingNumber)
        tvOsdChannelName.text = canal.name
        val isFav = EagleTvConfig.isFavoriteChannel(this, canal.streamId)
        tvOsdFavBadge.visibility = if (isFav) View.VISIBLE else View.GONE
        btnOsdToggleFav.text = if (isFav) "★ Remover Fav" else "☆ Favorito"

        if (!canal.streamIcon.isNullOrBlank()) {
            Glide.with(this).load(canal.streamIcon).into(ivOsdChannelLogo)
        } else {
            ivOsdChannelLogo.setImageResource(R.mipmap.ic_launcher)
        }

        val summary = inlineEpgCache[canal.streamId]
        if (summary != null) {
            tvOsdCurrentProgram.text = summary.currentTitle
            tvOsdProgramTime.text = if (summary.startStr.isNotEmpty() && summary.endStr.isNotEmpty()) {
                "${formatProgramTime(summary.startStr)} ➔ ${formatProgramTime(summary.endStr)}"
            } else {
                "EM DIRETO"
            }
            pbOsdProgram.progress = summary.progressPercent
            tvOsdNextProgram.text = if (summary.nextTitle.isNotEmpty()) {
                "A Seguir: ${summary.nextTitle}"
            } else {
                ""
            }
        } else {
            tvOsdCurrentProgram.text = "A carregar informação do programa..."
            tvOsdProgramTime.text = "EM DIRETO"
            pbOsdProgram.progress = 0
            tvOsdNextProgram.text = ""
        }
    }

    private fun abrirGavetaFullscreen() {
        layoutFullscreenOsd.visibility = View.GONE
        layoutRecentChannelsBar.visibility = View.GONE
        layoutFullscreenOverlayGuide.visibility = View.VISIBLE
        tvOverlayGroupTitle.text = currentLiveGroupName

        val pos = overlayChannelAdapter?.selecionarCanalPorId(currentStreamId) ?: -1
        if (pos >= 0) {
            rvOverlayChannels.post {
                rvOverlayChannels.scrollToPosition(pos)
                rvOverlayChannels.postDelayed({
                    rvOverlayChannels.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                }, 60)
            }
        } else {
            rvOverlayGroups.post {
                rvOverlayGroups.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun fecharGavetaFullscreen() {
        layoutFullscreenOverlayGuide.visibility = View.GONE
    }

    private fun abrirBarraCanaisRecentes() {
        val recentes = EagleTvConfig.getRecentChannels(this)
        if (recentes.isEmpty()) {
            Toast.makeText(this, "Ainda não tens canais recentes no histórico.", Toast.LENGTH_SHORT).show()
            return
        }
        layoutFullscreenOverlayGuide.visibility = View.GONE
        layoutFullscreenOsd.visibility = View.GONE
        layoutRecentChannelsBar.visibility = View.VISIBLE

        rvRecentChannels.adapter = RecentChannelsAdapter(recentes) { canal ->
            layoutRecentChannelsBar.visibility = View.GONE
            val idx = currentGroupChannels.indexOfFirst { it.streamId == canal.streamId }
            val num = if (idx >= 0) idx + 1 else 1
            sintonizarCanal(canal, num)
            mostrarOsdFullscreen()
        }
        rvRecentChannels.post {
            rvRecentChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN

        // 1. Zapping numérico (teclas 0 a 9 do comando da Box) em Live TV
        if (isDown && activeSection == WorkspaceSection.LIVE_TV && !etCatalogSearch.hasFocus()) {
            val digit = when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
                else -> -1
            }
            if (digit in 0..9 && numericBuffer.length < 4) {
                numericBuffer.append(digit)
                tvNumericZapOsd.text = "# ${numericBuffer.toString().padStart(3, '0')}"
                tvNumericZapOsd.visibility = View.VISIBLE
                uiHandler.removeCallbacks(commitNumericZapRunnable)
                uiHandler.postDelayed(commitNumericZapRunnable, 1150L)
                return true
            }
        }

        // 2. Atalhos em Modo Fullscreen (Estilo Formuler MYTVOnline / TiviMate)
        if (isFullScreen) {
            val overlayOpen = layoutFullscreenOverlayGuide.visibility == View.VISIBLE
            val recentOpen = layoutRecentChannelsBar.visibility == View.VISIBLE

            if (!overlayOpen && !recentOpen) {
                // Teclas CH+ / CH- ou Setas CIMA / BAIXO (quando o foco não está nos botões do OSD) mudam de canal!
                if (isDown && (keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                        (keyCode == KeyEvent.KEYCODE_DPAD_UP && !osdHasFocus()))) {
                    mudarCanalRelativo(-1)
                    return true
                }
                if (isDown && (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                        (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && !osdHasFocus()))) {
                    mudarCanalRelativo(1)
                    return true
                }
                // Seta ESQUERDA abre a Gaveta Transparente de Grupos + Canais sobre o vídeo
                if (isDown && keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !osdHasFocus()) {
                    abrirGavetaFullscreen()
                    return true
                }
                // Seta DIREITA ou Tecla INFO abre a Infobar OSD com foco nas ações rápidas
                if (isDown && (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_INFO) && !osdHasFocus()) {
                    mostrarOsdFullscreen(focusQuickActions = true)
                    return true
                }
                // Tecla OK abre a Lista de Canais Transparente sobre o vídeo (ou se já visível, mostra OSD)
                val isOk = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                if (isOk && !osdHasFocus()) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.isLongPress) {
                        currentPlayingChannel?.let {
                            alternarFavoritoCanal(it)
                            mostrarOsdFullscreen()
                        }
                        return true
                    }
                    if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                        abrirGavetaFullscreen()
                        return true
                    }
                    return true
                }
            } else if (overlayOpen) {
                // Na gaveta fullscreen: carregar ESQUERDA na lista de canais foca a lista de grupos flutuante
                if (isDown && keyCode == KeyEvent.KEYCODE_DPAD_LEFT && rvOverlayChannels.hasFocus()) {
                    val pos = overlayGroupAdapter?.posicaoSelecionada() ?: 0
                    rvOverlayGroups.scrollToPosition(pos)
                    rvOverlayGroups.post {
                        rvOverlayGroups.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                    }
                    return true
                }
                // Na gaveta fullscreen: carregar DIREITA na lista de grupos foca a lista de canais flutuante
                if (isDown && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && rvOverlayGroups.hasFocus()) {
                    val pos = (overlayChannelAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                    rvOverlayChannels.scrollToPosition(pos)
                    rvOverlayChannels.post {
                        rvOverlayChannels.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                    }
                    return true
                }
            }
        } else {
            // 3. Navegação D-Pad fluida entre Colunas no Modo Guia (3 Colunas + Left Rail)
            if (isDown && activeSection == WorkspaceSection.LIVE_TV) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    when {
                        rvChannelSchedule.hasFocus() -> {
                            val pos = (liveChannelAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            rvLiveChannels.scrollToPosition(pos)
                            rvLiveChannels.post {
                                rvLiveChannels.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                            }
                            return true
                        }
                        rvLiveChannels.hasFocus() -> {
                            val pos = (liveGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            rvLiveGroups.scrollToPosition(pos)
                            rvLiveGroups.post {
                                rvLiveGroups.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                            }
                            return true
                        }
                        rvLiveGroups.hasFocus() -> {
                            navLiveTv.requestFocus()
                            return true
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    when {
                        layoutLeftRail.hasFocus() -> {
                            val pos = (liveGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            rvLiveGroups.scrollToPosition(pos)
                            rvLiveGroups.post {
                                rvLiveGroups.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                            }
                            return true
                        }
                        rvLiveGroups.hasFocus() -> {
                            val pos = (liveChannelAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            rvLiveChannels.scrollToPosition(pos)
                            rvLiveChannels.post {
                                rvLiveChannels.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                            }
                            return true
                        }
                        rvLiveChannels.hasFocus() && (rvChannelSchedule.adapter?.itemCount ?: 0) > 0 -> {
                            val targetPos = (rvChannelSchedule.adapter as? ChannelScheduleAdapter)?.posicaoProgramaAtual() ?: 0
                            focarRecyclerViewPosicao(rvChannelSchedule, targetPos)
                            return true
                        }
                    }
                }
            } else if (isDown && activeSection == WorkspaceSection.VPN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    when {
                        rvVpnRegions.hasFocus() -> {
                            btnToggleVpn.requestFocus()
                            return true
                        }
                        btnToggleVpn.hasFocus() -> {
                            navVpn.requestFocus()
                            return true
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    when {
                        layoutLeftRail.hasFocus() -> {
                            btnToggleVpn.requestFocus()
                            return true
                        }
                        btnToggleVpn.hasFocus() && (rvVpnRegions.adapter?.itemCount ?: 0) > 0 -> {
                            focarRecyclerViewPosicao(rvVpnRegions, 0)
                            return true
                        }
                    }
                }
            } else if (isDown && activeSection == WorkspaceSection.SETTINGS) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (rvSettingsGrid.hasFocus()) {
                        navSettings.requestFocus()
                        return true
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (layoutLeftRail.hasFocus() && (rvSettingsGrid.adapter?.itemCount ?: 0) > 0) {
                        focarRecyclerViewPosicao(rvSettingsGrid, 0)
                        return true
                    }
                }
            } else if (isDown && activeSection != WorkspaceSection.LIVE_TV) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    when {
                        rvCatalogGrid.hasFocus() -> {
                            val pos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            focarRecyclerViewPosicao(rvCatalogGroups, pos)
                            return true
                        }
                        rvCatalogGroups.hasFocus() -> {
                            when (activeSection) {
                                WorkspaceSection.MOVIES -> navMovies.requestFocus()
                                WorkspaceSection.SERIES -> navSeries.requestFocus()
                                WorkspaceSection.CATCHUP -> navCatchUp.requestFocus()
                                WorkspaceSection.SPORTS -> navSports.requestFocus()
                                WorkspaceSection.NEWSPAPERS -> navNewspapers.requestFocus()
                                else -> navLiveTv.requestFocus()
                            }
                            return true
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    when {
                        layoutLeftRail.hasFocus() -> {
                            val pos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            focarRecyclerViewPosicao(rvCatalogGroups, pos)
                            return true
                        }
                        rvCatalogGroups.hasFocus() && (rvCatalogGrid.adapter?.itemCount ?: 0) > 0 -> {
                            focarRecyclerViewPosicao(rvCatalogGrid, 0)
                            return true
                        }
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun mudarCanalRelativo(delta: Int) {
        if (currentGroupChannels.isEmpty()) return
        val currentIdx = currentGroupChannels.indexOfFirst { it.streamId == currentStreamId }
        val baseIdx = if (currentIdx >= 0) currentIdx else 0
        val nextIdx = (baseIdx + delta + currentGroupChannels.size) % currentGroupChannels.size
        val nextCanal = currentGroupChannels[nextIdx]
        sintonizarCanal(nextCanal, nextIdx + 1)
        mostrarOsdFullscreen()
    }

    // =========================================================================
    // SECÇÃO 1: CARREGAMENTO E LÓGICA DE TV EM DIRETO (LIVE TV + EPG)
    // =========================================================================

    private fun carregarTudoLiveTv() {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@EagleTvActivity, "Erro ao carregar grupos de TV", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveCategory>>() {}.type
                    val remotas: List<LiveCategory> = gson.fromJson(json, type) ?: emptyList()

                    val listaCompleta = mutableListOf<LiveCategory>()
                    listaCompleta.add(LiveCategory("FAV_LIVE", "★ FAVORITOS"))
                    listaCompleta.add(LiveCategory("RECENT_LIVE", "🕒 RECENTES"))
                    listaCompleta.add(LiveCategory("ALL_LIVE", "📺 TODOS OS CANAIS"))
                    listaCompleta.addAll(remotas)
                    allLiveCategories = listaCompleta

                    runOnUiThread {
                        tvLiveGroupsCount.text = "${remotas.size} pastas"
                        val initialIdx = if (listaCompleta.size > 3) 3 else 0

                        liveGroupAdapter = GroupListAdapter(
                            items = listaCompleta.map { it.categoryId to it.categoryName },
                            initialSelected = initialIdx,
                            countProvider = { id -> obterContagemGrupoLive(id) },
                            onSelect = { id, name -> selecionarGrupoLive(id, name, iniciarCanal = false) }
                        )
                        rvLiveGroups.adapter = liveGroupAdapter

                        overlayGroupAdapter = GroupListAdapter(
                            items = listaCompleta.map { it.categoryId to it.categoryName },
                            initialSelected = initialIdx,
                            countProvider = { id -> obterContagemGrupoLive(id) },
                            onSelect = { id, name -> selecionarGrupoLive(id, name, iniciarCanal = false) }
                        )
                        rvOverlayGroups.adapter = overlayGroupAdapter

                        val initialCat = listaCompleta[initialIdx]
                        selecionarGrupoLive(initialCat.categoryId, initialCat.categoryName, iniciarCanal = true)
                        carregarTodosCanaisEmCache()
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun obterContagemGrupoLive(groupId: String): Int {
        return when (groupId) {
            "FAV_LIVE" -> EagleTvConfig.getFavoriteChannelIds(this).size
            "RECENT_LIVE" -> EagleTvConfig.getRecentChannels(this).size
            "ALL_LIVE" -> allLiveStreamsCache.size
            else -> CategoryCountHelper.getLiveCount(this, groupId)
        }
    }

    private fun carregarTodosCanaisEmCache() {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val streams: List<LiveStream> = gson.fromJson(json, type) ?: emptyList()
                    allLiveStreamsCache.clear()
                    allLiveStreamsCache.addAll(streams)
                    val counts = streams.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
                    CategoryCountHelper.saveLiveCounts(this@EagleTvActivity, counts)
                    val targetName = intent.getStringExtra("TARGET_CHANNEL_NAME")?.trim()
                    val matchedByTarget = if (!targetName.isNullOrBlank()) {
                        val clean = targetName.replace(" ", "").lowercase()
                        streams.firstOrNull { it.name.replace(" ", "").lowercase().contains(clean) }
                            ?: streams.firstOrNull { clean.contains(it.name.replace(" ", "").lowercase()) }
                    } else null
                    runOnUiThread {
                        liveGroupAdapter?.notifyDataSetChanged()
                        overlayGroupAdapter?.notifyDataSetChanged()
                        if (matchedByTarget != null) {
                            intent.removeExtra("TARGET_CHANNEL_NAME")
                            sintonizarCanal(matchedByTarget, 1)
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun selecionarGrupoLive(groupId: String, groupName: String, iniciarCanal: Boolean) {
        currentLiveGroupId = groupId
        currentLiveGroupName = groupName
        tvLiveChannelListHeader.text = groupName.uppercase(Locale.getDefault())
        tvOverlayGroupTitle.text = groupName.uppercase(Locale.getDefault())

        liveGroupAdapter?.selecionarPorId(groupId)
        overlayGroupAdapter?.selecionarPorId(groupId)

        when (groupId) {
            "FAV_LIVE" -> {
                val favIds = EagleTvConfig.getFavoriteChannelIds(this)
                if (allLiveStreamsCache.isNotEmpty()) {
                    val favChannels = allLiveStreamsCache.filter { favIds.contains(it.streamId) }
                    aplicarListaCanaisLive(favChannels, iniciarCanal)
                } else {
                    carregarTodosEFiltrar { todos -> todos.filter { favIds.contains(it.streamId) } }
                }
            }
            "RECENT_LIVE" -> {
                val recentes = EagleTvConfig.getRecentChannels(this)
                aplicarListaCanaisLive(recentes, iniciarCanal)
            }
            "ALL_LIVE" -> {
                if (allLiveStreamsCache.isNotEmpty()) {
                    aplicarListaCanaisLive(allLiveStreamsCache.toList(), iniciarCanal)
                } else {
                    carregarTodosEFiltrar { it }
                }
            }
            else -> {
                val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams&category_id=$groupId"
                val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
                client.newCall(req).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: return
                        try {
                            val type = object : TypeToken<List<LiveStream>>() {}.type
                            val canais: List<LiveStream> = gson.fromJson(json, type) ?: emptyList()
                            runOnUiThread {
                                CategoryCountHelper.saveLiveCounts(this@EagleTvActivity, mapOf(groupId to canais.size))
                                liveGroupAdapter?.notifyDataSetChanged()
                                overlayGroupAdapter?.notifyDataSetChanged()
                                aplicarListaCanaisLive(canais, iniciarCanal)
                            }
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    }

    private fun carregarTodosEFiltrar(filtro: (List<LiveStream>) -> List<LiveStream>) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val streams: List<LiveStream> = gson.fromJson(json, type) ?: emptyList()
                    allLiveStreamsCache.clear()
                    allLiveStreamsCache.addAll(streams)
                    val filtrados = filtro(streams)
                    runOnUiThread {
                        aplicarListaCanaisLive(filtrados, iniciarCanal = false)
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun aplicarListaCanaisLive(canais: List<LiveStream>, iniciarCanal: Boolean) {
        currentGroupChannels = canais
        tvLiveChannelsCount.text = "${canais.size} canais"

        liveChannelAdapter = TiviChannelAdapter(
            list = canais,
            onClick = { canal, num ->
                lastFocusArea = LastFocusArea.LIVE_CHANNELS
                lastLiveChannelPos = currentGroupChannels.indexOf(canal).coerceAtLeast(0)
                hasSavedFocus = true
                if (currentStreamId == canal.streamId) {
                    entrarEmFullscreen()
                } else {
                    sintonizarCanal(canal, num)
                }
            },
            onLongClick = { canal ->
                alternarFavoritoCanal(canal)
            },
            onFocused = { canal ->
                agendarPreviewProgramacaoCanal(canal)
            }
        )
        rvLiveChannels.adapter = liveChannelAdapter

        overlayChannelAdapter = TiviChannelAdapter(
            list = canais,
            onClick = { canal, num ->
                if (currentStreamId != canal.streamId) {
                    sintonizarCanal(canal, num)
                }
                fecharGavetaFullscreen()
                mostrarOsdFullscreen()
            },
            onLongClick = { canal ->
                alternarFavoritoCanal(canal)
            },
            onFocused = {}
        )
        rvOverlayChannels.adapter = overlayChannelAdapter

        if (currentStreamId != -1) {
            liveChannelAdapter?.selecionarCanalPorId(currentStreamId)
            overlayChannelAdapter?.selecionarCanalPorId(currentStreamId)
        }

        if (iniciarCanal && currentStreamId == -1 && canais.isNotEmpty()) {
            val targetName = intent.getStringExtra("TARGET_CHANNEL_NAME")?.trim()
            val byTarget = if (!targetName.isNullOrBlank()) {
                val clean = targetName.replace(" ", "").lowercase()
                canais.firstOrNull { it.name.replace(" ", "").lowercase().contains(clean) }
            } else null

            val lastId = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getInt("last_live_stream_id", -1)
            val inicial = byTarget ?: canais.firstOrNull { it.streamId == lastId } ?: canais[0]
            val idx = canais.indexOfFirst { it.streamId == inicial.streamId }.coerceAtLeast(0)
            sintonizarCanal(inicial, idx + 1)
            focarCanalNaListaPrincipal(inicial.streamId)
        }
    }

    private fun sintonizarCanal(canal: LiveStream, channelNumber: Int) {
        currentPlayingChannel = canal
        currentPlayingNumber = channelNumber
        currentStreamId = canal.streamId

        EagleTvConfig.recordRecentChannel(this, canal)
        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("last_live_stream_id", canal.streamId)
            .apply()

        val extension = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString("stream_type", "MPEGTS") == "HLS") "m3u8" else "ts"
        val streamUrl = "$baseUrl/live/$user/$pass/${canal.streamId}.$extension"

        player?.apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }

        liveChannelAdapter?.selecionarCanalPorId(canal.streamId)
        overlayChannelAdapter?.selecionarCanalPorId(canal.streamId)
        liveGroupAdapter?.notifyDataSetChanged()
        overlayGroupAdapter?.notifyDataSetChanged()

        if (activeSection == WorkspaceSection.LIVE_TV) {
            atualizarPainelTopoCanal(canal)
        }
        carregarEpgCompletoDoCanal(canal)
    }

    private fun focarCanalNaListaPrincipal(streamId: Int) {
        val pos = liveChannelAdapter?.selecionarCanalPorId(streamId) ?: -1
        if (pos >= 0) {
            rvLiveChannels.post {
                rvLiveChannels.scrollToPosition(pos)
                rvLiveChannels.postDelayed({
                    rvLiveChannels.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                }, 70)
            }
        }
    }

    private fun alternarFavoritoCanal(canal: LiveStream) {
        val added = EagleTvConfig.toggleFavoriteChannel(this, canal.streamId)
        val msg = if (added) "★ ${canal.name} adicionado aos Favoritos" else "${canal.name} removido dos Favoritos"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        liveChannelAdapter?.notifyDataSetChanged()
        overlayChannelAdapter?.notifyDataSetChanged()
        liveGroupAdapter?.notifyDataSetChanged()
        overlayGroupAdapter?.notifyDataSetChanged()

        if (currentPlayingChannel?.streamId == canal.streamId && activeSection == WorkspaceSection.LIVE_TV) {
            atualizarPainelTopoCanal(canal)
        }
        if (currentLiveGroupId == "FAV_LIVE") {
            selecionarGrupoLive("FAV_LIVE", "★ FAVORITOS", iniciarCanal = false)
        }
    }

    private fun atualizarPainelTopoCanal(canal: LiveStream) {
        tvTopHeaderTitle.text = "#${String.format(Locale.US, "%03d", currentPlayingNumber)}  ${canal.name}"
        if (!canal.streamIcon.isNullOrBlank()) {
            Glide.with(this).load(canal.streamIcon).into(ivTopHeaderLogo)
        } else {
            ivTopHeaderLogo.setImageResource(R.mipmap.ic_launcher)
        }

        val isFav = EagleTvConfig.isFavoriteChannel(this, canal.streamId)
        tvTopFavBadge.visibility = if (isFav) View.VISIBLE else View.GONE
        tvTopCatchupBadge.visibility = if (temCatchUp(canal)) View.VISIBLE else View.GONE
        tvTopLiveBadge.text = "● EM DIRETO"
        tvTopLiveBadge.visibility = View.VISIBLE
        layoutTopProgressRow.visibility = View.VISIBLE
        tvTopRemoteHints.text = "[OK] Ecrã Inteiro  •  [Long OK] ★ Fav  •  [0-9] Canal"

        val cached = inlineEpgCache[canal.streamId]
        if (cached != null) {
            aplicarEpgNoPainelTopo(cached)
        } else {
            tvTopHeaderSubtitle.text = "A procurar guia de programação..."
            tvTopProgramTime.text = "AO VIVO"
            pbTopProgram.progress = 0
            tvTopProgramPercent.text = ""
            tvTopProgramDesc.text = "Emissão em direto no servidor ${IptvServiceConfig.displayName(IptvServiceConfig.activeServiceId(this))}."
            tvTopNextProgram.text = ""
        }
    }

    private fun aplicarEpgNoPainelTopo(summary: InlineEpgSummary) {
        tvTopHeaderSubtitle.text = summary.currentTitle
        if (summary.startStr.isNotEmpty() && summary.endStr.isNotEmpty()) {
            tvTopProgramTime.text = "${formatProgramTime(summary.startStr)} ➔ ${formatProgramTime(summary.endStr)}"
            pbTopProgram.progress = summary.progressPercent
            tvTopProgramPercent.text = "${summary.progressPercent}%"
        } else {
            tvTopProgramTime.text = "EM DIRETO"
            pbTopProgram.progress = 0
            tvTopProgramPercent.text = ""
        }
        tvTopProgramDesc.text = summary.currentDesc.ifBlank { "Sem descrição adicional para este programa." }
        tvTopNextProgram.text = if (summary.nextTitle.isNotBlank()) {
            "A Seguir: ${summary.nextTitle} (${formatProgramTime(summary.nextStartStr)})"
        } else {
            ""
        }
    }

    private fun agendarPreviewProgramacaoCanal(canal: LiveStream) {
        scheduleFocusRunnable?.let { uiHandler.removeCallbacks(it) }
        val task = Runnable {
            if (previewedScheduleStreamId != canal.streamId) {
                carregarEpgCompletoDoCanal(canal, apenasSchedule = (canal.streamId != currentStreamId))
            }
        }
        scheduleFocusRunnable = task
        uiHandler.postDelayed(task, 240L)
    }

    private fun solicitarInlineEpgSeNecessario(streamId: Int) {
        val existing = inlineEpgCache[streamId]
        if (existing != null && (System.currentTimeMillis() - existing.fetchedAtMillis) < 180_000L) {
            return
        }
        if (pendingInlineEpgRequests.size >= 5 || !pendingInlineEpgRequests.add(streamId)) {
            return
        }

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$streamId&limit=2"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                pendingInlineEpgRequests.remove(streamId)
            }

            override fun onResponse(call: Call, response: Response) {
                pendingInlineEpgRequests.remove(streamId)
                val body = response.body?.string() ?: return
                try {
                    val jsonObj = JSONObject(body)
                    val listings = jsonObj.optJSONArray("epg_listings")
                    if (listings != null && listings.length() > 0) {
                        val cur = listings.getJSONObject(0)
                        val title = decodeSafe(cur.optString("title")).ifBlank { "Emissão em direto" }
                        val desc = decodeSafe(cur.optString("description"))
                        val start = cur.optString("start")
                        val end = cur.optString("end")
                        val prog = calcularPercentagem(start, end)

                        var nextTitle = ""
                        var nextStart = ""
                        if (listings.length() > 1) {
                            val nxt = listings.getJSONObject(1)
                            nextTitle = decodeSafe(nxt.optString("title"))
                            nextStart = nxt.optString("start")
                        }

                        val summary = InlineEpgSummary(title, desc, start, end, nextTitle, nextStart, prog)
                        inlineEpgCache[streamId] = summary

                        runOnUiThread {
                            liveChannelAdapter?.notificarEpgAtualizado(streamId)
                            overlayChannelAdapter?.notificarEpgAtualizado(streamId)
                            if (streamId == currentStreamId && activeSection == WorkspaceSection.LIVE_TV) {
                                aplicarEpgNoPainelTopo(summary)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun carregarEpgCompletoDoCanal(canal: LiveStream, apenasSchedule: Boolean = false) {
        previewedScheduleStreamId = canal.streamId
        tvChannelScheduleHeader.text = "GUIA: ${canal.name.uppercase(Locale.getDefault())}"

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_simple_data_table&stream_id=${canal.streamId}"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val epgData = gson.fromJson(json, EpgResponse::class.java)
                    val allPrograms = epgData?.epgListings ?: emptyList()
                    val now = System.currentTimeMillis()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                    // Encontrar programa atual
                    val currentIdx = allPrograms.indexOfFirst { p ->
                        val s = try { sdf.parse(p.start.orEmpty())?.time } catch (_: Exception) { null }
                        val e = try { sdf.parse(p.end.orEmpty())?.time } catch (_: Exception) { null }
                        s != null && e != null && now in s..e
                    }

                    if (currentIdx >= 0) {
                        val cur = allPrograms[currentIdx]
                        val nxt = allPrograms.getOrNull(currentIdx + 1)
                        val summary = InlineEpgSummary(
                            currentTitle = cur.title.ifBlank { "Emissão em direto" },
                            currentDesc = cur.description,
                            startStr = cur.start.orEmpty(),
                            endStr = cur.end.orEmpty(),
                            nextTitle = nxt?.title.orEmpty(),
                            nextStartStr = nxt?.start.orEmpty(),
                            progressPercent = calcularPercentagem(cur.start.orEmpty(), cur.end.orEmpty())
                        )
                        inlineEpgCache[canal.streamId] = summary
                    }

                    // Mostrar desde 2 programas anteriores (para Catch-Up rápido) até 15 programas seguintes
                    val startSlice = if (currentIdx >= 0) (currentIdx - 2).coerceAtLeast(0) else 0
                    val visiblePrograms = allPrograms.drop(startSlice).take(18)

                    runOnUiThread {
                        liveChannelAdapter?.notificarEpgAtualizado(canal.streamId)
                        overlayChannelAdapter?.notificarEpgAtualizado(canal.streamId)

                        if (!apenasSchedule && canal.streamId == currentStreamId) {
                            inlineEpgCache[canal.streamId]?.let { aplicarEpgNoPainelTopo(it) }
                            if (isFullScreen) atualizarDadosOsdFullscreen(canal)
                        }

                        if (previewedScheduleStreamId == canal.streamId) {
                            if (visiblePrograms.isEmpty()) {
                                tvEmptyChannelSchedule.visibility = View.VISIBLE
                                rvChannelSchedule.adapter = null
                            } else {
                                tvEmptyChannelSchedule.visibility = View.GONE
                                rvChannelSchedule.adapter = ChannelScheduleAdapter(canal, visiblePrograms)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun temCatchUp(canal: LiveStream): Boolean {
        val archiveVal = when (val raw = canal.tvArchive) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> raw?.toString() == "1" || raw?.toString()?.equals("true", true) == true
        }
        val durationVal = when (val raw = canal.tvArchiveDuration) {
            is Number -> raw.toInt()
            else -> raw?.toString()?.toIntOrNull() ?: 0
        }
        return archiveVal || durationVal > 0
    }

    private fun parseProgramDate(raw: String?, timestamp: String?): Date? {
        if (!raw.isNullOrBlank()) {
            val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss")
            for (pattern in formats) {
                try {
                    return SimpleDateFormat(pattern, Locale.getDefault()).parse(raw)
                } catch (_: Exception) {}
            }
        }
        val seconds = timestamp?.toLongOrNull() ?: return null
        return Date(if (seconds > 100_000_000_000L) seconds else seconds * 1000L)
    }

    private fun reproduzirProgramaCatchUp(canal: LiveStream, program: EpgProgram) {
        val startDate = parseProgramDate(program.start, program.startTimestamp)
        val endDate = parseProgramDate(program.end, program.stopTimestamp)
        if (startDate == null || endDate == null || endDate.time <= startDate.time) {
            Toast.makeText(this, "Horário indisponível para Catch-Up", Toast.LENGTH_SHORT).show()
            return
        }
        val durationMinutes = ((endDate.time - startDate.time + 59_999L) / 60_000L).coerceAtLeast(1L)
        val startForUrl = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(startDate)
        val extension = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString("stream_type", "MPEGTS") == "HLS") "m3u8" else "ts"
        val url = "$baseUrl/timeshift/$user/$pass/$durationMinutes/$startForUrl/${canal.streamId}.$extension"

        startActivity(Intent(this, VodPlayerActivity::class.java).apply {
            putExtra("STREAM_URL", url)
            putExtra("STREAM_TITLE", "${canal.name} • ${program.title}")
            putExtra("STREAM_ID", canal.streamId)
            putExtra("IS_SERIES", false)
        })
    }

    // =========================================================================
    // SECÇÃO 2: FILMES (VOD) E SÉRIES (ESTILO MYTVONLINE3)
    // =========================================================================

    private fun configurarPesquisaEOrdenacao() {
        etCatalogSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                when (activeSection) {
                    WorkspaceSection.MOVIES -> atualizarGrelhaFilmes()
                    WorkspaceSection.SERIES -> atualizarGrelhaSeries()
                    WorkspaceSection.CATCHUP -> if (!catchupViewingPrograms) atualizarListaCanaisCatchUp()
                    else -> {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnCatalogSort.setOnClickListener {
            val opcoes = arrayOf("Padrão", "Nome (A-Z)", "Mais Recentes", "Melhor Avaliação")
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ordenar Filmes")
                .setSingleChoiceItems(opcoes, currentVodSortMode) { dialog, which ->
                    currentVodSortMode = which
                    btnCatalogSort.text = "Ordenar: ${opcoes[which]}"
                    atualizarGrelhaFilmes()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun iniciarSecaoFilmes() {
        etCatalogSearch.setText("")
        if (allVodCategories.isNotEmpty()) {
            montarCategoriasFilmes(allVodCategories)
            return
        }
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<VodCategory>>() {}.type
                    val remotas: List<VodCategory> = gson.fromJson(json, type) ?: emptyList()
                    val completas = mutableListOf<VodCategory>()
                    completas.add(VodCategory("FAVORITES", "★ FAVORITOS", 0))
                    completas.add(VodCategory("RESUME", "▶ CONTINUAR A VER", 0))
                    completas.add(VodCategory("RECENT", "🕒 ADICIONADOS RECENTEMENTE", 0))
                    completas.addAll(remotas)
                    allVodCategories = completas
                    runOnUiThread {
                        if (activeSection == WorkspaceSection.MOVIES) {
                            montarCategoriasFilmes(completas)
                        }
                    }
                    carregarTodosFilmesEmCache()
                } catch (_: Exception) {}
            }
        })
    }

    private fun montarCategoriasFilmes(categorias: List<VodCategory>) {
        val initialIdx = if (categorias.size > 3) 3 else 0
        catalogGroupAdapter = GroupListAdapter(
            items = categorias.map { it.categoryId to it.categoryName },
            initialSelected = initialIdx,
            countProvider = { id ->
                when (id) {
                    "FAVORITES" -> getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
                        .getStringSet("fav_ids", emptySet())?.size ?: 0
                    "RESUME" -> {
                        val resumePrefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)
                        resumePrefs.all.keys.count { it.startsWith("pos_") && (resumePrefs.all[it] as? Long ?: 0L) > 10000L }
                    }
                    "RECENT" -> minOf(40, allMoviesCache.size)
                    else -> CategoryCountHelper.getVodCount(this, id)
                }
            },
            onSelect = { id, _ -> selecionarCategoriaFilmes(id) }
        )
        rvCatalogGroups.adapter = catalogGroupAdapter
        if (categorias.isNotEmpty()) {
            selecionarCategoriaFilmes(categorias[initialIdx].categoryId)
        }
    }

    private fun carregarTodosFilmesEmCache(onDone: (() -> Unit)? = null) {
        if (allMoviesCache.isNotEmpty()) {
            onDone?.invoke()
            return
        }
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_streams"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<VodStream>>() {}.type
                    val filmes: List<VodStream> = gson.fromJson(json, type) ?: emptyList()
                    allMoviesCache.clear()
                    allMoviesCache.addAll(filmes)
                    val counts = filmes.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
                    CategoryCountHelper.saveVodCounts(this@EagleTvActivity, counts)
                    runOnUiThread {
                        catalogGroupAdapter?.notifyDataSetChanged()
                        onDone?.invoke()
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun selecionarCategoriaFilmes(catId: String) {
        currentVodCategoryId = catId
        catalogGroupAdapter?.selecionarPorId(catId)

        when (catId) {
            "FAVORITES" -> {
                carregarTodosFilmesEmCache {
                    val favIds = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
                        .getStringSet("fav_ids", emptySet()) ?: emptySet()
                    currentVodCategoryMovies = allMoviesCache.filter { favIds.contains(it.streamId.toString()) }
                    atualizarGrelhaFilmes()
                }
            }
            "RESUME" -> {
                carregarTodosFilmesEmCache {
                    val resumePrefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)
                    val ids = resumePrefs.all.keys
                        .filter { it.startsWith("pos_") && (resumePrefs.all[it] as? Long ?: 0L) > 10000L }
                        .map { it.removePrefix("pos_") }
                        .toSet()
                    currentVodCategoryMovies = allMoviesCache.filter { ids.contains(it.streamId.toString()) }
                    atualizarGrelhaFilmes()
                }
            }
            "RECENT" -> {
                carregarTodosFilmesEmCache {
                    currentVodCategoryMovies = allMoviesCache.sortedByDescending { it.streamId }.take(40)
                    atualizarGrelhaFilmes()
                }
            }
            else -> {
                val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_streams&category_id=$catId"
                val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
                client.newCall(req).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: return
                        try {
                            val type = object : TypeToken<List<VodStream>>() {}.type
                            val filmes: List<VodStream> = gson.fromJson(json, type) ?: emptyList()
                            runOnUiThread {
                                currentVodCategoryMovies = filmes
                                CategoryCountHelper.saveVodCounts(this@EagleTvActivity, mapOf(catId to filmes.size))
                                catalogGroupAdapter?.notifyDataSetChanged()
                                atualizarGrelhaFilmes()
                            }
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    }

    private fun atualizarGrelhaFilmes() {
        val q = etCatalogSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        var filtrados = if (q.isEmpty()) currentVodCategoryMovies else currentVodCategoryMovies.filter {
            it.name.lowercase().contains(q)
        }
        filtrados = when (currentVodSortMode) {
            1 -> filtrados.sortedBy { it.name.lowercase() }
            2 -> filtrados.sortedByDescending { it.streamId }
            3 -> filtrados.sortedByDescending { it.rating_5based ?: it.rating?.toDoubleOrNull() ?: 0.0 }
            else -> filtrados
        }

        val items = filtrados.map { CatalogPosterItem.Movie(it) }
        mostrarItensNoCatalogo(items)
    }

    private fun iniciarSecaoSeries() {
        etCatalogSearch.setText("")
        if (allSeriesCategories.isNotEmpty()) {
            montarCategoriasSeries(allSeriesCategories)
            return
        }
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<SeriesCategory>>() {}.type
                    val remotas: List<SeriesCategory> = gson.fromJson(json, type) ?: emptyList()
                    val completas = mutableListOf<SeriesCategory>()
                    completas.add(SeriesCategory("FAVORITES", "★ FAVORITOS"))
                    completas.add(SeriesCategory("RECENT", "🕒 ADICIONADAS RECENTEMENTE"))
                    completas.addAll(remotas)
                    allSeriesCategories = completas
                    runOnUiThread {
                        if (activeSection == WorkspaceSection.SERIES) {
                            montarCategoriasSeries(completas)
                        }
                    }
                    carregarTodasSeriesEmCache()
                } catch (_: Exception) {}
            }
        })
    }

    private fun montarCategoriasSeries(categorias: List<SeriesCategory>) {
        val initialIdx = if (categorias.size > 2) 2 else 0
        catalogGroupAdapter = GroupListAdapter(
            items = categorias.map { it.categoryId to it.categoryName },
            initialSelected = initialIdx,
            countProvider = { id ->
                when (id) {
                    "FAVORITES" -> getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
                        .getStringSet("fav_ids", emptySet())?.size ?: 0
                    "RECENT" -> minOf(40, allSeriesCache.size)
                    else -> CategoryCountHelper.getSeriesCount(this, id)
                }
            },
            onSelect = { id, _ -> selecionarCategoriaSeries(id) }
        )
        rvCatalogGroups.adapter = catalogGroupAdapter
        if (categorias.isNotEmpty()) {
            selecionarCategoriaSeries(categorias[initialIdx].categoryId)
        }
    }

    private fun carregarTodasSeriesEmCache(onDone: (() -> Unit)? = null) {
        if (allSeriesCache.isNotEmpty()) {
            onDone?.invoke()
            return
        }
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<SeriesItem>>() {}.type
                    val series: List<SeriesItem> = gson.fromJson(json, type) ?: emptyList()
                    allSeriesCache.clear()
                    allSeriesCache.addAll(series)
                    val counts = series.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
                    CategoryCountHelper.saveSeriesCounts(this@EagleTvActivity, counts)
                    runOnUiThread {
                        catalogGroupAdapter?.notifyDataSetChanged()
                        onDone?.invoke()
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun selecionarCategoriaSeries(catId: String) {
        currentSeriesCategoryId = catId
        catalogGroupAdapter?.selecionarPorId(catId)

        when (catId) {
            "FAVORITES" -> {
                carregarTodasSeriesEmCache {
                    val favIds = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
                        .getStringSet("fav_ids", emptySet()) ?: emptySet()
                    currentSeriesCategoryItems = allSeriesCache.filter { favIds.contains(it.seriesId.toString()) }
                    atualizarGrelhaSeries()
                }
            }
            "RECENT" -> {
                carregarTodasSeriesEmCache {
                    currentSeriesCategoryItems = allSeriesCache.sortedByDescending { it.seriesId }.take(40)
                    atualizarGrelhaSeries()
                }
            }
            else -> {
                val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series&category_id=$catId"
                val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
                client.newCall(req).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: return
                        try {
                            val type = object : TypeToken<List<SeriesItem>>() {}.type
                            val series: List<SeriesItem> = gson.fromJson(json, type) ?: emptyList()
                            runOnUiThread {
                                currentSeriesCategoryItems = series
                                CategoryCountHelper.saveSeriesCounts(this@EagleTvActivity, mapOf(catId to series.size))
                                catalogGroupAdapter?.notifyDataSetChanged()
                                atualizarGrelhaSeries()
                            }
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    }

    private fun atualizarGrelhaSeries() {
        val q = etCatalogSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtradas = if (q.isEmpty()) currentSeriesCategoryItems else currentSeriesCategoryItems.filter {
            it.name.lowercase().contains(q)
        }
        val items = filtradas.map { CatalogPosterItem.Series(it) }
        mostrarItensNoCatalogo(items)
    }

    private fun mostrarItensNoCatalogo(items: List<CatalogPosterItem>) {
        if (items.isEmpty()) {
            tvCatalogEmpty.text = "Nenhum conteúdo encontrado nesta seleção."
            tvCatalogEmpty.visibility = View.VISIBLE
        } else {
            tvCatalogEmpty.visibility = View.GONE
        }

        catalogPosterAdapter = CatalogPosterAdapter(
            items = items,
            onFocused = { item -> atualizarHeroPreviewCatalogo(item) },
            onClick = { item ->
                when (item) {
                    is CatalogPosterItem.Movie -> {
                        startActivity(Intent(this, MovieDetailActivity::class.java).apply {
                            putExtra("STREAM_ID", item.vod.streamId)
                            putExtra("STREAM_TITLE", item.vod.name)
                            putExtra("STREAM_ICON", item.vod.streamIcon)
                            putExtra("CONTAINER_EXT", item.vod.containerExtension ?: "mp4")
                        })
                    }
                    is CatalogPosterItem.Series -> {
                        startActivity(Intent(this, SeriesDetailActivity::class.java).apply {
                            putExtra("SERIES_ID", item.series.seriesId)
                            putExtra("SERIES_NAME", item.series.name)
                            putExtra("SERIES_COVER", item.series.cover)
                            putExtra("SERIES_PLOT", item.series.plot)
                        })
                    }
                    is CatalogPosterItem.Channel -> {
                        sintonizarCanal(item.channel, 1)
                        selecionarSecao(WorkspaceSection.LIVE_TV)
                    }
                }
            },
            onLongClick = { item ->
                when (item) {
                    is CatalogPosterItem.Movie -> {
                        val prefs = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
                        val set = prefs.getStringSet("fav_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                        val key = item.vod.streamId.toString()
                        val added = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
                        prefs.edit().putStringSet("fav_ids", set).apply()
                        Toast.makeText(this, if (added) "★ ${item.vod.name} nos Favoritos" else "Removido dos Favoritos", Toast.LENGTH_SHORT).show()
                        catalogPosterAdapter?.notifyDataSetChanged()
                        catalogGroupAdapter?.notifyDataSetChanged()
                    }
                    is CatalogPosterItem.Series -> {
                        val prefs = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
                        val set = prefs.getStringSet("fav_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                        val key = item.series.seriesId.toString()
                        val added = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
                        prefs.edit().putStringSet("fav_ids", set).apply()
                        Toast.makeText(this, if (added) "★ ${item.series.name} nos Favoritos" else "Removida dos Favoritos", Toast.LENGTH_SHORT).show()
                        catalogPosterAdapter?.notifyDataSetChanged()
                        catalogGroupAdapter?.notifyDataSetChanged()
                    }
                    is CatalogPosterItem.Channel -> {
                        alternarFavoritoCanal(item.channel)
                        catalogPosterAdapter?.notifyDataSetChanged()
                    }
                }
            }
        )
        rvCatalogGrid.adapter = catalogPosterAdapter
    }

    private fun atualizarHeroPreviewCatalogo(item: CatalogPosterItem) {
        layoutTopProgressRow.visibility = View.GONE
        tvTopCatchupBadge.visibility = View.GONE

        when (item) {
            is CatalogPosterItem.Movie -> {
                val f = item.vod
                tvTopHeaderTitle.text = f.name
                val ratingStr = f.rating?.takeIf { it.isNotBlank() && it != "0" } ?: f.rating_5based?.toString()
                tvTopHeaderSubtitle.text = if (!ratingStr.isNullOrBlank()) "🎬 FILME VOD  •  ★ $ratingStr" else "🎬 FILME VOD"
                tvTopLiveBadge.text = (f.containerExtension ?: "MP4").uppercase(Locale.getDefault())
                tvTopRemoteHints.text = "[OK] Abrir Detalhes / Ver  •  [Long OK] ★ Favorito"

                val isFav = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
                    .getStringSet("fav_ids", emptySet())?.contains(f.streamId.toString()) == true
                tvTopFavBadge.visibility = if (isFav) View.VISIBLE else View.GONE

                if (!f.streamIcon.isNullOrBlank()) {
                    Glide.with(this).load(f.streamIcon).into(ivTopHeaderLogo)
                } else {
                    ivTopHeaderLogo.setImageResource(R.mipmap.ic_launcher)
                }

                val cachedInfo = vodPlotCache[f.streamId]
                if (cachedInfo != null) {
                    tvTopProgramDesc.text = (cachedInfo.plot ?: cachedInfo.description).orEmpty()
                        .ifBlank { "Carrega OK para abrir a ficha completa e reproduzir este filme." }
                    tvTopNextProgram.text = listOfNotNull(
                        cachedInfo.genre?.takeIf { it.isNotBlank() },
                        cachedInfo.releaseDate?.takeIf { it.isNotBlank() },
                        cachedInfo.duration?.takeIf { it.isNotBlank() }
                    ).joinToString("  •  ")
                } else {
                    tvTopProgramDesc.text = "Carrega OK para ver a sinopse completa, elenco e reproduzir o filme."
                    tvTopNextProgram.text = ""
                    carregarInfoFilmeEmBackground(f.streamId)
                }
            }
            is CatalogPosterItem.Series -> {
                val s = item.series
                tvTopHeaderTitle.text = s.name
                val ratingStr = s.rating?.takeIf { it.isNotBlank() && it != "0" }
                tvTopHeaderSubtitle.text = if (!ratingStr.isNullOrBlank()) "🎞️ SÉRIE TV  •  ★ $ratingStr" else "🎞️ SÉRIE TV"
                tvTopLiveBadge.text = "SÉRIE"
                tvTopRemoteHints.text = "[OK] Ver Temporadas & Episódios  •  [Long OK] ★ Favorito"

                val isFav = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
                    .getStringSet("fav_ids", emptySet())?.contains(s.seriesId.toString()) == true
                tvTopFavBadge.visibility = if (isFav) View.VISIBLE else View.GONE

                if (!s.cover.isNullOrBlank()) {
                    Glide.with(this).load(s.cover).into(ivTopHeaderLogo)
                } else {
                    ivTopHeaderLogo.setImageResource(R.mipmap.ic_launcher)
                }

                tvTopProgramDesc.text = s.plot?.takeIf { it.isNotBlank() }
                    ?: "Carrega OK para escolher a temporada e os episódios desta série."
                tvTopNextProgram.text = s.releaseDate?.takeIf { it.isNotBlank() }?.let { "Lançamento: $it" }.orEmpty()
            }
            is CatalogPosterItem.Channel -> {
                atualizarPainelTopoCanal(item.channel)
            }
        }
    }

    private fun carregarInfoFilmeEmBackground(streamId: Int) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_info&vod_id=$streamId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val infoResp = gson.fromJson(body, VodInfoResponse::class.java)
                    val info = infoResp?.info ?: return
                    vodPlotCache[streamId] = info
                } catch (_: Exception) {}
            }
        })
    }

    // =========================================================================
    // DIÁLOGOS DE SERVIDOR & SAÍDA
    // =========================================================================

    // =========================================================================
    // SECÇÕES INTEGRADAS: JOGOS DO DIA, JORNAIS E GRAVAÇÕES (CATCH-UP)
    // =========================================================================

    private val allMatches = mutableListOf<SportsMatch>()
    private var selectedLeague = "🌟 Todos"
    private val leaguesList = listOf(
        "🌟 Todos", "🇵🇹 Liga Portugal", "🇬🇧 Premier League", "🇪🇸 La Liga",
        "🇪🇺 Champions League", "🇪🇺 Liga Europa", "🇮🇹 Serie A", "🇩🇪 Bundesliga"
    )

    private val allNewspapers = mutableListOf<Newspaper>()
    private var selectedNewspaperCategory = "🌟 Todos"
    private val newspaperCategories = listOf(
        "🌟 Todos", "⚽ Desporto", "📰 Generalistas", "✨ Revistas", "🌍 Internacionais"
    )

    private var catchupRequestId = 0

    private fun configurarCatalogoParaSecao(grelha: Boolean, pesquisa: Boolean) {
        rvCatalogGrid.layoutManager = if (grelha) GridLayoutManager(this, 5) else LinearLayoutManager(this)
        (etCatalogSearch.parent as? View)?.visibility = if (pesquisa) View.VISIBLE else View.GONE
    }

    private fun definirTopoInformativo(titulo: String, subtitulo: String, descricao: String) {
        tvTopHeaderTitle.text = titulo
        tvTopHeaderSubtitle.text = subtitulo
        ivTopHeaderLogo.setImageResource(R.mipmap.ic_launcher)
        tvTopFavBadge.visibility = View.GONE
        tvTopCatchupBadge.visibility = View.GONE
        tvTopLiveBadge.text = "EAGLE TV"
        tvTopLiveBadge.visibility = View.VISIBLE
        layoutTopProgressRow.visibility = View.GONE
        tvTopProgramDesc.text = descricao
        tvTopNextProgram.text = ""
        tvTopRemoteHints.text = "[OK] Abrir  •  [◄] Voltar à lista"
    }

    private fun garantirCanaisEmCache(onDone: () -> Unit) {
        if (allLiveStreamsCache.isNotEmpty()) {
            onDone()
            return
        }
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvCatalogEmpty.text = "Não foi possível carregar os canais."; tvCatalogEmpty.visibility = View.VISIBLE }
            }
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val streams: List<LiveStream> = gson.fromJson(json, type) ?: emptyList()
                    allLiveStreamsCache.clear()
                    allLiveStreamsCache.addAll(streams)
                    runOnUiThread { onDone() }
                } catch (_: Exception) {}
            }
        })
    }

    private fun abrirCanalPorNome(nome: String) {
        val clean = nome.replace(" ", "").lowercase()
        val canal = allLiveStreamsCache.firstOrNull { it.name.replace(" ", "").lowercase().contains(clean) }
            ?: allLiveStreamsCache.firstOrNull {
                val n = it.name.replace(" ", "").lowercase()
                n.length > 2 && clean.contains(n)
            }
        if (canal == null) {
            Toast.makeText(this, "Canal \"$nome\" não encontrado na tua lista.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "A sintonizar ${canal.name}...", Toast.LENGTH_SHORT).show()
        sintonizarCanal(canal, 1)
        selecionarSecao(WorkspaceSection.LIVE_TV)
    }

    // --- JOGOS DO DIA ---
    private fun iniciarSecaoJogos() {
        selectedLeague = leaguesList[0]
        catalogGroupAdapter = GroupListAdapter(
            items = leaguesList.map { it to it },
            initialSelected = 0,
            countProvider = { 0 },
            onSelect = { id, _ ->
                selectedLeague = id
                mostrarJogos()
            }
        )
        rvCatalogGroups.adapter = catalogGroupAdapter
        garantirCanaisEmCache { }

        try {
            assets.open("agenda_jogos.json").bufferedReader().use { reader ->
                val feed = gson.fromJson(reader, SportsAgendaFeed::class.java)
                if (feed != null && feed.jogos.isNotEmpty()) {
                    allMatches.clear()
                    allMatches.addAll(feed.jogos)
                }
            }
        } catch (_: Exception) {}
        mostrarJogos()

        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/onbesttv/eagle-app/main/data/agenda_jogos.json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) return
                    try {
                        val feed = gson.fromJson(response.body?.string().orEmpty(), SportsAgendaFeed::class.java)
                        if (feed != null && feed.jogos.isNotEmpty()) {
                            runOnUiThread {
                                allMatches.clear()
                                allMatches.addAll(feed.jogos)
                                if (activeSection == WorkspaceSection.SPORTS) mostrarJogos()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun mostrarJogos() {
        val lista = if (selectedLeague == leaguesList[0]) {
            allMatches.toList()
        } else {
            val sel = selectedLeague.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
            allMatches.filter {
                val liga = it.liga.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
                liga.contains(sel) || sel.contains(liga)
            }
        }
        tvCatalogEmpty.text = "Sem jogos nesta competição."
        tvCatalogEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        rvCatalogGrid.adapter = SportsMatchAdapter(lista) { match ->
            if (match.canal.isNotBlank()) {
                garantirCanaisEmCache { abrirCanalPorNome(match.canal) }
            } else {
                Toast.makeText(this, "${match.equipaCasa} vs ${match.equipaFora}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- JORNAIS ---
    private fun iniciarSecaoJornais() {
        selectedNewspaperCategory = newspaperCategories[0]
        catalogGroupAdapter = GroupListAdapter(
            items = newspaperCategories.map { it to it },
            initialSelected = 0,
            countProvider = { 0 },
            onSelect = { id, _ ->
                selectedNewspaperCategory = id
                mostrarJornais()
            }
        )
        rvCatalogGroups.adapter = catalogGroupAdapter

        try {
            assets.open("jornais.json").bufferedReader().use { reader ->
                val feed = gson.fromJson(reader, NewspaperFeed::class.java)
                if (feed != null && feed.jornais.isNotEmpty()) {
                    allNewspapers.clear()
                    allNewspapers.addAll(feed.jornais)
                }
            }
        } catch (_: Exception) {}
        mostrarJornais()

        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/onbesttv/eagle-app/main/data/jornais.json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) return
                    try {
                        val feed = gson.fromJson(response.body?.string().orEmpty(), NewspaperFeed::class.java)
                        if (feed != null && feed.jornais.isNotEmpty()) {
                            runOnUiThread {
                                allNewspapers.clear()
                                allNewspapers.addAll(feed.jornais)
                                if (activeSection == WorkspaceSection.NEWSPAPERS) mostrarJornais()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun mostrarJornais() {
        val lista = if (selectedNewspaperCategory == newspaperCategories[0]) {
            allNewspapers.toList()
        } else {
            val sel = selectedNewspaperCategory.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
            allNewspapers.filter {
                val cat = it.categoria.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
                cat.contains(sel) || sel.contains(cat)
            }
        }
        tvCatalogEmpty.text = "Sem jornais nesta categoria."
        tvCatalogEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        rvCatalogGrid.adapter = NewspaperAdapter(lista) { jornal ->
            if (jornal.pdfUrl.isNotBlank()) {
                startActivity(Intent(this, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_PDF_URL, jornal.pdfUrl)
                    putExtra(PdfViewerActivity.EXTRA_NEWSPAPER_TITLE, "${jornal.nome} (${jornal.data})")
                })
            } else {
                Toast.makeText(this, "A edição de ${jornal.nome} está a ser carregada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- GRAVAÇÕES / CATCH-UP COM ESCOLHA DE DATA ---
    private var catchupDaysList = listOf<Pair<String, String>>()
    private var selectedCatchUpDateKey = ""
    private var selectedCatchUpDateLabel = ""
    private var catchupAllChannels = listOf<LiveStream>()
    private var catchupFilteredChannels = listOf<LiveStream>()
    private var catchupViewingPrograms = false
    private var currentCatchUpChannel: LiveStream? = null
    private var lastCatchUpChannelPos = 0

    private fun iniciarSecaoCatchUp() {
        catchupViewingPrograms = false
        currentCatchUpChannel = null
        tvCatalogEmpty.text = "A carregar dias e canais com gravação..."
        tvCatalogEmpty.visibility = View.VISIBLE
        rvCatalogGrid.adapter = null
        rvCatalogGroups.adapter = null

        // 1. Gerar os últimos 8 dias (Hoje, Ontem, Anteontem, etc.)
        val today = Calendar.getInstance()
        val dias = (0 until 8).map { offset ->
            val cal = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val dateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            val label = when (offset) {
                0 -> "Hoje (${SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time)})"
                1 -> "Ontem (${SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time)})"
                2 -> "Anteontem (${SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time)})"
                else -> {
                    val weekDay = SimpleDateFormat("EEEE, dd/MM", Locale("pt", "PT")).format(cal.time)
                    weekDay.replaceFirstChar { it.uppercase() }
                }
            }
            dateKey to label
        }
        catchupDaysList = dias
        selectedCatchUpDateKey = dias[0].first
        selectedCatchUpDateLabel = dias[0].second

        tvCatalogGroupsHeader.text = "ESCOLHER DATA"
        catalogGroupAdapter = GroupListAdapter(
            items = dias,
            initialSelected = 0,
            countProvider = { 0 },
            onSelect = { id, label ->
                selectedCatchUpDateKey = id
                selectedCatchUpDateLabel = label
                val ch = currentCatchUpChannel
                if (catchupViewingPrograms && ch != null) {
                    abrirProgramasDoCanalCatchUp(ch, selectedCatchUpDateKey, selectedCatchUpDateLabel)
                } else {
                    atualizarListaCanaisCatchUp()
                }
            }
        )
        rvCatalogGroups.adapter = catalogGroupAdapter

        garantirCanaisEmCache {
            if (activeSection != WorkspaceSection.CATCHUP) return@garantirCanaisEmCache
            catchupAllChannels = allLiveStreamsCache.filter { temCatchUp(it) }
            atualizarListaCanaisCatchUp()
        }
    }

    private fun atualizarListaCanaisCatchUp() {
        catchupViewingPrograms = false
        if (catchupAllChannels.isEmpty()) {
            tvCatalogEmpty.text = "O serviço não disponibiliza canais com gravação (Catch-Up)."
            tvCatalogEmpty.visibility = View.VISIBLE
            rvCatalogGrid.adapter = null
            return
        }

        val q = etCatalogSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        catchupFilteredChannels = if (q.isBlank()) {
            catchupAllChannels
        } else {
            catchupAllChannels.filter { it.name.lowercase().contains(q) }
        }

        definirTopoInformativo(
            "⏪ Gravações • $selectedCatchUpDateLabel",
            "${catchupFilteredChannels.size} canais disponíveis",
            "Escolhe o canal para ver os programas gravados nesta data."
        )

        if (catchupFilteredChannels.isEmpty()) {
            tvCatalogEmpty.text = "Nenhum canal encontrado para a pesquisa '$q'."
            tvCatalogEmpty.visibility = View.VISIBLE
            rvCatalogGrid.adapter = null
        } else {
            tvCatalogEmpty.visibility = View.GONE
            rvCatalogGrid.adapter = CatchUpChannelAdapter(catchupFilteredChannels, selectedCatchUpDateLabel) { canal ->
                lastCatchUpChannelPos = catchupFilteredChannels.indexOf(canal).coerceAtLeast(0)
                abrirProgramasDoCanalCatchUp(canal, selectedCatchUpDateKey, selectedCatchUpDateLabel)
            }
        }
    }

    private fun abrirProgramasDoCanalCatchUp(canal: LiveStream, dateKey: String, dateLabel: String) {
        val requestId = ++catchupRequestId
        catchupViewingPrograms = true
        currentCatchUpChannel = canal
        tvCatalogEmpty.text = "A carregar gravações de ${canal.name} ($dateLabel)..."
        tvCatalogEmpty.visibility = View.VISIBLE
        rvCatalogGrid.adapter = null

        definirTopoInformativo(
            "⏪ ${canal.name}",
            "Gravações de $dateLabel",
            "Pressiona no programa que queres rever. Pressiona VOLTAR para escolher outro canal."
        )

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_simple_data_table&stream_id=${canal.streamId}"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    if (requestId == catchupRequestId && activeSection == WorkspaceSection.CATCHUP) {
                        tvCatalogEmpty.text = "Não foi possível carregar a programação de ${canal.name}."
                        tvCatalogEmpty.visibility = View.VISIBLE
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val epg = gson.fromJson(json, EpgResponse::class.java)
                    val now = System.currentTimeMillis()
                    val matching = (epg?.epgListings ?: emptyList()).filter { program ->
                        val start = parseProgramDate(program.start, program.startTimestamp)
                        val end = parseProgramDate(program.end, program.stopTimestamp)
                        start != null && end != null &&
                                SimpleDateFormat("yyyyMMdd", Locale.US).format(start) == dateKey &&
                                end.time <= now
                    }.sortedByDescending { parseProgramDate(it.start, it.startTimestamp)?.time ?: 0L }

                    runOnUiThread {
                        if (requestId != catchupRequestId || activeSection != WorkspaceSection.CATCHUP) return@runOnUiThread
                        if (matching.isEmpty()) {
                            tvCatalogEmpty.text = "Sem programas gravados para ${canal.name} em $dateLabel."
                            tvCatalogEmpty.visibility = View.VISIBLE
                        } else {
                            tvCatalogEmpty.visibility = View.GONE
                            rvCatalogGrid.adapter = ChannelScheduleAdapter(canal, matching)
                            rvCatalogGrid.post {
                                focarRecyclerViewPosicao(rvCatalogGrid, 0)
                            }
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (requestId == catchupRequestId && activeSection == WorkspaceSection.CATCHUP) {
                            tvCatalogEmpty.text = "Catch-Up indisponível para este canal nesta data."
                            tvCatalogEmpty.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })
    }

    private fun voltarParaListaCanaisCatchUp() {
        catchupViewingPrograms = false
        atualizarListaCanaisCatchUp()
        rvCatalogGrid.post {
            focarRecyclerViewPosicao(rvCatalogGrid, lastCatchUpChannelPos)
        }
    }

    inner class CatchUpChannelAdapter(
        private val list: List<LiveStream>,
        private val dateLabel: String,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<CatchUpChannelAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvNum: TextView = v.findViewById(R.id.tvChannelNumber)
            val ivLogo: ImageView = v.findViewById(R.id.ivChannelLogo)
            val tvName: TextView = v.findViewById(R.id.tvChannelName)
            val tvCatchup: TextView = v.findViewById(R.id.tvCatchupBadge)
            val tvSub: TextView = v.findViewById(R.id.tvInlineEpgTitle)
            val tvPlaying: TextView = v.findViewById(R.id.tvPlayingBadge)
            val tvFav: TextView = v.findViewById(R.id.tvFavStar)
            val pbEpg: ProgressBar = v.findViewById(R.id.pbInlineEpg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_channel, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val canal = list[position]
            holder.tvNum.text = (position + 1).toString()
            holder.tvName.text = canal.name
            holder.tvCatchup.visibility = View.VISIBLE
            holder.tvCatchup.text = "⏪ GRAVAÇÃO"
            holder.tvSub.visibility = View.VISIBLE
            holder.tvSub.text = "Carrega para ver gravações de $dateLabel"
            holder.tvPlaying.visibility = View.GONE
            holder.tvFav.visibility = View.GONE
            holder.pbEpg.visibility = View.GONE

            if (!canal.streamIcon.isNullOrBlank()) {
                Glide.with(holder.itemView.context).load(canal.streamIcon).dontAnimate().into(holder.ivLogo)
            } else {
                holder.ivLogo.setImageResource(R.mipmap.ic_launcher)
            }

            val updateStyle: (Boolean) -> Unit = { hasFocus ->
                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(9).toFloat()
                    if (hasFocus) {
                        setColor(0xFF1A365D.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    } else {
                        setColor(0xFF0B1728.toInt())
                        setStroke(dp(1), 0x3338BDF8)
                    }
                }
                holder.tvNum.setTextColor(if (hasFocus) 0xFF00E5FF.toInt() else 0xFF64748B.toInt())
            }
            updateStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                updateStyle(hasFocus)
                if (hasFocus) {
                    lastFocusArea = LastFocusArea.CATALOG_GRID
                    lastCatalogGridPos = holder.bindingAdapterPosition
                    hasSavedFocus = true
                }
            }
            holder.itemView.setOnClickListener {
                lastFocusArea = LastFocusArea.CATALOG_GRID
                lastCatalogGridPos = holder.bindingAdapterPosition
                hasSavedFocus = true
                onClick(canal)
            }
        }
    }

    inner class SportsMatchAdapter(
        private val list: List<SportsMatch>,
        private val onClick: (SportsMatch) -> Unit
    ) : RecyclerView.Adapter<SportsMatchAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDay: TextView = view.findViewById(R.id.tvMatchDay)
            val tvTime: TextView = view.findViewById(R.id.tvMatchTime)
            val tvLeague: TextView = view.findViewById(R.id.tvMatchLeague)
            val tvHome: TextView = view.findViewById(R.id.tvHomeTeam)
            val tvAway: TextView = view.findViewById(R.id.tvAwayTeam)
            val tvChannel: TextView = view.findViewById(R.id.tvMatchChannel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sports_match, parent, false))
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val m = list[position]
            holder.tvDay.text = m.data
            holder.tvTime.text = m.hora
            holder.tvLeague.text = m.liga
            holder.tvHome.text = m.equipaCasa
            holder.tvAway.text = m.equipaFora
            holder.tvChannel.text = if (m.canal.isNotBlank()) m.canal else "A anunciar"
            holder.tvDay.setTextColor(if (m.destaque) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    lastFocusArea = LastFocusArea.CATALOG_GRID
                    lastCatalogGridPos = holder.bindingAdapterPosition
                    hasSavedFocus = true
                }
            }
            holder.itemView.setOnClickListener {
                lastFocusArea = LastFocusArea.CATALOG_GRID
                lastCatalogGridPos = holder.bindingAdapterPosition
                hasSavedFocus = true
                onClick(m)
            }
        }
    }

    inner class NewspaperAdapter(
        private val list: List<Newspaper>,
        private val onClick: (Newspaper) -> Unit
    ) : RecyclerView.Adapter<NewspaperAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivCover: ImageView = view.findViewById(R.id.ivNewspaperCover)
            val tvPlaceholder: TextView = view.findViewById(R.id.tvCoverPlaceholderTitle)
            val tvBadge: TextView = view.findViewById(R.id.tvNewspaperBadge)
            val tvName: TextView = view.findViewById(R.id.tvNewspaperName)
            val tvDate: TextView = view.findViewById(R.id.tvNewspaperDate)
            val tvPages: TextView = view.findViewById(R.id.tvNewspaperPages)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_newspaper_card, parent, false))
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val n = list[position]
            holder.tvName.text = n.nome
            holder.tvPlaceholder.text = n.nome.uppercase()
            holder.tvDate.text = n.data
            holder.tvPages.text = if (n.paginas > 0) "${n.paginas} pág." else "PDF"
            holder.tvBadge.text = n.pais.uppercase()
            if (n.capaUrl.isNotBlank()) {
                holder.ivCover.visibility = View.VISIBLE
                Glide.with(holder.itemView.context).load(n.capaUrl).into(holder.ivCover)
            } else {
                holder.ivCover.visibility = View.GONE
            }
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    lastFocusArea = LastFocusArea.CATALOG_GRID
                    lastCatalogGridPos = holder.bindingAdapterPosition
                    hasSavedFocus = true
                }
            }
            holder.itemView.setOnClickListener {
                lastFocusArea = LastFocusArea.CATALOG_GRID
                lastCatalogGridPos = holder.bindingAdapterPosition
                hasSavedFocus = true
                onClick(n)
            }
        }
    }

    private fun mostrarDialogoTrocarServidor() {
        val active = IptvServiceConfig.activeServiceId(this)
        val opcoes = arrayOf(
            "StreamPlay ${if (active == IptvServiceConfig.BEST) "(Ativo)" else ""}",
            "Templarios ${if (active == IptvServiceConfig.BEST2) "(Ativo)" else ""}",
            "Abrir Ecrã de Seleção de Serviço / Contas"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Servidor IPTV")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> alternarServidorDireto(IptvServiceConfig.BEST)
                    1 -> alternarServidorDireto(IptvServiceConfig.BEST2)
                    2 -> {
                        startActivity(Intent(this, ServiceSelectionActivity::class.java).apply {
                            putExtra(ServiceSelectionActivity.EXTRA_MANUAL_SELECTION, true)
                        })
                    }
                }
            }
            .show()
    }

    private fun alternarServidorDireto(targetService: String) {
        val u = IptvServiceConfig.username(this, targetService)
        val p = IptvServiceConfig.password(this, targetService)
        if (u.isBlank() || p.isBlank()) {
            startActivity(Intent(this, StreamPlayLoginActivity::class.java).apply {
                putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, targetService)
                putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, false)
            })
            return
        }
        IptvServiceConfig.select(this, targetService)
        user = u
        pass = p
        currentStreamId = -1
        inlineEpgCache.clear()
        allLiveStreamsCache.clear()
        allMoviesCache.clear()
        allSeriesCache.clear()
        allVodCategories = emptyList()
        allSeriesCategories = emptyList()
        atualizarEstadoServidorEVpn()
        selecionarSecao(WorkspaceSection.LIVE_TV)
        carregarTudoLiveTv()
        Toast.makeText(this, "Servidor alterado para ${IptvServiceConfig.displayName(targetService)}", Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // SECÇÃO 5: PIA VPN INTEGRADA NO ECRÃ PRINCIPAL
    // =========================================================================

    private val REQUEST_VPN_CODE = 4455
    private var vpnRegionsList: List<PiaRegion> = emptyList()
    private var selectedVpnRegion: PiaRegion? = null
    private var vpnRegionAdapter: VpnRegionAdapter? = null

    private val vpnDurationHandler = Handler(Looper.getMainLooper())
    private val vpnDurationRunnable = object : Runnable {
        override fun run() {
            val startTime = PiaVpnManager.getConnectionStartTime()
            if (startTime > 0 && PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTED) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val hours = elapsed / 3600
                val minutes = (elapsed % 3600) / 60
                val seconds = elapsed % 60
                tvVpnDuration.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                vpnDurationHandler.postDelayed(this, 1000)
            } else {
                tvVpnDuration.text = "00:00:00"
            }
        }
    }

    private val vpnStateListener: (PiaVpnManager.VpnStatus, String?) -> Unit = { status, msg ->
        runOnUiThread {
            atualizarInterfaceVpn(status, msg)
            vpnRegionAdapter?.notifyDataSetChanged()
            atualizarEstadoServidorEVpn()
        }
    }

    private fun iniciarSecaoVpn() {
        PiaVpnManager.removeListener(vpnStateListener)
        PiaVpnManager.addListener(vpnStateListener)

        btnToggleVpn.setOnClickListener {
            alternarVpn()
        }

        btnToggleVpn.setOnLongClickListener {
            mostrarDialogoCredenciaisOculto()
            true
        }

        atualizarInterfaceVpn(PiaVpnManager.getStatus(), null)
        carregarRegioesVpn()

        btnToggleVpn.post { btnToggleVpn.requestFocus() }
    }

    private fun carregarRegioesVpn() {
        pbVpnLoading.visibility = View.VISIBLE
        fun renderizarLista(list: List<PiaRegion>) {
            pbVpnLoading.visibility = View.GONE
            vpnRegionsList = list

            val savedId = PiaVpnConfig.getSelectedRegionId(this)
            selectedVpnRegion = list.find { it.id == savedId } ?: list.firstOrNull()
            atualizarTextoRegiaoVpnSelecionada()

            vpnRegionAdapter = VpnRegionAdapter(list) { reg ->
                val currentStatus = PiaVpnManager.getStatus()
                val connectedReg = PiaVpnManager.getConnectedRegion()
                val isCurrentlyConnectedToThis = (currentStatus == PiaVpnManager.VpnStatus.CONNECTED && connectedReg?.id == reg.id)

                if (isCurrentlyConnectedToThis) {
                    Toast.makeText(this, "A desligar ligação de ${reg.name}…", Toast.LENGTH_SHORT).show()
                    PiaVpnManager.disconnect(this)
                } else if (currentStatus == PiaVpnManager.VpnStatus.CONNECTED) {
                    Toast.makeText(this, "A mudar ligação para ${reg.name}…", Toast.LENGTH_SHORT).show()
                    PiaVpnManager.disconnect(this) {
                        iniciarLigacaoVpn(reg)
                    }
                } else if (currentStatus == PiaVpnManager.VpnStatus.CONNECTING) {
                    Toast.makeText(this, "Ligação cancelada", Toast.LENGTH_SHORT).show()
                    PiaVpnManager.disconnect(this)
                } else {
                    Toast.makeText(this, "A ligar a ${reg.name}…", Toast.LENGTH_SHORT).show()
                    iniciarLigacaoVpn(reg)
                }
            }
            rvVpnRegions.adapter = vpnRegionAdapter
        }

        PiaVpnManager.getRegions(this) { list ->
            renderizarLista(list)
        }

        RemotePanelManager.sync(this) {
            if (!isFinishing && !isDestroyed) {
                PiaVpnManager.getRegions(this) { updatedList ->
                    if (updatedList.map { it.id } != vpnRegionsList.map { it.id }) {
                        renderizarLista(updatedList)
                    }
                }
            }
        }
    }

    private fun atualizarTextoRegiaoVpnSelecionada() {
        val reg = selectedVpnRegion
        if (reg != null) {
            tvVpnSelectedLocation.text = "${reg.flagEmoji} ${reg.name}"
        }
    }

    private fun alternarVpn() {
        when (PiaVpnManager.getStatus()) {
            PiaVpnManager.VpnStatus.CONNECTED -> {
                Toast.makeText(this, "A desligar VPN…", Toast.LENGTH_SHORT).show()
                PiaVpnManager.disconnect(this)
            }
            PiaVpnManager.VpnStatus.CONNECTING -> {
                Toast.makeText(this, "Ligação cancelada", Toast.LENGTH_SHORT).show()
                PiaVpnManager.disconnect(this)
            }
            else -> {
                iniciarLigacaoVpn()
            }
        }
    }

    private fun iniciarLigacaoVpn(targetRegion: PiaRegion? = null) {
        val region = targetRegion ?: selectedVpnRegion ?: vpnRegionsList.firstOrNull()
        if (region == null) {
            Toast.makeText(this, "Nenhum servidor selecionado", Toast.LENGTH_SHORT).show()
            return
        }

        selectedVpnRegion = region
        PiaVpnConfig.setSelectedRegionId(this, region.id)
        atualizarTextoRegiaoVpnSelecionada()
        vpnRegionAdapter?.notifyDataSetChanged()

        val prepareIntent = PiaVpnManager.checkPermission(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQUEST_VPN_CODE)
            return
        }

        PiaVpnManager.connect(this, region)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val region = selectedVpnRegion ?: vpnRegionsList.firstOrNull()
                if (region != null) {
                    PiaVpnManager.connect(this, region)
                }
            } else {
                Toast.makeText(this, "Permissão de VPN necessária para proteger a ligação", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun atualizarInterfaceVpn(status: PiaVpnManager.VpnStatus, message: String?) {
        when (status) {
            PiaVpnManager.VpnStatus.CONNECTED -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFF48BB78.toInt())
                tvVpnStatus.text = "LIGADO • PROTEGIDO"
                tvVpnStatus.setTextColor(0xFF48BB78.toInt())
                tvVpnStatusSub.text = "A tua ligação está encriptada e protegida"
                tvVpnIp.text = PiaVpnManager.getProtectedIp() ?: "Ativo"
                tvVpnIp.setTextColor(0xFF38BDF8.toInt())

                btnToggleVpn.text = "DESLIGAR VPN"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFFE53E3E.toInt())
                    cornerRadius = dp(10).toFloat()
                }

                val connectedReg = PiaVpnManager.getConnectedRegion()
                if (connectedReg != null) {
                    tvVpnSelectedLocation.text = "${connectedReg.flagEmoji} ${connectedReg.name}"
                }

                vpnDurationHandler.removeCallbacks(vpnDurationRunnable)
                vpnDurationHandler.post(vpnDurationRunnable)
            }
            PiaVpnManager.VpnStatus.CONNECTING -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFFECC94B.toInt())
                tvVpnStatus.text = "A CONECTAR…"
                tvVpnStatus.setTextColor(0xFFECC94B.toInt())
                tvVpnStatusSub.text = message ?: "A negociar túnel WireGuard…"
                tvVpnIp.text = "A atribuir…"

                btnToggleVpn.text = "CANCELAR"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFF4A5568.toInt())
                    cornerRadius = dp(10).toFloat()
                }
            }
            PiaVpnManager.VpnStatus.DISCONNECTING -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFF718096.toInt())
                tvVpnStatus.text = "A DESLIGAR…"
                tvVpnStatus.setTextColor(0xFFCBD5E0.toInt())
                btnToggleVpn.text = "A AGUARDAR…"
            }
            PiaVpnManager.VpnStatus.DISCONNECTED -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFF718096.toInt())
                tvVpnStatus.text = "DESCONECTADO"
                tvVpnStatus.setTextColor(0xFFE2E8F0.toInt())
                tvVpnStatusSub.text = "A tua ligação não está protegida"
                tvVpnIp.text = "—"
                tvVpnDuration.text = "00:00:00"

                btnToggleVpn.text = "LIGAR VPN"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFF0284C7.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                vpnDurationHandler.removeCallbacks(vpnDurationRunnable)
            }
            PiaVpnManager.VpnStatus.ERROR -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFFE53E3E.toInt())
                tvVpnStatus.text = "FALHA NA LIGAÇÃO"
                tvVpnStatus.setTextColor(0xFFE53E3E.toInt())
                tvVpnStatusSub.text = "Não foi possível estabelecer ligação ao servidor. Tenta novamente."
                tvVpnIp.text = "—"

                btnToggleVpn.text = "TENTAR NOVAMENTE"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFF0284C7.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                vpnDurationHandler.removeCallbacks(vpnDurationRunnable)
            }
        }
    }

    private fun mostrarDialogoCredenciaisOculto() {
        if (isFinishing || isDestroyed) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }

        val etUser = EditText(this).apply {
            hint = "Utilizador PIA"
            setText(PiaVpnConfig.getUsername(this@EagleTvActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF718096.toInt())
            isSingleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF1A2639.toInt())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0xFF2D3E56.toInt())
            }
        }

        val etPass = EditText(this).apply {
            hint = "Palavra-passe PIA"
            setText(PiaVpnConfig.getPassword(this@EagleTvActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF718096.toInt())
            isSingleLine = true
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                setColor(0xFF1A2639.toInt())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0xFF2D3E56.toInt())
            }
        }

        container.addView(etUser)
        container.addView(etPass)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Credenciais PIA VPN")
            .setMessage("Altera as credenciais de acesso caso tenhas uma conta PIA dedicada.")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val u = etUser.text.toString().trim()
                val p = etPass.text.toString().trim()
                if (u.isNotEmpty() && p.isNotEmpty()) {
                    PiaVpnConfig.setUsername(this, u)
                    PiaVpnConfig.setPassword(this, p)
                    Toast.makeText(this, "Credenciais guardadas com sucesso", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Utilizador e password não podem estar vazios", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    inner class VpnRegionAdapter(
        private val list: List<PiaRegion>,
        private val onSelect: (PiaRegion) -> Unit
    ) : RecyclerView.Adapter<VpnRegionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFlag: TextView = view.findViewById(R.id.tvRegionFlag)
            val tvName: TextView = view.findViewById(R.id.tvRegionName)
            val tvBadge: TextView = view.findViewById(R.id.tvRegionBadge)
            val ivSelected: ImageView = view.findViewById(R.id.ivRegionSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vpn_region, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reg = list[position]
            holder.tvFlag.text = reg.flagEmoji
            holder.tvName.text = reg.name

            val isSelected = (selectedVpnRegion?.id == reg.id)
            val isConnectedToThis = (PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTED &&
                    PiaVpnManager.getConnectedRegion()?.id == reg.id)
            val isConnectingToThis = (PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTING &&
                    selectedVpnRegion?.id == reg.id)

            when {
                isConnectedToThis -> {
                    holder.ivSelected.visibility = View.VISIBLE
                    holder.ivSelected.setImageResource(android.R.drawable.checkbox_on_background)
                    holder.ivSelected.imageTintList = ColorStateList.valueOf(0xFF48BB78.toInt())
                    holder.tvBadge.visibility = View.VISIBLE
                    holder.tvBadge.text = "● Ligado agora"
                    holder.tvBadge.setTextColor(0xFF48BB78.toInt())
                }
                isConnectingToThis -> {
                    holder.ivSelected.visibility = View.VISIBLE
                    holder.ivSelected.setImageResource(android.R.drawable.ic_popup_sync)
                    holder.ivSelected.imageTintList = ColorStateList.valueOf(0xFFECC94B.toInt())
                    holder.tvBadge.visibility = View.VISIBLE
                    holder.tvBadge.text = "A ligar…"
                    holder.tvBadge.setTextColor(0xFFECC94B.toInt())
                }
                isSelected -> {
                    holder.ivSelected.visibility = View.VISIBLE
                    holder.ivSelected.setImageResource(android.R.drawable.checkbox_on_background)
                    holder.ivSelected.imageTintList = ColorStateList.valueOf(0xFF38BDF8.toInt())
                    holder.tvBadge.visibility = View.GONE
                }
                else -> {
                    holder.ivSelected.visibility = View.INVISIBLE
                    holder.tvBadge.visibility = View.GONE
                }
            }

            val card = holder.itemView as? CardView
            val updateStyle: (Boolean) -> Unit = { hasFocus ->
                if (hasFocus) {
                    card?.setCardBackgroundColor(0xFF1A365D.toInt())
                    card?.cardElevation = dp(8).toFloat()
                    holder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else if (isConnectedToThis) {
                    card?.setCardBackgroundColor(0xFF0F2D20.toInt())
                    card?.cardElevation = dp(2).toFloat()
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                } else if (isSelected) {
                    card?.setCardBackgroundColor(0xFF102844.toInt())
                    card?.cardElevation = dp(2).toFloat()
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                } else {
                    card?.setCardBackgroundColor(0xFF101A2B.toInt())
                    card?.cardElevation = dp(2).toFloat()
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            updateStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus -> updateStyle(hasFocus) }
            holder.itemView.setOnClickListener { onSelect(reg) }
        }
    }

    // =========================================================================
    // SECÇÃO 6: DEFINIÇÕES INTEGRADA NO ECRÃ PRINCIPAL
    // =========================================================================

    data class EagleSettingCard(
        val id: String,
        val iconEmoji: String,
        val title: String,
        val subtitle: String,
        val action: () -> Unit
    )

    private fun iniciarSecaoDefinicoes() {
        val timeFormat = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("time_format", "24 Hr") ?: "24 Hr"
        val streamType = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("stream_type", "MPEGTS") ?: "MPEGTS"
        val activeService = IptvServiceConfig.activeServiceId(this)
        val serverName = IptvServiceConfig.displayName(activeService)

        val cards = listOf(
            EagleSettingCard("ACCOUNT", "👤", "A Minha Conta", "Ver validade da subscrição, ligações e detalhes", ::mostrarDialogoConta),
            EagleSettingCard("UPDATES", "🔄", "Atualizações da App", "Verificar novas versões e atualizar via OTA", { AppUpdateManager.checkForUpdates(this, manual = true) }),
            EagleSettingCard("LANGUAGE", "🌐", "Idioma dos Menus", "Configurar idioma da interface", ::mostrarDialogoIdioma),
            EagleSettingCard("TIME", "🕒", "Formato de Hora", "Atualmente: $timeFormat", ::mostrarDialogoFormatoHora),
            EagleSettingCard("STREAM", "📺", "Formato de Stream", "Atualmente: $streamType (recomendado para Box)", ::mostrarDialogoFormatoStream),
            EagleSettingCard("SERVER", "🔀", "Servidor IPTV", "Atualmente: $serverName", ::mostrarDialogoTrocarServidor),
            EagleSettingCard("LOGOUT", "🚪", "Terminar Sessão", "Desconectar utilizador e mudar de conta", ::mostrarDialogoLogout)
        )

        rvSettingsGrid.adapter = EagleSettingsAdapter(cards)
        rvSettingsGrid.post {
            focarRecyclerViewPosicao(rvSettingsGrid, 0)
        }
    }

    private fun mostrarDialogoConta() {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("👤 A Minha Conta")
            .setMessage("A carregar detalhes da conta...")
            .setPositiveButton("OK", null)
            .create()
        dialog.show()

        val url = "$baseUrl/player_api.php?username=$user&password=$pass"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    dialog.setMessage("Utilizador: $user\nServidor: $baseUrl\nNão foi possível obter dados adicionais.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string().orEmpty()
                try {
                    val root = JSONObject(json)
                    val userInfo = root.optJSONObject("user_info")
                    val status = userInfo?.optString("status", "Ativo") ?: "Ativo"
                    val expDateRaw = userInfo?.optString("exp_date", "") ?: ""
                    val maxConn = userInfo?.optString("max_connections", "1") ?: "1"
                    val activeConn = userInfo?.optString("active_cons", "0") ?: "0"

                    val expFormatted = if (expDateRaw.isNotEmpty() && expDateRaw != "null") {
                        val ts = expDateRaw.toLongOrNull()
                        if (ts != null) {
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts * 1000L))
                        } else {
                            expDateRaw
                        }
                    } else {
                        "Ilimitada / Vitalícia"
                    }

                    val msg = """
                        👤 Utilizador: $user
                        ● Estado: $status
                        📅 Validade: $expFormatted
                        🔗 Ligações: $activeConn / $maxConn ativas
                        🌐 Servidor: ${IptvServiceConfig.displayName(IptvServiceConfig.activeServiceId(this@EagleTvActivity))}
                    """.trimIndent()

                    runOnUiThread {
                        dialog.setMessage(msg)
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        dialog.setMessage("Utilizador: $user\nServidor: $baseUrl")
                    }
                }
            }
        })
    }

    private fun mostrarDialogoIdioma() {
        val idiomas = arrayOf("Português (Padrão)", "English", "Español", "Français")
        val codes = arrayOf("pt", "en", "es", "fr")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🌐 Escolher Idioma")
            .setItems(idiomas) { _, which ->
                val code = codes[which]
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(code)
                )
                Toast.makeText(this, "Idioma alterado para ${idiomas[which]}", Toast.LENGTH_SHORT).show()
                iniciarSecaoDefinicoes()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoFormatoHora() {
        val opcoes = arrayOf("24 Horas (ex: 20:30)", "12 Horas (ex: 08:30 PM)")
        val valores = arrayOf("24 Hr", "12 Hr")
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val current = if (prefs.getString("time_format", "24 Hr") == "12 Hr") 1 else 0

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🕒 Formato de Hora")
            .setSingleChoiceItems(opcoes, current) { d, which ->
                prefs.edit().putString("time_format", valores[which]).apply()
                atualizarRelogio()
                Toast.makeText(this, "Formato guardado!", Toast.LENGTH_SHORT).show()
                d.dismiss()
                iniciarSecaoDefinicoes()
            }
            .show()
    }

    private fun mostrarDialogoFormatoStream() {
        val opcoes = arrayOf("MPEG-TS (.ts) - Recomendado para TV Box", "HLS (.m3u8) - Streaming Adaptativo")
        val valores = arrayOf("MPEGTS", "HLS")
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val current = if (prefs.getString("stream_type", "MPEGTS") == "HLS") 1 else 0

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("📺 Formato de Stream")
            .setSingleChoiceItems(opcoes, current) { d, which ->
                prefs.edit().putString("stream_type", valores[which]).apply()
                Toast.makeText(this, "Formato alterado para ${valores[which]}", Toast.LENGTH_SHORT).show()
                d.dismiss()
                iniciarSecaoDefinicoes()
            }
            .show()
    }

    private fun mostrarDialogoLogout() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🚪 Terminar Sessão")
            .setMessage("Tens a certeza que queres terminar sessão e aceder com outra conta?")
            .setPositiveButton("Sim, Sair") { _, _ ->
                getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_login", false)
                    .remove("SAVED_USER")
                    .remove("SAVED_PASS")
                    .apply()
                startActivity(Intent(this, ServiceSelectionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    inner class EagleSettingsAdapter(
        private val list: List<EagleSettingCard>
    ) : RecyclerView.Adapter<EagleSettingsAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon: TextView = v.findViewById(R.id.tvSettingIcon)
            val tvTitle: TextView = v.findViewById(R.id.tvSettingTitle)
            val tvSubtitle: TextView = v.findViewById(R.id.tvSettingSubtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_setting_card, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvIcon.text = item.iconEmoji
            holder.tvTitle.text = item.title
            holder.tvSubtitle.text = item.subtitle

            val updateStyle: (Boolean) -> Unit = { hasFocus ->
                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    if (hasFocus) {
                        setColor(0xFF1A365D.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    } else {
                        setColor(0xFF0F1B2D.toInt())
                        setStroke(dp(1), 0x3338BDF8)
                    }
                }
                if (hasFocus) {
                    holder.itemView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                } else {
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }

            updateStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus -> updateStyle(hasFocus) }
            holder.itemView.setOnClickListener { item.action() }
        }
    }

    private fun mostrarDialogoSaida() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Sair da Eagle TV")
            .setMessage("Queres fechar a aplicação Eagle TV?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sair") { _, _ -> finishAffinity() }
            .show()
    }

    // =========================================================================
    // UTILITÁRIOS DE TEMPO & BASE64
    // =========================================================================

    private fun formatProgramTime(value: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = input.parse(value) ?: return value
            val pattern = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("time_format", "24 Hr") == "12 Hr") "hh:mm a" else "HH:mm"
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (_: Exception) {
            value
        }
    }

    private fun calcularPercentagem(startStr: String, endStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val s = sdf.parse(startStr)?.time ?: return 0
            val e = sdf.parse(endStr)?.time ?: return 0
            val now = System.currentTimeMillis()
            val total = e - s
            if (total <= 0) return 0
            (((now - s).toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            0
        }
    }

    private fun decodeSafe(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(str, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            str
        }
    }

    // =========================================================================
    // ADAPTERS (GRUPOS, CANAIS COM EPG INLINE, TIMELINE EPG, RECENTES, POSTERS)
    // =========================================================================

    inner class GroupListAdapter(
        private val items: List<Pair<String, String>>,
        initialSelected: Int,
        private val countProvider: (String) -> Int,
        private val onSelect: (String, String) -> Unit
    ) : RecyclerView.Adapter<GroupListAdapter.ViewHolder>() {

        private var selectedPosition = initialSelected

        fun posicaoSelecionada(): Int = selectedPosition

        fun selecionarPorId(id: String) {
            val idx = items.indexOfFirst { it.first == id }
            if (idx >= 0 && idx != selectedPosition) {
                val old = selectedPosition
                selectedPosition = idx
                notifyItemChanged(old)
                notifyItemChanged(selectedPosition)
            }
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val indicator: View = v.findViewById(R.id.viewGroupIndicator)
            val tvName: TextView = v.findViewById(R.id.tvGroupName)
            val tvCount: TextView = v.findViewById(R.id.tvGroupCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_group, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (id, name) = items[position]
            holder.tvName.text = name

            val count = countProvider(id)
            if (count > 0) {
                holder.tvCount.text = count.toString()
                holder.tvCount.visibility = View.VISIBLE
            } else {
                holder.tvCount.visibility = View.GONE
            }

            val isSpecial = id.startsWith("FAV") || id.startsWith("RECENT") || id == "RESUME"

            val updateStyle: (Boolean) -> Unit = { hasFocus ->
                val isSelected = holder.bindingAdapterPosition == selectedPosition
                holder.indicator.visibility = if (isSelected || hasFocus) View.VISIBLE else View.INVISIBLE
                holder.indicator.setBackgroundColor(
                    if (isSpecial) 0xFFFBBF24.toInt() else 0xFF00E5FF.toInt()
                )

                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    when {
                        hasFocus -> {
                            setColor(0xFF1A365D.toInt())
                            setStroke(dp(2), 0xFF00E5FF.toInt())
                        }
                        isSelected -> {
                            setColor(0xFF112238.toInt())
                            setStroke(dp(1), 0x4D38BDF8)
                        }
                        else -> {
                            setColor(Color.TRANSPARENT)
                        }
                    }
                }

                holder.tvName.setTextColor(
                    when {
                        hasFocus -> Color.WHITE
                        isSpecial -> 0xFFFBBF24.toInt()
                        isSelected -> 0xFF38BDF8.toInt()
                        else -> 0xFFE2E8F0.toInt()
                    }
                )
            }

            updateStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                updateStyle(hasFocus)
                if (hasFocus) {
                    if (this@GroupListAdapter === liveGroupAdapter) {
                        lastFocusArea = LastFocusArea.LIVE_GROUPS
                        lastLiveGroupPos = holder.bindingAdapterPosition
                    } else {
                        lastFocusArea = LastFocusArea.CATALOG_GROUPS
                        lastCatalogGroupPos = holder.bindingAdapterPosition
                    }
                    hasSavedFocus = true
                }
            }

            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val prev = selectedPosition
                    selectedPosition = pos
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onSelect(id, name)
                }
            }
        }
    }

    inner class TiviChannelAdapter(
        private val list: List<LiveStream>,
        private val onClick: (LiveStream, Int) -> Unit,
        private val onLongClick: (LiveStream) -> Unit,
        private val onFocused: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<TiviChannelAdapter.ViewHolder>() {

        private var selectedPosition = RecyclerView.NO_POSITION

        fun posicaoSelecionada(): Int = selectedPosition

        fun selecionarCanalPorId(streamId: Int): Int {
            val index = list.indexOfFirst { it.streamId == streamId }
            if (index != -1 && index != selectedPosition) {
                val prev = selectedPosition
                selectedPosition = index
                if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
            }
            return index
        }

        fun notificarEpgAtualizado(streamId: Int) {
            val index = list.indexOfFirst { it.streamId == streamId }
            if (index >= 0) {
                notifyItemChanged(index)
            }
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvNum: TextView = v.findViewById(R.id.tvChannelNumber)
            val ivLogo: ImageView = v.findViewById(R.id.ivChannelLogo)
            val tvPlaying: TextView = v.findViewById(R.id.tvPlayingBadge)
            val tvName: TextView = v.findViewById(R.id.tvChannelName)
            val tvCatchup: TextView = v.findViewById(R.id.tvCatchupBadge)
            val tvFav: TextView = v.findViewById(R.id.tvFavStar)
            val tvEpg: TextView = v.findViewById(R.id.tvInlineEpgTitle)
            val pbEpg: ProgressBar = v.findViewById(R.id.pbInlineEpg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_channel, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val canal = list[position]
            val channelNum = position + 1
            holder.tvNum.text = channelNum.toString()
            holder.tvName.text = canal.name

            val isPlaying = canal.streamId == currentStreamId
            val isFav = EagleTvConfig.isFavoriteChannel(this@EagleTvActivity, canal.streamId)
            val hasCatchup = temCatchUp(canal)

            holder.tvPlaying.visibility = if (isPlaying) View.VISIBLE else View.GONE
            holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE
            holder.tvCatchup.visibility = if (hasCatchup) View.VISIBLE else View.GONE

            val epg = inlineEpgCache[canal.streamId]
            if (epg != null) {
                holder.tvEpg.text = epg.currentTitle
                holder.pbEpg.progress = epg.progressPercent
                holder.pbEpg.visibility = View.VISIBLE
            } else {
                holder.tvEpg.text = "Emissão em direto"
                holder.pbEpg.progress = 0
                solicitarInlineEpgSeNecessario(canal.streamId)
            }

            if (!canal.streamIcon.isNullOrBlank()) {
                Glide.with(holder.itemView.context)
                    .load(canal.streamIcon)
                    .dontAnimate()
                    .into(holder.ivLogo)
            } else {
                holder.ivLogo.setImageResource(R.mipmap.ic_launcher)
            }

            val updateStyle: (Boolean) -> Unit = { hasFocus ->
                val isSelected = holder.bindingAdapterPosition == selectedPosition || isPlaying
                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(9).toFloat()
                    when {
                        hasFocus -> {
                            setColor(0xFF1A365D.toInt())
                            setStroke(dp(2), 0xFF00E5FF.toInt())
                        }
                        isSelected -> {
                            setColor(0xFF10243C.toInt())
                            setStroke(dp(1), 0xFF10B981.toInt())
                        }
                        else -> {
                            setColor(Color.TRANSPARENT)
                        }
                    }
                }
                holder.tvNum.setTextColor(
                    if (hasFocus || isSelected) 0xFF00E5FF.toInt() else 0xFF64748B.toInt()
                )
            }

            updateStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                updateStyle(hasFocus)
                if (hasFocus) {
                    if (this@TiviChannelAdapter === liveChannelAdapter) {
                        lastFocusArea = LastFocusArea.LIVE_CHANNELS
                        lastLiveChannelPos = holder.bindingAdapterPosition
                        hasSavedFocus = true
                    }
                    onFocused(canal)
                }
            }

            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(canal, pos + 1)
                }
            }

            holder.itemView.setOnLongClickListener {
                onLongClick(canal)
                true
            }
        }
    }

    inner class ChannelScheduleAdapter(
        private val channel: LiveStream,
        private val programs: List<EpgProgram>
    ) : RecyclerView.Adapter<ChannelScheduleAdapter.ViewHolder>() {

        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private val hasArchive = temCatchUp(channel)

        fun posicaoProgramaAtual(): Int {
            val now = System.currentTimeMillis()
            val nowIndex = programs.indexOfFirst { p ->
                val s = try { sdf.parse(p.start.orEmpty())?.time } catch (_: Exception) { null }
                val e = try { sdf.parse(p.end.orEmpty())?.time } catch (_: Exception) { null }
                s != null && e != null && now in s..e
            }
            if (nowIndex >= 0) return nowIndex
            return lastSchedulePos.coerceIn(0, (programs.size - 1).coerceAtLeast(0))
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTime: TextView = v.findViewById(R.id.tvSlotTime)
            val tvBadge: TextView = v.findViewById(R.id.tvSlotBadge)
            val tvTitle: TextView = v.findViewById(R.id.tvSlotTitle)
            val tvDesc: TextView = v.findViewById(R.id.tvSlotDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_epg_slot, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = programs.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val p = programs[position]
            val startMillis = try { sdf.parse(p.start.orEmpty())?.time } catch (_: Exception) { null }
            val endMillis = try { sdf.parse(p.end.orEmpty())?.time } catch (_: Exception) { null }
            val now = System.currentTimeMillis()

            val isNow = startMillis != null && endMillis != null && now in startMillis..endMillis
            val isPast = endMillis != null && endMillis < now

            holder.tvTime.text = "${formatProgramTime(p.start.orEmpty())} - ${formatProgramTime(p.end.orEmpty())}"
            holder.tvTitle.text = p.title.ifBlank { "Programa" }

            if (p.description.isNotBlank()) {
                holder.tvDesc.text = p.description
                holder.tvDesc.visibility = View.VISIBLE
            } else {
                holder.tvDesc.visibility = View.GONE
            }

            when {
                isNow -> {
                    holder.tvBadge.visibility = View.VISIBLE
                    holder.tvBadge.text = "AGORA"
                    holder.tvBadge.setTextColor(0xFF04131D.toInt())
                    holder.tvBadge.background = GradientDrawable().apply {
                        setColor(0xFF10B981.toInt())
                        cornerRadius = dp(4).toFloat()
                    }
                }
                isPast && hasArchive -> {
                    holder.tvBadge.visibility = View.VISIBLE
                    holder.tvBadge.text = "⏪ VER"
                    holder.tvBadge.setTextColor(0xFF04131D.toInt())
                    holder.tvBadge.background = GradientDrawable().apply {
                        setColor(0xFF38BDF8.toInt())
                        cornerRadius = dp(4).toFloat()
                    }
                }
                else -> {
                    holder.tvBadge.visibility = View.GONE
                }
            }

            val updateBg: (Boolean) -> Unit = { hasFocus ->
                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    when {
                        hasFocus -> {
                            setColor(0xFF1A365D.toInt())
                            setStroke(dp(2), 0xFF00E5FF.toInt())
                        }
                        isNow -> {
                            setColor(0xFF0E2238.toInt())
                            setStroke(dp(1), 0x6610B981)
                        }
                        else -> {
                            setColor(0xFF091322.toInt())
                        }
                    }
                }
            }
            updateBg(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                updateBg(hasFocus)
                if (hasFocus) {
                    if (activeSection == WorkspaceSection.LIVE_TV) {
                        lastFocusArea = LastFocusArea.CHANNEL_SCHEDULE
                        lastSchedulePos = holder.bindingAdapterPosition
                    } else {
                        lastFocusArea = LastFocusArea.CATALOG_GRID
                        lastCatalogGridPos = holder.bindingAdapterPosition
                    }
                    hasSavedFocus = true
                }
            }

            holder.itemView.setOnClickListener {
                if (activeSection == WorkspaceSection.LIVE_TV) {
                    lastFocusArea = LastFocusArea.CHANNEL_SCHEDULE
                    lastSchedulePos = holder.bindingAdapterPosition
                } else {
                    lastFocusArea = LastFocusArea.CATALOG_GRID
                    lastCatalogGridPos = holder.bindingAdapterPosition
                }
                hasSavedFocus = true

                when {
                    isPast && hasArchive -> reproduzirProgramaCatchUp(channel, p)
                    isNow -> {
                        if (currentStreamId != channel.streamId) {
                            sintonizarCanal(channel, 1)
                        } else {
                            entrarEmFullscreen()
                        }
                    }
                    else -> {
                        Toast.makeText(
                            this@EagleTvActivity,
                            "${p.title} (${formatProgramTime(p.start.orEmpty())})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    inner class RecentChannelsAdapter(
        private val list: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<RecentChannelsAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivLogo: ImageView = v.findViewById(R.id.ivRecentLogo)
            val tvName: TextView = v.findViewById(R.id.tvRecentName)
            val tvSub: TextView = v.findViewById(R.id.tvRecentSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_recent_card, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val canal = list[position]
            holder.tvName.text = canal.name
            holder.tvSub.text = inlineEpgCache[canal.streamId]?.currentTitle ?: "Canal Recente"

            if (!canal.streamIcon.isNullOrBlank()) {
                Glide.with(holder.itemView.context).load(canal.streamIcon).into(holder.ivLogo)
            } else {
                holder.ivLogo.setImageResource(R.mipmap.ic_launcher)
            }

            val styleCard: (Boolean) -> Unit = { hasFocus ->
                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    if (hasFocus) {
                        setColor(0xFF1D4ED8.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    } else {
                        setColor(0xFF101E33.toInt())
                        setStroke(dp(1), 0xFF23395B.toInt())
                    }
                }
            }
            styleCard(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus -> styleCard(hasFocus) }
            holder.itemView.setOnClickListener { onClick(canal) }
        }
    }

    inner class CatalogPosterAdapter(
        private val items: List<CatalogPosterItem>,
        private val onFocused: (CatalogPosterItem) -> Unit,
        private val onClick: (CatalogPosterItem) -> Unit,
        private val onLongClick: (CatalogPosterItem) -> Unit
    ) : RecyclerView.Adapter<CatalogPosterAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivThumb: ImageView = v.findViewById(R.id.ivPosterThumb)
            val tvFav: TextView = v.findViewById(R.id.tvPosterFav)
            val tvBadge: TextView = v.findViewById(R.id.tvPosterBadge)
            val pbResume: ProgressBar = v.findViewById(R.id.pbPosterResume)
            val tvTitle: TextView = v.findViewById(R.id.tvPosterTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_eagletv_poster, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            when (item) {
                is CatalogPosterItem.Movie -> {
                    val m = item.vod
                    holder.tvTitle.text = m.name
                    val isFav = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
                        .getStringSet("fav_ids", emptySet())?.contains(m.streamId.toString()) == true
                    holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE

                    val rating = m.rating?.takeIf { it.isNotBlank() && it != "0" }
                    if (rating != null) {
                        holder.tvBadge.text = "★ $rating"
                        holder.tvBadge.visibility = View.VISIBLE
                    } else {
                        holder.tvBadge.visibility = View.GONE
                    }

                    val resumePrefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)
                    val posMs = resumePrefs.getLong("pos_${m.streamId}", 0L)
                    val durMs = resumePrefs.getLong("dur_${m.streamId}", 0L)
                    if (posMs > 10_000L && durMs > 0L) {
                        holder.pbResume.progress = ((posMs * 100L) / durMs).toInt().coerceIn(5, 98)
                        holder.pbResume.visibility = View.VISIBLE
                    } else {
                        holder.pbResume.visibility = View.GONE
                    }

                    if (!m.streamIcon.isNullOrBlank()) {
                        Glide.with(holder.itemView.context).load(m.streamIcon).into(holder.ivThumb)
                    } else {
                        holder.ivThumb.setImageResource(R.mipmap.ic_launcher)
                    }
                }
                is CatalogPosterItem.Series -> {
                    val s = item.series
                    holder.tvTitle.text = s.name
                    holder.pbResume.visibility = View.GONE
                    val isFav = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
                        .getStringSet("fav_ids", emptySet())?.contains(s.seriesId.toString()) == true
                    holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE

                    val rating = s.rating?.takeIf { it.isNotBlank() && it != "0" }
                    if (rating != null) {
                        holder.tvBadge.text = "★ $rating"
                        holder.tvBadge.visibility = View.VISIBLE
                    } else {
                        holder.tvBadge.visibility = View.GONE
                    }

                    if (!s.cover.isNullOrBlank()) {
                        Glide.with(holder.itemView.context).load(s.cover).into(holder.ivThumb)
                    } else {
                        holder.ivThumb.setImageResource(R.mipmap.ic_launcher)
                    }
                }
                is CatalogPosterItem.Channel -> {
                    val c = item.channel
                    holder.tvTitle.text = c.name
                    holder.pbResume.visibility = View.GONE
                    val isFav = EagleTvConfig.isFavoriteChannel(this@EagleTvActivity, c.streamId)
                    holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE
                    holder.tvBadge.text = "📺 TV"
                    holder.tvBadge.visibility = View.VISIBLE

                    if (!c.streamIcon.isNullOrBlank()) {
                        Glide.with(holder.itemView.context).load(c.streamIcon).into(holder.ivThumb)
                    } else {
                        holder.ivThumb.setImageResource(R.mipmap.ic_launcher)
                    }
                }
            }

            val updateStyle: (Boolean) -> Unit = { hasFocus ->
                holder.itemView.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    if (hasFocus) {
                        setColor(0xFF1A365D.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    } else {
                        setColor(Color.TRANSPARENT)
                    }
                }
                if (hasFocus) {
                    holder.itemView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                } else {
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            updateStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                updateStyle(hasFocus)
                if (hasFocus) {
                    lastFocusArea = LastFocusArea.CATALOG_GRID
                    lastCatalogGridPos = holder.bindingAdapterPosition
                    hasSavedFocus = true
                    onFocused(item)
                }
            }
            holder.itemView.setOnClickListener {
                lastFocusArea = LastFocusArea.CATALOG_GRID
                lastCatalogGridPos = holder.bindingAdapterPosition
                hasSavedFocus = true
                onClick(item)
            }
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }
}
