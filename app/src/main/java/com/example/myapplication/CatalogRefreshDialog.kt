package com.example.myapplication

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

object CatalogRefreshDialog {
    fun show(
        activity: AppCompatActivity,
        username: String,
        password: String,
        onFinished: (ServiceRefreshResult) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_catalog_refresh)
        dialog.setCancelable(false)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.62f)
        }

        val panel = dialog.findViewById<View>(R.id.cardCatalogRefresh)
        val refreshIcon = dialog.findViewById<ImageView>(R.id.ivCatalogRefreshIcon)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.pbCatalogRefresh)
        val progressText = dialog.findViewById<TextView>(R.id.tvCatalogRefreshMessage)
        val titleText = dialog.findViewById<TextView>(R.id.tvCatalogRefreshTitle)
        val summaryText = dialog.findViewById<TextView>(R.id.tvCatalogRefreshSummary)
        val counterViews = mapOf(
            "canais" to dialog.findViewById<TextView>(R.id.tvCatalogChannelStatus),
            "filmes" to dialog.findViewById<TextView>(R.id.tvCatalogMovieStatus),
            "séries" to dialog.findViewById<TextView>(R.id.tvCatalogSeriesStatus)
        )

        panel.alpha = 0f
        panel.scaleX = 0.92f
        panel.scaleY = 0.92f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(panel, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(panel, View.SCALE_X, 0.92f, 1f),
                ObjectAnimator.ofFloat(panel, View.SCALE_Y, 0.92f, 1f)
            )
            duration = 380
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val spin = ObjectAnimator.ofFloat(refreshIcon, View.ROTATION, 0f, 360f).apply {
            duration = 1400
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        dialog.setOnDismissListener { spin.cancel() }

        ServiceCatalogUpdater.refresh(
            IptvServiceConfig.baseUrl(activity),
            username,
            password,
            onProgress = { message -> activity.runOnUiThread {
                if (dialog.isShowing) {
                    progressText.text = message
                    Regex("(\\d+)/(\\d+)").find(message)?.let { match ->
                        progressBar.max = match.groupValues[2].toIntOrNull() ?: 6
                        progressBar.progress = match.groupValues[1].toIntOrNull() ?: 0
                    }
                }
            } },
            onComplete = { result -> activity.runOnUiThread {
                activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                    .putLong("last_service_update", System.currentTimeMillis())
                    .apply()
                if (dialog.isShowing) {
                    spin.cancel()
                    refreshIcon.rotation = 0f
                    refreshIcon.setColorFilter(if (result.errors.isEmpty()) 0xFF36D399.toInt() else 0xFFF5C56B.toInt())
                    titleText.text = if (result.errors.isEmpty()) "Atualização concluída" else "Atualização parcial"
                    progressText.text = if (result.errors.isEmpty()) "Conteúdos disponíveis na tua biblioteca." else "Algumas listas não responderam."
                    progressBar.progress = progressBar.max
                    if (result.errors.isNotEmpty()) {
                        summaryText.text = "Sem resposta: ${result.errors.joinToString(", ")}"
                        summaryText.visibility = View.VISIBLE
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (dialog.isShowing) dialog.dismiss()
                        onFinished(result)
                    }, 1000L)
                } else {
                    onFinished(result)
                }
            } },
            onItemStart = { key -> activity.runOnUiThread {
                if (dialog.isShowing) counterViews[key]?.apply {
                    text = "A atualizar…"
                    setTextColor(0xFF42D6E8.toInt())
                }
            } },
            onItemComplete = { key, count -> activity.runOnUiThread {
                if (dialog.isShowing) counterViews[key]?.apply {
                    text = if (count == null) "Sem resposta" else "Concluído com sucesso"
                    setTextColor(if (count == null) 0xFFF5C56B.toInt() else 0xFF36D399.toInt())
                }
            } }
        )
    }
}
