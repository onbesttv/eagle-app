package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class ServiceSelectionActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MANUAL_SELECTION = "manual_service_selection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_selection)
        val saved = getSharedPreferences("iptv_login_prefs", MODE_PRIVATE)
        val savedUser = saved.getString("SAVED_USER", "").orEmpty()
        val savedPass = saved.getString("SAVED_PASS", "").orEmpty()
        val savedService = saved.getString("SAVED_SERVICE", "").orEmpty()
        val manualSelection = intent.getBooleanExtra(EXTRA_MANUAL_SELECTION, false)
        if (!manualSelection && (savedService == "streamplay" || savedService.isBlank()) && savedUser.isNotBlank() && savedPass.isNotBlank()) {
            if (savedService.isBlank()) saved.edit().putString("SAVED_SERVICE", "streamplay").apply()
            findViewById<LinearLayout>(R.id.serviceChoices).visibility = View.GONE
            findViewById<TextView>(R.id.tvServiceSelectionTitle).text = "A entrar no StreamPlay…"
            findViewById<ProgressBar>(R.id.pbServiceSelection).visibility = View.VISIBLE
            startActivity(Intent(this, StreamPlayLoginActivity::class.java).putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, true))
            return
        }
        findViewById<CardView>(R.id.cardStreamPlay).setOnClickListener {
            startActivity(Intent(this, StreamPlayLoginActivity::class.java))
        }
        // O segundo serviço fica visível, mas desativado até ser configurado.
        findViewById<CardView>(R.id.cardOtherService).isEnabled = false
    }
}
