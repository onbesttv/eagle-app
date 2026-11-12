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

        val isUserLoggedOut = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).getBoolean("USER_LOGGED_OUT", false)

        if (!isUserLoggedOut && savedUser.isNotBlank() && savedPass.isNotBlank()) {
            startActivity(
                Intent(this, StreamPlayLoginActivity::class.java)
                    .putExtra(StreamPlayLoginActivity.EXTRA_AUTO_LOGIN, true)
                    .putExtra(StreamPlayLoginActivity.EXTRA_SERVICE_ID, activeService)
            )
        } else {
            val rid = RemotePanelManager.getResellerId(this)
            if (rid == "dream") {
                startActivity(Intent(this, DeviceActivationActivity::class.java))
            } else {
                startActivity(Intent(this, ServiceSelectionActivity::class.java))
            }
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
