package com.example.myapplication

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class ExtraTutorial(
    val id: String,
    val category: String,
    val categoryLabel: String,
    val question: String,
    val hint: String,
    val drawableName: String
)

class ExtrasActivity : AppCompatActivity() {

    private lateinit var rvExtras: RecyclerView
    private lateinit var flViewerOverlay: FrameLayout
    private lateinit var ivViewerImage: ImageView
    private lateinit var tvViewerTitle: TextView
    private lateinit var tvViewerCounter: TextView
    private lateinit var btnViewerClose: TextView
    private lateinit var btnViewerPrev: ImageButton
    private lateinit var btnViewerNext: ImageButton

    private lateinit var chipAll: TextView
    private lateinit var chipDespistes: TextView
    private lateinit var chipInternet: TextView
    private lateinit var chipVpn: TextView
    private lateinit var chipDevices: TextView

    private val allTutorials = listOf(
        ExtraTutorial(
            id = "despistes_iptv",
            category = "despistes",
            categoryLabel = "⚠️ DESPISTES",
            question = "O IPTV está a falhar ou com paragens?",
            hint = "9 passos simples para resolver 99% das paragens antes de chamar o suporte.",
            drawableName = "extra_despistes_iptv"
        ),
        ExtraTutorial(
            id = "problemas_assistir",
            category = "despistes",
            categoryLabel = "⚠️ DESPISTES",
            question = "Problemas ao assistir aos conteúdos?",
            hint = "Desligar equipamentos 2 minutos da tomada e testar com e sem VPN.",
            drawableName = "extra_problemas_assistir"
        ),
        ExtraTutorial(
            id = "problemas_som_imagem",
            category = "despistes",
            categoryLabel = "⚠️ DESPISTES",
            question = "Filme ou série sem som ou imagem preta?",
            hint = "Como alterar o Player interno nas Definições da aplicação e testar.",
            drawableName = "extra_problemas_som_imagem"
        ),
        ExtraTutorial(
            id = "limpar_cache",
            category = "despistes",
            categoryLabel = "⚠️ DESPISTES",
            question = "A app está lenta ou a fechar? Como limpar o cache?",
            hint = "Passo a passo na Box Android para libertar espaço sem perder login.",
            drawableName = "extra_limpar_cache"
        ),
        ExtraTutorial(
            id = "speedtest",
            category = "internet",
            categoryLabel = "🌐 INTERNET",
            question = "O que é o Speedtest e como sei se a internet está boa?",
            hint = "Descubra como medir a velocidade real de download, upload e estabilidade.",
            drawableName = "extra_speedtest"
        ),
        ExtraTutorial(
            id = "manutencao_router",
            category = "internet",
            categoryLabel = "🌐 INTERNET",
            question = "Como fazer a manutenção do seu Router?",
            hint = "Dicas de reinício periódico, posicionamento e otimização do sinal Wi-Fi.",
            drawableName = "extra_manutencao_router"
        ),
        ExtraTutorial(
            id = "como_funciona_vpn",
            category = "vpn",
            categoryLabel = "🔒 VPN",
            question = "Como funciona a VPN no IPTV e o que faz?",
            hint = "Entenda o túnel seguro encriptado e a proteção contra bloqueios de operadoras.",
            drawableName = "extra_como_funciona_vpn"
        ),
        ExtraTutorial(
            id = "quando_usar_vpn",
            category = "vpn",
            categoryLabel = "🔒 VPN",
            question = "Quando e como devo utilizar a VPN?",
            hint = "Guia simples para saber quando é necessária, quando desligar e como usar.",
            drawableName = "extra_quando_usar_vpn"
        ),
        ExtraTutorial(
            id = "anydesk",
            category = "devices",
            categoryLabel = "💻 SUPORTE",
            question = "O que é o AnyDesk e para que serve?",
            hint = "Como partilhar o ID em segurança para assistência técnica remota à distância.",
            drawableName = "extra_anydesk"
        ),
        ExtraTutorial(
            id = "var_player_ios",
            category = "devices",
            categoryLabel = "📱 DISPOSITIVOS",
            question = "Como instalar e assistir no iPhone ou iPad (iOS)?",
            hint = "Guia de instalação e login na App Store com o leitor VAR Player.",
            drawableName = "extra_var_player_ios"
        ),
        ExtraTutorial(
            id = "supremexc_pc",
            category = "devices",
            categoryLabel = "💻 DISPOSITIVOS",
            question = "Como instalar e assistir no Computador Windows (PC)?",
            hint = "Passo a passo completo com o reprodutor SupremeXC para PC.",
            drawableName = "extra_supremexc_pc"
        )
    )

    private var displayedTutorials = ArrayList(allTutorials)
    private var currentViewerIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extras)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvExtras = findViewById(R.id.rvExtras)
        flViewerOverlay = findViewById(R.id.flViewerOverlay)
        ivViewerImage = findViewById(R.id.ivViewerImage)
        tvViewerTitle = findViewById(R.id.tvViewerTitle)
        tvViewerCounter = findViewById(R.id.tvViewerCounter)
        btnViewerClose = findViewById(R.id.btnViewerClose)
        btnViewerPrev = findViewById(R.id.btnViewerPrev)
        btnViewerNext = findViewById(R.id.btnViewerNext)

        chipAll = findViewById(R.id.chipAll)
        chipDespistes = findViewById(R.id.chipDespistes)
        chipInternet = findViewById(R.id.chipInternet)
        chipVpn = findViewById(R.id.chipVpn)
        chipDevices = findViewById(R.id.chipDevices)

        // 3 colunas em ecrã horizontal (Box TV e Telemóvel Landscape)
        rvExtras.layoutManager = GridLayoutManager(this, 3)
        rvExtras.adapter = ExtrasAdapter(displayedTutorials) { position ->
            abrirViewer(position)
        }

        chipAll.isSelected = true
        configurarFiltros()
        configurarViewer()
    }

    private fun configurarFiltros() {
        val chips = listOf(chipAll, chipDespistes, chipInternet, chipVpn, chipDevices)

        fun selecionarChip(selected: TextView, categoria: String?) {
            chips.forEach { it.isSelected = (it == selected) }
            displayedTutorials.clear()
            if (categoria == null) {
                displayedTutorials.addAll(allTutorials)
            } else {
                displayedTutorials.addAll(allTutorials.filter { it.category == categoria })
            }
            rvExtras.adapter?.notifyDataSetChanged()
        }

        chipAll.setOnClickListener { selecionarChip(chipAll, null) }
        chipDespistes.setOnClickListener { selecionarChip(chipDespistes, "despistes") }
        chipInternet.setOnClickListener { selecionarChip(chipInternet, "internet") }
        chipVpn.setOnClickListener { selecionarChip(chipVpn, "vpn") }
        chipDevices.setOnClickListener { selecionarChip(chipDevices, "devices") }
    }

    private fun configurarViewer() {
        btnViewerClose.setOnClickListener { fecharViewer() }
        flViewerOverlay.setOnClickListener { fecharViewer() }
        ivViewerImage.setOnClickListener { /* Não fecha ao clicar na imagem */ }

        btnViewerPrev.setOnClickListener {
            if (displayedTutorials.isNotEmpty()) {
                currentViewerIndex = (currentViewerIndex - 1 + displayedTutorials.size) % displayedTutorials.size
                atualizarConteudoViewer()
            }
        }

        btnViewerNext.setOnClickListener {
            if (displayedTutorials.isNotEmpty()) {
                currentViewerIndex = (currentViewerIndex + 1) % displayedTutorials.size
                atualizarConteudoViewer()
            }
        }
    }

    private fun abrirViewer(index: Int) {
        if (index !in displayedTutorials.indices) return
        currentViewerIndex = index
        flViewerOverlay.visibility = View.VISIBLE
        atualizarConteudoViewer()
        btnViewerClose.requestFocus()
    }

    private fun atualizarConteudoViewer() {
        val item = displayedTutorials[currentViewerIndex]
        tvViewerTitle.text = item.question
        tvViewerCounter.text = "Guia ${currentViewerIndex + 1} de ${displayedTutorials.size}"

        val resId = resources.getIdentifier(item.drawableName, "drawable", packageName)
        if (resId != 0) {
            Glide.with(this)
                .load(resId)
                .fitCenter()
                .into(ivViewerImage)
        } else {
            ivViewerImage.setImageDrawable(null)
        }
    }

    private fun fecharViewer() {
        flViewerOverlay.visibility = View.GONE
        ivViewerImage.setImageDrawable(null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (flViewerOverlay.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    fecharViewer()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    btnViewerPrev.performClick()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    btnViewerNext.performClick()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private class ExtrasAdapter(
        private val items: List<ExtraTutorial>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ExtrasAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardContainer: CardView = view.findViewById(R.id.cardContainer)
            val tvCategoryBadge: TextView = view.findViewById(R.id.tvCategoryBadge)
            val tvTutorialNumber: TextView = view.findViewById(R.id.tvTutorialNumber)
            val tvQuestionTitle: TextView = view.findViewById(R.id.tvQuestionTitle)
            val tvQuestionHint: TextView = view.findViewById(R.id.tvQuestionHint)
            val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_extra_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvCategoryBadge.text = item.categoryLabel
            holder.tvTutorialNumber.text = "#${position + 1}"
            holder.tvQuestionTitle.text = item.question
            holder.tvQuestionHint.text = item.hint

            val context = holder.itemView.context
            val resId = context.resources.getIdentifier(item.drawableName, "drawable", context.packageName)
            if (resId != 0) {
                Glide.with(context)
                    .load(resId)
                    .centerCrop()
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageDrawable(null)
            }

            holder.cardContainer.setOnClickListener {
                onItemClick(holder.bindingAdapterPosition)
            }

            holder.cardContainer.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                    v.elevation = 8f
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    v.elevation = 2f
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
