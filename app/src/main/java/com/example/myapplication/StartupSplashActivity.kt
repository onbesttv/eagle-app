package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class StartupSplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val continueToApp = Runnable {
        if (!isFinishing) {
            startActivity(Intent(this, ServiceSelectionActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup_splash)
        handler.postDelayed(continueToApp, 650L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(continueToApp)
        super.onDestroy()
    }
}
