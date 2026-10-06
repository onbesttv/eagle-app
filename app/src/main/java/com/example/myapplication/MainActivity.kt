package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import android.content.Intent

class MainActivity : AppCompatActivity() {

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
    }

    private fun atualizarCatalogo(button: ImageButton, user: String, pass: String) {
        button.isEnabled = false
        CatalogRefreshDialog.show(this, user, pass) {
            button.isEnabled = true
        }
    }

}
