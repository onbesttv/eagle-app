package com.example.myapplication

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Gestor do aviso de expiração da conta/linha.
 * Apresenta um diálogo otimizado para Android TV quando a linha estiver a 5 dias ou menos de expirar.
 */
object AccountExpirationAlert {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private var dialogShownInThisSession = false

    fun parseExpirationDate(raw: String?): Date? {
        if (raw.isNullOrBlank() || raw == "null" || raw == "0") return null
        raw.toLongOrNull()?.let { timestamp ->
            val millis = if (timestamp > 100_000_000_000L) timestamp else timestamp * 1000L
            return Date(millis)
        }
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd hh:mm:ss a",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd hh:mm:ss a",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        for (pattern in patterns) {
            val format = SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }
            val pos = ParsePosition(0)
            val date = format.parse(raw, pos)
            if (date != null && pos.index == raw.length) return date
        }
        return null
    }

    fun checkAndShow(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("SAVED_USER", "").orEmpty()
        val pass = prefs.getString("SAVED_PASS", "").orEmpty()
        if (user.isBlank() || pass.isBlank()) return

        // 1. Verificar data guardada em cache primeiro
        val cachedExp = prefs.getString("ACCOUNT_EXP_DATE", "").orEmpty()
        val cachedDate = parseExpirationDate(cachedExp)
        if (cachedDate != null) {
            verificarESugerirAlerta(activity, cachedDate)
        }

        // 2. Consultar o servidor em segundo plano para obter sempre o valor mais recente
        val baseUrl = IptvServiceConfig.baseUrl(activity)
        val url = "$baseUrl/player_api.php?username=${android.net.Uri.encode(user)}&password=${android.net.Uri.encode(pass)}"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val json = JSONObject(body)
                    val info = json.optJSONObject("user_info") ?: return
                    val expRaw = info.optString("exp_date")
                    if (expRaw.isNotBlank() && expRaw != "null") {
                        prefs.edit().putString("ACCOUNT_EXP_DATE", expRaw).apply()
                        val serverDate = parseExpirationDate(expRaw)
                        if (serverDate != null && !dialogShownInThisSession) {
                            activity.runOnUiThread {
                                verificarESugerirAlerta(activity, serverDate)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun verificarESugerirAlerta(activity: AppCompatActivity, expDate: Date) {
        if (activity.isFinishing || activity.isDestroyed) return
        val now = System.currentTimeMillis()
        val diffMillis = expDate.time - now
        val fiveDaysMillis = 5L * 24L * 60L * 60L * 1000L

        // Só mostra se faltarem 5 dias ou menos (ou se já tiver expirado)
        if (diffMillis <= fiveDaysMillis) {
            mostrarDialogoExpiracao(activity, expDate, diffMillis)
        }
    }

    private fun mostrarDialogoExpiracao(activity: AppCompatActivity, expDate: Date, diffMillis: Long) {
        if (dialogShownInThisSession) return
        dialogShownInThisSession = true

        val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(expDate)
        val isExpired = diffMillis <= 0
        val daysRemaining = if (isExpired) 0 else ceil(diffMillis.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)).toInt().coerceAtLeast(1)

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = GradientDrawable().apply {
                setColor(0xFF101827.toInt())
                setStroke(dp(2), if (isExpired) 0xFFFF513F.toInt() else 0xFFF5C56B.toInt())
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(440), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Ícone de Aviso
        val icon = ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(if (isExpired) 0xFFFF513F.toInt() else 0xFFF5C56B.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                bottomMargin = dp(12)
            }
        }
        root.addView(icon)

        // Título
        val tvTitle = TextView(activity).apply {
            text = if (isExpired) "CONTA EXPIRADA" else "AVISO DE EXPIRAÇÃO"
            textSize = 19f
            setTextColor(if (isExpired) 0xFFFF513F.toInt() else 0xFFF5C56B.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
        root.addView(tvTitle)

        // Mensagem Principal
        val tvMsg = TextView(activity).apply {
            text = when {
                isExpired -> "A sua subscrição terminou!\nPor favor renove o seu serviço para continuar a ver."
                daysRemaining == 1 -> "Atenção: A sua linha expira em menos de 24 horas!"
                else -> "Atenção: A sua linha expira em $daysRemaining dias!"
            }
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        root.addView(tvMsg)

        // Detalhe da Data
        val tvDate = TextView(activity).apply {
            text = "Data de validade: $dateFormatted\nRenove junto do seu fornecedor para evitar cortes."
            textSize = 13f
            setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
        }
        root.addView(tvDate)

        // Botão OK / ENTENDIDO
        val btnOk = Button(activity).apply {
            text = "ENTENDIDO"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFocusable = true
            isClickable = true

            val updateBtnBg: (Boolean) -> Unit = { hasFocus ->
                background = GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(10).toFloat()
                    } else {
                        setColor(0xFF1E293B.toInt())
                        setStroke(dp(1), 0xFF334155.toInt())
                        cornerRadius = dp(10).toFloat()
                    }
                }
            }
            updateBtnBg(false)
            setOnFocusChangeListener { _, hasFocus -> updateBtnBg(hasFocus) }

            setOnClickListener {
                dialog.dismiss()
            }

            layoutParams = LinearLayout.LayoutParams(dp(200), dp(48))
        }
        root.addView(btnOk)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.CENTER)
        }

        dialog.show()

        // Focar de imediato o botão ENTENDIDO no comando remoto da Box
        btnOk.post {
            btnOk.requestFocus()
        }
    }
}
