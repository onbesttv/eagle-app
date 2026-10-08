package com.example.myapplication

import android.content.Context
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient

object ExoPlayerHelper {

    /**
     * Construtor original do leitor ExoPlayer (modo limpo/padrão de origem).
     * Sem forçar bitrates, buffers customizados ou modos de redimensionamento manuais.
     */
    fun buildOptimizedPlayer(
        context: Context,
        playerView: PlayerView? = null
    ): Pair<ExoPlayer, DefaultTrackSelector?> {
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

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerView?.player = player

        return Pair(player, null)
    }
}
