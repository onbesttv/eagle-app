package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
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
        // Remove the temporary test login so the user can restore their previous account.
        if (saved.getString("SAVED_USER", "") == "889248") {
            saved.edit()
                .remove("SAVED_USER")
                .remove("SAVED_PASS")
                .remove("SAVED_SERVICE")
                .remove("NUNESTV2_TEST_ACCOUNT_APPLIED")
                .apply()
        }
        val savedUser = saved.getString("SAVED_USER", "").orEmpty()
        val savedPass = saved.getString("SAVED_PASS", "").orEmpty()
        val savedService = saved.getString("SAVED_SERVICE", "").orEmpty()
        val manualSelection = intent.getBooleanExtra(EXTRA_MANUAL_SELECTION, false)
        if (!manualSelection && (savedService == "streamplay" || savedService.isBlank()) && savedUser.isNotBlank() && savedPass.isNotBlank()) {
            if (savedService.isBlank()) saved.edit().putString("SAVED_SERVICE", "streamplay").apply()
            findViewById<LinearLayout>(R.id.serviceChoices).visibility = View.GONE
            findViewById<LinearLayout>(R.id.autoLoginPanel).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.pbServiceSelection).visibility = View.VISIBLE
            startActivity(Intent(this, StreamPlayLoginActivity::class.java).putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, true))
            return
        }
        val openLogin = {
            startActivity(Intent(this, StreamPlayLoginActivity::class.java))
        }
        findViewById<CardView>(R.id.cardStreamPlay).setOnClickListener { openLogin() }
        findViewById<android.widget.Button>(R.id.btnSelectServiceEnter).setOnClickListener { openLogin() }
        // O segundo serviço fica visível, mas desativado até ser configurado.
        findViewById<CardView>(R.id.cardOtherService).isEnabled = false
    }
}
