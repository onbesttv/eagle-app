package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class VpnActivity : BaseActivity() {

    private val REQUEST_VPN_CODE = 4455

    private lateinit var btnBackVpn: ImageButton
    private lateinit var btnToggleVpn: Button
    private lateinit var ivVpnShield: ImageView
    private lateinit var tvVpnStatus: TextView
    private lateinit var tvVpnStatusSub: TextView
    private lateinit var tvVpnSelectedLocation: TextView
    private lateinit var tvVpnIp: TextView
    private lateinit var tvVpnDuration: TextView
    private lateinit var rvVpnRegions: RecyclerView
    private lateinit var pbVpnLoading: ProgressBar

    private var regionsList: List<PiaRegion> = emptyList()
    private var selectedRegion: PiaRegion? = null
    private var regionAdapter: RegionAdapter? = null

    private val durationHandler = Handler(Looper.getMainLooper())
    private val durationRunnable = object : Runnable {
        override fun run() {
            val startTime = PiaVpnManager.getConnectionStartTime()
            if (startTime > 0 && PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTED) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val hours = elapsed / 3600
                val minutes = (elapsed % 3600) / 60
                val seconds = elapsed % 60
                tvVpnDuration.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                durationHandler.postDelayed(this, 1000)
            } else {
                tvVpnDuration.text = "00:00:00"
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn)

        btnBackVpn = findViewById(R.id.btnBackVpn)
        btnToggleVpn = findViewById(R.id.btnToggleVpn)
        ivVpnShield = findViewById(R.id.ivVpnShield)
        tvVpnStatus = findViewById(R.id.tvVpnStatus)
        tvVpnStatusSub = findViewById(R.id.tvVpnStatusSub)
        tvVpnSelectedLocation = findViewById(R.id.tvVpnSelectedLocation)
        tvVpnIp = findViewById(R.id.tvVpnIp)
        tvVpnDuration = findViewById(R.id.tvVpnDuration)
        rvVpnRegions = findViewById(R.id.rvVpnRegions)
        pbVpnLoading = findViewById(R.id.pbVpnLoading)

        rvVpnRegions.layoutManager = GridLayoutManager(this, 2)

        btnBackVpn.setOnClickListener { finish() }

        btnToggleVpn.setOnClickListener {
            alternarVpn()
        }

        // Atalho de administração (manter premido OK no botão de ligar ou clicar no escudo em erro)
        btnToggleVpn.setOnLongClickListener {
            mostrarDialogoCredenciaisOculto()
            true
        }

        ivVpnShield.setOnClickListener {
            if (PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.ERROR) {
                mostrarDialogoCredenciaisOculto()
            }
        }

        // Foco dos botões no comando TV
        configurarFoco(btnBackVpn, 1.08f)
        configurarFoco(btnToggleVpn, 1.04f)

        // Carrega servidores
        carregarRegioes()

        // Regista listener de estado
        PiaVpnManager.addListener(vpnStateListener)

        // Foco inicial no botão de ligar/desligar
        btnToggleVpn.post { btnToggleVpn.requestFocus() }
    }

    override fun onDestroy() {
        super.onDestroy()
        PiaVpnManager.removeListener(vpnStateListener)
        durationHandler.removeCallbacks(durationRunnable)
    }

    private fun configurarFoco(view: View, scale: Float) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                v.elevation = dp(8).toFloat()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                v.elevation = 0f
            }
        }
    }

    private val vpnStateListener: (PiaVpnManager.VpnStatus, String?) -> Unit = { status, msg ->
        runOnUiThread {
            atualizarInterface(status, msg)
            regionAdapter?.notifyDataSetChanged()
        }
    }

    private fun carregarRegioes() {
        pbVpnLoading.visibility = View.VISIBLE
        PiaVpnManager.getRegions { list ->
            pbVpnLoading.visibility = View.GONE
            regionsList = list

            val savedId = PiaVpnConfig.getSelectedRegionId(this)
            selectedRegion = list.find { it.id == savedId } ?: list.firstOrNull()

            atualizarTextoRegiaoSelecionada()

            regionAdapter = RegionAdapter(list) { reg ->
                val currentStatus = PiaVpnManager.getStatus()
                val connectedReg = PiaVpnManager.getConnectedRegion()
                val isCurrentlyConnectedToThis = (currentStatus == PiaVpnManager.VpnStatus.CONNECTED && connectedReg?.id == reg.id)

                if (isCurrentlyConnectedToThis) {
                    // Clicou no país que já está conectado -> DESCONECTAR
                    Toast.makeText(this, "A desligar ligação de ${reg.name}…", Toast.LENGTH_SHORT).show()
                    PiaVpnManager.disconnect(this)
                } else if (currentStatus == PiaVpnManager.VpnStatus.CONNECTED) {
                    // Clicou noutro país enquanto está conectado -> MUDAR DE PAÍS
                    Toast.makeText(this, "A mudar ligação para ${reg.name}…", Toast.LENGTH_SHORT).show()
                    PiaVpnManager.disconnect(this) {
                        iniciarLigacao(reg)
                    }
                } else if (currentStatus == PiaVpnManager.VpnStatus.CONNECTING) {
                    // Clicou enquanto está a conectar -> CANCELAR
                    Toast.makeText(this, "Ligação cancelada", Toast.LENGTH_SHORT).show()
                    PiaVpnManager.disconnect(this)
                } else {
                    // Está desconectado ou em erro -> CONECTAR DIRETAMENTE AO PAÍS CLICADO
                    Toast.makeText(this, "A ligar a ${reg.name}…", Toast.LENGTH_SHORT).show()
                    iniciarLigacao(reg)
                }
            }
            rvVpnRegions.adapter = regionAdapter
        }
    }

    private fun atualizarTextoRegiaoSelecionada() {
        val reg = selectedRegion
        if (reg != null) {
            tvVpnSelectedLocation.text = "${reg.flagEmoji} ${reg.name}"
        }
    }

    private fun alternarVpn() {
        when (PiaVpnManager.getStatus()) {
            PiaVpnManager.VpnStatus.CONNECTED -> {
                Toast.makeText(this, "A desligar VPN…", Toast.LENGTH_SHORT).show()
                PiaVpnManager.disconnect(this)
            }
            PiaVpnManager.VpnStatus.CONNECTING -> {
                Toast.makeText(this, "Ligação cancelada", Toast.LENGTH_SHORT).show()
                PiaVpnManager.disconnect(this)
            }
            else -> {
                iniciarLigacao()
            }
        }
    }

    private fun iniciarLigacao(targetRegion: PiaRegion? = null) {
        val region = targetRegion ?: selectedRegion ?: regionsList.firstOrNull()
        if (region == null) {
            Toast.makeText(this, "Nenhum servidor selecionado", Toast.LENGTH_SHORT).show()
            return
        }

        selectedRegion = region
        PiaVpnConfig.setSelectedRegionId(this, region.id)
        atualizarTextoRegiaoSelecionada()
        regionAdapter?.notifyDataSetChanged()

        // Verifica permissão do sistema Android VpnService
        val prepareIntent = PiaVpnManager.checkPermission(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQUEST_VPN_CODE)
            return
        }

        PiaVpnManager.connect(this, region)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val region = selectedRegion ?: regionsList.firstOrNull()
                if (region != null) {
                    PiaVpnManager.connect(this, region)
                }
            } else {
                Toast.makeText(this, "Permissão de VPN necessária para proteger a ligação", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun atualizarInterface(status: PiaVpnManager.VpnStatus, message: String?) {
        when (status) {
            PiaVpnManager.VpnStatus.CONNECTED -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFF48BB78.toInt())
                tvVpnStatus.text = "LIGADO • PROTEGIDO"
                tvVpnStatus.setTextColor(0xFF48BB78.toInt())
                tvVpnStatusSub.text = "A tua ligação está encriptada e protegida"
                tvVpnIp.text = PiaVpnManager.getProtectedIp() ?: "Ativo"
                tvVpnIp.setTextColor(0xFF42D6E8.toInt())

                btnToggleVpn.text = "DESLIGAR VPN"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFFE53E3E.toInt())
                    cornerRadius = dp(10).toFloat()
                }

                val connectedReg = PiaVpnManager.getConnectedRegion()
                if (connectedReg != null) {
                    tvVpnSelectedLocation.text = "${connectedReg.flagEmoji} ${connectedReg.name}"
                }

                durationHandler.removeCallbacks(durationRunnable)
                durationHandler.post(durationRunnable)
            }
            PiaVpnManager.VpnStatus.CONNECTING -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFFECC94B.toInt())
                tvVpnStatus.text = "A CONECTAR…"
                tvVpnStatus.setTextColor(0xFFECC94B.toInt())
                tvVpnStatusSub.text = message ?: "A negociar túnel WireGuard…"
                tvVpnIp.text = "A atribuir…"

                btnToggleVpn.text = "CANCELAR"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFF4A5568.toInt())
                    cornerRadius = dp(10).toFloat()
                }
            }
            PiaVpnManager.VpnStatus.DISCONNECTING -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFF718096.toInt())
                tvVpnStatus.text = "A DESLIGAR…"
                tvVpnStatus.setTextColor(0xFFCBD5E0.toInt())
                btnToggleVpn.text = "A AGUARDAR…"
            }
            PiaVpnManager.VpnStatus.DISCONNECTED -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFF718096.toInt())
                tvVpnStatus.text = "DESCONECTADO"
                tvVpnStatus.setTextColor(0xFFE2E8F0.toInt())
                tvVpnStatusSub.text = "A tua ligação não está protegida"
                tvVpnIp.text = "—"
                tvVpnDuration.text = "00:00:00"

                btnToggleVpn.text = "LIGAR VPN"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFF2B6CB0.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                durationHandler.removeCallbacks(durationRunnable)
            }
            PiaVpnManager.VpnStatus.ERROR -> {
                ivVpnShield.imageTintList = ColorStateList.valueOf(0xFFE53E3E.toInt())
                tvVpnStatus.text = "ERRO DE AUTENTICAÇÃO"
                tvVpnStatus.setTextColor(0xFFE53E3E.toInt())
                val detail = message ?: "Servidor rejeitou as credenciais (utilizador/password incorretos ou conta inativa)."
                tvVpnStatusSub.text = detail
                tvVpnIp.text = "Acesso Negado"

                btnToggleVpn.text = "TENTAR NOVAMENTE"
                btnToggleVpn.background = GradientDrawable().apply {
                    setColor(0xFF2B6CB0.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                durationHandler.removeCallbacks(durationRunnable)

                mostrarDialogoErro(detail)
            }
        }
    }

    private var isErrorDialogShowing = false

    private fun mostrarDialogoErro(detail: String) {
        if (isFinishing || isDestroyed || isErrorDialogShowing) return
        isErrorDialogShowing = true

        val username = PiaVpnConfig.getUsername(this)
        AlertDialog.Builder(this)
            .setTitle("Falha na Ligação VPN")
            .setMessage(
                "A conta configurada ($username) foi rejeitada pelo servidor (Acesso Negado).\n\n" +
                "Por favor confirma os dados de acesso ou se a subscrição se encontra ativa."
            )
            .setPositiveButton("Compreendi") { dialog, _ ->
                dialog.dismiss()
                isErrorDialogShowing = false
                btnToggleVpn.requestFocus()
            }
            .setNeutralButton("Alterar Conta") { dialog, _ ->
                dialog.dismiss()
                isErrorDialogShowing = false
                mostrarDialogoCredenciaisOculto()
            }
            .setOnDismissListener {
                isErrorDialogShowing = false
            }
            .show()
    }

    private fun mostrarDialogoCredenciaisOculto() {
        if (isFinishing || isDestroyed) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }

        val etUser = EditText(this).apply {
            hint = "Utilizador"
            setText(PiaVpnConfig.getUsername(this@VpnActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF718096.toInt())
            isSingleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF1A2639.toInt())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0xFF2D3E56.toInt())
            }
        }

        val etPass = EditText(this).apply {
            hint = "Palavra-passe"
            setText(PiaVpnConfig.getPassword(this@VpnActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF718096.toInt())
            isSingleLine = true
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                setColor(0xFF1A2639.toInt())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0xFF2D3E56.toInt())
            }
        }

        container.addView(etUser)
        container.addView(etPass)

        AlertDialog.Builder(this)
            .setTitle("Configurar Credenciais VPN")
            .setView(container)
            .setPositiveButton("Guardar e Ligar") { dialog, _ ->
                val u = etUser.text.toString().trim()
                val p = etPass.text.toString().trim()
                if (u.isNotBlank() && p.isNotBlank()) {
                    PiaVpnConfig.setUsername(this, u)
                    PiaVpnConfig.setPassword(this, p)
                    PiaVpnManager.resetCachedToken()
                    Toast.makeText(this, "Credenciais guardadas. A tentar ligar…", Toast.LENGTH_SHORT).show()
                    iniciarLigacao()
                } else {
                    Toast.makeText(this, "Preenche utilizador e password", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // --- ADAPTADOR DE LOCALIZAÇÕES ---
    private inner class RegionAdapter(
        private val list: List<PiaRegion>,
        private val onSelect: (PiaRegion) -> Unit
    ) : RecyclerView.Adapter<RegionAdapter.Holder>() {

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val card: CardView = v as CardView
            val flag: TextView = v.findViewById(R.id.tvRegionFlag)
            val name: TextView = v.findViewById(R.id.tvRegionName)
            val badge: TextView = v.findViewById(R.id.tvRegionBadge)
            val check: ImageView = v.findViewById(R.id.ivRegionSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vpn_region, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val reg = list[position]
            val isSelected = selectedRegion?.id == reg.id
            val isConnectedThis = PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTED && PiaVpnManager.getConnectedRegion()?.id == reg.id
            val isConnectingThis = PiaVpnManager.getStatus() == PiaVpnManager.VpnStatus.CONNECTING && PiaVpnManager.getConnectedRegion()?.id == reg.id

            holder.flag.text = reg.flagEmoji
            holder.name.text = reg.name

            // Badge de estado do país
            when {
                isConnectedThis -> {
                    holder.badge.visibility = View.VISIBLE
                    holder.badge.text = "● LIGADO"
                    holder.badge.setTextColor(0xFF48BB78.toInt())
                }
                isConnectingThis -> {
                    holder.badge.visibility = View.VISIBLE
                    holder.badge.text = "● A LIGAR…"
                    holder.badge.setTextColor(0xFFECC94B.toInt())
                }
                reg.isStreamingOptimized -> {
                    holder.badge.visibility = View.VISIBLE
                    holder.badge.text = "⚡ Otimizado para Streaming"
                    holder.badge.setTextColor(0xFF42D6E8.toInt())
                }
                else -> {
                    holder.badge.visibility = View.GONE
                }
            }

            // Ícone de seleção ou ligado
            when {
                isConnectedThis -> {
                    holder.check.visibility = View.VISIBLE
                    holder.check.setImageResource(android.R.drawable.checkbox_on_background)
                    holder.check.imageTintList = ColorStateList.valueOf(0xFF48BB78.toInt())
                }
                isSelected -> {
                    holder.check.visibility = View.VISIBLE
                    holder.check.setImageResource(android.R.drawable.checkbox_on_background)
                    holder.check.imageTintList = ColorStateList.valueOf(0xFF42D6E8.toInt())
                }
                else -> {
                    holder.check.visibility = View.INVISIBLE
                }
            }

            val updateCardStyle: (Boolean) -> Unit = { hasFocus ->
                if (hasFocus) {
                    holder.card.setCardBackgroundColor(0xFF2A4365.toInt())
                    holder.card.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                    holder.card.elevation = dp(8).toFloat()
                } else if (isConnectedThis) {
                    holder.card.setCardBackgroundColor(0xFF163828.toInt())
                    holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    holder.card.elevation = dp(4).toFloat()
                } else if (isSelected) {
                    holder.card.setCardBackgroundColor(0xFF1E3250.toInt())
                    holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    holder.card.elevation = dp(4).toFloat()
                } else {
                    holder.card.setCardBackgroundColor(0xFF101A2B.toInt())
                    holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    holder.card.elevation = dp(2).toFloat()
                }
            }

            updateCardStyle(holder.itemView.hasFocus())
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                updateCardStyle(hasFocus)
            }

            holder.itemView.setOnClickListener {
                onSelect(reg)
            }
        }
    }
}
