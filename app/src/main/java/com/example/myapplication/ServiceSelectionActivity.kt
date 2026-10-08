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
        IptvServiceConfig.initialize(this)
        var selectedService = IptvServiceConfig.activeServiceId(this)
        val manualSelection = intent.getBooleanExtra(EXTRA_MANUAL_SELECTION, false)
        val savedUser = IptvServiceConfig.username(this, selectedService)
        val savedPass = IptvServiceConfig.password(this, selectedService)
        if (!manualSelection && savedUser.isNotBlank() && savedPass.isNotBlank()) {
            findViewById<LinearLayout>(R.id.serviceChoices).visibility = View.GONE
            findViewById<LinearLayout>(R.id.autoLoginPanel).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.pbServiceSelection).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvStartupServiceBrand).text = selectedService.uppercase()
            startActivity(Intent(this, StreamPlayLoginActivity::class.java)
                .putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, true)
                .putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, selectedService))
            return
        }

        val cardStreamPlay = findViewById<View>(R.id.cardStreamPlay)
        val cardOtherService = findViewById<View>(R.id.cardOtherService)
        val bestCheck = findViewById<TextView>(R.id.tvBestCheck)
        val best2Check = findViewById<TextView>(R.id.tvBest2Check)

        fun updateSelection(service: String) {
            selectedService = service
            bestCheck.visibility = if (service == IptvServiceConfig.BEST) View.VISIBLE else View.INVISIBLE
            best2Check.visibility = if (service == IptvServiceConfig.BEST2) View.VISIBLE else View.INVISIBLE
        }
        updateSelection(selectedService)

        fun entrarNoServico(service: String) {
            updateSelection(service)
            IptvServiceConfig.select(this, service)
            val user = IptvServiceConfig.username(this, service)
            val pass = IptvServiceConfig.password(this, service)
            val hasCredentials = user.isNotBlank() && pass.isNotBlank()
            startActivity(
                Intent(this, StreamPlayLoginActivity::class.java)
                    .putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, service)
                    .putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, hasCredentials)
            )
        }

        cardStreamPlay.setOnClickListener { entrarNoServico(IptvServiceConfig.BEST) }
        cardOtherService.setOnClickListener { entrarNoServico(IptvServiceConfig.BEST2) }

        // Animação suave de navegação com comando da TV
        val interactiveCards = listOf(cardStreamPlay, cardOtherService)
        interactiveCards.forEach { view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        // Foco inicial direto no cartão do serviço atualmente ativo
        val initialCard = if (selectedService == IptvServiceConfig.BEST2) cardOtherService else cardStreamPlay
        initialCard.post { initialCard.requestFocus() }
    }
}
