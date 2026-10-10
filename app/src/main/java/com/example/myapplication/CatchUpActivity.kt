package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CatchUpActivity : BaseActivity() {

    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val client = OkHttpClient()
    private val gson = Gson()

    private lateinit var rvChannels: RecyclerView
    private lateinit var rvPrograms: RecyclerView
    private lateinit var dayContainer: LinearLayout
    private lateinit var channelTitle: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var loading: ProgressBar

    private var user = ""
    private var pass = ""
    private var selectedChannel: LiveStream? = null
    private var availableDays = 7
    private var selectedDate: String = ""
    private var programs: List<EpgProgram> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catch_up)

        findViewById<ImageButton>(R.id.btnBackCatchUp).setOnClickListener { finish() }
        rvChannels = findViewById(R.id.rvCatchUpChannels)
        rvPrograms = findViewById(R.id.rvCatchUpPrograms)
        dayContainer = findViewById(R.id.layoutCatchUpDays)
        channelTitle = findViewById(R.id.tvCatchUpChannelTitle)
        emptyMessage = findViewById(R.id.tvCatchUpEmpty)
        loading = findViewById(R.id.pbCatchUpLoading)
        rvChannels.layoutManager = LinearLayoutManager(this)
        rvPrograms.layoutManager = LinearLayoutManager(this)

        val login = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = login.getString("SAVED_USER", "").orEmpty()
        pass = login.getString("SAVED_PASS", "").orEmpty()

        carregarCanaisComArquivo()
    }

    private fun carregarCanaisComArquivo() {
        mostrarLoading("A procurar canais com Catch Up…")
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams"
        val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    esconderLoading()
                    emptyMessage.text = "Não foi possível carregar os canais. Verifica a ligação e tenta novamente."
                    emptyMessage.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string().orEmpty()
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val todos: List<LiveStream> = gson.fromJson(json, type) ?: emptyList()
                    val comArquivo = todos.filter { channel ->
                        archiveEnabled(channel.tvArchive) || archiveDaysFromValue(channel.tvArchiveDuration) > 0
                    }
                    runOnUiThread {
                        esconderLoading()
                        if (comArquivo.isEmpty()) {
                            emptyMessage.text = "O serviço não indicou canais com Catch Up disponível."
                            emptyMessage.visibility = View.VISIBLE
                        } else {
                            emptyMessage.visibility = View.GONE
                            rvChannels.adapter = ChannelAdapter(comArquivo) { selecionarCanal(it) }
                            selecionarCanal(comArquivo[0])
                            rvChannels.postDelayed({
                                val vh = rvChannels.findViewHolderForAdapterPosition(0)
                                if (vh != null) {
                                    vh.itemView.requestFocus()
                                } else {
                                    rvChannels.scrollToPosition(0)
                                    rvChannels.postDelayed({
                                        rvChannels.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                    }, 80)
                                }
                            }, 100)
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        esconderLoading()
                        emptyMessage.text = "A resposta do serviço não contém uma lista de canais válida."
                        emptyMessage.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    private fun archiveEnabled(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> value?.toString()?.equals("1") == true || value?.toString()?.equals("true", true) == true
    }

    private fun archiveDays(channel: LiveStream): Int {
        val duration = archiveDaysFromValue(channel.tvArchiveDuration)
        return if (duration > 0) duration.coerceAtMost(7) else 7
    }

    private fun archiveDaysFromValue(value: Any?): Int {
        return when (val raw = value) {
            is Number -> raw.toInt()
            else -> raw?.toString()?.toIntOrNull() ?: 0
        }
    }

    private fun selecionarCanal(channel: LiveStream) {
        selectedChannel = channel
        availableDays = archiveDays(channel)
        channelTitle.text = channel.name
        buildDayButtons()
        carregarProgramacao(channel)
    }

    private fun buildDayButtons() {
        dayContainer.removeAllViews()
        val today = Calendar.getInstance()
        repeat(availableDays) { offset ->
            val date = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val key = SimpleDateFormat("yyyyMMdd", Locale.US).format(date.time)
            val label = when (offset) {
                0 -> "Hoje"
                1 -> "Ontem"
                else -> "$offset dias atrás"
            }
            val button = TextView(this).apply {
                text = "$label\n${SimpleDateFormat("dd/MM", Locale.getDefault()).format(date.time)}"
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(5), dp(14), dp(5))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedDate = key
                    updateDayButtons()
                    showProgramsForSelectedDay()
                }
                tag = key
            }
            dayContainer.addView(button, LinearLayout.LayoutParams(-2, dp(44)).apply {
                marginEnd = dp(7)
            })
        }
        selectedDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(today.time)
        updateDayButtons()
    }

    private fun updateDayButtons() {
        for (index in 0 until dayContainer.childCount) {
            val button = dayContainer.getChildAt(index) as TextView
            val selected = button.tag == selectedDate
            button.background = GradientDrawable().apply {
                setColor(if (selected) 0xFF254675.toInt() else 0xFF172338.toInt())
                cornerRadius = dp(9).toFloat()
                if (!selected) setStroke(dp(1), 0xFF2B3D59.toInt())
            }
            button.setTextColor(Color.WHITE)
        }
    }

    private fun carregarProgramacao(channel: LiveStream) {
        mostrarLoading("A carregar a programação…")
        emptyMessage.visibility = View.GONE
        rvPrograms.adapter = null
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_simple_data_table&stream_id=${channel.streamId}"
        val request = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    esconderLoading()
                    emptyMessage.text = "Não foi possível carregar a programação deste canal."
                    emptyMessage.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = JSONObject(response.body?.string().orEmpty())
                    val listings = root.optJSONArray("epg_listings")
                    val result = ArrayList<EpgProgram>()
                    if (listings != null) {
                        val type = object : TypeToken<EpgProgram>() {}.type
                        for (i in 0 until listings.length()) result.add(gson.fromJson(listings.getJSONObject(i).toString(), type))
                    }
                    runOnUiThread {
                        esconderLoading()
                        programs = result
                        showProgramsForSelectedDay()
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        esconderLoading()
                        programs = emptyList()
                        emptyMessage.text = "A programação Catch Up não está disponível para este canal."
                        emptyMessage.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    private fun showProgramsForSelectedDay() {
        val selected = selectedDate
        val now = System.currentTimeMillis()
        val matching = programs.filter { program ->
            val start = parseProgramDate(program.start, program.startTimestamp)
            val end = parseProgramDate(program.end, program.stopTimestamp)
            start != null && end != null && SimpleDateFormat("yyyyMMdd", Locale.US).format(start) == selected && end.time <= now
        }.sortedBy { parseProgramDate(it.start, it.startTimestamp)?.time ?: Long.MAX_VALUE }

        if (matching.isEmpty()) {
            rvPrograms.adapter = null
            emptyMessage.text = if (programs.isEmpty()) {
                "O serviço não forneceu programas Catch Up para este canal."
            } else {
                "Não há programas arquivados neste dia. Experimenta outro dia."
            }
            emptyMessage.visibility = View.VISIBLE
        } else {
            emptyMessage.visibility = View.GONE
            rvPrograms.adapter = ProgramAdapter(matching)
        }
    }

    private fun parseProgramDate(raw: String?, timestamp: String?): Date? {
        if (!raw.isNullOrBlank()) {
            val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy/MM/dd HH:mm:ss")
            for (pattern in formats) {
                try {
                    return SimpleDateFormat(pattern, Locale.getDefault()).parse(raw)
                } catch (_: Exception) {
                    // Tenta o próximo formato que a API Xtream pode devolver.
                }
            }
        }
        val seconds = timestamp?.toLongOrNull() ?: return null
        return Date(if (seconds > 100_000_000_000L) seconds else seconds * 1000L)
    }

    private fun playCatchUp(program: EpgProgram) {
        val channel = selectedChannel ?: return
        val startDate = parseProgramDate(program.start, program.startTimestamp)
        val endDate = parseProgramDate(program.end, program.stopTimestamp)
        if (startDate == null || endDate == null || endDate.time <= startDate.time) {
            Toast.makeText(this, "Horário do programa inválido para Catch Up", Toast.LENGTH_SHORT).show()
            return
        }
        val durationMinutes = ((endDate.time - startDate.time + 59_999L) / 60_000L).coerceAtLeast(1L)
        val startForUrl = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(startDate)
        val extension = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getString("stream_type", "MPEGTS") == "HLS") "m3u8" else "ts"
        val url = "$baseUrl/timeshift/$user/$pass/$durationMinutes/$startForUrl/${channel.streamId}.$extension"
        startActivity(Intent(this, VodPlayerActivity::class.java).apply {
            putExtra("STREAM_URL", url)
            putExtra("STREAM_TITLE", program.title.ifBlank { channel.name })
            putExtra("STREAM_ID", channel.streamId)
            putExtra("IS_SERIES", false)
        })
    }

    private fun mostrarLoading(message: String) {
        loading.visibility = View.VISIBLE
        emptyMessage.text = message
        emptyMessage.visibility = View.VISIBLE
    }

    private fun esconderLoading() {
        loading.visibility = View.GONE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class ChannelAdapter(
        private val channels: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ChannelHolder>() {
        private var selectedPosition = RecyclerView.NO_POSITION
        inner class ChannelHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvChannelName)
            val logo: ImageView = view.findViewById(R.id.ivChannelLogo)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelHolder {
            return ChannelHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
        }
        override fun getItemCount(): Int = channels.size
        override fun onBindViewHolder(holder: ChannelHolder, position: Int) {
            val channel = channels[position]
            holder.name.text = channel.name

            val updateBg: (Boolean) -> Unit = { hasFocus ->
                val currentSelected = holder.bindingAdapterPosition == selectedPosition
                holder.itemView.background = android.graphics.drawable.GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else if (currentSelected) {
                        setColor(0xFF23324A.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else {
                        setColor(android.graphics.Color.TRANSPARENT)
                    }
                }
            }
            updateBg(holder.itemView.isFocused)
            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                updateBg(hasFocus)
            }

            if (channel.streamIcon.isNullOrBlank()) holder.logo.setImageResource(R.mipmap.ic_launcher)
            else Glide.with(holder.itemView.context).load(channel.streamIcon).into(holder.logo)
            holder.itemView.setOnClickListener {
                val old = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
                notifyItemChanged(selectedPosition)
                onClick(channel)
            }
        }
    }

    private inner class ProgramAdapter(private val items: List<EpgProgram>) : RecyclerView.Adapter<ProgramAdapter.ProgramHolder>() {
        inner class ProgramHolder(view: View) : RecyclerView.ViewHolder(view) {
            val time: TextView = view.findViewById(1)
            val title: TextView = view.findViewById(2)
            val description: TextView = view.findViewById(3)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramHolder {
            val card = CardView(parent.context).apply {
                radius = dp(14).toFloat()
                cardElevation = dp(2).toFloat()
                setCardBackgroundColor(0xFF121D2D.toInt())
                useCompatPadding = true
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { v, hasFocus ->
                    val cv = v as CardView
                    if (hasFocus) {
                        cv.setCardBackgroundColor(0xFF2A4365.toInt())
                        cv.elevation = dp(6).toFloat()
                        cv.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                    } else {
                        cv.setCardBackgroundColor(0xFF121D2D.toInt())
                        cv.elevation = dp(2).toFloat()
                        cv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                }
            }
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val time = TextView(parent.context).apply {
                id = 1
                textSize = 13f
                setTextColor(0xFF42D6E8.toInt())
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            row.addView(time, LinearLayout.LayoutParams(dp(105), -2))
            val textColumn = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(parent.context).apply {
                id = 2
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
            }
            val description = TextView(parent.context).apply {
                id = 3
                textSize = 12f
                setTextColor(0xFF9BAAC0.toInt())
                maxLines = 2
            }
            textColumn.addView(title)
            textColumn.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            card.addView(row)
            return ProgramHolder(card)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: ProgramHolder, position: Int) {
            val program = items[position]
            val start = parseProgramDate(program.start, program.startTimestamp)
            val end = parseProgramDate(program.end, program.stopTimestamp)
            val pattern = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("time_format", "12 Hr") == "24 Hr") "HH:mm" else "hh:mm a"
            val formatter = SimpleDateFormat(pattern, Locale.getDefault())
            holder.time.text = if (start != null && end != null) "${formatter.format(start)}\n—\n${formatter.format(end)}" else ""
            holder.title.text = program.title.ifBlank { "Programa sem título" }
            holder.description.text = program.description
            holder.itemView.setOnClickListener { playCatchUp(program) }
        }
    }
}
