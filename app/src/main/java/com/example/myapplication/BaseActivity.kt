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
                heartbeatHandler.postDelayed(this, 60_000L) // Atualiza estado online a cada 60s mesmo durante reprodução
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
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
        heartbeatHandler.post(heartbeatRunnable)
    }

    override fun onPause() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        super.onPause()
    }
}
