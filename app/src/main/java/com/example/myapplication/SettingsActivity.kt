package com.example.myapplication

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.os.LocaleListCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SettingsActivity : BaseActivity() {

    private data class SettingItem(val title: String, val icon: Int, val action: () -> Unit, val enabled: Boolean = true)
    private var settingsCardIndex = 0
    private val settings by lazy { getSharedPreferences("app_settings", MODE_PRIVATE) }
    private val login by lazy { getSharedPreferences("iptv_login_prefs", MODE_PRIVATE) }
    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val items = listOf(
            SettingItem(getString(R.string.settings_account), android.R.drawable.ic_menu_myplaces, ::showAccount),
            SettingItem(getString(R.string.settings_player_settings), android.R.drawable.ic_menu_crop, ::showPlayerSettings, enabled = false),
            SettingItem(getString(R.string.settings_player), android.R.drawable.ic_media_play, ::showPlayer, enabled = false),
            SettingItem(getString(R.string.settings_stream_type), android.R.drawable.ic_menu_slideshow, ::showStreamType, enabled = false),
            SettingItem(getString(R.string.settings_updates), R.drawable.ic_system_update, { AppUpdateManager.checkForUpdates(this, manual = true) }, enabled = true),
            SettingItem(getString(R.string.settings_language), android.R.drawable.ic_menu_sort_alphabetically, ::showLanguage),
            SettingItem(getString(R.string.settings_other), android.R.drawable.ic_menu_manage, ::showOtherSettings, enabled = false),
            SettingItem(getString(R.string.settings_logout), android.R.drawable.ic_lock_power_off, ::logout)
        )

        val grid = findViewById<GridLayout>(R.id.settingsGrid)
        grid.columnCount = 4
        grid.rowCount = 2
        items.forEachIndexed { index, item ->
            val card = createSettingCard(item)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 4, 1f),
                GridLayout.spec(index % 4, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(7), dp(7), dp(7), dp(7))
            }
            grid.addView(card, params)
        }
    }

    private fun createSettingCard(item: SettingItem): CardView {
        val accentColors = intArrayOf(
            0xFF62D7FF.toInt(), 0xFF9BA8FF.toInt(), 0xFF6BE6C2.toInt(), 0xFF73BFFF.toInt(),
            0xFFFFC76B.toInt(), 0xFFB49BFF.toInt(), 0xFF65D7E8.toInt(), 0xFFFF7B83.toInt()
        )
        val accent = accentColors[settingsCardIndex++ % accentColors.size]
        val card = CardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(7).toFloat()
            setCardBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            isEnabled = true
            alpha = if (item.enabled) 1f else 0.45f
            foreground = getDrawable(R.drawable.fg_settings_card_selector)
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(140).start()
                    v.elevation = dp(12).toFloat()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                    v.elevation = dp(7).toFloat()
                }
            }
            contentDescription = item.title
            setOnClickListener {
                if (item.enabled) {
                    item.action()
                } else {
                    Toast.makeText(this@SettingsActivity, getString(R.string.option_disabled_by_admin), Toast.LENGTH_SHORT).show()
                }
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = getDrawable(R.drawable.bg_dashboard_card)
        }
        val iconPlate = android.widget.FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((accent and 0x00FFFFFF) or 0x24000000)
                setStroke(dp(1), (accent and 0x00FFFFFF) or 0x66000000)
            }
        }
        val icon = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)
            setImageResource(item.icon)
            setColorFilter(accent)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconPlate.addView(icon)
        val title = TextView(this).apply {
            text = item.title
            setTextColor(Color.WHITE)
            textSize = 13f
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(9), 0, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        content.addView(iconPlate, LinearLayout.LayoutParams(dp(58), dp(58)))
        content.addView(title, LinearLayout.LayoutParams(-1, -2))
        card.addView(content)
        return card
    }

    private fun logout() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_logout))
            .setMessage(getString(R.string.logout_confirm_message))
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton(getString(R.string.btn_exit)) { _, _ ->
                startActivity(Intent(this, StreamPlayLoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            .create()
        dialog.show()
        styleSettingsDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { btnExit ->
            btnExit.isFocusable = true
            btnExit.isFocusableInTouchMode = true
            btnExit.post {
                btnExit.requestFocus()
            }
        }
    }

    private fun showAccount() {
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(14))
        }
        addAccountRow(details, "Conta", "A carregar…", Color.LTGRAY)
        val accountScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(details)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("ACCOUNT INFORMATION")
            .setView(accountScroll)
            .setPositiveButton("FECHAR", null)
            .create()
        dialog.show()
        val screenWidth = resources.displayMetrics.widthPixels
        dialog.window?.setLayout((screenWidth * 0.72f).toInt().coerceAtMost(screenWidth - dp(48)), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)

        val user = login.getString("SAVED_USER", "").orEmpty()
        val pass = login.getString("SAVED_PASS", "").orEmpty()
        val request = Request.Builder()
            .url("$baseUrl/player_api.php?username=${android.net.Uri.encode(user)}&password=${android.net.Uri.encode(pass)}")
            .header("User-Agent", "IPTVSmartersPro/3.1.5")
            .build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    if (dialog.isShowing) {
                        details.removeAllViews()
                        addAccountRow(details, "Estado", "Indisponível", 0xFFFF513F.toInt())
                        addAccountRow(details, "Informação", "Não foi possível consultar o servidor. Verifica a ligação à Internet.", Color.LTGRAY)
                    }
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string()
                try {
                    val responseJson = org.json.JSONObject(body ?: "{}")
                    val info = responseJson.optJSONObject("user_info")
                        ?: throw IllegalStateException("Resposta sem user_info")
                    val expRaw = info.optString("exp_date")
                        .takeUnless { it == "null" || it.isBlank() }
                        ?: responseJson.optString("exp_date").takeUnless { it == "null" || it.isBlank() }
                    val expirationDate = parseExpirationDate(expRaw)
                    val expiry = when {
                        expRaw == null || expRaw == "0" -> "Ilimitada"
                        expirationDate != null -> SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.getDefault()).format(expirationDate)
                        else -> "Data não disponibilizada"
                    }
                    val trialValue = info.opt("is_trial")
                    val isTrial = trialValue == true || trialValue?.toString() == "1"
                    val authValue = info.optString("auth")
                    val statusValue = info.optString("status")
                    val authenticated = when {
                        authValue.isNotBlank() && authValue != "null" -> authValue == "1" || authValue.equals("true", true)
                        else -> statusValue.equals("Active", true)
                    }
                    val notExpired = expirationDate == null || expirationDate.time > System.currentTimeMillis()
                    val active = authenticated && notExpired && !statusValue.equals("Expired", true) && !statusValue.equals("Disabled", true)

                    runOnUiThread {
                        if (dialog.isShowing) {
                            details.removeAllViews()
                            addAccountRow(details, "Nome de usuário", user, Color.WHITE)
                            addAccountRow(
                                details,
                                "Mensagem",
                                if (isTrial) "Esta é uma conta de avaliação." else "Esta não é uma conta de avaliação.",
                                0xFFFFD54F.toInt()
                            )
                            addAccountRow(details, "Expira", expiry, 0xFFFFD54F.toInt())
                            addAccountRow(details, "Status", if (active) "ACTIVE" else "INACTIVE", if (active) 0xFFB6FF00.toInt() else 0xFFFF513F.toInt())
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (dialog.isShowing) {
                            details.removeAllViews()
                            addAccountRow(details, "Estado", "Indisponível", 0xFFFF513F.toInt())
                            addAccountRow(details, "Informação", "O servidor devolveu dados de conta inválidos.", Color.LTGRAY)
                        }
                    }
                }
            }
        })
    }

    private fun parseExpirationDate(raw: String?): Date? {
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
            val position = ParsePosition(0)
            val date = format.parse(raw, position)
            if (date != null && position.index == raw.length) return date
        }
        return null
    }

    private fun addAccountRow(container: LinearLayout, label: String, value: String, valueColor: Int) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF152238.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFF344967.toInt())
            }
        }
        val labelView = TextView(this).apply {
            text = label.uppercase(Locale.getDefault())
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(0xFFB8C5D3.toInt())
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = if (label == "Status") 18f else 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(valueColor)
            setPadding(0, dp(5), 0, 0)
            maxLines = 2
            if (label == "Status") {
                background = GradientDrawable().apply {
                    setColor(if (value == "ACTIVE") 0xFF174526.toInt() else 0xFF542B30.toInt())
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }
        }
        card.addView(labelView)
        card.addView(valueView)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(9)
        container.addView(card, params)
    }

    private fun showPlayerSettings() {
        val categories = arrayOf("Live TV", "VOD", "Series", "Catchup", "Multi-Screen")
        AlertDialog.Builder(this)
            .setTitle("Player Settings")
            .setItems(categories) { _, which ->
                val category = categories[which]
                val key = "player_${category.lowercase().replace('-', '_')}"
                val current = settings.getString(key, settings.getString("player_global", "EXO"))
                AlertDialog.Builder(this)
                    .setTitle(category)
                    .setSingleChoiceItems(arrayOf("EXO Player", "VLC Player"), if (current == "VLC") 1 else 0) { dialog, choice ->
                        settings.edit().putString(key, if (choice == 0) "EXO" else "VLC").apply()
                        dialog.dismiss()
                        Toast.makeText(this, "$category: ${if (choice == 0) "EXO Player" else "VLC Player"}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showPlayer() {
        val current = settings.getString("player_global", "EXO")
        showModernChoiceDialog(
            "PLAYER",
            arrayOf("VLC PLAYER", "EXO PLAYER"),
            if (current == "VLC") 0 else 1
        ) { which ->
            val player = if (which == 0) "VLC" else "EXO"
            settings.edit().putString("player_global", player).apply()
            Toast.makeText(this, "$player Player selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStreamType() {
        val options = arrayOf("MPEGTS", "HLS")
        val selected = if (settings.getString("stream_type", "MPEGTS") == "HLS") 1 else 0
        showModernChoiceDialog("STREAM TYPE", options, selected) { which ->
            settings.edit().putString("stream_type", options[which]).apply()
            Toast.makeText(this, "${options[which]} selecionado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSpeedTest() {
        val result = TextView(this).apply {
            text = "PING\n— ms\n\nBAIXAR\n— Mbps\n\nENVIAR\n— Mbps"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Teste rápido")
            .setView(result)
            .setPositiveButton("Iniciar Teste", null)
            .setNegativeButton("Fechar", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            result.text = "A testar a ligação…"
            runSpeedTest { output ->
                runOnUiThread {
                    if (dialog.isShowing) {
                        result.text = output
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }
            }
        }
    }

    private fun runSpeedTest(done: (String) -> Unit) {
        thread(name = "network-speed-test") {
            try {
                val pingSamples = (1..3).map {
                    val start = System.nanoTime()
                    val connection = URL("https://speed.cloudflare.com/cdn-cgi/trace").openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.inputStream.use { it.readBytes() }
                    connection.disconnect()
                    (System.nanoTime() - start) / 1_000_000
                }
                val ping = pingSamples.average().toLong()

                val download = URL("https://speed.cloudflare.com/__down?bytes=1000000").openConnection() as HttpURLConnection
                download.connectTimeout = 10000
                download.readTimeout = 15000
                val downStart = System.nanoTime()
                val bytes = download.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                    }
                    total
                }
                val downSeconds = ((System.nanoTime() - downStart).coerceAtLeast(1)) / 1_000_000_000.0
                download.disconnect()
                val downMbps = bytes * 8 / downSeconds / 1_000_000

                val upload = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
                upload.requestMethod = "POST"
                upload.doOutput = true
                upload.connectTimeout = 10000
                upload.readTimeout = 15000
                val payload = ByteArray(250_000)
                val upStart = System.nanoTime()
                upload.outputStream.use { out: OutputStream -> out.write(payload) }
                val code = upload.responseCode
                val upSeconds = ((System.nanoTime() - upStart).coerceAtLeast(1)) / 1_000_000_000.0
                if (code in 200..299) upload.inputStream.use { it.readBytes() }
                upload.disconnect()
                val upMbps = if (code in 200..299) payload.size * 8 / upSeconds / 1_000_000 else null
                done("PING\n$ping ms\n\nBAIXAR\n${"%.2f".format(Locale.US, downMbps)} Mbps\n\nENVIAR\n${upMbps?.let { "%.2f".format(Locale.US, it) + " Mbps" } ?: "indisponível"}")
            } catch (_: Exception) {
                done("O teste não conseguiu alcançar o servidor de medição.\nVerifica a ligação à Internet e tenta novamente.")
            }
        }
    }

    private fun showLanguage() {
        val languages = arrayOf("Português", "Español", "English", "Français")
        val tags = arrayOf("pt", "es", "en", "fr")
        val currentTag = LocaleHelper.getSelectedLanguage(this)
        val selected = tags.indexOfFirst { it.equals(currentTag, ignoreCase = true) || currentTag.startsWith(it, ignoreCase = true) }.coerceAtLeast(0)
        showModernChoiceDialog(getString(R.string.select_language_title), languages, selected) { which ->
            LocaleHelper.setLocale(this, tags[which])
            recreate()
        }
    }

    private fun showOtherSettings() {
        val options = arrayOf(
            "Modo de Sono Automático",
            "TV ao vivo ativar controle de mídia",
            "Load EPG",
            "Exibição de entalhe em tela cheia",
            "Iniciar automaticamente o aplicativo após a reinicialização",
            "Status do serviço",
            "Formato da hora",
            "Carregar o último canal de TV ao vivo",
            "Redefinir o volume",
            "OTR Layout",
            "Open Source Licenses"
        )
        val keys = arrayOf("sleep_mode", "media_controls", "epg_period", "notch_fullscreen", "auto_start", "service_status", "time_format", "last_live", "reset_volume", "otr_layout", "licenses")
        val status: Array<() -> String> = arrayOf(
            { settings.getString("sleep_mode", "Automatic") ?: "Automatic" },
            { settings.getBoolean("media_controls", false).let { if (it) "LIGADO" else "DESLIGADO" } },
            { settings.getString("epg_period", "1 Day") ?: "1 Day" },
            { settings.getBoolean("notch_fullscreen", true).let { if (it) "LIGADO" else "DESLIGADO" } },
            { settings.getBoolean("auto_start", false).let { if (it) "LIGADO" else "DESLIGADO" } },
            { if (settings.getBoolean("service_active", true)) "LIGADO" else "DESLIGADO" },
            { settings.getString("time_format", "12 Hr") ?: "12 Hr" },
            { settings.getBoolean("last_live", false).let { if (it) "LIGADO" else "DESLIGADO" } },
            { "DESLIGADO" },
            { settings.getBoolean("otr_layout", false).let { if (it) "LIGADO" else "DESLIGADO" } },
            { "" }
        )

        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(this).apply { addView(rows) }
        val dialog = AlertDialog.Builder(this).setTitle("OTHER SETTINGS").setView(scroller).setPositiveButton("Fechar", null).create()
        options.forEachIndexed { index, label ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(12))
                isClickable = true
                isFocusable = true
            }
            val name = TextView(this).apply { text = label; textSize = 16f; setTextColor(Color.WHITE) }
            val value = TextView(this).apply {
                text = status[index]()
                textSize = 15f
                setTextColor(if (text == "DESLIGADO" || text == "Off") 0xFFFF513F.toInt() else 0xFFB6FF00.toInt())
                gravity = Gravity.END
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(value, LinearLayout.LayoutParams(dp(100), -2))
            rows.addView(row, LinearLayout.LayoutParams(-1, -2))
            val divider = View(this).apply { setBackgroundColor(0x55FFFFFF) }
            rows.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
            row.setOnClickListener {
                when (index) {
                    0 -> chooseValue("Modo de Sono Automático", arrayOf("Automatic", "On", "Off"), settings.getString("sleep_mode", "Automatic")) {
                        settings.edit().putString("sleep_mode", it).apply()
                        value.text = it
                        value.setTextColor(if (it == "Off") 0xFFFF513F.toInt() else 0xFFB6FF00.toInt())
                    }
                    1, 3, 4, 7, 9 -> {
                        val newValue = !settings.getBoolean(keys[index], index == 0 || index == 3)
                        settings.edit().putBoolean(keys[index], newValue).apply()
                        value.text = if (newValue) "LIGADO" else "DESLIGADO"
                        value.setTextColor(if (newValue) 0xFFB6FF00.toInt() else 0xFFFF513F.toInt())
                    }
                    2 -> chooseValue("Load EPG", arrayOf("1 Day", "3 Days", "7 Days"), settings.getString(keys[index], "1 Day")) {
                        settings.edit().putString(keys[index], it).apply(); value.text = it
                    }
                    5 -> showServiceStatus(value)
                    6 -> chooseValue("Formato da hora", arrayOf("12 Hr", "24 Hr"), settings.getString(keys[index], "12 Hr")) {
                        settings.edit().putString(keys[index], it).apply(); value.text = it
                    }
                    8 -> {
                        settings.edit().putInt("player_volume", 100).apply()
                        Toast.makeText(this, "Volume reposto", Toast.LENGTH_SHORT).show()
                    }
                    10 -> AlertDialog.Builder(this).setTitle("Open Source Licenses")
                        .setMessage("Esta aplicação utiliza bibliotecas AndroidX, Material Components, Media3, Retrofit, OkHttp, Glide e Kotlin Coroutines. Consulta as licenças incluídas nas bibliotecas para mais informações.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun showModernChoiceDialog(
        title: String,
        options: Array<String>,
        selectedIndex: Int,
        onChosen: (Int) -> Unit
    ) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(14))
            background = getDrawable(R.drawable.bg_auth_panel)
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 16f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(7))
        }
        panel.addView(heading, LinearLayout.LayoutParams(-1, -2))
        val subtitle = TextView(this).apply {
            text = getString(R.string.select_option_subtitle)
            textSize = 11f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(0xFF91A8BB.toInt())
            setPadding(0, 0, 0, dp(16))
        }
        panel.addView(subtitle, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(this)
            .setView(panel)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        var initialFocusedRow: View? = null

        options.forEachIndexed { index, option ->
            val isCurrentSelection = index == selectedIndex
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(14), 0)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = false
            }

            fun updateRowAppearance(hasFocus: Boolean) {
                row.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    if (hasFocus) {
                        setColor(0x5542D6E8.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                    } else if (isCurrentSelection) {
                        setColor(0x3342D6E8.toInt())
                        setStroke(dp(1), 0xFF42D6E8.toInt())
                    } else {
                        setColor(0x22202C3A.toInt())
                        setStroke(dp(1), 0x554B6378.toInt())
                    }
                }
            }

            updateRowAppearance(false)

            row.setOnFocusChangeListener { _, hasFocus ->
                updateRowAppearance(hasFocus)
                if (hasFocus) {
                    row.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                } else {
                    row.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }

            val label = TextView(this).apply {
                text = option
                textSize = 14f
                letterSpacing = 0.04f
                setTextColor(if (isCurrentSelection) 0xFFECFCFF.toInt() else 0xFFD2DDE7.toInt())
            }
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))

            val marker = TextView(this).apply {
                text = if (isCurrentSelection) "✓" else "○"
                textSize = if (isCurrentSelection) 17f else 19f
                gravity = Gravity.CENTER
                setTextColor(if (isCurrentSelection) 0xFF42D6E8.toInt() else 0xFF8192A3.toInt())
            }
            row.addView(marker, LinearLayout.LayoutParams(dp(28), dp(28)))

            row.setOnClickListener {
                dialog.dismiss()
                onChosen(index)
            }

            row.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) && event.action == android.view.KeyEvent.ACTION_UP) {
                    row.performClick()
                    true
                } else {
                    false
                }
            }

            val params = LinearLayout.LayoutParams(-1, dp(50))
            params.bottomMargin = dp(8)
            panel.addView(row, params)

            if (isCurrentSelection || (initialFocusedRow == null && index == 0)) {
                initialFocusedRow = row
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.68f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        val cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        cancelBtn?.setTextColor(0xFF42D6E8.toInt())
        cancelBtn?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                cancelBtn.setTextColor(Color.WHITE)
                cancelBtn.background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(0x5542D6E8.toInt())
                    setStroke(dp(2), 0xFF42D6E8.toInt())
                }
            } else {
                cancelBtn.setTextColor(0xFF42D6E8.toInt())
                cancelBtn.background = null
            }
        }

        initialFocusedRow?.post {
            initialFocusedRow.requestFocus()
        }
    }

    private fun styleSettingsDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_auth_panel)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.68f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(0xFFD2DDE7.toInt())
        val btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        btnPos?.setTextColor(0xFF42D6E8.toInt())
        btnNeg?.setTextColor(0xFF91A8BB.toInt())

        val setupButtonFocus = { btn: Button, defaultColor: Int ->
            btn.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    btn.setTextColor(Color.WHITE)
                    btn.background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(0x5542D6E8.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                    }
                } else {
                    btn.setTextColor(defaultColor)
                    btn.background = null
                }
            }
        }
        btnPos?.let { setupButtonFocus(it, 0xFF42D6E8.toInt()) }
        btnNeg?.let { setupButtonFocus(it, 0xFF91A8BB.toInt()) }
    }

    private fun chooseValue(title: String, values: Array<String>, current: String?, onChosen: (String) -> Unit) {
        showModernChoiceDialog(title.uppercase(), values, values.indexOf(current).coerceAtLeast(0)) { which ->
            onChosen(values[which])
        }
    }

    private fun showServiceStatus(valueView: TextView) {
        valueView.text = "A verificar…"
        val user = login.getString("SAVED_USER", "").orEmpty()
        val pass = login.getString("SAVED_PASS", "").orEmpty()
        val request = Request.Builder().url("$baseUrl/player_api.php?username=${android.net.Uri.encode(user)}&password=${android.net.Uri.encode(pass)}").build()
        http.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread { valueView.text = "INDISPONÍVEL"; settings.edit().putBoolean("service_active", false).apply() }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string().orEmpty()
                val active = try { org.json.JSONObject(body).optJSONObject("user_info")?.optString("auth") == "1" } catch (_: Exception) { false }
                runOnUiThread {
                    valueView.text = if (active) "LIGADO" else "DESLIGADO"
                    valueView.setTextColor(if (active) 0xFFB6FF00.toInt() else 0xFFFF513F.toInt())
                    settings.edit().putBoolean("service_active", active).apply()
                }
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
