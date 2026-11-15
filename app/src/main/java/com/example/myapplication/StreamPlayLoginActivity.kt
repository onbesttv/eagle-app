package com.example.myapplication

import android.content.Context
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

class StreamPlayLoginActivity : BaseActivity() {

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
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
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

        // Se a app já estava em execução e o utilizador saiu há menos de 15 segundos:
        // volta diretamente para onde estava sem reiniciar e sem recarregar!
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
        val rid = RemotePanelManager.getResellerId(this)
        val isDream = rid.equals("dream", ignoreCase = true) || packageName.equals("com.redon.tv", ignoreCase = true)
        if (isDream) {
            val user = IptvServiceConfig.username(this)
            val pass = IptvServiceConfig.password(this)
            val isUserLoggedOut = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).getBoolean("USER_LOGGED_OUT", false)
            if (!isUserLoggedOut && user.isNotBlank() && pass.isNotBlank()) {
                startActivity(Intent(this, EagleTvActivity::class.java))
            } else {
                startActivity(Intent(this, DeviceActivationActivity::class.java))
            }
            overridePendingTransition(0, 0)
            finish()
            return
        }
        IptvServiceConfig.initialize(this)
        setContentView(R.layout.activity_streamplay_login)
        usernameInput = findViewById(R.id.etServiceUsername)
        passwordInput = findViewById(R.id.etServicePassword)

        // Sincroniza em segundo plano com o painel para atualizar a lista de DNS
        RemotePanelManager.sync(this)
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
        val serviceBrandLabel = if (RemotePanelManager.getResellerId(this).equals("suporte24", ignoreCase = true)) "PLAYTV" else IptvServiceConfig.displayName(selectedService).uppercase()
        findViewById<TextView>(R.id.tvLoginServiceBrand).text = serviceBrandLabel
        findViewById<TextView>(R.id.tvAutoLoginServiceBrand).text = serviceBrandLabel

        val isEagle = EagleTvConfig.isEagleTvEdition(this)
        if (isEagle) {
            findViewById<View>(R.id.ivLoginBgFuturistic)?.visibility = View.GONE
            findViewById<View>(R.id.viewLoginBgOverlay)?.visibility = View.GONE
            window.decorView.setBackgroundColor(0xFF07080B.toInt())
        }

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
            findViewById<View?>(R.id.ivJokerAppLogoLogin)?.visibility = View.GONE
            chooseServiceButton.visibility = View.GONE
            autoProgressPanel.visibility = View.VISIBLE
        }
        if (!isAutoLogin || usernameInput.text.isBlank() || passwordInput.text.isBlank()) {
            usernameInput.post {
                usernameInput.requestFocus()
                usernameInput.setSelection(usernameInput.text.length)
            }
        }
        loginButton.setOnClickListener { login() }
        if (isAutoLogin &&
            usernameInput.text.isNotBlank() && passwordInput.text.isNotBlank()) {
            login()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isAutoLogin || usernameInput.text.isBlank() || passwordInput.text.isBlank()) {
            usernameInput.postDelayed({
                usernameInput.requestFocus()
                usernameInput.setSelection(usernameInput.text.length)
            }, 80)
        }
    }

    private fun login() {
        if (RemotePanelManager.enforceOnUi(this)) {
            return
        }
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (username.isBlank() || password.isBlank()) {
            showStatus("Preenche o username e a password.")
            return
        }

        showBusy("A verificar a conta…")

        // Lista de DNS a tentar: APENAS os autorizados e configurados no painel
        val available = RemotePanelManager.getAvailableDnsList(this)
        val dnsCandidates = available
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()

        if (dnsCandidates.isEmpty()) {
            val currentDns = baseUrl.trim().trimEnd('/')
            if (currentDns.isNotBlank()) {
                dnsCandidates.add(currentDns)
            }
        }

        if (dnsCandidates.isEmpty()) {
            showStatus("Não existem servidores configurados no painel.")
            return
        }

        tentarAutenticacaoEmLista(dnsCandidates, 0, username, password)
    }

    private fun tentarAutenticacaoEmLista(
        dnsList: List<String>,
        index: Int,
        username: String,
        password: String
    ) {
        if (index >= dnsList.size) {
            runOnUiThread {
                showStatus("Username ou password incorretos.")
            }
            return
        }

        val targetDns = dnsList[index]
        val url = "$targetDns/player_api.php?username=${android.net.Uri.encode(username)}&password=${android.net.Uri.encode(password)}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVSmartersPro/3.1.5")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Se falhar a ligar a este servidor, tenta imediatamente o próximo DNS configurado
                tentarAutenticacaoEmLista(dnsList, index + 1, username, password)
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                val validation = try {
                    if (!response.isSuccessful) false to "Erro (${response.code})"
                    else validateAccount(JSONObject(raw))
                } catch (_: Exception) {
                    false to "Resposta inválida."
                }

                if (validation.first) {
                    val cleanTarget = targetDns.trim().trimEnd('/')
                    getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).edit()
                        .putString("AUTHENTICATED_DNS", cleanTarget)
                        .remove("USER_LOGGED_OUT")
                        .apply()

                    // Autenticação bem-sucedida! Define este DNS como o ativo da app
                    IptvServiceConfig.updateRemoteUrls(this@StreamPlayLoginActivity, cleanTarget, "")
                    IptvServiceConfig.saveCredentials(this@StreamPlayLoginActivity, selectedService, username, password)
                    RemotePanelManager.sync(this@StreamPlayLoginActivity)

                    runOnUiThread {
                        if (isAutoLogin) autoProgressPanel.visibility = View.GONE
                        CatalogRefreshDialog.show(this@StreamPlayLoginActivity, username, password, ::finishLogin)
                    }
                } else {
                    // Se não autenticou neste DNS (ex: conta não existe nele), testa o próximo DNS
                    tentarAutenticacaoEmLista(dnsList, index + 1, username, password)
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
        if (expiration.isNotBlank() && expiration != "null") {
            getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE).edit()
                .putString("ACCOUNT_EXP_DATE", expiration)
                .apply()
        }
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
        if (isAutoLogin) {
            autoProgressPanel.visibility = View.VISIBLE
            autoStatusText.text = "A iniciar a televisão…"
        }
        openMainActivity()
    }

    private fun openMainActivity() {
        val target = if (EagleTvConfig.isEagleTvEdition(this)) EagleTvActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        overridePendingTransition(0, 0)
        finish()
    }
}
