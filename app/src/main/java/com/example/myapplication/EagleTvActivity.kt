package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
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
    private lateinit var btnExpandRail: View
    private var isRailCollapsed = false
    private lateinit var layoutTopPreviewRow: LinearLayout
    private lateinit var layoutTopProgramDetails: View
    private lateinit var viewTopHorizontalDivider: View
    private lateinit var layoutLiveTvColumns: View
    private lateinit var layoutCatalogSection: View

    // Menu Lateral (Left Rail)
    private lateinit var tvRailClock: TextView
    private lateinit var tvRailActiveServer: TextView
    private lateinit var tvRailEditionBadge: TextView
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
    private var isLoadingMoviesCache = false
    private val pendingMoviesCacheCallbacks = mutableListOf<() -> Unit>()
    private var currentVodCategoryMovies = listOf<VodStream>()
    private var currentVodCategoryId = ""
    private var currentVodSortMode = 0 // 0=Padrão, 1=A-Z, 2=Recentes, 3=Rating
    private var filterJob: Job? = null

    private var allSeriesCategories = listOf<SeriesCategory>()
    private val allSeriesCache = mutableListOf<SeriesItem>()
    private var isLoadingSeriesCache = false
    private val pendingSeriesCallbacks = mutableListOf<() -> Unit>()
    private var currentSeriesCategoryItems = listOf<SeriesItem>()
    private var currentSeriesCategoryId = ""
    private var seriesFilterJob: Job? = null

    private var liveGroupAdapter: GroupListAdapter? = null
    private var overlayGroupAdapter: GroupListAdapter? = null
    private var liveChannelAdapter: TiviChannelAdapter? = null
    private var overlayChannelAdapter: TiviChannelAdapter? = null
    private var movieGroupAdapter: GroupListAdapter? = null
    private var seriesGroupAdapter: GroupListAdapter? = null
    private var sportsGroupAdapter: GroupListAdapter? = null
    private var newspaperGroupAdapter: GroupListAdapter? = null
    private var catchupGroupAdapter: GroupListAdapter? = null
    private val catalogGroupAdapter: GroupListAdapter?
        get() = when (activeSection) {
            WorkspaceSection.MOVIES -> movieGroupAdapter
            WorkspaceSection.SERIES -> seriesGroupAdapter
            WorkspaceSection.SPORTS -> sportsGroupAdapter
            WorkspaceSection.NEWSPAPERS -> newspaperGroupAdapter
            WorkspaceSection.CATCHUP -> catchupGroupAdapter
            else -> movieGroupAdapter ?: seriesGroupAdapter
        }
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
    private var focarConteudoAoCarregar = false

    // Caches em memória para performance máxima sem I/O de disco no scroll
    private val movieFavIdsCache = HashSet<String>()
    private val seriesFavIdsCache = HashSet<String>()
    private val liveFavIdsCache = HashSet<Int>()
    private val vodResumePosCache = HashMap<Int, Long>()
    private val vodResumeDurCache = HashMap<Int, Long>()
    private var currentPreviewVodStreamId = -1
    private var heroPreviewRunnable: Runnable? = null

    private fun carregarCachesLocais() {
        movieFavIdsCache.clear()
        getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
            .getStringSet("fav_ids", emptySet())?.let { movieFavIdsCache.addAll(it) }

        seriesFavIdsCache.clear()
        getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
            .getStringSet("fav_ids", emptySet())?.let { seriesFavIdsCache.addAll(it) }

        liveFavIdsCache.clear()
        liveFavIdsCache.addAll(EagleTvConfig.getFavoriteChannelIds(this))

        vodResumePosCache.clear()
        vodResumeDurCache.clear()
        val resumePrefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)
        resumePrefs.all.forEach { (k, v) ->
            if (k.startsWith("pos_")) {
                val streamId = k.removePrefix("pos_").toIntOrNull()
                val pos = (v as? Long) ?: (v as? Int)?.toLong() ?: 0L
                if (streamId != null && pos > 10_000L) vodResumePosCache[streamId] = pos
            } else if (k.startsWith("dur_")) {
                val streamId = k.removePrefix("dur_").toIntOrNull()
                val dur = (v as? Long) ?: (v as? Int)?.toLong() ?: 0L
                if (streamId != null && dur > 0L) vodResumeDurCache[streamId] = dur
            }
        }
    }

    private fun focarRecyclerViewPosicao(rv: RecyclerView, targetPos: Int, retries: Int = 8) {
        if (targetPos < 0) return
        val count = rv.adapter?.itemCount ?: 0
        if (count == 0) {
            if (retries > 0) {
                rv.postDelayed({ focarRecyclerViewPosicao(rv, targetPos, retries - 1) }, 60)
            }
            return
        }
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

    private val periodicUpdateCheckRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                AppUpdateManager.checkForUpdates(this@EagleTvActivity, manual = false)
                // Volta a verificar a cada 10 minutos automaticamente em segundo plano
                uiHandler.postDelayed(this, 10 * 60_000L)
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

        if (IptvServiceConfig.username(this).isBlank()) {
            IptvServiceConfig.saveCredentials(this, IptvServiceConfig.activeServiceId(this), user, pass)
        }
        RemotePanelManager.sync(this)

        inicializarViews()
        configurarMenuLateral()
        configurarPlayer()
        configurarEventosFullscreen()
        configurarPesquisaEOrdenacao()

        atualizarRelogio()
        carregarCachesLocais()
        atualizarEstadoServidorEVpn()
        AccountExpirationAlert.checkAndShow(this)
        rvLiveGroups.postDelayed({
            if (!isFinishing && !isDestroyed) {
                uiHandler.removeCallbacks(periodicUpdateCheckRunnable)
                uiHandler.post(periodicUpdateCheckRunnable)
            }
        }, 1200)

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
                    // Em Live TV: retroceder gradualmente entre colunas
                    activeSection == WorkspaceSection.LIVE_TV -> {
                        when {
                            rvChannelSchedule.hasFocus() -> {
                                val pos = (liveChannelAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                                focarRecyclerViewPosicao(rvLiveChannels, pos)
                            }
                            rvLiveChannels.hasFocus() -> {
                                val pos = (liveGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                                focarRecyclerViewPosicao(rvLiveGroups, pos)
                            }
                            rvLiveGroups.hasFocus() -> {
                                mostrarMenuLateral(navLiveTv)
                            }
                            layoutLeftRail.hasFocus() -> {
                                mostrarDialogoSaida()
                            }
                            else -> {
                                navLiveTv.requestFocus()
                            }
                        }
                    }
                    // Em Gravações com lista de programas aberta:
                    catchupViewingPrograms && activeSection == WorkspaceSection.CATCHUP -> {
                        voltarParaListaCanaisCatchUp()
                    }
                    // Em Catálogo (Filmes, Séries, Desporto, Jornais, Gravações):
                    layoutCatalogSection.visibility == View.VISIBLE -> {
                        when {
                            rvCatalogGrid.hasFocus() -> {
                                val groupPos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                                focarRecyclerViewPosicao(rvCatalogGroups, groupPos)
                            }
                            rvCatalogGroups.hasFocus() -> {
                                val target = when (activeSection) {
                                    WorkspaceSection.MOVIES -> navMovies
                                    WorkspaceSection.SERIES -> navSeries
                                    WorkspaceSection.CATCHUP -> navCatchUp
                                    WorkspaceSection.SPORTS -> navSports
                                    WorkspaceSection.NEWSPAPERS -> navNewspapers
                                    else -> navLiveTv
                                }
                                mostrarMenuLateral(target)
                            }
                            layoutLeftRail.hasFocus() -> {
                                selecionarSecao(WorkspaceSection.LIVE_TV)
                            }
                            else -> {
                                when (activeSection) {
                                    WorkspaceSection.MOVIES -> navMovies.requestFocus()
                                    WorkspaceSection.SERIES -> navSeries.requestFocus()
                                    WorkspaceSection.CATCHUP -> navCatchUp.requestFocus()
                                    WorkspaceSection.SPORTS -> navSports.requestFocus()
                                    WorkspaceSection.NEWSPAPERS -> navNewspapers.requestFocus()
                                    else -> navLiveTv.requestFocus()
                                }
                            }
                        }
                    }
                    // Em VPN:
                    activeSection == WorkspaceSection.VPN -> {
                        if (rvVpnRegions.hasFocus()) {
                            btnToggleVpn.requestFocus()
                        } else if (btnToggleVpn.hasFocus()) {
                            mostrarMenuLateral(navVpn)
                        } else {
                            selecionarSecao(WorkspaceSection.LIVE_TV)
                        }
                    }
                    // Em Definições:
                    activeSection == WorkspaceSection.SETTINGS -> {
                        if (rvSettingsGrid.hasFocus()) {
                            mostrarMenuLateral(navSettings)
                        } else {
                            selecionarSecao(WorkspaceSection.LIVE_TV)
                        }
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
        carregarCachesLocais()
        uiHandler.removeCallbacks(clockTickRunnable)
        uiHandler.post(clockTickRunnable)
        atualizarEstadoServidorEVpn()
        if (player != null && currentStreamId != -1 && activeSection == WorkspaceSection.LIVE_TV) {
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
        // Verificar atualizações sempre que a app volta ao primeiro plano (ex: após fechar reprodutor/definições)
        uiHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                AppUpdateManager.checkForUpdates(this, manual = false)
            }
        }, 1500)
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        uiHandler.removeCallbacks(periodicUpdateCheckRunnable)
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
        btnExpandRail = findViewById(R.id.btnExpandRail)
        btnExpandRail.isFocusable = false
        btnExpandRail.isFocusableInTouchMode = false
        btnExpandRail.setOnClickListener { mostrarMenuLateral() }
        layoutTopPreviewRow = findViewById(R.id.layoutTopPreviewRow)
        layoutTopProgramDetails = findViewById(R.id.layoutTopProgramDetails)
        viewTopHorizontalDivider = findViewById(R.id.viewTopHorizontalDivider)
        layoutLiveTvColumns = findViewById(R.id.layoutLiveTvColumns)
        layoutCatalogSection = findViewById(R.id.layoutCatalogSection)

        tvRailClock = findViewById(R.id.tvRailClock)
        tvRailActiveServer = findViewById(R.id.tvRailActiveServer)
        tvRailEditionBadge = findViewById(R.id.tvRailEditionBadge)
        tvRailEditionBadge.text = if (user.isNotBlank()) "User: $user" else ""
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
        rvLiveGroups.setHasFixedSize(true)
        rvLiveGroups.setItemViewCacheSize(15)

        rvLiveChannels.layoutManager = LinearLayoutManager(this)
        rvLiveChannels.itemAnimator = null
        rvLiveChannels.setHasFixedSize(true)
        rvLiveChannels.setItemViewCacheSize(20)

        rvChannelSchedule.layoutManager = LinearLayoutManager(this)
        rvChannelSchedule.itemAnimator = null
        rvChannelSchedule.setHasFixedSize(true)

        rvOverlayGroups.layoutManager = LinearLayoutManager(this)
        rvOverlayGroups.itemAnimator = null
        rvOverlayGroups.setHasFixedSize(true)

        rvOverlayChannels.layoutManager = LinearLayoutManager(this)
        rvOverlayChannels.itemAnimator = null
        rvOverlayChannels.setHasFixedSize(true)

        rvRecentChannels.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRecentChannels.itemAnimator = null

        rvCatalogGroups.layoutManager = LinearLayoutManager(this)
        rvCatalogGroups.itemAnimator = null
        rvCatalogGroups.setHasFixedSize(true)
        rvCatalogGroups.setItemViewCacheSize(15)

        rvCatalogGrid.layoutManager = GridLayoutManager(this, 5)
        rvCatalogGrid.itemAnimator = null
        rvCatalogGrid.setHasFixedSize(true)
        rvCatalogGrid.setItemViewCacheSize(25)

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

        navLiveTv.setOnClickListener {
            selecionarSecao(WorkspaceSection.LIVE_TV)
            ocultarMenuLateral()
            rvLiveGroups.postDelayed({
                val pos = (liveGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                focarRecyclerViewPosicao(rvLiveGroups, pos)
            }, 80)
        }
        navMovies.setOnClickListener {
            selecionarSecao(WorkspaceSection.MOVIES)
            ocultarMenuLateral()
            rvCatalogGroups.postDelayed({
                catalogGroupAdapter?.selecionarPorId("RECENT")
                focarRecyclerViewPosicao(rvCatalogGroups, 0)
            }, 80)
        }
        navSeries.setOnClickListener {
            selecionarSecao(WorkspaceSection.SERIES)
            ocultarMenuLateral()
            rvCatalogGroups.postDelayed({
                catalogGroupAdapter?.selecionarPorId("RECENT")
                focarRecyclerViewPosicao(rvCatalogGroups, 0)
            }, 80)
        }

        navEpgGuide.setOnClickListener {
            // O Guia TV é a própria vista de TV em Direto: foca diretamente o programa atual no guia EPG
            lastFocusArea = LastFocusArea.CHANNEL_SCHEDULE
            selecionarSecao(WorkspaceSection.LIVE_TV)
            ocultarMenuLateral()
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
        navCatchUp.setOnClickListener {
            selecionarSecao(WorkspaceSection.CATCHUP)
            ocultarMenuLateral()
            rvCatalogGroups.postDelayed({
                val pos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                focarRecyclerViewPosicao(rvCatalogGroups, pos)
            }, 80)
        }
        navSports.setOnClickListener {
            selecionarSecao(WorkspaceSection.SPORTS)
            ocultarMenuLateral()
            rvCatalogGroups.postDelayed({
                val pos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                focarRecyclerViewPosicao(rvCatalogGroups, pos)
            }, 80)
        }
        navNewspapers.setOnClickListener {
            selecionarSecao(WorkspaceSection.NEWSPAPERS)
            ocultarMenuLateral()
            rvCatalogGroups.postDelayed({
                val pos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                focarRecyclerViewPosicao(rvCatalogGroups, pos)
            }, 80)
        }
        navVpn.setOnClickListener {
            if (PiaVpnConfig.isVpnAllowedForCurrentUser(this)) {
                selecionarSecao(WorkspaceSection.VPN)
                ocultarMenuLateral()
                btnToggleVpn.postDelayed({ btnToggleVpn.requestFocus() }, 80)
            } else {
                Toast.makeText(this, "VPN inativa para esta conta no painel.", Toast.LENGTH_SHORT).show()
            }
        }
        navRefresh.setOnClickListener {
            CatalogRefreshDialog.show(this, user, pass) {
                inlineEpgCache.clear()
                allLiveStreamsCache.clear()
                allMoviesCache.clear()
                allSeriesCache.clear()
                allVodCategories = emptyList()
                allSeriesCategories = emptyList()
                try { File(cacheDir, "vod_movies_cache.json").delete() } catch (_: Exception) {}
                try { File(cacheDir, "vod_series_cache.json").delete() } catch (_: Exception) {}
                try { File(cacheDir, "vod_categories_cache.json").delete() } catch (_: Exception) {}
                try { File(cacheDir, "series_categories_cache.json").delete() } catch (_: Exception) {}
                carregarTudoLiveTv()
                SportsAgendaManager.loadOrRefresh(this, forceOnline = true) { matches ->
                    allMatches.clear()
                    allMatches.addAll(matches)
                    if (activeSection == WorkspaceSection.SPORTS) mostrarJogos()
                }
                Toast.makeText(this, "Lista e guia atualizados!", Toast.LENGTH_SHORT).show()
            }
        }
        navSettings.setOnClickListener {
            selecionarSecao(WorkspaceSection.SETTINGS)
            ocultarMenuLateral()
            rvSettingsGrid.postDelayed({
                focarRecyclerViewPosicao(rvSettingsGrid, 0)
            }, 80)
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
        val previousSection = activeSection
        activeSection = sec
        aplicarDimensoesTopo()
        atualizarEstilosMenuLateral()
        configurarCatalogoParaSecao(grelha = true, pesquisa = true)

        val isLiveTv = (sec == WorkspaceSection.LIVE_TV)
        val isCatalog = (sec == WorkspaceSection.MOVIES || sec == WorkspaceSection.SERIES ||
                sec == WorkspaceSection.CATCHUP || sec == WorkspaceSection.SPORTS || sec == WorkspaceSection.NEWSPAPERS)
        val isVpn = (sec == WorkspaceSection.VPN)
        val isSettings = (sec == WorkspaceSection.SETTINGS)

        layoutTopPreviewRow.visibility = if (isLiveTv) View.VISIBLE else View.GONE
        viewTopHorizontalDivider.visibility = if (isLiveTv) View.VISIBLE else View.GONE

        if (!isLiveTv) {
            // Poupança de RAM e CPU na TV Box: parar a TV completamente quando se sai da secção de TV
            player?.stop()
        } else if (previousSection != WorkspaceSection.LIVE_TV) {
            // Retorno à secção de TV: retomar o último canal reproduzido se existir
            currentPlayingChannel?.let { canal ->
                sintonizarCanal(canal, currentPlayingNumber)
            }
        }

        layoutLiveTvColumns.visibility = if (isLiveTv) View.VISIBLE else View.GONE
        layoutCatalogSection.visibility = if (isCatalog) View.VISIBLE else View.GONE
        layoutVpnSection.visibility = if (isVpn) View.VISIBLE else View.GONE
        layoutSettingsSection.visibility = if (isSettings) View.VISIBLE else View.GONE

        if (isCatalog) {
            rvCatalogGroups.adapter = null
            rvCatalogGrid.adapter = null
        }

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
            }
            WorkspaceSection.MOVIES -> {
                layoutCatalogSection.visibility = View.VISIBLE
                btnCatalogSort.visibility = View.VISIBLE
                tvCatalogGroupsHeader.text = "PASTAS DE FILMES"
                etCatalogSearch.hint = "🔍 Pesquisar filmes nesta pasta..."
                definirTopoInformativo("🎬 Catálogo de Filmes", "Pastas VOD", "Escolhe uma pasta à esquerda para explorar os filmes disponíveis.")
                iniciarSecaoFilmes()
            }
            WorkspaceSection.SERIES -> {
                layoutCatalogSection.visibility = View.VISIBLE
                btnCatalogSort.visibility = View.GONE
                tvCatalogGroupsHeader.text = "PASTAS DE SÉRIES"
                etCatalogSearch.hint = "🔍 Pesquisar séries nesta pasta..."
                definirTopoInformativo("🎞️ Catálogo de Séries", "Séries e Temporadas", "Escolhe uma pasta à esquerda para explorar as séries disponíveis.")
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
            if (vpnConnected) {
                PiaVpnManager.disconnect(this)
            }
            if (activeSection == WorkspaceSection.VPN) {
                selecionarSecao(WorkspaceSection.LIVE_TV)
            }
            navVpn.text = "🛡️  VPN: Inativa"
            navVpn.alpha = 0.40f
            navVpn.setTextColor(0xFF94A3B8.toInt())
        } else if (vpnConnected) {
            navVpn.text = "🛡️  VPN: LIGADA"
            navVpn.alpha = 1.0f
            navVpn.setTextColor(0xFF10B981.toInt())
        } else {
            navVpn.text = "🛡️  VPN: OFF"
            navVpn.alpha = 1.0f
            navVpn.setTextColor(0xFFF1F5F9.toInt())
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
        val (p, _) = ExoPlayerHelper.buildOptimizedPlayer(this, playerView, isLiveTv = true)
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

    private fun ocultarMenuLateral() {
        if (isFullScreen) return
        isRailCollapsed = true
        layoutLeftRail.visibility = View.GONE
        viewRailDivider.visibility = View.GONE
        btnExpandRail.visibility = View.VISIBLE
    }

    private fun mostrarMenuLateral(focarItem: View? = null) {
        if (isFullScreen) return
        isRailCollapsed = false
        layoutLeftRail.visibility = View.VISIBLE
        viewRailDivider.visibility = View.VISIBLE
        btnExpandRail.visibility = View.GONE
        if (focarItem != null) {
            focarItem.post { focarItem.requestFocus() }
        } else {
            focarItemMenuAtivo()
        }
    }

    private fun focarItemMenuAtivo() {
        val target = when (activeSection) {
            WorkspaceSection.LIVE_TV -> navLiveTv
            WorkspaceSection.MOVIES -> navMovies
            WorkspaceSection.SERIES -> navSeries
            WorkspaceSection.CATCHUP -> navCatchUp
            WorkspaceSection.SPORTS -> navSports
            WorkspaceSection.NEWSPAPERS -> navNewspapers
            WorkspaceSection.VPN -> navVpn
            WorkspaceSection.SETTINGS -> navSettings
        }
        target.post { target.requestFocus() }
    }

    private fun entrarEmFullscreen() {
        isFullScreen = true
        layoutLeftRail.visibility = View.GONE
        viewRailDivider.visibility = View.GONE
        btnExpandRail.visibility = View.GONE
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
        framePlayerWrapper.clipToOutline = false

        mostrarOsdFullscreen()
    }

    private fun sairDeFullscreen() {
        isFullScreen = false
        fecharGavetaFullscreen()
        layoutRecentChannelsBar.visibility = View.GONE
        layoutFullscreenOsd.visibility = View.GONE
        uiHandler.removeCallbacks(hideOsdRunnable)

        if (isRailCollapsed) {
            layoutLeftRail.visibility = View.GONE
            viewRailDivider.visibility = View.GONE
            btnExpandRail.visibility = View.VISIBLE
        } else {
            layoutLeftRail.visibility = View.VISIBLE
            viewRailDivider.visibility = View.VISIBLE
            btnExpandRail.visibility = View.GONE
        }
        val isLiveTv = (activeSection == WorkspaceSection.LIVE_TV)
        layoutTopPreviewRow.visibility = if (isLiveTv) View.VISIBLE else View.GONE
        viewTopHorizontalDivider.visibility = if (isLiveTv) View.VISIBLE else View.GONE
        layoutTopProgramDetails.visibility = if (isLiveTv) View.VISIBLE else View.GONE
        layoutMiniPlayerBadge.visibility = if (isLiveTv) View.VISIBLE else View.GONE

        layoutLiveTvColumns.visibility = if (isLiveTv) View.VISIBLE else View.GONE
        layoutCatalogSection.visibility = if (activeSection in listOf(WorkspaceSection.MOVIES, WorkspaceSection.SERIES, WorkspaceSection.CATCHUP, WorkspaceSection.SPORTS, WorkspaceSection.NEWSPAPERS)) View.VISIBLE else View.GONE
        layoutVpnSection.visibility = if (activeSection == WorkspaceSection.VPN) View.VISIBLE else View.GONE
        layoutSettingsSection.visibility = if (activeSection == WorkspaceSection.SETTINGS) View.VISIBLE else View.GONE

        if (isLiveTv) {
            aplicarDimensoesTopo()
        }

        restaurarFocoSalvo()
    }

    private fun aplicarDimensoesTopo() {
        if (isFullScreen) return
        val isLiveTv = (activeSection == WorkspaceSection.LIVE_TV)
        if (!isLiveTv) {
            layoutTopPreviewRow.visibility = View.GONE
            viewTopHorizontalDivider.visibility = View.GONE
            return
        }
        layoutTopPreviewRow.visibility = View.VISIBLE
        viewTopHorizontalDivider.visibility = View.VISIBLE

        val lp = layoutTopPreviewRow.layoutParams
        lp.height = dp(196)
        layoutTopPreviewRow.layoutParams = lp
        val pad = dp(10)
        layoutTopPreviewRow.setPadding(pad, pad, pad, pad)

        val playerLp = framePlayerWrapper.layoutParams
        playerLp.width = dp(310)
        playerLp.height = ViewGroup.LayoutParams.MATCH_PARENT
        framePlayerWrapper.layoutParams = playerLp

        layoutTopProgressRow.visibility = View.VISIBLE
        tvTopProgramDesc.maxLines = 3
        tvMiniPlayerHint.text = "OK ➔ Ecrã Inteiro"
        tvTopHeaderTitle.textSize = 17f
        layoutTopPreviewRow.requestLayout()
        framePlayerWrapper.requestLayout()

        framePlayerWrapper.background = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(dp(if (framePlayerWrapper.hasFocus()) 2 else 1), if (framePlayerWrapper.hasFocus()) 0xFF00E5FF.toInt() else 0xFF1E293B.toInt())
            cornerRadius = dp(6).toFloat()
        }
        framePlayerWrapper.clipToOutline = true
        layoutMiniPlayerBadge.visibility = View.VISIBLE
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

        // Bloqueio de navegação no Menu Lateral: não permitir saltar para fora ao andar para baixo em Definições,
        // para cima em TV Direto, ou para a esquerda no rail.
        if (isDown) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && navSettings.hasFocus()) {
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && navLiveTv.hasFocus()) {
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && layoutLeftRail.hasFocus()) {
                return true
            }
        }

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
                            mostrarMenuLateral(navLiveTv)
                            return true
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    when {
                        layoutLeftRail.hasFocus() -> {
                            ocultarMenuLateral()
                            val pos = (liveGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            rvLiveGroups.scrollToPosition(pos)
                            rvLiveGroups.post {
                                rvLiveGroups.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus()
                                    ?: focarRecyclerViewPosicao(rvLiveGroups, pos)
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
                            mostrarMenuLateral(navVpn)
                            return true
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    when {
                        layoutLeftRail.hasFocus() -> {
                            ocultarMenuLateral()
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
                    val focusedChild = rvSettingsGrid.findFocus()
                    val pos = if (focusedChild != null) {
                        rvSettingsGrid.getChildAdapterPosition(rvSettingsGrid.findContainingItemView(focusedChild) ?: focusedChild)
                    } else -1

                    if (pos != -1) {
                        if (pos % 2 == 0) {
                            // Está na coluna da esquerda (pos 0, 2, 4, 6): volta para Definições no menu lateral
                            mostrarMenuLateral(navSettings)
                            return true
                        } else {
                            // Está na coluna da direita (pos 1, 3, 5): move para o cartão da esquerda na mesma linha!
                            focarRecyclerViewPosicao(rvSettingsGrid, pos - 1)
                            return true
                        }
                    } else if (rvSettingsGrid.hasFocus()) {
                        mostrarMenuLateral(navSettings)
                        return true
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (layoutLeftRail.hasFocus() && (rvSettingsGrid.adapter?.itemCount ?: 0) > 0) {
                        ocultarMenuLateral()
                        focarRecyclerViewPosicao(rvSettingsGrid, 0)
                        return true
                    }
                    val focusedChild = rvSettingsGrid.findFocus()
                    val pos = if (focusedChild != null) {
                        rvSettingsGrid.getChildAdapterPosition(rvSettingsGrid.findContainingItemView(focusedChild) ?: focusedChild)
                    } else -1

                    if (pos != -1) {
                        val totalCount = rvSettingsGrid.adapter?.itemCount ?: 0
                        if (pos % 2 == 0 && pos + 1 < totalCount) {
                            // Está na coluna da esquerda e tem cartão à direita: move para a coluna da direita!
                            focarRecyclerViewPosicao(rvSettingsGrid, pos + 1)
                            return true
                        } else {
                            // Já está na coluna da direita (ou pos 6 sem item à direita): bloqueia para não saltar fora
                            return true
                        }
                    }
                }
            } else if (isDown && activeSection != WorkspaceSection.LIVE_TV) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    when {
                        rvCatalogGrid.hasFocus() -> {
                            val lm = rvCatalogGrid.layoutManager
                            val spanCount = if (lm is GridLayoutManager) lm.spanCount else 1
                            val focusedChild = rvCatalogGrid.findFocus()
                            val pos = if (focusedChild != null) {
                                rvCatalogGrid.getChildAdapterPosition(rvCatalogGrid.findContainingItemView(focusedChild) ?: focusedChild)
                            } else 0

                            if (spanCount > 1 && pos % spanCount != 0) {
                                // Move para a coluna anterior na mesma grelha
                                focarRecyclerViewPosicao(rvCatalogGrid, pos - 1)
                                return true
                            }
                            val groupPos = (catalogGroupAdapter?.posicaoSelecionada() ?: 0).coerceAtLeast(0)
                            focarRecyclerViewPosicao(rvCatalogGroups, groupPos)
                            return true
                        }
                        rvCatalogGroups.hasFocus() -> {
                            val target = when (activeSection) {
                                WorkspaceSection.MOVIES -> navMovies
                                WorkspaceSection.SERIES -> navSeries
                                WorkspaceSection.CATCHUP -> navCatchUp
                                WorkspaceSection.SPORTS -> navSports
                                WorkspaceSection.NEWSPAPERS -> navNewspapers
                                else -> navLiveTv
                            }
                            mostrarMenuLateral(target)
                            return true
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    when {
                        layoutLeftRail.hasFocus() -> {
                            ocultarMenuLateral()
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

                        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        val savedGroupId = prefs.getString("last_live_group_id", null)

                        var initialIdx = -1
                        if (!savedGroupId.isNullOrBlank()) {
                            initialIdx = listaCompleta.indexOfFirst { it.categoryId == savedGroupId }
                        }
                        if (initialIdx == -1) {
                            initialIdx = if (listaCompleta.size > 3) 3 else 0
                        }

                        liveGroupAdapter = GroupListAdapter(
                            items = listaCompleta.map { it.categoryId to it.categoryName },
                            initialSelected = initialIdx,
                            countProvider = { id -> obterContagemGrupoLive(id) },
                            onSelect = { id, name -> selecionarGrupoLive(id, name, iniciarCanal = false) }
                        )
                        rvLiveGroups.adapter = liveGroupAdapter
                        rvLiveGroups.scrollToPosition(initialIdx)

                        overlayGroupAdapter = GroupListAdapter(
                            items = listaCompleta.map { it.categoryId to it.categoryName },
                            initialSelected = initialIdx,
                            countProvider = { id -> obterContagemGrupoLive(id) },
                            onSelect = { id, name -> selecionarGrupoLive(id, name, iniciarCanal = false) }
                        )
                        rvOverlayGroups.adapter = overlayGroupAdapter
                        rvOverlayGroups.scrollToPosition(initialIdx)

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
                    val lastId = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getInt("last_live_stream_id", -1)
                    val matchedStream = if (currentStreamId == -1 && lastId != -1) {
                        streams.firstOrNull { it.streamId == lastId }
                    } else null
                    runOnUiThread {
                        liveGroupAdapter?.notifyDataSetChanged()
                        overlayGroupAdapter?.notifyDataSetChanged()
                        if (matchedByTarget != null) {
                            intent.removeExtra("TARGET_CHANNEL_NAME")
                            sintonizarCanal(matchedByTarget, 1)
                        } else if (matchedStream != null && currentStreamId == -1) {
                            sintonizarCanal(matchedStream, 1)
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

        getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("last_live_group_id", groupId)
            .putString("last_live_group_name", groupName)
            .apply()

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
                    override fun onFailure(call: Call, e: IOException) {
                        focarConteudoAoCarregar = false
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: return
                        try {
                            val type = object : TypeToken<List<LiveStream>>() {}.type
                            val canais: List<LiveStream> = gson.fromJson(json, type) ?: emptyList()
                            runOnUiThread {
                                CategoryCountHelper.saveLiveCounts(this@EagleTvActivity, mapOf(groupId to canais.size))
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

        if (focarConteudoAoCarregar) {
            focarConteudoAoCarregar = false
            rvLiveChannels.post {
                val targetPos = if (currentStreamId != -1) {
                    val idx = canais.indexOfFirst { it.streamId == currentStreamId }
                    if (idx >= 0) idx else 0
                } else 0
                focarRecyclerViewPosicao(rvLiveChannels, targetPos)
            }
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
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
        prefs.putInt("last_live_stream_id", canal.streamId)
        prefs.putString("last_live_channel_name", canal.name)
        if (currentLiveGroupId.isNotBlank()) {
            prefs.putString("last_live_group_id", currentLiveGroupId)
            prefs.putString("last_live_group_name", currentLiveGroupName)
        } else if (!canal.categoryId.isNullOrBlank()) {
            prefs.putString("last_live_group_id", canal.categoryId)
        }
        prefs.apply()

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
        if (added) liveFavIdsCache.add(canal.streamId) else liveFavIdsCache.remove(canal.streamId)
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
        if (pendingInlineEpgRequests.size >= 3 || !pendingInlineEpgRequests.add(streamId)) {
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

    private fun salvarCategoriasFilmesCache(categorias: List<VodCategory>) {
        try {
            val file = File(cacheDir, "vod_categories_cache.json")
            val json = gson.toJson(categorias)
            file.writeText(json)
        } catch (_: Exception) {}
    }

    private fun carregarCategoriasFilmesCache() {
        try {
            val file = File(cacheDir, "vod_categories_cache.json")
            if (file.exists() && file.length() > 0) {
                val type = object : TypeToken<List<VodCategory>>() {}.type
                val cached: List<VodCategory>? = FileReader(file).use { gson.fromJson(it, type) }
                if (!cached.isNullOrEmpty()) {
                    allVodCategories = cached
                }
            }
        } catch (_: Exception) {}
    }

    private fun iniciarSecaoFilmes() {
        etCatalogSearch.setText("")
        carregarTodosFilmesEmCache()
        if (allVodCategories.isEmpty()) {
            carregarCategoriasFilmesCache()
        }
        if (allVodCategories.isNotEmpty()) {
            montarCategoriasFilmes(allVodCategories)
            rvCatalogGroups.post {
                focarRecyclerViewPosicao(rvCatalogGroups, 0)
            }
            return
        }

        // Categorias base imediatas para ter foco e ecrã completo logo à 1ª vez sem atrasos
        val baseCategorias = listOf(
            VodCategory("RECENT", "🕒 ADICIONADOS RECENTEMENTE", 0),
            VodCategory("FAVORITES", "★ FAVORITOS", 0),
            VodCategory("RESUME", "▶ CONTINUAR A VER", 0),
            VodCategory("ALL_MOVIES", "🌟 TODOS OS FILMES", 0)
        )
        montarCategoriasFilmes(baseCategorias)
        rvCatalogGroups.post {
            focarRecyclerViewPosicao(rvCatalogGroups, 0)
        }

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                focarConteudoAoCarregar = false
            }
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<VodCategory>>() {}.type
                    val remotas: List<VodCategory> = gson.fromJson(json, type) ?: emptyList()
                    val completas = mutableListOf<VodCategory>()
                    completas.add(VodCategory("RECENT", "🕒 ADICIONADOS RECENTEMENTE", 0))
                    completas.add(VodCategory("FAVORITES", "★ FAVORITOS", 0))
                    completas.add(VodCategory("RESUME", "▶ CONTINUAR A VER", 0))
                    completas.add(VodCategory("ALL_MOVIES", "🌟 TODOS OS FILMES", 0))
                    completas.addAll(remotas)
                    allVodCategories = completas
                    salvarCategoriasFilmesCache(completas)
                    runOnUiThread {
                        if (activeSection == WorkspaceSection.MOVIES) {
                            if (movieGroupAdapter != null && rvCatalogGroups.adapter === movieGroupAdapter) {
                                // Atualiza os itens preservando a categoria atualmente focada/selecionada pelo utilizador
                                movieGroupAdapter?.updateItems(completas.map { it.categoryId to it.categoryName })
                            } else {
                                montarCategoriasFilmes(completas)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun montarCategoriasFilmes(categorias: List<VodCategory>) {
        val initialIdx = 0
        movieGroupAdapter = GroupListAdapter(
            items = categorias.map { it.categoryId to it.categoryName },
            initialSelected = initialIdx,
            countProvider = { id ->
                when (id) {
                    "ALL_MOVIES" -> {
                        val count = allMoviesCache.size
                        if (count > 0) count else CategoryCountHelper.getVodCount(this, "ALL_MOVIES")
                    }
                    "FAVORITES" -> movieFavIdsCache.size
                    "RESUME" -> vodResumePosCache.size
                    "RECENT" -> minOf(40, if (allMoviesCache.isNotEmpty()) allMoviesCache.size else CategoryCountHelper.getVodCount(this, "RECENT"))
                    else -> CategoryCountHelper.getVodCount(this, id)
                }
            },
            onSelect = { id, _ -> selecionarCategoriaFilmes(id) }
        )
        rvCatalogGroups.adapter = movieGroupAdapter
        if (categorias.isNotEmpty()) {
            selecionarCategoriaFilmes(categorias[initialIdx].categoryId)
        }
    }

    private fun carregarTodosFilmesEmCache(onDone: (() -> Unit)? = null) {
        if (allMoviesCache.isNotEmpty()) {
            onDone?.invoke()
            return
        }
        synchronized(pendingMoviesCacheCallbacks) {
            if (onDone != null) pendingMoviesCacheCallbacks.add(onDone)
            if (isLoadingMoviesCache) return
            isLoadingMoviesCache = true
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Tentar carregar instantaneamente da cache em disco se existir
            val cacheFile = File(cacheDir, "vod_movies_cache.json")
            if (allMoviesCache.isEmpty() && cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    val type = object : TypeToken<List<VodStream>>() {}.type
                    val filmes: List<VodStream> = FileReader(cacheFile).use { reader ->
                        gson.fromJson(reader, type)
                    } ?: emptyList()
                    if (filmes.isNotEmpty()) {
                        val sortedFilmes = filmes.sortedBy { it.name.lowercase() }
                        synchronized(allMoviesCache) {
                            if (allMoviesCache.isEmpty()) {
                                allMoviesCache.addAll(sortedFilmes)
                            }
                        }
                        val counts = filmes.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().toMutableMap()
                        counts["ALL_MOVIES"] = filmes.size
                        CategoryCountHelper.saveVodCounts(this@EagleTvActivity, counts)
                        withContext(Dispatchers.Main) {
                            movieGroupAdapter?.notifyDataSetChanged()
                            catalogGroupAdapter?.notifyDataSetChanged()
                            val callbacks = synchronized(pendingMoviesCacheCallbacks) {
                                val list = pendingMoviesCacheCallbacks.toList()
                                pendingMoviesCacheCallbacks.clear()
                                list
                            }
                            callbacks.forEach { it.invoke() }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Fazer pedido à rede para atualizar a cache
            val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_streams"
            val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    synchronized(pendingMoviesCacheCallbacks) {
                        isLoadingMoviesCache = false
                        pendingMoviesCacheCallbacks.clear()
                    }
                    runOnUiThread {
                        if (activeSection == WorkspaceSection.MOVIES && allMoviesCache.isEmpty()) {
                            tvCatalogEmpty.text = "Não foi possível carregar os filmes. Verifica a ligação."
                            tvCatalogEmpty.visibility = View.VISIBLE
                        }
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body ?: run {
                        synchronized(pendingMoviesCacheCallbacks) {
                            isLoadingMoviesCache = false
                            pendingMoviesCacheCallbacks.clear()
                        }
                        return
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val tempFile = File(cacheDir, "vod_movies_cache.tmp")
                            body.byteStream().use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val finalFile = File(cacheDir, "vod_movies_cache.json")
                            tempFile.renameTo(finalFile)

                            val type = object : TypeToken<List<VodStream>>() {}.type
                            val filmes: List<VodStream> = FileReader(finalFile).use { reader ->
                                gson.fromJson(reader, type)
                            } ?: emptyList()

                            val sortedFilmes = filmes.sortedBy { it.name.lowercase() }
                            synchronized(allMoviesCache) {
                                allMoviesCache.clear()
                                allMoviesCache.addAll(sortedFilmes)
                            }
                            val counts = filmes.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().toMutableMap()
                            counts["ALL_MOVIES"] = filmes.size
                            CategoryCountHelper.saveVodCounts(this@EagleTvActivity, counts)

                            val callbacks = synchronized(pendingMoviesCacheCallbacks) {
                                val list = pendingMoviesCacheCallbacks.toList()
                                pendingMoviesCacheCallbacks.clear()
                                isLoadingMoviesCache = false
                                list
                            }
                            withContext(Dispatchers.Main) {
                                movieGroupAdapter?.notifyDataSetChanged()
                                catalogGroupAdapter?.notifyDataSetChanged()
                                callbacks.forEach { it.invoke() }
                            }
                        } catch (_: Exception) {
                            synchronized(pendingMoviesCacheCallbacks) {
                                isLoadingMoviesCache = false
                                pendingMoviesCacheCallbacks.clear()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun selecionarCategoriaFilmes(catId: String) {
        currentVodCategoryId = catId
        catalogGroupAdapter?.selecionarPorId(catId)
        val catNome = allVodCategories.firstOrNull { it.categoryId == catId }?.categoryName ?: "Filmes"
        definirTopoInformativo("🎬 $catNome", "Pasta de Filmes", "Navega pelos filmes com o comando e carrega OK para ver a sinopse.")

        val isAllMovies = (catId == "ALL_MOVIES")
        (etCatalogSearch.parent as? View)?.visibility = if (isAllMovies) View.VISIBLE else View.GONE
        btnCatalogSort.visibility = if (isAllMovies) View.VISIBLE else View.GONE

        if ((isAllMovies || catId == "RECENT") && allMoviesCache.isEmpty()) {
            tvCatalogEmpty.text = if (catId == "RECENT") "⏳ A carregar adicionados recentemente... Por favor aguarda." else "⏳ A carregar catálogo de filmes... Por favor aguarda."
            tvCatalogEmpty.visibility = View.VISIBLE
            catalogPosterAdapter = null
            rvCatalogGrid.adapter = null
        }

        when (catId) {
            "ALL_MOVIES" -> {
                carregarTodosFilmesEmCache {
                    currentVodCategoryMovies = allMoviesCache
                    atualizarGrelhaFilmes()
                }
            }
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
                    override fun onFailure(call: Call, e: IOException) {
                        focarConteudoAoCarregar = false
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: return
                        try {
                            val type = object : TypeToken<List<VodStream>>() {}.type
                            val filmes: List<VodStream> = gson.fromJson(json, type) ?: emptyList()
                            runOnUiThread {
                                if (activeSection == WorkspaceSection.MOVIES && currentVodCategoryId == catId) {
                                    currentVodCategoryMovies = filmes
                                    CategoryCountHelper.saveVodCounts(this@EagleTvActivity, mapOf(catId to filmes.size))
                                    atualizarGrelhaFilmes()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    }

    private fun atualizarGrelhaFilmes() {
        filterJob?.cancel()
        val q = etCatalogSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val movies = currentVodCategoryMovies
        val sortMode = currentVodSortMode

        if (movies.isEmpty()) {
            mostrarItensNoCatalogo(emptyList())
            return
        }

        filterJob = lifecycleScope.launch(Dispatchers.Default) {
            var filtrados = if (q.isEmpty()) movies else movies.filter {
                it.name.lowercase().contains(q)
            }
            filtrados = when (sortMode) {
                1 -> filtrados.sortedBy { it.name.lowercase() }
                2 -> filtrados.sortedByDescending { it.streamId }
                3 -> filtrados.sortedByDescending { it.rating_5based ?: it.rating?.toDoubleOrNull() ?: 0.0 }
                else -> filtrados
            }

            val items = filtrados.map { CatalogPosterItem.Movie(it) }
            withContext(Dispatchers.Main) {
                if (activeSection == WorkspaceSection.MOVIES) {
                    mostrarItensNoCatalogo(items)
                }
            }
        }
    }

    private fun salvarCategoriasSeriesCache(categorias: List<SeriesCategory>) {
        try {
            val file = File(cacheDir, "series_categories_cache.json")
            val json = gson.toJson(categorias)
            file.writeText(json)
        } catch (_: Exception) {}
    }

    private fun carregarCategoriasSeriesCache() {
        try {
            val file = File(cacheDir, "series_categories_cache.json")
            if (file.exists() && file.length() > 0) {
                val type = object : TypeToken<List<SeriesCategory>>() {}.type
                val cached: List<SeriesCategory>? = FileReader(file).use { gson.fromJson(it, type) }
                if (!cached.isNullOrEmpty()) {
                    allSeriesCategories = cached
                }
            }
        } catch (_: Exception) {}
    }

    private fun iniciarSecaoSeries() {
        etCatalogSearch.setText("")
        carregarTodasSeriesEmCache()
        if (allSeriesCategories.isEmpty()) {
            carregarCategoriasSeriesCache()
        }
        if (allSeriesCategories.isNotEmpty()) {
            montarCategoriasSeries(allSeriesCategories)
            rvCatalogGroups.post {
                focarRecyclerViewPosicao(rvCatalogGroups, 0)
            }
            return
        }

        // Categorias base imediatas para ter foco e ecrã completo logo à 1ª vez sem atrasos
        val baseCategorias = listOf(
            SeriesCategory("RECENT", "🕒 ADICIONADAS RECENTEMENTE"),
            SeriesCategory("FAVORITES", "★ FAVORITOS"),
            SeriesCategory("ALL_SERIES", "🌟 TODAS AS SÉRIES")
        )
        montarCategoriasSeries(baseCategorias)
        rvCatalogGroups.post {
            focarRecyclerViewPosicao(rvCatalogGroups, 0)
        }

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                focarConteudoAoCarregar = false
            }
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<SeriesCategory>>() {}.type
                    val remotas: List<SeriesCategory> = gson.fromJson(json, type) ?: emptyList()
                    val completas = mutableListOf<SeriesCategory>()
                    completas.add(SeriesCategory("RECENT", "🕒 ADICIONADAS RECENTEMENTE"))
                    completas.add(SeriesCategory("FAVORITES", "★ FAVORITOS"))
                    completas.add(SeriesCategory("ALL_SERIES", "🌟 TODAS AS SÉRIES"))
                    completas.addAll(remotas)
                    allSeriesCategories = completas
                    salvarCategoriasSeriesCache(completas)
                    runOnUiThread {
                        if (activeSection == WorkspaceSection.SERIES) {
                            if (seriesGroupAdapter != null && rvCatalogGroups.adapter === seriesGroupAdapter) {
                                // Atualiza os itens preservando a categoria atualmente focada/selecionada pelo utilizador
                                seriesGroupAdapter?.updateItems(completas.map { it.categoryId to it.categoryName })
                            } else {
                                montarCategoriasSeries(completas)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun montarCategoriasSeries(categorias: List<SeriesCategory>) {
        val initialIdx = 0
        seriesGroupAdapter = GroupListAdapter(
            items = categorias.map { it.categoryId to it.categoryName },
            initialSelected = initialIdx,
            countProvider = { id ->
                when (id) {
                    "ALL_SERIES" -> {
                        val count = allSeriesCache.size
                        if (count > 0) count else CategoryCountHelper.getSeriesCount(this, "ALL_SERIES")
                    }
                    "FAVORITES" -> seriesFavIdsCache.size
                    "RECENT" -> minOf(40, if (allSeriesCache.isNotEmpty()) allSeriesCache.size else CategoryCountHelper.getSeriesCount(this, "RECENT"))
                    else -> CategoryCountHelper.getSeriesCount(this, id)
                }
            },
            onSelect = { id, _ -> selecionarCategoriaSeries(id) }
        )
        rvCatalogGroups.adapter = seriesGroupAdapter
        if (categorias.isNotEmpty()) {
            selecionarCategoriaSeries(categorias[initialIdx].categoryId)
        }
    }

    private fun carregarTodasSeriesEmCache(onDone: (() -> Unit)? = null) {
        if (allSeriesCache.isNotEmpty()) {
            onDone?.invoke()
            return
        }
        synchronized(pendingSeriesCallbacks) {
            if (onDone != null) pendingSeriesCallbacks.add(onDone)
            if (isLoadingSeriesCache) return
            isLoadingSeriesCache = true
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Tentar carregar instantaneamente da cache em disco se existir
            val cacheFile = File(cacheDir, "vod_series_cache.json")
            if (allSeriesCache.isEmpty() && cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    val type = object : TypeToken<List<SeriesItem>>() {}.type
                    val series: List<SeriesItem> = FileReader(cacheFile).use { reader ->
                        gson.fromJson(reader, type)
                    } ?: emptyList()
                    if (series.isNotEmpty()) {
                        val sortedSeries = series.sortedBy { it.name.lowercase() }
                        synchronized(allSeriesCache) {
                            if (allSeriesCache.isEmpty()) {
                                allSeriesCache.addAll(sortedSeries)
                            }
                        }
                        val counts = series.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().toMutableMap()
                        counts["ALL_SERIES"] = series.size
                        CategoryCountHelper.saveSeriesCounts(this@EagleTvActivity, counts)
                        withContext(Dispatchers.Main) {
                            seriesGroupAdapter?.notifyDataSetChanged()
                            catalogGroupAdapter?.notifyDataSetChanged()
                            val callbacks = synchronized(pendingSeriesCallbacks) {
                                val list = pendingSeriesCallbacks.toList()
                                pendingSeriesCallbacks.clear()
                                list
                            }
                            callbacks.forEach { it.invoke() }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Fazer pedido à rede para atualizar a cache
            val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series"
            val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    synchronized(pendingSeriesCallbacks) {
                        isLoadingSeriesCache = false
                        pendingSeriesCallbacks.clear()
                    }
                    runOnUiThread {
                        if (activeSection == WorkspaceSection.SERIES && allSeriesCache.isEmpty()) {
                            tvCatalogEmpty.text = "Não foi possível carregar as séries. Verifica a ligação."
                            tvCatalogEmpty.visibility = View.VISIBLE
                        }
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body ?: run {
                        synchronized(pendingSeriesCallbacks) {
                            isLoadingSeriesCache = false
                            pendingSeriesCallbacks.clear()
                        }
                        return
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val tempFile = File(cacheDir, "vod_series_cache.tmp")
                            body.byteStream().use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            val finalFile = File(cacheDir, "vod_series_cache.json")
                            tempFile.renameTo(finalFile)

                            val type = object : TypeToken<List<SeriesItem>>() {}.type
                            val series: List<SeriesItem> = FileReader(finalFile).use { reader ->
                                gson.fromJson(reader, type)
                            } ?: emptyList()

                            val sortedSeries = series.sortedBy { it.name.lowercase() }
                            synchronized(allSeriesCache) {
                                allSeriesCache.clear()
                                allSeriesCache.addAll(sortedSeries)
                            }
                            val counts = series.mapNotNull { it.categoryId }.groupingBy { it }.eachCount().toMutableMap()
                            counts["ALL_SERIES"] = series.size
                            CategoryCountHelper.saveSeriesCounts(this@EagleTvActivity, counts)

                            val callbacks = synchronized(pendingSeriesCallbacks) {
                                val list = pendingSeriesCallbacks.toList()
                                pendingSeriesCallbacks.clear()
                                isLoadingSeriesCache = false
                                list
                            }
                            withContext(Dispatchers.Main) {
                                seriesGroupAdapter?.notifyDataSetChanged()
                                catalogGroupAdapter?.notifyDataSetChanged()
                                callbacks.forEach { it.invoke() }
                            }
                        } catch (_: Exception) {
                            synchronized(pendingSeriesCallbacks) {
                                isLoadingSeriesCache = false
                                pendingSeriesCallbacks.clear()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun selecionarCategoriaSeries(catId: String) {
        currentSeriesCategoryId = catId
        catalogGroupAdapter?.selecionarPorId(catId)
        val catNome = allSeriesCategories.firstOrNull { it.categoryId == catId }?.categoryName ?: "Séries"
        definirTopoInformativo("🎞️ $catNome", "Pasta de Séries", "Navega pelas séries com o comando e carrega OK para ver episódios.")

        val isAllSeries = (catId == "ALL_SERIES")
        (etCatalogSearch.parent as? View)?.visibility = if (isAllSeries) View.VISIBLE else View.GONE
        btnCatalogSort.visibility = View.GONE

        if ((isAllSeries || catId == "RECENT") && allSeriesCache.isEmpty()) {
            tvCatalogEmpty.text = if (catId == "RECENT") "⏳ A carregar adicionadas recentemente... Por favor aguarda." else "⏳ A carregar catálogo de séries... Por favor aguarda."
            tvCatalogEmpty.visibility = View.VISIBLE
            catalogPosterAdapter = null
            rvCatalogGrid.adapter = null
        }

        when (catId) {
            "ALL_SERIES" -> {
                carregarTodasSeriesEmCache {
                    currentSeriesCategoryItems = allSeriesCache
                    atualizarGrelhaSeries()
                }
            }
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
                    override fun onFailure(call: Call, e: IOException) {
                        focarConteudoAoCarregar = false
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val json = response.body?.string() ?: return
                        try {
                            val type = object : TypeToken<List<SeriesItem>>() {}.type
                            val series: List<SeriesItem> = gson.fromJson(json, type) ?: emptyList()
                            runOnUiThread {
                                if (activeSection == WorkspaceSection.SERIES && currentSeriesCategoryId == catId) {
                                    currentSeriesCategoryItems = series
                                    CategoryCountHelper.saveSeriesCounts(this@EagleTvActivity, mapOf(catId to series.size))
                                    atualizarGrelhaSeries()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                })
            }
        }
    }

    private fun atualizarGrelhaSeries() {
        seriesFilterJob?.cancel()
        val q = etCatalogSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val seriesList = currentSeriesCategoryItems

        if (seriesList.isEmpty()) {
            mostrarItensNoCatalogo(emptyList())
            return
        }

        seriesFilterJob = lifecycleScope.launch(Dispatchers.Default) {
            val filtradas = if (q.isEmpty()) seriesList else seriesList.filter {
                it.name.lowercase().contains(q)
            }
            val items = filtradas.map { CatalogPosterItem.Series(it) }
            withContext(Dispatchers.Main) {
                if (activeSection == WorkspaceSection.SERIES) {
                    mostrarItensNoCatalogo(items)
                }
            }
        }
    }

    private fun mostrarItensNoCatalogo(items: List<CatalogPosterItem>) {
        if (items.isEmpty()) {
            val isLoading = (activeSection == WorkspaceSection.MOVIES && isLoadingMoviesCache) ||
                            (activeSection == WorkspaceSection.SERIES && isLoadingSeriesCache)
            tvCatalogEmpty.text = if (isLoading) {
                "⏳ A carregar catálogo... Por favor aguarda."
            } else {
                "Nenhum conteúdo encontrado nesta seleção."
            }
            tvCatalogEmpty.visibility = View.VISIBLE
        } else {
            tvCatalogEmpty.visibility = View.GONE
        }

        catalogPosterAdapter = CatalogPosterAdapter(
            items = items,
            onFocused = { item -> agendarHeroPreviewCatalogo(item) },
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
                        if (added) movieFavIdsCache.add(key) else movieFavIdsCache.remove(key)
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
                        if (added) seriesFavIdsCache.add(key) else seriesFavIdsCache.remove(key)
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

        if (focarConteudoAoCarregar) {
            focarConteudoAoCarregar = false
            if (items.isNotEmpty()) {
                rvCatalogGrid.post {
                    focarRecyclerViewPosicao(rvCatalogGrid, 0)
                }
            }
        }
    }

    private fun agendarHeroPreviewCatalogo(item: CatalogPosterItem) {
        heroPreviewRunnable?.let { uiHandler.removeCallbacks(it) }
        val r = Runnable {
            if (!isFinishing && !isDestroyed) {
                atualizarHeroPreviewCatalogo(item)
            }
        }
        heroPreviewRunnable = r
        uiHandler.postDelayed(r, 220L)
    }

    private fun atualizarHeroPreviewCatalogo(item: CatalogPosterItem) {
        layoutTopProgressRow.visibility = View.GONE
        tvTopCatchupBadge.visibility = View.GONE

        when (item) {
            is CatalogPosterItem.Movie -> {
                val f = item.vod
                currentPreviewVodStreamId = f.streamId
                tvTopHeaderTitle.text = f.name
                val ratingStr = f.rating?.takeIf { it.isNotBlank() && it != "0" } ?: f.rating_5based?.toString()
                tvTopHeaderSubtitle.text = if (!ratingStr.isNullOrBlank()) "🎬 FILME VOD  •  ★ $ratingStr" else "🎬 FILME VOD"
                tvTopLiveBadge.text = (f.containerExtension ?: "MP4").uppercase(Locale.getDefault())
                tvTopRemoteHints.text = "[OK] Abrir Detalhes / Ver  •  [Long OK] ★ Favorito"

                val isFav = movieFavIdsCache.contains(f.streamId.toString())
                tvTopFavBadge.visibility = if (isFav) View.VISIBLE else View.GONE

                if (!f.streamIcon.isNullOrBlank()) {
                    Glide.with(this)
                        .load(f.streamIcon)
                        .override(dp(80), dp(110))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate()
                        .into(ivTopHeaderLogo)
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
                currentPreviewVodStreamId = -1
                tvTopHeaderTitle.text = s.name
                val ratingStr = s.rating?.takeIf { it.isNotBlank() && it != "0" }
                tvTopHeaderSubtitle.text = if (!ratingStr.isNullOrBlank()) "🎞️ SÉRIE TV  •  ★ $ratingStr" else "🎞️ SÉRIE TV"
                tvTopLiveBadge.text = "SÉRIE"
                tvTopRemoteHints.text = "[OK] Ver Temporadas & Episódios  •  [Long OK] ★ Favorito"

                val isFav = seriesFavIdsCache.contains(s.seriesId.toString())
                tvTopFavBadge.visibility = if (isFav) View.VISIBLE else View.GONE

                if (!s.cover.isNullOrBlank()) {
                    Glide.with(this)
                        .load(s.cover)
                        .override(dp(80), dp(110))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate()
                        .into(ivTopHeaderLogo)
                } else {
                    ivTopHeaderLogo.setImageResource(R.mipmap.ic_launcher)
                }

                tvTopProgramDesc.text = s.plot?.takeIf { it.isNotBlank() }
                    ?: "Carrega OK para escolher a temporada e os episódios desta série."
                tvTopNextProgram.text = s.releaseDate?.takeIf { it.isNotBlank() }?.let { "Lançamento: $it" }.orEmpty()
            }
            is CatalogPosterItem.Channel -> {
                currentPreviewVodStreamId = -1
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
                    runOnUiThread {
                        if (currentPreviewVodStreamId == streamId && activeSection == WorkspaceSection.MOVIES) {
                            tvTopProgramDesc.text = (info.plot ?: info.description).orEmpty()
                                .ifBlank { "Carrega OK para abrir a ficha completa e reproduzir este filme." }
                            tvTopNextProgram.text = listOfNotNull(
                                info.genre?.takeIf { it.isNotBlank() },
                                info.releaseDate?.takeIf { it.isNotBlank() },
                                info.duration?.takeIf { it.isNotBlank() }
                            ).joinToString("  •  ")
                        }
                    }
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
        tvTopLiveBadge.visibility = View.GONE
        layoutTopProgressRow.visibility = View.GONE
        tvTopProgramDesc.text = descricao
        tvTopNextProgram.text = ""
        tvTopRemoteHints.text = "[OK] Abrir  •  [◄] Voltar"
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

        selecionarSecao(WorkspaceSection.LIVE_TV)
        ocultarMenuLateral()

        val catId = canal.categoryId?.takeIf { it.isNotBlank() }
        val cat = if (catId != null) allLiveCategories.firstOrNull { it.categoryId == catId } else null
        val targetGroupId = cat?.categoryId ?: "ALL_LIVE"
        val targetGroupName = cat?.categoryName ?: "Todos os Canais"

        focarConteudoAoCarregar = true
        selecionarGrupoLive(targetGroupId, targetGroupName, iniciarCanal = false)
        sintonizarCanal(canal, 1)

        rvLiveChannels.postDelayed({
            val pos = currentGroupChannels.indexOfFirst { it.streamId == canal.streamId }
            if (pos >= 0) {
                lastFocusArea = LastFocusArea.LIVE_CHANNELS
                lastLiveChannelPos = pos
                hasSavedFocus = true
                focarRecyclerViewPosicao(rvLiveChannels, pos)
            } else {
                val p = liveChannelAdapter?.selecionarCanalPorId(canal.streamId) ?: -1
                if (p >= 0) focarRecyclerViewPosicao(rvLiveChannels, p)
            }
        }, 300)
    }

    // --- JOGOS DO DIA ---
    private fun iniciarSecaoJogos() {
        selectedLeague = leaguesList[0]
        sportsGroupAdapter = GroupListAdapter(
            items = leaguesList.map { it to it },
            initialSelected = 0,
            countProvider = { 0 },
            onSelect = { id, _ ->
                selectedLeague = id
                mostrarJogos()
            }
        )
        rvCatalogGroups.adapter = sportsGroupAdapter
        garantirCanaisEmCache { }

        SportsAgendaManager.loadOrRefresh(this, forceOnline = false) { matches ->
            allMatches.clear()
            allMatches.addAll(matches)
            if (activeSection == WorkspaceSection.SPORTS) {
                mostrarJogos()
            }
        }
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
        newspaperGroupAdapter = GroupListAdapter(
            items = newspaperCategories.map { it to it },
            initialSelected = 0,
            countProvider = { 0 },
            onSelect = { id, _ ->
                selectedNewspaperCategory = id
                mostrarJornais()
            }
        )
        rvCatalogGroups.adapter = newspaperGroupAdapter

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
            .url("https://raw.githubusercontent.com/onbesttv/eagle-app/main/data/jornais.json?v=${System.currentTimeMillis()}")
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
        catchupGroupAdapter = GroupListAdapter(
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
        rvCatalogGroups.adapter = catchupGroupAdapter

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
        tvRailEditionBadge.text = if (user.isNotBlank()) "User: $user" else ""
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
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("stream_type", "MPEGTS").apply()
        val timeFormat = prefs.getString("time_format", "24 Hr") ?: "24 Hr"
        val activeService = IptvServiceConfig.activeServiceId(this)
        val serverName = IptvServiceConfig.displayName(activeService)

        val cards = listOf(
            EagleSettingCard("ACCOUNT", "👤", "A Minha Conta", "Ver validade da subscrição, ligações e detalhes", ::mostrarDialogoConta),
            EagleSettingCard("UPDATES", "🔄", "Atualizações da App", "Verificar novas versões", { AppUpdateManager.checkForUpdates(this, manual = true) }),
            EagleSettingCard("LANGUAGE", "🌐", "Idioma dos Menus", "Configurar idioma da interface", ::mostrarDialogoIdioma),
            EagleSettingCard("TIME", "🕒", "Formato de Hora", "Atualmente: $timeFormat", ::mostrarDialogoFormatoHora),
            EagleSettingCard("STREAM", "🔒", "Formato de Stream", "MPEG-TS (Bloqueado)", {
                Toast.makeText(this, "Formato fixado e otimizado em MPEG-TS para a TV Box.", Toast.LENGTH_SHORT).show()
            }),
            EagleSettingCard("SERVER", "🔒", "Servidor IPTV", "Atualmente: $serverName (Bloqueado)", {
                Toast.makeText(this, "Servidor IPTV fixo e bloqueado.", Toast.LENGTH_SHORT).show()
            }),
            EagleSettingCard("LOGOUT", "🚪", "Terminar Sessão", "Desconectar utilizador e mudar de conta", ::mostrarDialogoLogout)
        )

        rvSettingsGrid.adapter = EagleSettingsAdapter(cards)
        rvSettingsGrid.post {
            focarRecyclerViewPosicao(rvSettingsGrid, 0)
        }
    }

    private fun criarDialogoTvElegante(
        titulo: String,
        icone: String = ""
    ): Pair<Dialog, LinearLayout> {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF09111D.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFF19324F.toInt())
            }
            setPadding(dp(22), dp(18), dp(22), dp(18))
            elevation = dp(16).toFloat()
        }

        val layoutHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        if (icone.isNotBlank()) {
            val tvIcon = TextView(this).apply {
                text = icone
                textSize = 20f
                setPadding(0, 0, dp(10), 0)
            }
            layoutHeader.addView(tvIcon)
        }

        val tvTitle = TextView(this).apply {
            text = titulo
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        layoutHeader.addView(tvTitle)
        root.addView(layoutHeader)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, 0, 0, dp(14))
            }
            setBackgroundColor(0xFF162942.toInt())
        }
        root.addView(divider)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(content)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.52f).toInt().coerceIn(dp(360), dp(560))
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.72f)
        }

        return Pair(dialog, content)
    }

    private fun criarBotaoTvDialogo(
        texto: String,
        destaque: Boolean = false,
        perigo: Boolean = false,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = texto
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            isFocusable = true
            isClickable = true
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                setMargins(0, dp(4), 0, dp(4))
            }

            val normalBg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                when {
                    perigo -> {
                        setColor(0xFF2A1215.toInt())
                        setStroke(dp(1), 0xFF5C1D24.toInt())
                    }
                    destaque -> {
                        setColor(0xFF132B47.toInt())
                        setStroke(dp(1), 0xFF0284C7.toInt())
                    }
                    else -> {
                        setColor(0xFF0F1D30.toInt())
                        setStroke(dp(1), 0xFF1B2F4A.toInt())
                    }
                }
            }

            val focusedBg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                when {
                    perigo -> {
                        setColor(0xFFDC2626.toInt())
                        setStroke(dp(2), 0xFFFCA5A5.toInt())
                    }
                    destaque -> {
                        setColor(0xFF0284C7.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    }
                    else -> {
                        setColor(0xFF183B63.toInt())
                        setStroke(dp(2), 0xFF00E5FF.toInt())
                    }
                }
            }

            background = normalBg
            setTextColor(
                when {
                    perigo -> 0xFFF87171.toInt()
                    destaque -> 0xFF00E5FF.toInt()
                    else -> 0xFFCBD5E1.toInt()
                }
            )

            setOnFocusChangeListener { _, hasFocus ->
                background = if (hasFocus) focusedBg else normalBg
                setTextColor(
                    if (hasFocus) Color.WHITE else when {
                        perigo -> 0xFFF87171.toInt()
                        destaque -> 0xFF00E5FF.toInt()
                        else -> 0xFFCBD5E1.toInt()
                    }
                )
                animate().scaleX(if (hasFocus) 1.02f else 1.0f).scaleY(if (hasFocus) 1.02f else 1.0f).setDuration(100).start()
            }

            setOnClickListener { onClick() }
        }
    }

    private fun mostrarDialogoConta() {
        val (dialog, content) = criarDialogoTvElegante("A Minha Conta", "👤")

        val tvStatusMsg = TextView(this).apply {
            text = "A carregar detalhes da conta…"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(20))
        }
        val pbLoading = ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(0xFF00E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                gravity = Gravity.CENTER
            }
        }
        val layoutLoading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(pbLoading)
            addView(tvStatusMsg)
        }
        content.addView(layoutLoading)

        val layoutRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        content.addView(layoutRows)

        val btnClose = criarBotaoTvDialogo("FECHAR", destaque = true) { dialog.dismiss() }
        content.addView(btnClose)
        dialog.show()
        btnClose.requestFocus()

        fun criarLinhaInfo(rotulo: String, valor: String, icone: String = "", corValor: Int = Color.WHITE) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(0xFF0D1829.toInt())
                }
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, dp(6))
                }
            }
            val tvLabel = TextView(this).apply {
                text = if (icone.isNotBlank()) "$icone  $rotulo" else rotulo
                setTextColor(0xFF94A3B8.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            }
            val tvVal = TextView(this).apply {
                text = valor
                setTextColor(corValor)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
            }
            row.addView(tvLabel)
            row.addView(tvVal)
            layoutRows.addView(row)
        }

        val url = "$baseUrl/player_api.php?username=$user&password=$pass"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    layoutLoading.visibility = View.GONE
                    layoutRows.removeAllViews()
                    criarLinhaInfo("Utilizador", user, "👤")
                    criarLinhaInfo("Servidor", baseUrl, "🌐")
                    criarLinhaInfo("Estado", "Sem ligação", "⚠️", 0xFFF87171.toInt())
                    layoutRows.visibility = View.VISIBLE
                    btnClose.requestFocus()
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

                    val serverDisplay = IptvServiceConfig.displayName(IptvServiceConfig.activeServiceId(this@EagleTvActivity))

                    runOnUiThread {
                        layoutLoading.visibility = View.GONE
                        layoutRows.removeAllViews()
                        criarLinhaInfo("Utilizador", user, "👤")
                        criarLinhaInfo("Estado", status, "●", if (status.equals("Active", true)) 0xFF34D399.toInt() else 0xFFFBBF24.toInt())
                        criarLinhaInfo("Validade", expFormatted, "📅")
                        criarLinhaInfo("Ligações", "$activeConn / $maxConn ativas", "🔗")
                        criarLinhaInfo("Servidor", serverDisplay, "🌐", 0xFF38BDF8.toInt())
                        layoutRows.visibility = View.VISIBLE
                        btnClose.requestFocus()
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        layoutLoading.visibility = View.GONE
                        layoutRows.removeAllViews()
                        criarLinhaInfo("Utilizador", user, "👤")
                        criarLinhaInfo("Servidor", baseUrl, "🌐")
                        layoutRows.visibility = View.VISIBLE
                        btnClose.requestFocus()
                    }
                }
            }
        })
    }

    private fun mostrarDialogoIdioma() {
        val (dialog, content) = criarDialogoTvElegante("Escolher Idioma", "🌐")
        val idiomas = listOf(
            "🇵🇹  Português (Padrão)" to "pt",
            "🇬🇧  English" to "en",
            "🇪🇸  Español" to "es",
            "🇫🇷  Français" to "fr"
        )
        val currentLocale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()

        idiomas.forEachIndexed { index, (label, code) ->
            val isCurrent = if (currentLocale.isBlank()) code == "pt" else currentLocale.startsWith(code)
            val btn = criarBotaoTvDialogo(if (isCurrent) "$label  ✓" else label, destaque = isCurrent) {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(code)
                )
                Toast.makeText(this, "Idioma alterado para $label", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                iniciarSecaoDefinicoes()
            }
            content.addView(btn)
            if (isCurrent || index == 0) {
                btn.post { btn.requestFocus() }
            }
        }

        val btnCancel = criarBotaoTvDialogo("CANCELAR") { dialog.dismiss() }
        content.addView(btnCancel)
        dialog.show()
    }

    private fun mostrarDialogoFormatoHora() {
        val (dialog, content) = criarDialogoTvElegante("Formato de Hora", "🕒")
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val current = prefs.getString("time_format", "24 Hr") ?: "24 Hr"

        val op24 = criarBotaoTvDialogo(
            if (current == "24 Hr") "24 Horas (ex: 20:30)  ✓" else "24 Horas (ex: 20:30)",
            destaque = current == "24 Hr"
        ) {
            prefs.edit().putString("time_format", "24 Hr").apply()
            atualizarRelogio()
            Toast.makeText(this, "Formato alterado para 24 Horas", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            iniciarSecaoDefinicoes()
        }
        content.addView(op24)

        val op12 = criarBotaoTvDialogo(
            if (current == "12 Hr") "12 Horas (ex: 08:30 PM)  ✓" else "12 Horas (ex: 08:30 PM)",
            destaque = current == "12 Hr"
        ) {
            prefs.edit().putString("time_format", "12 Hr").apply()
            atualizarRelogio()
            Toast.makeText(this, "Formato alterado para 12 Horas", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            iniciarSecaoDefinicoes()
        }
        content.addView(op12)

        val btnCancel = criarBotaoTvDialogo("CANCELAR") { dialog.dismiss() }
        content.addView(btnCancel)

        dialog.show()
        if (current == "24 Hr") op24.requestFocus() else op12.requestFocus()
    }

    private fun mostrarDialogoLogout() {
        val (dialog, content) = criarDialogoTvElegante("Terminar Sessão", "🚪")

        val tvMsg = TextView(this).apply {
            text = "Tens a certeza que queres terminar sessão e aceder com outra conta?"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 14f
            setPadding(0, dp(4), 0, dp(14))
        }
        content.addView(tvMsg)

        val btnSair = criarBotaoTvDialogo("SIM, TERMINAR SESSÃO", perigo = true) {
            dialog.dismiss()
            val activeSrv = IptvServiceConfig.activeServiceId(this)
            val currentUser = IptvServiceConfig.username(this, activeSrv)
            IptvServiceConfig.clearAllCredentials(this)
            getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("USER_LOGGED_OUT", true)
                .putString("LOGGED_OUT_USER", currentUser)
                .apply()

            val rid = RemotePanelManager.getResellerId(this)
            val targetClass = if (rid == "dream") {
                DeviceActivationActivity::class.java
            } else {
                ServiceSelectionActivity::class.java
            }
            startActivity(Intent(this, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
        content.addView(btnSair)

        val btnCancel = criarBotaoTvDialogo("CANCELAR", destaque = true) { dialog.dismiss() }
        content.addView(btnCancel)

        dialog.show()
        btnCancel.requestFocus()
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
        val (dialog, content) = criarDialogoTvElegante("Sair da Eagle TV", "🚪")
        val tvMsg = TextView(this).apply {
            text = "Tens a certeza que queres fechar a aplicação?"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 14.5f
            setPadding(0, dp(4), 0, dp(14))
        }
        content.addView(tvMsg)

        val btnSair = criarBotaoTvDialogo("SIM, SAIR", perigo = true) {
            dialog.dismiss()
            finishAffinity()
        }
        content.addView(btnSair)

        val btnCancel = criarBotaoTvDialogo("CANCELAR", destaque = true) { dialog.dismiss() }
        content.addView(btnCancel)

        dialog.show()
        btnCancel.requestFocus()
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
        items: List<Pair<String, String>>,
        initialSelected: Int,
        private val countProvider: (String) -> Int,
        private val onSelect: (String, String) -> Unit
    ) : RecyclerView.Adapter<GroupListAdapter.ViewHolder>() {

        private val items: MutableList<Pair<String, String>> = items.toMutableList()
        private var selectedPosition = initialSelected
        private var recyclerViewRef: RecyclerView? = null

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            recyclerViewRef = recyclerView
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            recyclerViewRef = null
            super.onDetachedFromRecyclerView(recyclerView)
        }

        fun posicaoSelecionada(): Int = selectedPosition

        fun idSelecionado(): String? {
            return if (selectedPosition in items.indices) items[selectedPosition].first else null
        }

        fun idNaPosicao(pos: Int): String? {
            return if (pos in items.indices) items[pos].first else null
        }

        fun selecionarPorId(id: String) {
            val idx = items.indexOfFirst { it.first == id }
            if (idx >= 0 && idx != selectedPosition) {
                val old = selectedPosition
                selectedPosition = idx
                atualizarEstiloItem(old)
                atualizarEstiloItem(selectedPosition)
            }
        }

        fun updateItems(newItems: List<Pair<String, String>>) {
            val currentSelectedId = idSelecionado()
            val rv = recyclerViewRef
            val focusedChild = rv?.focusedChild
            val focusedPos = if (focusedChild != null) rv.getChildAdapterPosition(focusedChild) else RecyclerView.NO_POSITION
            val focusedId = idNaPosicao(focusedPos)

            items.clear()
            items.addAll(newItems)

            val targetId = focusedId ?: currentSelectedId
            val newIdx = if (targetId != null) items.indexOfFirst { it.first == targetId } else -1
            if (newIdx >= 0) {
                selectedPosition = newIdx
            } else {
                selectedPosition = selectedPosition.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            }

            notifyDataSetChanged()

            if (focusedPos != RecyclerView.NO_POSITION && newIdx >= 0) {
                rv?.post {
                    val vh = rv.findViewHolderForAdapterPosition(newIdx)
                    vh?.itemView?.requestFocus()
                }
            }
        }

        private fun atualizarEstiloItem(pos: Int) {
            val rv = recyclerViewRef ?: return
            val vh = rv.findViewHolderForAdapterPosition(pos) as? ViewHolder ?: return
            val isSelected = pos == selectedPosition
            val hasFocus = vh.itemView.hasFocus()
            vh.indicator.visibility = if (isSelected || hasFocus) View.VISIBLE else View.INVISIBLE
            val (id, _) = if (pos in items.indices) items[pos] else ("" to "")
            val isSpecial = id.startsWith("FAV") || id.startsWith("RECENT") || id == "RESUME"
            vh.indicator.setBackgroundColor(
                if (isSpecial) 0xFFFBBF24.toInt() else 0xFF00E5FF.toInt()
            )
            vh.itemView.background = GradientDrawable().apply {
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
            vh.tvName.setTextColor(
                when {
                    hasFocus -> Color.WHITE
                    isSpecial -> 0xFFFBBF24.toInt()
                    isSelected -> 0xFF38BDF8.toInt()
                    else -> 0xFFE2E8F0.toInt()
                }
            )
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
                    atualizarEstiloItem(prev)
                    atualizarEstiloItem(selectedPosition)
                    holder.itemView.requestFocus()
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
            val isFav = liveFavIdsCache.contains(canal.streamId)
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

            val logoSize = dp(44)
            if (!canal.streamIcon.isNullOrBlank()) {
                Glide.with(holder.itemView.context)
                    .load(canal.streamIcon)
                    .override(logoSize, logoSize)
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
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
                    if (!isRailCollapsed) {
                        ocultarMenuLateral()
                    }
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
            val thumbWidth = dp(120)
            val thumbHeight = dp(170)

            when (item) {
                is CatalogPosterItem.Movie -> {
                    val m = item.vod
                    holder.tvTitle.text = m.name
                    val isFav = movieFavIdsCache.contains(m.streamId.toString())
                    holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE

                    val rating = m.rating?.takeIf { it.isNotBlank() && it != "0" }
                    if (rating != null) {
                        holder.tvBadge.text = "★ $rating"
                        holder.tvBadge.visibility = View.VISIBLE
                    } else {
                        holder.tvBadge.visibility = View.GONE
                    }

                    val posMs = vodResumePosCache[m.streamId] ?: 0L
                    val durMs = vodResumeDurCache[m.streamId] ?: 0L
                    if (posMs > 10_000L && durMs > 0L) {
                        holder.pbResume.progress = ((posMs * 100L) / durMs).toInt().coerceIn(5, 98)
                        holder.pbResume.visibility = View.VISIBLE
                    } else {
                        holder.pbResume.visibility = View.GONE
                    }

                    if (!m.streamIcon.isNullOrBlank()) {
                        Glide.with(holder.itemView.context)
                            .load(m.streamIcon)
                            .override(thumbWidth, thumbHeight)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .dontAnimate()
                            .into(holder.ivThumb)
                    } else {
                        holder.ivThumb.setImageResource(R.mipmap.ic_launcher)
                    }
                }
                is CatalogPosterItem.Series -> {
                    val s = item.series
                    holder.tvTitle.text = s.name
                    holder.pbResume.visibility = View.GONE
                    val isFav = seriesFavIdsCache.contains(s.seriesId.toString())
                    holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE

                    val rating = s.rating?.takeIf { it.isNotBlank() && it != "0" }
                    if (rating != null) {
                        holder.tvBadge.text = "★ $rating"
                        holder.tvBadge.visibility = View.VISIBLE
                    } else {
                        holder.tvBadge.visibility = View.GONE
                    }

                    if (!s.cover.isNullOrBlank()) {
                        Glide.with(holder.itemView.context)
                            .load(s.cover)
                            .override(thumbWidth, thumbHeight)
                            .centerCrop()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .dontAnimate()
                            .into(holder.ivThumb)
                    } else {
                        holder.ivThumb.setImageResource(R.mipmap.ic_launcher)
                    }
                }
                is CatalogPosterItem.Channel -> {
                    val c = item.channel
                    holder.tvTitle.text = c.name
                    holder.pbResume.visibility = View.GONE
                    val isFav = liveFavIdsCache.contains(c.streamId)
                    holder.tvFav.visibility = if (isFav) View.VISIBLE else View.GONE
                    holder.tvBadge.text = "📺 TV"
                    holder.tvBadge.visibility = View.VISIBLE

                    if (!c.streamIcon.isNullOrBlank()) {
                        Glide.with(holder.itemView.context)
                            .load(c.streamIcon)
                            .override(dp(64), dp(64))
                            .fitCenter()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .dontAnimate()
                            .into(holder.ivThumb)
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
                holder.itemView.scaleX = if (hasFocus) 1.04f else 1.0f
                holder.itemView.scaleY = if (hasFocus) 1.04f else 1.0f
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
