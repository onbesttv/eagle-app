package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.content.Intent

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        IptvServiceConfig.initialize(this)
        val ivLogo = findViewById<ImageView>(R.id.ivLogo)

        val cardLiveTv = findViewById<CardView>(R.id.cardLiveTv)
        val cardTvGuide = findViewById<CardView>(R.id.cardEpg)
        val cardMovies = findViewById<CardView>(R.id.cardMovies)
        val cardSeries = findViewById<CardView>(R.id.cardSeries)
        val cardCatchUp = findViewById<CardView>(R.id.cardCatchUp)

        // Carrega o teu logótipo
        ivLogo.setImageResource(R.drawable.eagle_wordmark)

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

        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val btnUpdateService = findViewById<ImageButton>(R.id.btnUpdateService)

        // Destaque visual e sombra ao navegar com comando da Box
        val interactiveViews = listOf(cardLiveTv, cardTvGuide, cardMovies, cardSeries, cardCatchUp, btnSettings, btnUpdateService)
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

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnUpdateService.setOnClickListener { button ->
            atualizarCatalogo(button as ImageButton, savedUser, savedPass)
        }

        // 1. Foco inicial direto no LIVE TV ao abrir a aplicação
        cardLiveTv.post {
            cardLiveTv.requestFocus()
        }

        // 2. Verificar se existe atualização disponível no GitHub;
        // Se houver, mostra automaticamente o aviso com o botão de atualizar
        AppUpdateManager.checkForUpdates(this, manual = false)
    }

    override fun onResume() {
        super.onResume()
        // Atualiza os títulos caso o idioma tenha sido alterado nas Definições
        findViewById<TextView>(R.id.tvLiveTvTitle)?.text = getString(R.string.live_tv)
        findViewById<TextView>(R.id.tvEpgTitle)?.text = getString(R.string.tv_guide)
        findViewById<TextView>(R.id.tvMoviesTitle)?.text = getString(R.string.movies)
        findViewById<TextView>(R.id.tvSeriesTitle)?.text = getString(R.string.series)
        findViewById<TextView>(R.id.tvCatchUpTitle)?.text = getString(R.string.catch_up)

        // Garante que ao regressar ao ecrã inicial o foco volta para o LIVE TV caso estivesse no botão de definições
        val cardLiveTv = findViewById<CardView>(R.id.cardLiveTv)
        cardLiveTv?.post {
            if (currentFocus == null || currentFocus?.id == R.id.btnSettings) {
                cardLiveTv.requestFocus()
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
