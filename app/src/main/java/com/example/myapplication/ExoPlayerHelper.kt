package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import okhttp3.OkHttpClient

object ExoPlayerHelper {

    fun buildOptimizedPlayer(
        context: Context,
        playerView: PlayerView? = null
    ): Pair<ExoPlayer, DefaultTrackSelector> {
        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "IPTVSmartersPro/3.1.5")
                    .build()
                chain.proceed(req)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        // 1. Forçar a melhor faixa de vídeo disponível sem degradação
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }

        // 2. Aceleração de hardware com fallback automático de descodificadores
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        // 3. Gestão de buffer suave para Live TV e VOD de alto débito (HD / FHD / 4K)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs (15s)
                50_000, // maxBufferMs (50s)
                2_500,  // bufferForPlaybackMs (2.5s)
                5_000   // bufferForPlaybackAfterRebufferMs (5s)
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

        playerView?.let { pv ->
            pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            pv.player = player

            // 4. Renderização nítida de legendas (estilo Cinema / Netflix)
            // Resolve texto distorcido, borrado ou pixelizado em televisores
            pv.subtitleView?.apply {
                val captionStyle = CaptionStyleCompat(
                    Color.WHITE,                          // Letra: Branco puro de alto contraste
                    Color.TRANSPARENT,                    // Fundo: Sem caixa cinzenta a tapar
                    Color.TRANSPARENT,                    // Janela: Transparente
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE, // Contorno preto sólido anti-aliasing
                    Color.BLACK,                          // Cor do contorno
                    Typeface.DEFAULT_BOLD                 // Tipografia sólida e legível
                )
                setStyle(captionStyle)
                setApplyEmbeddedStyles(false)             // Sobrescreve fontes distorcidas da stream
                setApplyEmbeddedFontSizes(false)          // Força tamanho proporcional nítido na TV
                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.25f)
                setBottomPaddingFraction(0.08f)
            }
        }

        return Pair(player, trackSelector)
    }
}
