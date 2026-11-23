package com.example.myapplication

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
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
    private lateinit var tvSubtitleNewspapers: TextView
    private lateinit var rvNewspaperTabs: RecyclerView
    private lateinit var rvNewspapers: RecyclerView
    private lateinit var tvNewspaperCountBadge: TextView
    private lateinit var tvEmptyNewspapers: TextView
    private lateinit var pbLoadingNewspapers: ProgressBar

    private val allNewspapers = mutableListOf<Newspaper>()
    private val displayedNewspapers = mutableListOf<Newspaper>()
    private var selectedCategory: String = "🌟 Todos"

    private var isEagleXciptv: Boolean = false

    private val defaultCategories = listOf(
        "🌟 Todos",
        "⚽ Desporto",
        "📰 Generalistas",
        "✨ Revistas",
        "🌍 Internacionais"
    )

    private val eagleCategories = listOf(
        "⚡ Ao Minuto",
        "📺 TV Notícias",
        "⚽ Desporto em Direto",
        "📰 Grandes Jornais",
        "💶 Economia",
        "📄 Jornais em PDF",
        "⚽ Desporto (PDF)",
        "📰 Generalistas (PDF)",
        "✨ Revistas (PDF)"
    )

    private val client = OkHttpClient()
    private val gson = Gson()
    private lateinit var newspaperAdapter: NewspaperAdapter
    private lateinit var categoryTabAdapter: CategoryTabAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_newspapers)

        val resId = RemotePanelManager.getResellerId(this)
        isEagleXciptv = resId.equals("master", ignoreCase = true)

        btnBackNewspapers = findViewById(R.id.btnBackNewspapers)
        tvSubtitleNewspapers = findViewById(R.id.tvSubtitleNewspapers)
        rvNewspaperTabs = findViewById(R.id.rvNewspaperTabs)
        rvNewspapers = findViewById(R.id.rvNewspapers)
        tvNewspaperCountBadge = findViewById(R.id.tvNewspaperCountBadge)
        tvEmptyNewspapers = findViewById(R.id.tvEmptyNewspapers)
        pbLoadingNewspapers = findViewById(R.id.pbLoadingNewspapers)

        if (isEagleXciptv) {
            tvSubtitleNewspapers.text = "Edições do Dia em PDF & Notícias em Tempo Real"
            selectedCategory = eagleCategories[0]
        } else {
            selectedCategory = defaultCategories[0]
        }

        btnBackNewspapers.setOnClickListener { finish() }
        btnBackNewspapers.foreground = null
        btnBackNewspapers.outlineProvider = null
        btnBackNewspapers.elevation = 0f
        btnBackNewspapers.stateListAnimator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnBackNewspapers.defaultFocusHighlightEnabled = false
        }
        btnBackNewspapers.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }

        setupRecyclerViews()
        loadLocalNewspapers()
        fetchRemoteNewspapers()

        // Carrega o primeiro separador
        filterNewspapers()
    }

    private fun setupRecyclerViews() {
        val categories = if (isEagleXciptv) eagleCategories else defaultCategories

        categoryTabAdapter = CategoryTabAdapter(categories) { cat ->
            selectedCategory = cat
            filterNewspapers()
        }
        rvNewspaperTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvNewspaperTabs.adapter = categoryTabAdapter

        newspaperAdapter = NewspaperAdapter(displayedNewspapers) { newspaper ->
            if (newspaper.isRss) {
                showNewsArticleDialog(newspaper)
            } else if (newspaper.pdfUrl.isNotBlank()) {
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
                    if (selectedCategory.contains("PDF") || !isEagleXciptv) {
                        filterNewspapers()
                    }
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
                                    if (selectedCategory.contains("PDF") || !isEagleXciptv) {
                                        filterNewspapers()
                                    }
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
        if (isEagleXciptv) {
            when {
                selectedCategory.contains("PDF") -> {
                    pbLoadingNewspapers.visibility = View.GONE
                    displayedNewspapers.clear()
                    when (selectedCategory) {
                        "📄 Jornais em PDF" -> displayedNewspapers.addAll(allNewspapers)
                        "⚽ Desporto (PDF)" -> displayedNewspapers.addAll(allNewspapers.filter {
                            it.categoria.contains("Desporto", ignoreCase = true)
                        })
                        "📰 Generalistas (PDF)" -> displayedNewspapers.addAll(allNewspapers.filter {
                            it.categoria.contains("Generalistas", ignoreCase = true)
                        })
                        "✨ Revistas (PDF)" -> displayedNewspapers.addAll(allNewspapers.filter {
                            it.categoria.contains("Revistas", ignoreCase = true)
                        })
                        else -> displayedNewspapers.addAll(allNewspapers)
                    }
                    updateUiState("Jornais")
                }
                else -> {
                    // Carregamento inteligente sob demanda por categoria (com cache em memória)
                    pbLoadingNewspapers.visibility = View.VISIBLE
                    val currentCat = selectedCategory
                    RssNewsManager.fetchFeedsForCategory(currentCat) { newsList ->
                        if (selectedCategory == currentCat) {
                            pbLoadingNewspapers.visibility = View.GONE
                            displayedNewspapers.clear()
                            displayedNewspapers.addAll(newsList)
                            updateUiState("Notícias")
                        }
                    }
                }
            }
        } else {
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
            updateUiState("Jornais")
        }
    }

    private fun updateUiState(itemUnit: String) {
        newspaperAdapter.notifyDataSetChanged()
        tvNewspaperCountBadge.text = "${displayedNewspapers.size} $itemUnit"
        tvEmptyNewspapers.visibility = if (displayedNewspapers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNewsArticleDialog(article: Newspaper) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_news_article)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val ivImage = dialog.findViewById<ImageView>(R.id.ivDialogNewsImage)
        val ivPlaceholder = dialog.findViewById<ImageView>(R.id.ivDialogNewsPlaceholder)
        val tvSource = dialog.findViewById<TextView>(R.id.tvDialogNewsSource)
        val tvDate = dialog.findViewById<TextView>(R.id.tvDialogNewsDate)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogNewsTitle)
        val tvDesc = dialog.findViewById<TextView>(R.id.tvDialogNewsDesc)
        val btnClose = dialog.findViewById<Button>(R.id.btnDialogNewsClose)

        tvSource.text = article.sourceName.uppercase()
        tvDate.text = article.data
        tvTitle.text = article.nome
        tvDesc.text = if (article.description.isNotBlank()) article.description else "Para ver a notícia completa, consulta a fonte original."

        if (article.capaUrl.isNotBlank()) {
            ivImage.visibility = View.VISIBLE
            ivPlaceholder.visibility = View.GONE
            Glide.with(this)
                .load(article.capaUrl)
                .into(ivImage)
        } else {
            ivImage.visibility = View.GONE
            ivPlaceholder.visibility = View.VISIBLE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        btnClose.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
        btnClose.requestFocus()

        dialog.show()
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
            holder.tvCoverPlaceholderTitle.text = newspaper.nome.take(20).uppercase()
            holder.tvNewspaperDate.text = newspaper.data
            holder.tvNewspaperPages.text = if (newspaper.isRss) "EM DIRETO" else if (newspaper.paginas > 0) "${newspaper.paginas} pág." else "PDF"
            holder.tvNewspaperBadge.text = if (newspaper.isRss) newspaper.sourceName.uppercase() else newspaper.pais.uppercase()

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
