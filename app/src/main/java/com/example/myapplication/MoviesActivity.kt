package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

class MoviesActivity : AppCompatActivity() {

    private val BASE_URL = "https://allrevplay.online:443"
    private val client = OkHttpClient()
    private val gson = Gson()

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvMovies: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnSortOrder: Button

    private var allMoviesInCat = listOf<VodStream>()
    private val allMoviesCache = mutableListOf<VodStream>() // Cache geral para Favoritos e Histórico
    private var movieAdapter: MovieGridAdapter? = null

    private var currentCategoryId = ""
    private var user = ""
    private var pass = ""

    // 0 = Padrão, 1 = Nome (A-Z), 2 = Mais Recentes, 3 = Melhor Avaliação
    private var currentSortMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movies)

        // Botão para sair da atividade e regressar ao menu principal
        findViewById<ImageButton>(R.id.btnBackMovies)?.setOnClickListener {
            finish()
        }

        val prefs = getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        user = prefs.getString("SAVED_USER", "Wcjhrr3mzj") ?: "Wcjhrr3mzj"
        pass = prefs.getString("SAVED_PASS", "qww2rsEHnY") ?: "qww2rsEHnY"

        rvCategories = findViewById(R.id.rvMovieCategories)
        rvMovies = findViewById(R.id.rvMoviesGrid)
        etSearch = findViewById(R.id.etSearchMovie)
        btnSortOrder = findViewById(R.id.btnSortOrder)

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvMovies.layoutManager = GridLayoutManager(this, 5)

        carregarCategoriasFilmes()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                aplicarFiltroEOrdenacao()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSortOrder.setOnClickListener {
            abrirDialogoOrdenacao()
        }
    }

    override fun onResume() {
        super.onResume()
        // Atualiza a vista ao regressar da reprodução ou dos detalhes
        when (currentCategoryId) {
            "FAVORITES" -> carregarFavoritos()
            "RESUME" -> carregarContinuarAVer()
            else -> movieAdapter?.notifyDataSetChanged()
        }
    }

    private fun carregarCategoriasFilmes() {
        val url = "$BASE_URL/player_api.php?username=$user&password=$pass&action=get_vod_categories"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MoviesActivity, "Erro ao carregar categorias", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<VodCategory>>() {}.type
                    val categoriasRemotas: List<VodCategory> = gson.fromJson(json, type)

                    val categoriasCompletas = mutableListOf<VodCategory>()
                    categoriasCompletas.add(VodCategory("FAVORITES", "★ FAVORITOS", 0))
                    categoriasCompletas.add(VodCategory("RESUME", "▶ CONTINUAR A VER", 0))
                    categoriasCompletas.addAll(categoriasRemotas)

                    runOnUiThread {
                        val initialPos = if (categoriasCompletas.size > 2) 2 else 0
                        rvCategories.adapter = MovieCategoryAdapter(categoriasCompletas, initialPos) { cat ->
                            tratarSelecaoCategoria(cat.categoryId)
                        }
                        if (categoriasCompletas.size > 2) {
                            tratarSelecaoCategoria(categoriasCompletas[2].categoryId)
                        } else {
                            tratarSelecaoCategoria("FAVORITES")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MoviesActivity, "Erro no formato de categorias", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun tratarSelecaoCategoria(catId: String) {
        currentCategoryId = catId
        when (catId) {
            "FAVORITES" -> carregarFavoritos()
            "RESUME" -> carregarContinuarAVer()
            else -> carregarFilmesDaCategoria(catId)
        }
    }

    private fun carregarFavoritos() {
        val favPrefs = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
        val favIds = favPrefs.getStringSet("fav_ids", emptySet()) ?: emptySet()

        if (favIds.isEmpty()) {
            allMoviesInCat = emptyList()
            aplicarFiltroEOrdenacao()
            Toast.makeText(this, "Nenhum filme nos favoritos", Toast.LENGTH_SHORT).show()
            return
        }

        val filmesFavoritos = allMoviesCache.filter { favIds.contains(it.streamId.toString()) }

        if (filmesFavoritos.isNotEmpty()) {
            allMoviesInCat = filmesFavoritos
            aplicarFiltroEOrdenacao()
        } else {
            // Busca o catálogo geral para preencher a cache caso o utilizador entre logo em favoritos
            val url = "$BASE_URL/player_api.php?username=$user&password=$pass&action=get_vod_streams"
            val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string() ?: return
                    try {
                        val type = object : TypeToken<List<VodStream>>() {}.type
                        val todosFilmes: List<VodStream> = gson.fromJson(json, type)

                        runOnUiThread {
                            allMoviesCache.clear()
                            allMoviesCache.addAll(todosFilmes)
                            allMoviesInCat = todosFilmes.filter { favIds.contains(it.streamId.toString()) }
                            aplicarFiltroEOrdenacao()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    private fun carregarContinuarAVer() {
        val resumePrefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)
        val todosOsRegistos = resumePrefs.all

        val idsComProgresso = todosOsRegistos.keys
            .filter { it.startsWith("pos_") && (todosOsRegistos[it] as? Long ?: 0L) > 10000L }
            .map { it.removePrefix("pos_") }
            .toSet()

        if (idsComProgresso.isEmpty()) {
            allMoviesInCat = emptyList()
            aplicarFiltroEOrdenacao()
            Toast.makeText(this, "Nenhum filme em progresso", Toast.LENGTH_SHORT).show()
            return
        }

        val filmesEmProgresso = allMoviesCache.filter { idsComProgresso.contains(it.streamId.toString()) }

        if (filmesEmProgresso.isNotEmpty()) {
            allMoviesInCat = filmesEmProgresso
            aplicarFiltroEOrdenacao()
        } else {
            val url = "$BASE_URL/player_api.php?username=$user&password=$pass&action=get_vod_streams"
            val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string() ?: return
                    try {
                        val type = object : TypeToken<List<VodStream>>() {}.type
                        val todosFilmes: List<VodStream> = gson.fromJson(json, type)

                        runOnUiThread {
                            allMoviesCache.clear()
                            allMoviesCache.addAll(todosFilmes)
                            allMoviesInCat = todosFilmes.filter { idsComProgresso.contains(it.streamId.toString()) }
                            aplicarFiltroEOrdenacao()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    private fun carregarFilmesDaCategoria(catId: String) {
        val url = "$BASE_URL/player_api.php?username=$user&password=$pass&action=get_vod_streams&category_id=$catId"
        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro/3.1.5").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                try {
                    val type = object : TypeToken<List<VodStream>>() {}.type
                    val filmes: List<VodStream> = gson.fromJson(json, type)

                    runOnUiThread {
                        allMoviesInCat = filmes
                        filmes.forEach { f ->
                            if (allMoviesCache.none { it.streamId == f.streamId }) {
                                allMoviesCache.add(f)
                            }
                        }
                        aplicarFiltroEOrdenacao()
                    }
                } catch (e: Exception) {
                    // Ignora parsing vazio
                }
            }
        })
    }

    private fun aplicarFiltroEOrdenacao() {
        val query = etSearch.text.toString().lowercase().trim()
        var lista = if (query.isEmpty()) {
            allMoviesInCat
        } else {
            allMoviesInCat.filter { it.name.lowercase().contains(query) }
        }

        lista = when (currentSortMode) {
            1 -> lista.sortedBy { it.name.lowercase() }
            2 -> lista.sortedByDescending { it.streamId }
            3 -> lista.sortedByDescending { it.rating_5based ?: it.rating?.toDoubleOrNull() ?: 0.0 }
            else -> lista
        }

        if (movieAdapter == null) {
            movieAdapter = MovieGridAdapter(
                lista,
                onClick = { filme -> abrirFilme(filme) },
                onLongClick = { filme -> alternarFavorito(filme) }
            )
            rvMovies.adapter = movieAdapter
        } else {
            movieAdapter?.atualizar(lista)
        }
    }

    private fun abrirDialogoOrdenacao() {
        val opcoes = arrayOf("Padrão", "Nome (A-Z)", "Mais Recentes", "Melhor Classificação (Rating)")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Ordenar filmes por:")
            .setSingleChoiceItems(opcoes, currentSortMode) { dialog, which ->
                currentSortMode = which
                btnSortOrder.text = "Ordenar: ${opcoes[which]}"
                aplicarFiltroEOrdenacao()
                dialog.dismiss()
            }
            .show()
    }

    private fun alternarFavorito(filme: VodStream) {
        val favPrefs = getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
        val setAtual = favPrefs.getStringSet("fav_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val idStr = filme.streamId.toString()

        if (setAtual.contains(idStr)) {
            setAtual.remove(idStr)
            Toast.makeText(this, "${filme.name} removido dos favoritos", Toast.LENGTH_SHORT).show()
        } else {
            setAtual.add(idStr)
            Toast.makeText(this, "${filme.name} adicionado aos favoritos!", Toast.LENGTH_SHORT).show()
        }

        favPrefs.edit().putStringSet("fav_ids", setAtual).apply()

        if (currentCategoryId == "FAVORITES") {
            carregarFavoritos()
        } else {
            movieAdapter?.notifyDataSetChanged()
        }
    }

    private fun abrirFilme(filme: VodStream) {
        val intent = Intent(this, MovieDetailActivity::class.java).apply {
            putExtra("STREAM_ID", filme.streamId)
            putExtra("STREAM_TITLE", filme.name)
            putExtra("STREAM_ICON", filme.streamIcon)
            putExtra("CONTAINER_EXT", filme.containerExtension ?: "mp4")
        }
        startActivity(intent)
    }

    // --- ADAPTER CATEGORIAS VOD ---
    inner class MovieCategoryAdapter(
        private val list: List<VodCategory>,
        private var selectedPosition: Int,
        private val onClick: (VodCategory) -> Unit
    ) : RecyclerView.Adapter<MovieCategoryAdapter.ViewHolder>() {

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

            if (cat.categoryId == "FAVORITES" || cat.categoryId == "RESUME") {
                holder.tv.setTextColor(0xFFFFD93D.toInt())
            } else {
                holder.tv.setTextColor(0xFFFFFFFF.toInt())
            }

            holder.tv.setBackgroundColor(if (position == selectedPosition) 0xFF1E2836.toInt() else 0x00000000)

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

    // --- ADAPTER GRELHA DE POSTERS ---
    inner class MovieGridAdapter(
        private var list: List<VodStream>,
        private val onClick: (VodStream) -> Unit,
        private val onLongClick: (VodStream) -> Unit
    ) : RecyclerView.Adapter<MovieGridAdapter.ViewHolder>() {

        fun atualizar(novaLista: List<VodStream>) {
            list = novaLista
            notifyDataSetChanged()
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ivPoster: ImageView = v.findViewById(R.id.ivMoviePoster)
            val tvTitle: TextView = v.findViewById(R.id.tvMovieTitle)
            val ivFavStar: ImageView = v.findViewById(R.id.ivFavStar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val movie = list[position]
            holder.tvTitle.text = movie.name

            val favPrefs = holder.itemView.context.getSharedPreferences("movie_fav_prefs", Context.MODE_PRIVATE)
            val favIds = favPrefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
            val isFav = favIds.contains(movie.streamId.toString())

            holder.ivFavStar.visibility = if (isFav) View.VISIBLE else View.GONE
            holder.ivFavStar.setImageResource(if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)

            if (!movie.streamIcon.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(movie.streamIcon)
                    .centerCrop()
                    .into(holder.ivPoster)
            } else {
                holder.ivPoster.setImageResource(R.mipmap.ic_launcher)
            }

            holder.itemView.setOnClickListener { onClick(movie) }

            holder.itemView.setOnLongClickListener {
                onLongClick(movie)
                true
            }
        }
    }
}