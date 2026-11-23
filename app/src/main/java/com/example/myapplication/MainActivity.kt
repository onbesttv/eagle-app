package com.example.myapplication

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.content.Intent
import android.view.View

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PiaVpnManager.cleanZombieVpnIfNeeded(this)
        setContentView(R.layout.activity_main)
        IptvServiceConfig.initialize(this)

        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                AppUpdateManager.checkForUpdates(this, manual = false)
            }
        }, 3500L)

        val ivLogo = findViewById<ImageView>(R.id.ivLogo)

        val cardLiveTv = findViewById<CardView>(R.id.cardLiveTv)
        val cardTvGuide = findViewById<CardView>(R.id.cardEpg)
        val cardMovies = findViewById<CardView>(R.id.cardMovies)
        val cardSeries = findViewById<CardView>(R.id.cardSeries)
        val cardCatchUp = findViewById<CardView>(R.id.cardCatchUp)
        val cardSportsAgenda = findViewById<CardView?>(R.id.cardSportsAgenda)
        val cardNewspapers = findViewById<CardView?>(R.id.cardNewspapers)

        // Carrega o teu logótipo
        ivLogo.setImageResource(R.drawable.eagle_wordmark)
        if (AppThemeColors.isGodinhoTheme(this)) {
            val density = resources.displayMetrics.density
            ivLogo.layoutParams = ivLogo.layoutParams.apply {
                width = (156 * density).toInt()
                height = (104 * density).toInt()
            }
        } else if (AppThemeColors.isPlayTvTheme(this)) {
            val density = resources.displayMetrics.density
            ivLogo.layoutParams = ivLogo.layoutParams.apply {
                width = (168 * density).toInt()
                height = (52 * density).toInt()
            }
        }

        val prefs = getSharedPreferences("iptv_login_prefs", MODE_PRIVATE)
        val savedUser = prefs.getString("SAVED_USER", "").orEmpty()
        val savedPass = prefs.getString("SAVED_PASS", "").orEmpty()

        // Cliques instantâneos nos cartões
        cardLiveTv.setOnClickListener {
            abrirEcra(Intent(this, LiveTvActivity::class.java))
        }

        cardTvGuide.setOnClickListener {
            abrirEcra(Intent(this, EpgActivity::class.java))
        }
        cardMovies.setOnClickListener {
            abrirEcra(Intent(this, MoviesActivity::class.java))
        }
        cardSeries.setOnClickListener {
            abrirEcra(Intent(this, SeriesActivity::class.java))
        }
        cardCatchUp.setOnClickListener {
            abrirEcra(Intent(this, CatchUpActivity::class.java))
        }

        val resId = RemotePanelManager.getResellerId(this)
        val hasSportsAndNews = resId.equals("master", ignoreCase = true) ||
                resId.equals("tv_fabio", ignoreCase = true) ||
                resId.equals("tv_joker", ignoreCase = true) ||
                resId.equals("joker", ignoreCase = true) ||
                resId.equals("suporte24", ignoreCase = true)
        if (hasSportsAndNews) {
            cardSportsAgenda?.visibility = android.view.View.VISIBLE
            cardNewspapers?.visibility = android.view.View.VISIBLE
            if (resId.equals("master", ignoreCase = true)) {
                findViewById<TextView>(R.id.tvNewspapersTitle)?.text = "Jornais & Notícias"
            }
            cardSportsAgenda?.setOnClickListener {
                abrirEcra(Intent(this, SportsAgendaActivity::class.java))
            }
            cardNewspapers?.setOnClickListener {
                abrirEcra(Intent(this, NewspapersActivity::class.java))
            }
        } else {
            cardSportsAgenda?.visibility = android.view.View.GONE
            cardNewspapers?.visibility = android.view.View.GONE
            val catchUpParams = cardCatchUp.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            catchUpParams?.marginEnd = 0
            val d = resources.displayMetrics.density
            catchUpParams?.width = (210 * d).toInt()
            catchUpParams?.height = (50 * d).toInt()
            cardCatchUp.layoutParams = catchUpParams
        }

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnUpdateService = findViewById<ImageButton>(R.id.btnUpdateService)
        val btnVpn = findViewById<ImageButton>(R.id.btnVpn)

        val hideVpn = RemotePanelManager.getResellerId(this).equals("pereira_tv", ignoreCase = true)
        if (hideVpn) {
            btnVpn.visibility = android.view.View.GONE
            btnVpn.isFocusable = false
            btnVpn.isClickable = false
            val catchUpLayout = cardCatchUp.getChildAt(0) as? android.view.ViewGroup
            val ivCatchUp = catchUpLayout?.getChildAt(0) as? ImageView
            ivCatchUp?.imageTintList = null
        } else {
            atualizarEstadoBotaoVpn()
        }

        val isJoker = AppThemeColors.isJokerTheme(this)
        if (isJoker) {
            btnSettings?.setImageDrawable(null)
            btnUpdateService?.setImageDrawable(null)
            btnVpn?.setImageDrawable(null)
        }

        // Destaque visual e sombra ao navegar com comando da Box (apenas para layouts dinâmicos padrão)
        if (!isJoker) {
            val interactiveViews = mutableListOf(cardLiveTv, cardTvGuide, cardMovies, cardSeries, cardCatchUp, btnUpdateService, btnSettings)
            if (!hideVpn) interactiveViews.add(btnVpn)
            if (hasSportsAndNews) {
                cardSportsAgenda?.let { interactiveViews.add(it) }
                cardNewspapers?.let { interactiveViews.add(it) }
            }
            interactiveViews.forEach { view ->
                view.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        val s = if (v == cardCatchUp || v == cardSportsAgenda || v == cardNewspapers) 1.04f else 1.06f
                        v.animate().scaleX(s).scaleY(s).setDuration(140).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                    }
                }
            }
        }

        btnSettings.setOnClickListener {
            abrirEcra(Intent(this, SettingsActivity::class.java))
        }

        btnUpdateService.setOnClickListener { button ->
            atualizarCatalogo(button as ImageButton, savedUser, savedPass)
        }

        val extrasBtn = findViewById<android.view.View?>(R.id.btnExtras)
        extrasBtn?.setOnClickListener {
            try {
                abrirEcra(Intent(this, ExtrasActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Foco inicial direto no LIVE TV ao abrir a aplicação
        cardLiveTv.post {
            cardLiveTv.requestFocus()
        }

        // Verificar expiração da subscrição/linha (alerta se faltarem 5 dias ou menos)
        AccountExpirationAlert.checkAndShow(this)
    }

    private fun abrirEcra(intent: Intent) {
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private val periodicSyncRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                RemotePanelManager.syncAndEnforce(this@MainActivity)
                window.decorView.postDelayed(this, 30_000L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.removeCallbacks(periodicSyncRunnable)
        window.decorView.postDelayed(periodicSyncRunnable, 30_000L)
        // Atualiza os títulos caso o idioma tenha sido alterado nas Definições
        findViewById<TextView>(R.id.tvLiveTvTitle)?.text = getString(R.string.live_tv)
        findViewById<TextView>(R.id.tvEpgTitle)?.text = getString(R.string.tv_guide)
        findViewById<TextView>(R.id.tvMoviesTitle)?.text = getString(R.string.movies)
        findViewById<TextView>(R.id.tvSeriesTitle)?.text = getString(R.string.series)
        findViewById<TextView>(R.id.tvCatchUpTitle)?.text = getString(R.string.catch_up)

        // Atualiza estado e ícone da VPN conforme permissão do utilizador no painel e estado de ligação
        atualizarEstadoBotaoVpn()

        // Garante que ao regressar ao ecrã inicial o foco volta para o LIVE TV caso estivesse no botão de definições
        val cardLiveTv = findViewById<CardView>(R.id.cardLiveTv)
        cardLiveTv?.post {
            if (currentFocus == null || currentFocus?.id == R.id.btnSettings) {
                cardLiveTv.requestFocus()
            }
        }
    }

    fun atualizarEstadoBotaoVpn() {
        val btnVpn = findViewById<ImageButton>(R.id.btnVpn) ?: return
        val hideVpn = RemotePanelManager.getResellerId(this).equals("pereira_tv", ignoreCase = true)
        if (hideVpn) {
            btnVpn.visibility = android.view.View.GONE
            btnVpn.isFocusable = false
            btnVpn.isClickable = false
            return
        }

        val vpnAllowed = PiaVpnConfig.isVpnAllowedForCurrentUser(this)
        if (!vpnAllowed) {
            if (PiaVpnManager.getStatus() != PiaVpnManager.VpnStatus.DISCONNECTED) {
                PiaVpnManager.disconnect(this)
            }
            if (btnVpn.hasFocus()) {
                findViewById<CardView>(R.id.cardLiveTv)?.requestFocus()
            }
            btnVpn.visibility = android.view.View.VISIBLE
            btnVpn.alpha = 0.35f
            btnVpn.isEnabled = false
            btnVpn.isClickable = true
            btnVpn.isFocusable = false
            btnVpn.isFocusableInTouchMode = false
            btnVpn.imageTintList = null
            btnVpn.colorFilter = null
            btnVpn.backgroundTintList = null
            btnVpn.setOnClickListener {
                android.widget.Toast.makeText(this, "Acesso à VPN indisponível para esta conta.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            val isVpnConnected = PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTED
            btnVpn.visibility = android.view.View.VISIBLE
            btnVpn.alpha = 1.0f
            btnVpn.isEnabled = true
            btnVpn.isClickable = true
            btnVpn.isFocusable = true
            btnVpn.imageTintList = null
            btnVpn.colorFilter = null
            if (AppThemeColors.isJokerTheme(this)) {
                btnVpn.backgroundTintList = null
                btnVpn.setImageDrawable(null)
            } else {
                btnVpn.backgroundTintList = if (isVpnConnected) ColorStateList.valueOf(0xFF15803D.toInt()) else null
            }
            btnVpn.setOnClickListener {
                startActivity(Intent(this, VpnActivity::class.java))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        window.decorView.removeCallbacks(periodicSyncRunnable)
    }

    private fun atualizarCatalogo(button: ImageButton, user: String, pass: String) {
        button.isEnabled = false
        CatalogRefreshDialog.show(this, user, pass) {
            button.isEnabled = true
        }
    }

}
