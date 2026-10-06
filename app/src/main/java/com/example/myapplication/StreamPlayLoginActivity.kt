package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class StreamPlayLoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_LOGIN = "auto_login_saved_account"
        const val EXTRA_SERVICE_ID = "selected_service_id"
    }

    private val selectedService by lazy {
        intent.getStringExtra(EXTRA_SERVICE_ID) ?: IptvServiceConfig.activeServiceId(this)
    }
    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this, selectedService)
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(25, TimeUnit.SECONDS)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var loginButton: Button
    private lateinit var autoProgressPanel: View
    private lateinit var autoStatusText: TextView
    private val isAutoLogin by lazy { intent.getBooleanExtra(EXTRA_AUTO_LOGIN, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IptvServiceConfig.initialize(this)
        setContentView(R.layout.activity_streamplay_login)
        usernameInput = findViewById(R.id.etServiceUsername)
        passwordInput = findViewById(R.id.etServicePassword)
        val togglePassword = findViewById<ImageButton>(R.id.btnTogglePassword)
        togglePassword.setOnClickListener {
            val cursor = passwordInput.selectionStart
            val showPassword = passwordInput.transformationMethod is PasswordTransformationMethod
            passwordInput.transformationMethod = if (showPassword) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            passwordInput.setSelection(cursor.coerceIn(0, passwordInput.text.length))
            togglePassword.setImageResource(if (showPassword) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
            togglePassword.contentDescription = if (showPassword) "Ocultar password" else "Mostrar password"
        }
        statusText = findViewById(R.id.tvServiceLoginStatus)
        progress = findViewById(R.id.pbServiceLogin)
        loginButton = findViewById(R.id.btnServiceLogin)
        autoProgressPanel = findViewById(R.id.autoLoginProgressPanel)
        autoStatusText = findViewById(R.id.tvAutoLoginStatus)
        findViewById<TextView>(R.id.tvLoginServiceBrand).text = selectedService.uppercase()
        findViewById<TextView>(R.id.tvAutoLoginServiceBrand).text = selectedService.uppercase()

        IptvServiceConfig.select(this, selectedService)
        usernameInput.setText(IptvServiceConfig.username(this, selectedService))
        passwordInput.setText(IptvServiceConfig.password(this, selectedService))

        val chooseServiceButton = findViewById<ImageButton>(R.id.btnChooseService)
        chooseServiceButton.setOnClickListener {
            startActivity(Intent(this, ServiceSelectionActivity::class.java).apply {
                putExtra(ServiceSelectionActivity.EXTRA_MANUAL_SELECTION, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        if (isAutoLogin && usernameInput.text.isNotBlank() && passwordInput.text.isNotBlank()) {
            findViewById<androidx.cardview.widget.CardView>(R.id.cardServiceLogin).visibility = View.GONE
            chooseServiceButton.visibility = View.GONE
            autoProgressPanel.visibility = View.VISIBLE
        }
        loginButton.setOnClickListener { login() }
        if (isAutoLogin &&
            usernameInput.text.isNotBlank() && passwordInput.text.isNotBlank()) {
            login()
        }
    }

    private fun login() {
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (username.isBlank() || password.isBlank()) {
            showStatus("Preenche o username e a password.")
            return
        }

        showBusy("A verificar a conta…")
        val url = "$baseUrl/player_api.php?username=${android.net.Uri.encode(username)}&password=${android.net.Uri.encode(password)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVSmartersPro/3.1.5")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showStatus("Não foi possível contactar o serviço. Verifica a Internet e tenta novamente.") }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                val validation = try {
                    if (!response.isSuccessful) false to "O serviço respondeu com erro (${response.code})."
                    else validateAccount(JSONObject(raw))
                } catch (_: Exception) {
                    false to "Resposta de login inválida. Confirma os dados e tenta novamente."
                }

                if (!validation.first) {
                    runOnUiThread { showStatus(validation.second) }
                    return
                }

                IptvServiceConfig.saveCredentials(this@StreamPlayLoginActivity, selectedService, username, password)

                runOnUiThread {
                    if (isAutoLogin) autoProgressPanel.visibility = View.GONE
                    CatalogRefreshDialog.show(this@StreamPlayLoginActivity, username, password, ::finishLogin)
                }
            }
        })
    }

    private fun validateAccount(response: JSONObject): Pair<Boolean, String> {
        val info = response.optJSONObject("user_info")
            ?: return false to "Username ou password incorretos."
        val auth = info.optString("auth")
        val status = info.optString("status")
        val authenticated = when {
            auth.isNotBlank() && auth != "null" -> auth == "1" || auth.equals("true", true)
            else -> status.equals("Active", true)
        }
        if (!authenticated || status.equals("Disabled", true) || status.equals("Expired", true) || status.equals("Banned", true)) {
            return false to "A conta está inativa ou os dados de login estão incorretos."
        }

        val expiration = info.optString("exp_date")
        val expirationTimestamp = expiration.toLongOrNull()
        if (expirationTimestamp != null && expirationTimestamp != 0L) {
            val expiryMillis = if (expirationTimestamp > 100_000_000_000L) expirationTimestamp else expirationTimestamp * 1000L
            if (expiryMillis <= System.currentTimeMillis()) return false to "A conta expirou."
        } else if (expiration.isNotBlank() && expiration != "null" && expiration != "0") {
            val parsed = listOf("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd")
                .firstNotNullOfOrNull { pattern ->
                    try { SimpleDateFormat(pattern, Locale.getDefault()).parse(expiration) } catch (_: Exception) { null }
                }
            if (parsed != null && parsed.time <= System.currentTimeMillis()) return false to "A conta expirou."
        }
        return true to ""
    }

    private fun showBusy(message: String) {
        if (isAutoLogin) {
            autoProgressPanel.visibility = View.VISIBLE
            autoStatusText.text = message
            return
        }
        statusText.text = message
        statusText.setTextColor(0xFFB8C5D3.toInt())
        statusText.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        loginButton.isEnabled = false
        usernameInput.isEnabled = false
        passwordInput.isEnabled = false
    }

    private fun showStatus(message: String) {
        if (isAutoLogin) {
            autoProgressPanel.visibility = View.GONE
            findViewById<androidx.cardview.widget.CardView>(R.id.cardServiceLogin).visibility = View.VISIBLE
            findViewById<ImageButton>(R.id.btnChooseService).visibility = View.VISIBLE
        }
        statusText.text = message
        statusText.setTextColor(0xFFFF7777.toInt())
        statusText.visibility = View.VISIBLE
        progress.visibility = View.GONE
        loginButton.isEnabled = true
        usernameInput.isEnabled = true
        passwordInput.isEnabled = true
        loginButton.text = "LOGIN"
    }

    private fun finishLogin(result: ServiceRefreshResult) {
        progress.visibility = View.GONE
        statusText.visibility = View.GONE
        openMainActivity()
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
