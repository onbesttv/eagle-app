package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent

class MainActivity : AppCompatActivity() {

    // Servidor DNS fixo
    private val BASE_URL = "https://allrevplay.online:443"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ivLogo = findViewById<ImageView>(R.id.ivLogo)
        val tvAccountName = findViewById<TextView>(R.id.tvAccountName)
        val tvExpiration = findViewById<TextView>(R.id.tvExpiration)

        val cardLiveTv = findViewById<CardView>(R.id.cardLiveTv)
        val cardTvGuide = findViewById<CardView>(R.id.cardEpg)
        val cardMovies = findViewById<CardView>(R.id.cardMovies)
        val cardSeries = findViewById<CardView>(R.id.cardSeries)
        val cardCatchUp = findViewById<CardView>(R.id.cardCatchUp)

        // Carrega o teu logótipo
        ivLogo.setImageResource(R.drawable.img)

        // Credenciais guardadas ou predefinidas
        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("SAVED_USER", "").orEmpty()
        val savedPass = prefs.getString("SAVED_PASS", "").orEmpty()

        tvAccountName.text = "Utilizador: $savedUser"
        tvExpiration.text = "A verificar validade..."

        // Procura a data de expiração real na API Xtream
        obterValidadeConta(savedUser, savedPass, tvExpiration)

        // Cliques nos cartões
        cardLiveTv.setOnClickListener {
            val intent = Intent(this, LiveTvActivity::class.java)
            startActivity(intent)
        }

        val cardEpg: CardView = findViewById(R.id.cardEpg)
        cardTvGuide.setOnClickListener {
            val intent = Intent(this, EpgActivity::class.java)
            startActivity(intent)
        }
        cardMovies.setOnClickListener {
            val intent = Intent(this, MoviesActivity::class.java)
            startActivity(intent)
        }
        cardSeries.setOnClickListener {
            val intent = Intent(this, SeriesActivity::class.java)
            startActivity(intent)
        }
        cardCatchUp.setOnClickListener {
            startActivity(Intent(this, CatchUpActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnUpdateService).setOnClickListener { button ->
            atualizarCatalogo(button as ImageButton, savedUser, savedPass)
        }
    }

    private fun atualizarCatalogo(button: ImageButton, user: String, pass: String) {
        button.isEnabled = false
        CatalogRefreshDialog.show(this, user, pass) {
            button.isEnabled = true
        }
    }

    private fun obterValidadeConta(user: String, pass: String, tvExp: TextView) {
        val authUrl = "$BASE_URL/player_api.php?username=$user&password=$pass"

        val request = Request.Builder()
            .url(authUrl)
            .header("User-Agent", "IPTVSmartersPro/3.1.5")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvExp.text = "Expiração: Indisponível"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val corpo = response.body?.string() ?: return
                try {
                    val json = JSONObject(corpo)
                    val userInfo = json.optJSONObject("user_info")
                    val expDateStr = userInfo?.optString("exp_date")

                    val formatado = if (!expDateStr.isNullOrEmpty() && expDateStr != "null") {
                        val timestampSegundos = expDateStr.toLongOrNull()
                        if (timestampSegundos != null) {
                            // Multiplica por 1000 porque o Java Date trabalha em milissegundos
                            val date = Date(timestampSegundos * 1000)
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            "Expira em: ${sdf.format(date)}"
                        } else {
                            "Expira em: Ilimitado"
                        }
                    } else {
                        "Expira em: Ilimitado"
                    }

                    runOnUiThread {
                        tvExp.text = formatado
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvExp.text = "Conta Ativa"
                    }
                }
            }
        })
    }
}
