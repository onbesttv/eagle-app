package com.example.myapplication

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
        startActivity(Intent(this, ServiceSelectionActivity::class.java))
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
        handler.postDelayed(minSplashRunnable, 650L)
        handler.postDelayed(maxTimeoutRunnable, 2000L)
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
