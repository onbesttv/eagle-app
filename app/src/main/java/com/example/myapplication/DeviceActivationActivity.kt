package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class DeviceActivationActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false
    private lateinit var tvActivationStatus: TextView
    private lateinit var btnCheckActivation: Button

    private val autoPollRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                checkActivationStatus(silent = true)
                handler.postDelayed(this, 7000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_activation)

        val tvMac = findViewById<TextView>(R.id.tvDeviceMac)
        tvActivationStatus = findViewById(R.id.tvActivationStatus)
        btnCheckActivation = findViewById(R.id.btnCheckActivation)

        val mac = RemotePanelManager.getDeviceMac(this)
        tvMac.text = mac

        btnCheckActivation.setOnClickListener {
            getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).edit()
                .remove("USER_LOGGED_OUT")
                .remove("LOGGED_OUT_USER")
                .apply()
            checkActivationStatus(silent = false)
        }

        btnCheckActivation.post {
            btnCheckActivation.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(autoPollRunnable)
        handler.postDelayed(autoPollRunnable, 1500L)
    }

    override fun onPause() {
        handler.removeCallbacks(autoPollRunnable)
        super.onPause()
    }

    private fun checkActivationStatus(silent: Boolean) {
        if (silent && getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).getBoolean("USER_LOGGED_OUT", false)) {
            return
        }
        if (isChecking) return
        isChecking = true

        if (!silent) {
            btnCheckActivation.isEnabled = false
            btnCheckActivation.text = "A VERIFICAR ATIVAÇÃO..."
            tvActivationStatus.text = "● A verificar comunicação com o servidor..."
            tvActivationStatus.setTextColor(0xFF38BDF8.toInt())
        }

        RemotePanelManager.sync(this) { success ->
            if (isFinishing || isDestroyed) return@sync
            isChecking = false
            btnCheckActivation.isEnabled = true
            btnCheckActivation.text = "🔄 VERIFICAR ATIVAÇÃO"

            IptvServiceConfig.initialize(this)
            val activeService = IptvServiceConfig.activeServiceId(this)
            var savedUser = IptvServiceConfig.username(this, activeService)
            var savedPass = IptvServiceConfig.password(this, activeService)

            val loginPrefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
            if (savedUser.isBlank()) {
                savedUser = loginPrefs.getString("SAVED_USER", "").orEmpty()
                savedPass = loginPrefs.getString("SAVED_PASS", "").orEmpty()
            }

            // Se fechou sessão por engano, compara se o Device ID continua ativo no painel e restaura os dados
            if (savedUser.isBlank() && RemotePanelManager.isDeviceActivatedInPanel(this)) {
                val lastUser = loginPrefs.getString("LAST_ACTIVATED_USER", "").orEmpty()
                val lastPass = loginPrefs.getString("LAST_ACTIVATED_PASS", "").orEmpty()
                val lastDns = loginPrefs.getString("LAST_ACTIVATED_DNS", "").orEmpty()
                if (lastUser.isNotBlank() && lastPass.isNotBlank()) {
                    IptvServiceConfig.saveCredentials(this, activeService, lastUser, lastPass)
                    if (lastDns.isNotBlank()) {
                        IptvServiceConfig.updateRemoteUrls(this, lastDns, "")
                    }
                    savedUser = lastUser
                    savedPass = lastPass
                }
            }

            if (savedUser.isNotBlank() && savedPass.isNotBlank()) {
                loginPrefs.edit()
                    .putString("LAST_ACTIVATED_USER", savedUser)
                    .putString("LAST_ACTIVATED_PASS", savedPass)
                    .apply()

                tvActivationStatus.text = "✔ Dispositivo Ativado com Sucesso! A iniciar..."
                tvActivationStatus.setTextColor(0xFF34D399.toInt())
                Toast.makeText(this, "Dispositivo ativado! Bem-vindo.", Toast.LENGTH_SHORT).show()

                handler.postDelayed({
                    val target = if (EagleTvConfig.isEagleTvEdition(this)) {
                        EagleTvActivity::class.java
                    } else {
                        MainActivity::class.java
                    }
                    startActivity(Intent(this, target))
                    finish()
                }, 800L)
            } else {
                if (!silent) {
                    tvActivationStatus.text = "● Dispositivo ainda não ativado no painel."
                    tvActivationStatus.setTextColor(0xFFFBBF24.toInt())
                    Toast.makeText(this, "Ainda não ativado. Informa o teu fornecedor.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
