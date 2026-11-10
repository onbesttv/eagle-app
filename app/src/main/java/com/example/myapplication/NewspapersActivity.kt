package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class NewspapersActivity : BaseActivity() {

    private lateinit var btnBackNewspapers: ImageButton
    private lateinit var rvNewspaperTabs: RecyclerView
    private lateinit var rvNewspapers: RecyclerView
    private lateinit var tvNewspaperCountBadge: TextView
    private lateinit var tvEmptyNewspapers: TextView
    private lateinit var pbLoadingNewspapers: ProgressBar

    private val allNewspapers = mutableListOf<Newspaper>()
    private val displayedNewspapers = mutableListOf<Newspaper>()
    private var selectedCategory: String = "🌟 Todos"

    private val categoriesList = listOf(
        "🌟 Todos",
        "⚽ Desporto",
        "📰 Generalistas",
        "✨ Revistas",
        "🌍 Internacionais"
    )

    private val client = OkHttpClient()
    private val gson = Gson()
    private lateinit var newspaperAdapter: NewspaperAdapter
    private lateinit var categoryTabAdapter: CategoryTabAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newspapers)

        btnBackNewspapers = findViewById(R.id.btnBackNewspapers)
        rvNewspaperTabs = findViewById(R.id.rvNewspaperTabs)
        rvNewspapers = findViewById(R.id.rvNewspapers)
        tvNewspaperCountBadge = findViewById(R.id.tvNewspaperCountBadge)
        tvEmptyNewspapers = findViewById(R.id.tvEmptyNewspapers)
        pbLoadingNewspapers = findViewById(R.id.pbLoadingNewspapers)

        btnBackNewspapers.setOnClickListener { finish() }

        setupRecyclerViews()
        loadLocalNewspapers()
        fetchRemoteNewspapers()
    }

    private fun setupRecyclerViews() {
        categoryTabAdapter = CategoryTabAdapter(categoriesList) { cat ->
            selectedCategory = cat
            filterNewspapers()
        }
        rvNewspaperTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvNewspaperTabs.adapter = categoryTabAdapter

        newspaperAdapter = NewspaperAdapter(displayedNewspapers) { newspaper ->
            if (newspaper.pdfUrl.isNotBlank()) {
                val intent = Intent(this, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_PDF_URL, newspaper.pdfUrl)
                    putExtra(PdfViewerActivity.EXTRA_NEWSPAPER_TITLE, "${newspaper.nome} (${newspaper.data})")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "A edição de ${newspaper.nome} está a ser carregada.", Toast.LENGTH_SHORT).show()
            }
        }

        // 5 colunas em ecrã horizontal / TV Box
        rvNewspapers.layoutManager = GridLayoutManager(this, 5)
        rvNewspapers.adapter = newspaperAdapter
    }

    private fun loadLocalNewspapers() {
        try {
            assets.open("jornais.json").bufferedReader().use { reader ->
                val feed = gson.fromJson(reader, NewspaperFeed::class.java)
                if (feed != null && feed.jornais.isNotEmpty()) {
                    allNewspapers.clear()
                    allNewspapers.addAll(feed.jornais)
                    filterNewspapers()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchRemoteNewspapers() {
        val remoteUrl = "https://raw.githubusercontent.com/onbesttv/eagle-app/main/data/jornais.json?v=${System.currentTimeMillis()}"
        val request = Request.Builder().url(remoteUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silencioso - já temos dados locais/em cache
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        try {
                            val feed = gson.fromJson(body, NewspaperFeed::class.java)
                            if (feed != null && feed.jornais.isNotEmpty()) {
                                runOnUiThread {
                                    allNewspapers.clear()
                                    allNewspapers.addAll(feed.jornais)
                                    filterNewspapers()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        })
    }

    private fun filterNewspapers() {
        displayedNewspapers.clear()
        if (selectedCategory == "🌟 Todos") {
            displayedNewspapers.addAll(allNewspapers)
        } else {
            val cleanSelected = selectedCategory.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
            displayedNewspapers.addAll(allNewspapers.filter { newspaper ->
                val cleanCat = newspaper.categoria.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
                cleanCat.contains(cleanSelected) || cleanSelected.contains(cleanCat)
            })
        }

        newspaperAdapter.notifyDataSetChanged()
        tvNewspaperCountBadge.text = "${displayedNewspapers.size} Jornais"
        tvEmptyNewspapers.visibility = if (displayedNewspapers.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- ADAPTER DE TABS DE CATEGORIAS ---
    inner class CategoryTabAdapter(
        private val categories: List<String>,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<CategoryTabAdapter.ViewHolder>() {

        private var selectedPos = 0

        inner class ViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv) {
            init {
                tv.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val oldPos = selectedPos
                        selectedPos = pos
                        notifyItemChanged(oldPos)
                        notifyItemChanged(selectedPos)
                        onSelect(categories[pos])
                    }
                }
                tv.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_league_tab, parent, false) as TextView
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cat = categories[position]
            holder.tv.text = cat
            holder.tv.isSelected = (position == selectedPos)
        }

        override fun getItemCount(): Int = categories.size
    }

    // --- ADAPTER DE CARTÕES DE JORNAIS ---
    inner class NewspaperAdapter(
        private val list: List<Newspaper>,
        private val onClick: (Newspaper) -> Unit
    ) : RecyclerView.Adapter<NewspaperAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardNewspaper: CardView = view.findViewById(R.id.cardNewspaper)
            val ivNewspaperCover: ImageView = view.findViewById(R.id.ivNewspaperCover)
            val tvCoverPlaceholderTitle: TextView = view.findViewById(R.id.tvCoverPlaceholderTitle)
            val tvNewspaperBadge: TextView = view.findViewById(R.id.tvNewspaperBadge)
            val tvNewspaperName: TextView = view.findViewById(R.id.tvNewspaperName)
            val tvNewspaperDate: TextView = view.findViewById(R.id.tvNewspaperDate)
            val tvNewspaperPages: TextView = view.findViewById(R.id.tvNewspaperPages)

            init {
                cardNewspaper.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onClick(list[pos])
                    }
                }
                cardNewspaper.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(130).start()
                        v.elevation = 12f
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(130).start()
                        v.elevation = 0f
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_newspaper_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val newspaper = list[position]
            holder.tvNewspaperName.text = newspaper.nome
            holder.tvCoverPlaceholderTitle.text = newspaper.nome.uppercase()
            holder.tvNewspaperDate.text = newspaper.data
            holder.tvNewspaperPages.text = if (newspaper.paginas > 0) "${newspaper.paginas} pág." else "PDF"
            holder.tvNewspaperBadge.text = newspaper.pais.uppercase()

            if (newspaper.capaUrl.isNotBlank()) {
                holder.ivNewspaperCover.visibility = View.VISIBLE
                Glide.with(this@NewspapersActivity)
                    .load(newspaper.capaUrl)
                    .into(holder.ivNewspaperCover)
            } else {
                holder.ivNewspaperCover.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
