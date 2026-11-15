package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class StartupSplashActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var minDelayPassed = false
    private var syncFinished = false
    private var navigated = false

    private fun tryProceed() {
        if (navigated || isFinishing || !minDelayPassed || !syncFinished) return
        if (RemotePanelManager.enforceOnUi(this)) {
            return
        }
        navigated = true
        IptvServiceConfig.initialize(this)
        val activeService = IptvServiceConfig.activeServiceId(this)
        val savedUser = IptvServiceConfig.username(this, activeService)
        val savedPass = IptvServiceConfig.password(this, activeService)

        val isDnsAllowed = RemotePanelManager.isCurrentDnsAllowed(this)
        if (!isDnsAllowed) {
            IptvServiceConfig.clearAllCredentials(this)
            getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("USER_LOGGED_OUT", true)
                .apply()
        }

        val isUserLoggedOut = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).getBoolean("USER_LOGGED_OUT", false) || !isDnsAllowed

        val rid = RemotePanelManager.getResellerId(this)
        val isDream = rid.equals("dream", ignoreCase = true) || packageName.equals("com.redon.tv", ignoreCase = true)

        if (isDream) {
            if (!isUserLoggedOut && savedUser.isNotBlank() && savedPass.isNotBlank()) {
                startActivity(Intent(this, EagleTvActivity::class.java))
            } else {
                startActivity(Intent(this, DeviceActivationActivity::class.java))
            }
        } else if (!isUserLoggedOut && savedUser.isNotBlank() && savedPass.isNotBlank()) {
            startActivity(
                Intent(this, StreamPlayLoginActivity::class.java)
                    .putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, true)
                    .putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, activeService)
            )
        } else {
            startActivity(Intent(this, ServiceSelectionActivity::class.java))
        }
        overridePendingTransition(0, 0)
        finish()
    }

    private val minSplashRunnable = Runnable {
        minDelayPassed = true
        tryProceed()
    }

    private val maxTimeoutRunnable = Runnable {
        syncFinished = true
        tryProceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se a app já estava em execução e o utilizador saiu há menos de 15 segundos:
        // volta diretamente para onde estava sem reiniciar, sem splash e sem recarregar!
        if (EagleTvActivity.isAlive) {
            val act = EagleTvActivity.instance?.get()
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                val elapsed = System.currentTimeMillis() - EagleTvActivity.lastBackgroundTs
                if (elapsed < 15_000L && RemotePanelManager.isCurrentDnsAllowed(this)) {
                    val intent = Intent(this, EagleTvActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    overridePendingTransition(0, 0)
                    return
                }
            }
        }

        PiaVpnManager.cleanZombieVpnIfNeeded(this)
        setContentView(R.layout.activity_startup_splash)
        handler.postDelayed(minSplashRunnable, 350L)
        handler.postDelayed(maxTimeoutRunnable, 1500L)
        RemotePanelManager.sync(this) {
            syncFinished = true
            tryProceed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(minSplashRunnable)
        handler.removeCallbacks(maxTimeoutRunnable)
        super.onDestroy()
    }
}
