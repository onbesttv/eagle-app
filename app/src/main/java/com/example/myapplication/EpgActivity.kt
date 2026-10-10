package com.example.myapplication

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class EpgActivity : BaseActivity() {

    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val client = OkHttpClient()
    private val gson = Gson()

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var rvPrograms: RecyclerView
    private lateinit var tvChannelHeader: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var pbLoading: ProgressBar

    private var user = ""
    private var pass = ""

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatProgramTime(value: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(value) ?: return value
            val pattern = if (getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("time_format", "12 Hr") == "24 Hr") "HH:mm" else "hh:mm a"
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (_: Exception) {
            value
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epg)

        // Botão para voltar ao Dashboard / Menu Principal
        findViewById<ImageButton>(R.id.btnBackEpg)?.apply {
            setOnClickListener { finish() }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).start()
                    v.elevation = dp(8).toFloat()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                    v.elevation = 0f
                }
            }
        }

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "").orEmpty()
        pass = prefs.getString("SAVED_PASS", "").orEmpty()

        rvCategories = findViewById(R.id.rvEpgCategories)
        rvChannels = findViewById(R.id.rvEpgChannels)
        rvPrograms = findViewById(R.id.rvEpgPrograms)
        tvChannelHeader = findViewById(R.id.tvEpgChannelHeader)
        tvEmpty = findViewById(R.id.tvEpgEmpty)
        pbLoading = findViewById(R.id.pbEpgLoading)

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvChannels.layoutManager = LinearLayoutManager(this)
        rvPrograms.layoutManager = LinearLayoutManager(this)

        carregarCategorias()
    }

    private fun carregarCategorias() {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@EpgActivity, "Erro ao carregar categorias", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveCategory>>() {}.type
                    val categorias: List<LiveCategory> = gson.fromJson(json, type)

                    runOnUiThread {
                        rvCategories.adapter = EpgCategoryAdapter(categorias) { cat ->
                            carregarCanais(cat.categoryId)
                        }
                        if (categorias.isNotEmpty()) {
                            carregarCanais(categorias[0].categoryId)
                            rvCategories.postDelayed({
                                val vh = rvCategories.findViewHolderForAdapterPosition(0)
                                if (vh != null) {
                                    vh.itemView.requestFocus()
                                } else {
                                    rvCategories.scrollToPosition(0)
                                    rvCategories.postDelayed({
                                        rvCategories.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                    }, 80)
                                }
                            }, 100)
                        }
                    }
                } catch (e: Exception) {
                    // Erro de parse
                }
            }
        })
    }

    private fun carregarCanais(catId: String) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams&category_id=$catId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<LiveStream>>() {}.type
                    val canais: List<LiveStream> = gson.fromJson(json, type)

                    runOnUiThread {
                        rvChannels.adapter = EpgChannelAdapter(canais) { canal ->
                            tvChannelHeader.text = "GUIA: ${canal.name.uppercase()}"
                            carregarEpgDoCanal(canal.streamId)
                        }
                        if (canais.isNotEmpty()) {
                            tvChannelHeader.text = "GUIA: ${canais[0].name.uppercase()}"
                            carregarEpgDoCanal(canais[0].streamId)
                        }
                    }
                } catch (e: Exception) {
                    // Erro de parse
                }
            }
        })
    }

    private fun carregarEpgDoCanal(streamId: Int) {
        pbLoading.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvPrograms.adapter = null

        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_simple_data_table&stream_id=$streamId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val epgData = gson.fromJson(json, EpgResponse::class.java)
                    val programas = epgData.epgListings ?: emptyList()
                    val days = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getString("epg_period", "1 Day")?.substringBefore(' ')?.toLongOrNull() ?: 1L
                    val horizon = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val filteredPrograms = programas.filter { program ->
                        val start = try { sdf.parse(program.start.orEmpty())?.time } catch (_: Exception) { null }
                        start == null || start <= horizon
                    }
                    val displayedPrograms = filteredPrograms.ifEmpty { programas }

                    runOnUiThread {
                        pbLoading.visibility = View.GONE
                        if (displayedPrograms.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            tvEmpty.visibility = View.GONE
                            rvPrograms.adapter = ProgramAdapter(displayedPrograms)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        pbLoading.visibility = View.GONE
                        tvEmpty.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    // Adaptador de Categorias
    inner class EpgCategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<EpgCategoryAdapter.ViewHolder>() {

        private var selectedPosition = 0

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvCategoryTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cat = list[position]
            holder.tv.text = cat.categoryName

            val updateCatBg: (Boolean) -> Unit = { hasFocus ->
                val currentSelected = holder.bindingAdapterPosition == selectedPosition
                holder.tv.background = GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else if (currentSelected) {
                        setColor(0xFF23324A.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else {
                        setColor(0x00000000)
                    }
                }
                if (hasFocus) {
                    holder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                    holder.itemView.elevation = dp(4).toFloat()
                } else {
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    holder.itemView.elevation = 0f
                }
            }
            updateCatBg(holder.itemView.isFocused)

            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                updateCatBg(hasFocus)
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onClick(cat)
                }
            }
        }
    }

    // Adaptador de Canais
    inner class EpgChannelAdapter(
        private val list: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<EpgChannelAdapter.ViewHolder>() {

        private var selectedPosition = 0

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvChannelName)
            val iv: ImageView = v.findViewById(R.id.ivChannelLogo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val canal = list[position]
            holder.tv.text = canal.name

            val updateChannelBg: (Boolean) -> Unit = { hasFocus ->
                val currentSelected = holder.bindingAdapterPosition == selectedPosition
                holder.itemView.background = GradientDrawable().apply {
                    if (hasFocus) {
                        setColor(0xFF2A4365.toInt())
                        setStroke(dp(2), 0xFF42D6E8.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else if (currentSelected) {
                        setColor(0xFF172338.toInt())
                        cornerRadius = dp(8).toFloat()
                    } else {
                        setColor(0x00000000)
                    }
                }
                if (hasFocus) {
                    holder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                    holder.itemView.elevation = dp(4).toFloat()
                } else {
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    holder.itemView.elevation = 0f
                }
            }
            updateChannelBg(holder.itemView.isFocused)

            holder.itemView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                updateChannelBg(hasFocus)
            }

            if (!canal.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context).load(canal.streamIcon).into(holder.iv)
            } else {
                holder.iv.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onClick(canal)
                }
            }
        }
    }

    // Adaptador de Programas (Guia Cronológico)
    inner class ProgramAdapter(
        private val list: List<EpgProgram>
    ) : RecyclerView.Adapter<ProgramAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvTime: TextView = v.findViewById(R.id.tvProgramTime)
            val tvTitle: TextView = v.findViewById(R.id.tvProgramTitle)
            val tvDesc: TextView = v.findViewById(R.id.tvProgramDesc)
            val badgeNow: TextView = v.findViewById(R.id.tvNowBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_epg_program, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prog = list[position]

            val sTime = prog.start ?: ""
            val eTime = prog.end ?: ""
            holder.tvTime.text = if (sTime.isNotEmpty()) "${this@EpgActivity.formatProgramTime(sTime)}  ➔  ${this@EpgActivity.formatProgramTime(eTime)}" else "Horário a definir"

            holder.tvTitle.text = if (prog.title.isNotEmpty()) prog.title else "Sem título"
            holder.tvDesc.text = if (prog.description.isNotEmpty()) prog.description else "Sem descrição disponível."

            if (prog.nowPlaying == 1) {
                holder.badgeNow.visibility = View.VISIBLE
            } else {
                holder.badgeNow.visibility = View.GONE
            }

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start()
                    (v as? androidx.cardview.widget.CardView)?.cardElevation = dp(8).toFloat()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    (v as? androidx.cardview.widget.CardView)?.cardElevation = dp(2).toFloat()
                }
            }

            holder.itemView.setOnClickListener {
                AlertDialog.Builder(this@EpgActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(prog.title.ifEmpty { "Programa" })
                    .setMessage("${holder.tvTime.text}\n\n${prog.description.ifEmpty { "Sem sinopse disponível." }}")
                    .setPositiveButton("Fechar", null)
                    .show()
            }
        }
    }
}
