package com.example.myapplication

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed) {
                RemotePanelManager.syncAndEnforce(this@BaseActivity)
                heartbeatHandler.postDelayed(this, 15_000L) // Sincroniza a cada 15s em tempo real
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        AppThemeColors.applyToActivity(this)
    }

    override fun setContentView(view: android.view.View?) {
        super.setContentView(view)
        AppThemeColors.applyToActivity(this)
    }

    override fun onResume() {
        super.onResume()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.postDelayed(heartbeatRunnable, 4_000L)
    }

    override fun onPause() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        super.onPause()
    }
}
