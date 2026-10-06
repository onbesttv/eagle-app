package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
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

class SeriesActivity : AppCompatActivity() {

    private val baseUrl: String get() = IptvServiceConfig.baseUrl(this)
    private val client = OkHttpClient()
    private val gson = Gson()

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvSeries: RecyclerView
    private lateinit var etSearch: EditText

    private var allSeriesInCat: List<SeriesItem> = emptyList()
    private val allSeriesCache = mutableListOf<SeriesItem>() // Cache geral para os Favoritos
    private var seriesAdapter: SeriesGridAdapter? = null

    private var currentCategoryId = ""
    private var user = ""
    private var pass = ""

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)

        findViewById<ImageButton>(R.id.btnBackSeries)?.apply {
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

        rvCategories = findViewById(R.id.rvSeriesCategories)
        rvSeries = findViewById(R.id.rvSeriesGrid)
        etSearch = findViewById(R.id.etSearchSeries)

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvSeries.layoutManager = GridLayoutManager(this, 5)

        carregarCategoriasSeries()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                aplicarFiltroPesquisa()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        if (currentCategoryId == "FAVORITES") {
            carregarFavoritosSeries()
        } else {
            seriesAdapter?.notifyDataSetChanged()
        }
    }

    private fun carregarCategoriasSeries() {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SeriesActivity, "Erro ao carregar categorias de séries", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<SeriesCategory>>() {}.type
                    val categoriasRemotas: List<SeriesCategory> = gson.fromJson(json, type)

                    val categoriasCompletas = mutableListOf<SeriesCategory>()
                    categoriasCompletas.add(SeriesCategory("FAVORITES", "★ FAVORITOS"))
                    categoriasCompletas.addAll(categoriasRemotas)

                    runOnUiThread {
                        val initialPos = if (categoriasCompletas.size > 1) 1 else 0
                        rvCategories.adapter = SeriesCategoryAdapter(categoriasCompletas, initialPos) { cat ->
                            tratarSelecaoCategoria(cat.categoryId)
                        }
                        if (categoriasCompletas.size > 1) {
                            tratarSelecaoCategoria(categoriasCompletas[1].categoryId)
                        } else {
                            tratarSelecaoCategoria("FAVORITES")
                        }
                        rvCategories.postDelayed({
                            val vh = rvCategories.findViewHolderForAdapterPosition(initialPos)
                            if (vh != null) {
                                vh.itemView.requestFocus()
                            } else {
                                rvCategories.scrollToPosition(initialPos)
                                rvCategories.postDelayed({
                                    rvCategories.findViewHolderForAdapterPosition(initialPos)?.itemView?.requestFocus()
                                }, 80)
                            }
                        }, 100)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@SeriesActivity, "Erro no formato de categorias", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun tratarSelecaoCategoria(catId: String) {
        currentCategoryId = catId
        if (catId == "FAVORITES") {
            carregarFavoritosSeries()
        } else {
            carregarSeriesDaCategoria(catId)
        }
    }

    private fun carregarFavoritosSeries() {
        val favPrefs = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
        val favIds = favPrefs.getStringSet("fav_ids", emptySet()) ?: emptySet()

        if (favIds.isEmpty()) {
            allSeriesInCat = emptyList()
            aplicarFiltroPesquisa()
            Toast.makeText(this, "Nenhuma série nos favoritos", Toast.LENGTH_SHORT).show()
            return
        }

        val seriesFavoritas = allSeriesCache.filter { favIds.contains(it.seriesId.toString()) }

        if (seriesFavoritas.isNotEmpty()) {
            allSeriesInCat = seriesFavoritas
            aplicarFiltroPesquisa()
        } else {
            // Se ainda não estiver em cache, vai buscar a lista geral da API
            val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series"
            val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string() ?: return
                    try {
                        val type = object : TypeToken<List<SeriesItem>>() {}.type
                        val todasSeries: List<SeriesItem> = gson.fromJson(json, type)

                        runOnUiThread {
                            allSeriesCache.clear()
                            allSeriesCache.addAll(todasSeries)
                            allSeriesInCat = todasSeries.filter { favIds.contains(it.seriesId.toString()) }
                            aplicarFiltroPesquisa()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    private fun carregarSeriesDaCategoria(catId: String) {
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series&category_id=$catId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<SeriesItem>>() {}.type
                    val series: List<SeriesItem> = gson.fromJson(json, type)

                    runOnUiThread {
                        allSeriesInCat = series
                        series.forEach { s ->
                            if (allSeriesCache.none { it.seriesId == s.seriesId }) {
                                allSeriesCache.add(s)
                            }
                        }
                        aplicarFiltroPesquisa()
                    }
                } catch (e: Exception) {
                    // Ignora parsing vazio
                }
            }
        })
    }

    private fun aplicarFiltroPesquisa() {
        val q = etSearch.text?.toString()?.trim()?.lowercase() ?: ""
        val lista = if (q.isEmpty()) {
            allSeriesInCat
        } else {
            allSeriesInCat.filter { it.name.lowercase().contains(q) }
        }

        if (seriesAdapter == null) {
            seriesAdapter = SeriesGridAdapter(lista,
                onClick = { item -> abrirDetalhesSerie(item) },
                onLongClick = { item -> alternarFavoritoSerie(item) }
            )
            rvSeries.adapter = seriesAdapter
        } else {
            seriesAdapter?.atualizar(lista)
        }
    }

    private fun alternarFavoritoSerie(serie: SeriesItem) {
        val favPrefs = getSharedPreferences("series_fav_prefs", Context.MODE_PRIVATE)
        val setAtual = favPrefs.getStringSet("fav_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val idStr = serie.seriesId.toString()

        if (setAtual.contains(idStr)) {
            setAtual.remove(idStr)
            Toast.makeText(this, "${serie.name} removida dos favoritos", Toast.LENGTH_SHORT).show()
        } else {
            setAtual.add(idStr)
            Toast.makeText(this, "${serie.name} adicionada aos favoritos!", Toast.LENGTH_SHORT).show()
        }

        favPrefs.edit().putStringSet("fav_ids", setAtual).apply()

        if (currentCategoryId == "FAVORITES") {
            carregarFavoritosSeries()
        } else {
            seriesAdapter?.notifyDataSetChanged()
        }
    }

    private fun abrirDetalhesSerie(serie: SeriesItem) {
        val intent = Intent(this, SeriesDetailActivity::class.java).apply {
            putExtra("SERIES_ID", serie.seriesId)
            putExtra("SERIES_NAME", serie.name)
            putExtra("SERIES_COVER", serie.cover)
            putExtra("SERIES_PLOT", serie.plot)
        }
        startActivity(intent)
    }

    // Adaptador Categorias
    inner class SeriesCategoryAdapter(
        private val list: List<SeriesCategory>,
        private var selectedPosition: Int,
        private val onClick: (SeriesCategory) -> Unit
    ) : RecyclerView.Adapter<SeriesCategoryAdapter.ViewHolder>() {

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

            if (cat.categoryId == "FAVORITES") {
                holder.tv.setTextColor(0xFFF5C56B.toInt())
            } else {
                holder.tv.setTextColor(0xFFFFFFFF.toInt())
            }

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
                    holder.itemView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
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

    // Adaptador Grelha de Séries
    inner class SeriesGridAdapter(
        private var list: List<SeriesItem>,
        private val onClick: (SeriesItem) -> Unit,
        private val onLongClick: (SeriesItem) -> Unit
    ) : RecyclerView.Adapter<SeriesGridAdapter.ViewHolder>() {

        fun atualizar(novaLista: List<SeriesItem>) {
            list = novaLista
            notifyDataSetChanged()
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivCover: ImageView = v.findViewById(R.id.ivSeriesCover)
            val tvTitle: TextView = v.findViewById(R.id.tvSeriesTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_series, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = list[position]
            holder.tvTitle.text = s.name

            if (!s.cover.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(s.cover)
                    .centerCrop()
                    .into(holder.ivCover)
            } else {
                holder.ivCover.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(140).start()
                    (v as? androidx.cardview.widget.CardView)?.cardElevation = dp(12).toFloat()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                    (v as? androidx.cardview.widget.CardView)?.cardElevation = dp(4).toFloat()
                }
            }

            holder.itemView.setOnClickListener { onClick(s) }

            holder.itemView.setOnLongClickListener {
                onLongClick(s)
                true
            }
        }
    }
}
