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
        setContentView(R.layout.activity_main)
        IptvServiceConfig.initialize(this)

        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                AppUpdateManager.checkForUpdates(this, manual = false)
            }
        }, 600L)

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
        }

        val prefs = getSharedPreferences("iptv_login_prefs", MODE_PRIVATE)
        val savedUser = prefs.getString("SAVED_USER", "").orEmpty()
        val savedPass = prefs.getString("SAVED_PASS", "").orEmpty()

        // Cliques nos cartões
        cardLiveTv.setOnClickListener {
            val intent = Intent(this, LiveTvActivity::class.java)
            startActivity(intent)
        }

        cardTvGuide.setOnClickListener {
            val intent = Intent(this, EpgActivity::class.java)
            startActivity(intent)
        }
        cardMovies.setOnClickListener {
            val intent = Intent(this, MoviesActivity::class.java)
            startActivity(intent)
        }
        cardSeries.setOnClickListener {
            val intent = Intent(this, SeriesActivity::class.java)
            startActivity(intent)
        }
        cardCatchUp.setOnClickListener {
            startActivity(Intent(this, CatchUpActivity::class.java))
        }

        val resId = RemotePanelManager.getResellerId(this)
        val hasSportsAndNews = resId.equals("master", ignoreCase = true) || resId.equals("tv_joker", ignoreCase = true) || resId.equals("joker", ignoreCase = true)
        if (hasSportsAndNews) {
            cardSportsAgenda?.visibility = android.view.View.VISIBLE
            cardNewspapers?.visibility = android.view.View.VISIBLE
            cardSportsAgenda?.setOnClickListener {
                startActivity(Intent(this, SportsAgendaActivity::class.java))
            }
            cardNewspapers?.setOnClickListener {
                startActivity(Intent(this, NewspapersActivity::class.java))
            }
        } else {
            cardSportsAgenda?.visibility = android.view.View.GONE
            cardNewspapers?.visibility = android.view.View.GONE
            val catchUpParams = cardCatchUp.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            catchUpParams?.marginEnd = 0
            val d = resources.displayMetrics.density
            catchUpParams?.width = (200 * d).toInt()
            catchUpParams?.height = (48 * d).toInt()
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
                        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start()
                        v.elevation = 14f
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                        v.elevation = 0f
                    }
                }
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnUpdateService.setOnClickListener { button ->
            atualizarCatalogo(button as ImageButton, savedUser, savedPass)
        }

        val extrasBtn = findViewById<android.view.View?>(R.id.btnExtras)
        extrasBtn?.setOnClickListener {
            try {
                startActivity(Intent(this, ExtrasActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Foco inicial direto no LIVE TV ao abrir a aplicação; a home já está visível
        // quando começa a verificação automática de atualizações.
        cardLiveTv.post {
            cardLiveTv.requestFocus()
            AppUpdateManager.checkForUpdates(this, manual = false)
        }

        // Verificar expiração da subscrição/linha (alerta se faltarem 5 dias ou menos)
        AccountExpirationAlert.checkAndShow(this)
    }

    override fun onResume() {
        super.onResume()
        RemotePanelManager.syncAndEnforce(this)
        AppUpdateManager.checkForUpdates(this, manual = false)
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
            btnVpn.alpha = 0.32f
            btnVpn.isEnabled = false
            btnVpn.isClickable = false
            btnVpn.isFocusable = false
            btnVpn.isFocusableInTouchMode = false
            btnVpn.imageTintList = null
            btnVpn.colorFilter = null
            btnVpn.backgroundTintList = null
            btnVpn.setOnClickListener(null)
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

    private fun atualizarCatalogo(button: ImageButton, user: String, pass: String) {
        button.isEnabled = false
        CatalogRefreshDialog.show(this, user, pass) {
            button.isEnabled = true
        }
    }

}
