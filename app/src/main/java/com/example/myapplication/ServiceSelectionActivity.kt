package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class ServiceSelectionActivity : BaseActivity() {
    companion object {
        const val EXTRA_MANUAL_SELECTION = "manual_service_selection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_selection)
        IptvServiceConfig.initialize(this)
        if (EagleTvConfig.isEagleTvEdition(this)) {
            findViewById<View>(R.id.ivServiceSelectionBgFuturistic)?.visibility = View.GONE
            findViewById<View>(R.id.viewServiceSelectionBgOverlay)?.visibility = View.GONE
            window.decorView.setBackgroundColor(0xFF07080B.toInt())
        }
        var selectedService = IptvServiceConfig.activeServiceId(this)
        val manualSelection = intent.getBooleanExtra(EXTRA_MANUAL_SELECTION, false)
        val savedUser = IptvServiceConfig.username(this, selectedService)
        val savedPass = IptvServiceConfig.password(this, selectedService)
        if (!manualSelection && savedUser.isNotBlank() && savedPass.isNotBlank()) {
            findViewById<LinearLayout>(R.id.serviceChoices).visibility = View.GONE
            findViewById<LinearLayout>(R.id.autoLoginPanel).visibility = View.VISIBLE
            findViewById<ProgressBar>(R.id.pbServiceSelection).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvStartupServiceBrand).text = IptvServiceConfig.displayName(selectedService).uppercase()
            startActivity(Intent(this, StreamPlayLoginActivity::class.java)
                .putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, true)
                .putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, selectedService))
            overridePendingTransition(0, 0)
            finish()
            return
        }

        val cardStreamPlay = findViewById<View>(R.id.cardStreamPlay)
        val cardOtherService = findViewById<View>(R.id.cardOtherService)
        val bestCheck = findViewById<TextView>(R.id.tvBestCheck)
        val best2Check = findViewById<TextView>(R.id.tvBest2Check)
        val streamPlayDisabled = IptvServiceConfig.isStreamPlayDisabled(this)
        val templariosDisabled = IptvServiceConfig.isTemplariosDisabled(this)

        fun updateSelection(service: String) {
            selectedService = when {
                streamPlayDisabled -> IptvServiceConfig.BEST2
                templariosDisabled -> IptvServiceConfig.BEST
                else -> service
            }
            bestCheck.visibility = if (!streamPlayDisabled && selectedService == IptvServiceConfig.BEST) View.VISIBLE else View.INVISIBLE
            best2Check.visibility = if (!templariosDisabled && selectedService == IptvServiceConfig.BEST2) View.VISIBLE else View.INVISIBLE
        }
        updateSelection(selectedService)

        fun entrarNoServico(service: String) {
            updateSelection(service)
            IptvServiceConfig.select(this, selectedService)
            val user = IptvServiceConfig.username(this, selectedService)
            val pass = IptvServiceConfig.password(this, selectedService)
            val hasCredentials = user.isNotBlank() && pass.isNotBlank()
            startActivity(
                Intent(this, StreamPlayLoginActivity::class.java)
                    .putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, selectedService)
                    .putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, hasCredentials)
            )
        }

        if (streamPlayDisabled) {
            cardStreamPlay.isEnabled = false
            cardStreamPlay.isClickable = false
            cardStreamPlay.isFocusable = false
            cardStreamPlay.isFocusableInTouchMode = false
            cardStreamPlay.alpha = 0.38f
        } else {
            cardStreamPlay.setOnClickListener { entrarNoServico(IptvServiceConfig.BEST) }
        }

        if (templariosDisabled) {
            cardOtherService.visibility = View.GONE
            cardOtherService.isEnabled = false
            cardOtherService.isClickable = false
            cardOtherService.isFocusable = false
            cardOtherService.isFocusableInTouchMode = false
        } else {
            cardOtherService.setOnClickListener { entrarNoServico(IptvServiceConfig.BEST2) }
        }

        // Animação suave de navegação com comando da TV
        val interactiveCards = when {
            streamPlayDisabled -> listOf(cardOtherService)
            templariosDisabled -> listOf(cardStreamPlay)
            else -> listOf(cardStreamPlay, cardOtherService)
        }
        interactiveCards.forEach { view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        // Foco inicial direto no cartão do serviço atualmente ativo e verificação automática de atualizações
        val initialCard = when {
            streamPlayDisabled -> cardOtherService
            templariosDisabled -> cardStreamPlay
            selectedService == IptvServiceConfig.BEST2 -> cardOtherService
            else -> cardStreamPlay
        }
        initialCard.post {
            initialCard.requestFocus()
            AppUpdateManager.checkForUpdates(this, manual = false)
        }
    }
}
