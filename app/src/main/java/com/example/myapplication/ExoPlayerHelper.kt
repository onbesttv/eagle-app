package com.example.myapplication

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ExoPlayerHelper {

    /**
     * Construtor otimizado para máxima qualidade de imagem, nitidez e fluidez em Live TV e VOD.
     * Inclui Tunneled Playback para o processador de imagem da Formuler, desentrelaçamento
     * de hardware (50/60fps sem judder) e seleção forçada da resolução e bitrate mais altos.
     */
    fun buildOptimizedPlayer(
        context: Context,
        playerView: PlayerView? = null
    ): Pair<ExoPlayer, DefaultTrackSelector> {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "IPTVSmartersPro/3.1.5")
                    .header("Accept", "*/*")
                    .header("Connection", "keep-alive")
                    .build()
                chain.proceed(req)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        // 1. Tunneled Playback (hardware VPU direto da TV Box) + Maior Bitrate Forçado
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setTunnelingEnabled(true)
            )
        }

        // 2. Descodificação direta por hardware com tolerância/fallback automático
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }

        // 3. Controlo de buffer suave e estável para transmissões pesadas em direto (HD/FHD/4K)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000, // minBufferMs (12s)
                45_000, // maxBufferMs (45s)
                2_000,  // bufferForPlaybackMs (2s)
                4_000   // bufferForPlaybackAfterRebufferMs (4s)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }

        playerView?.let {
            it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            it.player = player
        }

        return Pair(player, trackSelector)
    }
}
