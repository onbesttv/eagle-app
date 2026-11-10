package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class SportsAgendaActivity : BaseActivity() {

    private lateinit var btnBackSports: ImageButton
    private lateinit var btnRefreshSports: ImageButton
    private lateinit var rvLeagueTabs: RecyclerView
    private lateinit var rvMatches: RecyclerView
    private lateinit var tvMatchCountBadge: TextView
    private lateinit var tvEmptyMatches: TextView
    private lateinit var pbLoadingSports: ProgressBar

    private val allMatches = mutableListOf<SportsMatch>()
    private val displayedMatches = mutableListOf<SportsMatch>()
    private var selectedLeague: String = "🌟 Todos"

    private val leaguesList = listOf(
        "🌟 Todos",
        "🇵🇹 Liga Portugal",
        "🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League",
        "🇪🇸 La Liga",
        "⭐ Champions League",
        "🏆 Liga Europa",
        "🇮🇹 Serie A",
        "🇩🇪 Bundesliga"
    )

    private lateinit var matchAdapter: MatchAdapter
    private lateinit var leagueTabAdapter: LeagueTabAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sports_agenda)

        btnBackSports = findViewById(R.id.btnBackSports)
        btnRefreshSports = findViewById(R.id.btnRefreshSports)
        rvLeagueTabs = findViewById(R.id.rvLeagueTabs)
        rvMatches = findViewById(R.id.rvMatches)
        tvMatchCountBadge = findViewById(R.id.tvMatchCountBadge)
        tvEmptyMatches = findViewById(R.id.tvEmptyMatches)
        pbLoadingSports = findViewById(R.id.pbLoadingSports)

        btnBackSports.setOnClickListener { finish() }
        btnRefreshSports.setOnClickListener {
            Toast.makeText(this, "A atualizar jogos do dia...", Toast.LENGTH_SHORT).show()
            carregarAgenda(forceOnline = true)
        }

        setupRecyclerViews()
        carregarAgenda(forceOnline = false)
    }

    private fun setupRecyclerViews() {
        leagueTabAdapter = LeagueTabAdapter(leaguesList) { league ->
            selectedLeague = league
            filterMatches()
        }
        rvLeagueTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvLeagueTabs.adapter = leagueTabAdapter

        matchAdapter = MatchAdapter(displayedMatches) { match ->
            if (match.canal.isNotBlank() && !match.canal.contains("anunciar", ignoreCase = true)) {
                Toast.makeText(this, "A sintonizar ${match.canal}...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LiveTvActivity::class.java).apply {
                    putExtra("TARGET_CHANNEL_NAME", match.canal)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "${match.equipaCasa} vs ${match.equipaFora}", Toast.LENGTH_SHORT).show()
            }
        }
        rvMatches.layoutManager = LinearLayoutManager(this)
        rvMatches.adapter = matchAdapter
    }

    private fun carregarAgenda(forceOnline: Boolean) {
        pbLoadingSports.visibility = View.VISIBLE
        SportsAgendaManager.loadOrRefresh(this, forceOnline = forceOnline) { matches ->
            runOnUiThread {
                pbLoadingSports.visibility = View.GONE
                allMatches.clear()
                allMatches.addAll(matches)
                filterMatches()
            }
        }
    }

    private fun filterMatches() {
        displayedMatches.clear()
        if (selectedLeague == "🌟 Todos") {
            displayedMatches.addAll(allMatches)
        } else {
            val cleanSelected = selectedLeague.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
            displayedMatches.addAll(allMatches.filter { match ->
                val cleanMatch = match.liga.replace(Regex("[^a-zA-Z ]"), "").trim().lowercase()
                cleanMatch.contains(cleanSelected) || cleanSelected.contains(cleanMatch)
            })
        }

        matchAdapter.notifyDataSetChanged()
        tvMatchCountBadge.text = "${displayedMatches.size} Jogos"
        tvEmptyMatches.visibility = if (displayedMatches.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- ADAPTER DE TABS DE LIGAS ---
    inner class LeagueTabAdapter(
        private val leagues: List<String>,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<LeagueTabAdapter.ViewHolder>() {

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
                        onSelect(leagues[pos])
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
            val league = leagues[position]
            holder.tv.text = league
            holder.tv.isSelected = (position == selectedPos)
        }

        override fun getItemCount(): Int = leagues.size
    }

    // --- ADAPTER DE JOGOS ---
    inner class MatchAdapter(
        private val list: List<SportsMatch>,
        private val onClick: (SportsMatch) -> Unit
    ) : RecyclerView.Adapter<MatchAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardMatch: CardView = view.findViewById(R.id.cardMatch)
            val tvMatchDay: TextView = view.findViewById(R.id.tvMatchDay)
            val tvMatchTime: TextView = view.findViewById(R.id.tvMatchTime)
            val tvMatchLeague: TextView = view.findViewById(R.id.tvMatchLeague)
            val tvHomeTeam: TextView = view.findViewById(R.id.tvHomeTeam)
            val tvAwayTeam: TextView = view.findViewById(R.id.tvAwayTeam)
            val tvMatchChannel: TextView = view.findViewById(R.id.tvMatchChannel)

            init {
                cardMatch.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onClick(list[pos])
                    }
                }
                cardMatch.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(130).start()
                        v.elevation = 10f
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(130).start()
                        v.elevation = 0f
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sports_match, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val match = list[position]
            holder.tvMatchDay.text = match.data
            holder.tvMatchTime.text = match.hora
            holder.tvMatchLeague.text = match.liga
            holder.tvHomeTeam.text = match.equipaCasa
            holder.tvAwayTeam.text = match.equipaFora
            holder.tvMatchChannel.text = if (match.canal.isNotBlank()) match.canal else "A anunciar"

            if (match.destaque) {
                holder.tvMatchDay.setTextColor(0xFF38BDF8.toInt())
            } else {
                holder.tvMatchDay.setTextColor(0xFF94A3B8.toInt())
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
