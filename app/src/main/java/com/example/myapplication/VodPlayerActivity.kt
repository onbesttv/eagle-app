package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import okhttp3.OkHttpClient

class VodPlayerActivity : BaseActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private var streamUrl = ""
    private var streamTitle = ""
    private var streamId = 0
    private var isSeries = false

    private var episodeUrls: ArrayList<String> = arrayListOf()
    private var currentEpisodeIndex = 0
    private var jaConfigurouLegenda = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_vod_player)

            playerView = findViewById(R.id.vodPlayerView)

            streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
            streamTitle = intent.getStringExtra("STREAM_TITLE") ?: ""
            streamId = intent.getIntExtra("STREAM_ID", 0)
            isSeries = intent.getBooleanExtra("IS_SERIES", false)

            val extraEpisodes = intent.getStringArrayListExtra("EPISODE_URLS")
            if (extraEpisodes != null) {
                episodeUrls = extraEpisodes
                currentEpisodeIndex = intent.getIntExtra("CURRENT_EPISODE_INDEX", 0)
            }

            if (streamUrl.isEmpty()) {
                Toast.makeText(this, "URL do vídeo não encontrado", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            configurarPlayer()
            configurarBotoesControlo()
            verificarRetomar()

        } catch (e: Exception) {
            Log.e("VodPlayer", "Erro ao iniciar: ${e.message}", e)
            Toast.makeText(this, "Erro ao abrir vídeo: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun configurarBotoesControlo() {
        val tvTitle = playerView.findViewById<TextView>(R.id.tvVodTitle)
        tvTitle?.text = streamTitle

        // 1. Botão Sair
        val btnBack = playerView.findViewById<ImageButton>(R.id.btnBackVod)
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Botão Áudio
        val btnAudio = playerView.findViewById<ImageButton>(R.id.btnAudioTracks)
        btnAudio?.setOnClickListener {
            abrirDialogoAudio()
        }

        // 3. Botão Legendas
        val btnSubs = playerView.findViewById<ImageButton>(R.id.btnSubtitles)
        btnSubs?.setOnClickListener {
            abrirDialogoLegendas()
        }
    }

    private fun configurarPlayer() {
        val (p, _) = ExoPlayerHelper.buildOptimizedPlayer(this, playerView)
        player = p
        player?.volume = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getInt("player_volume", 100).coerceIn(0, 100) / 100f

        player?.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (jaConfigurouLegenda) return

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.lowercase() ?: ""
                            val label = format.label?.lowercase() ?: ""

                            val isPt = lang.contains("por") || lang.contains("pt") ||
                                    label.contains("portug") || label.contains("pt")

                            if (isPt || i == 0) {
                                jaConfigurouLegenda = true
                                val override = TrackSelectionOverride(group.mediaTrackGroup, i)
                                player?.trackSelectionParameters = player?.trackSelectionParameters?.buildUpon()
                                    ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    ?.setOverrideForType(override)
                                    ?.build() ?: return
                                return
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    reproduzirProximoEpisodio()
                }
            }
        })
    }

    // --- DIÁLOGO DE SELEÇÃO DE ÁUDIO ---
    private fun abrirDialogoAudio() {
        val tracks = player?.currentTracks ?: return
        val audioGroups = mutableListOf<Pair<Tracks.Group, Int>>()
        val nomesOpcoes = mutableListOf<String>()
        var selectedIndex = -1

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    audioGroups.add(Pair(group, i))

                    val lang = format.language?.uppercase() ?: "UND"
                    val label = format.label ?: "Áudio ${nomesOpcoes.size + 1}"
                    nomesOpcoes.add("$label ($lang)")

                    if (group.isTrackSelected(i)) {
                        selectedIndex = nomesOpcoes.size - 1
                    }
                }
            }
        }

        if (audioGroups.isEmpty()) {
            Toast.makeText(this, "Nenhuma faixa de áudio encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Selecionar Áudio")
            .setSingleChoiceItems(nomesOpcoes.toTypedArray(), selectedIndex) { dialog, which ->
                val (group, trackIndex) = audioGroups[which]
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)

                player?.trackSelectionParameters = player?.trackSelectionParameters?.buildUpon()
                    ?.setOverrideForType(override)
                    ?.build() ?: return@setSingleChoiceItems

                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- DIÁLOGO DE SELEÇÃO DE LEGENDAS ---
    private fun abrirDialogoLegendas() {
        val tracks = player?.currentTracks ?: return
        val textGroups = mutableListOf<Pair<Tracks.Group, Int>>()
        val nomesOpcoes = mutableListOf<String>()

        nomesOpcoes.add("Desativadas")
        var selectedIndex = 0

        val isDisabled = player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    textGroups.add(Pair(group, i))

                    val lang = format.language?.uppercase() ?: "UND"
                    val label = format.label ?: "Legenda ${nomesOpcoes.size}"
                    nomesOpcoes.add("$label ($lang)")

                    if (!isDisabled && group.isTrackSelected(i)) {
                        selectedIndex = nomesOpcoes.size - 1
                    }
                }
            }
        }

        if (textGroups.isEmpty()) {
            Toast.makeText(this, "Nenhuma legenda encontrada neste vídeo", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Selecionar Legendas")
            .setSingleChoiceItems(nomesOpcoes.toTypedArray(), selectedIndex) { dialog, which ->
                val builder = player?.trackSelectionParameters?.buildUpon() ?: return@setSingleChoiceItems

                if (which == 0) {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    val (group, trackIndex) = textGroups[which - 1]
                    val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    builder.setOverrideForType(override)
                }

                player?.trackSelectionParameters = builder.build()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun verificarRetomar() {
        val prefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)
        val savedPosition = prefs.getLong("pos_$streamId", 0L)

        if (savedPosition > 10000L) {
            val minutos = (savedPosition / 1000) / 60
            val segundos = (savedPosition / 1000) % 60
            val tempoFormatado = String.format("%02d:%02d", minutos, segundos)

            AlertDialog.Builder(this)
                .setTitle("Continuar a ver?")
                .setMessage("Ficou no minuto $tempoFormatado. Quer retomar de onde parou?")
                .setPositiveButton("Retomar") { _, _ ->
                    iniciarReproducao(savedPosition)
                }
                .setNegativeButton("Do Início") { _, _ ->
                    iniciarReproducao(0L)
                }
                .setCancelable(false)
                .show()
        } else {
            iniciarReproducao(0L)
        }
    }

    private fun iniciarReproducao(startPosition: Long) {
        jaConfigurouLegenda = false
        player?.apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            if (startPosition > 0) {
                seekTo(startPosition)
            }
            prepare()
            playWhenReady = true
        }
    }

    private fun reproduzirProximoEpisodio() {
        if (isSeries && episodeUrls.isNotEmpty() && currentEpisodeIndex + 1 < episodeUrls.size) {
            currentEpisodeIndex++
            streamUrl = episodeUrls[currentEpisodeIndex]
            Toast.makeText(this, "A reproduzir próximo episódio...", Toast.LENGTH_SHORT).show()

            playerView.findViewById<TextView>(R.id.tvVodTitle)?.text = "A reproduzir próximo episódio..."

            jaConfigurouLegenda = false
            player?.apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                playWhenReady = true
            }
        }
    }

    private fun salvarProgresso() {
        player?.let {
            val currentPos = it.currentPosition
            val duration = it.duration
            val prefs = getSharedPreferences("vod_resume_prefs", Context.MODE_PRIVATE)

            if (duration > 0 && currentPos >= duration - 30000L) {
                prefs.edit().remove("pos_$streamId").apply()
            } else if (currentPos > 5000L) {
                prefs.edit().putLong("pos_$streamId", currentPos).apply()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        salvarProgresso()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        salvarProgresso()
        playerView.player = null
        player?.release()
        player = null
    }
}
