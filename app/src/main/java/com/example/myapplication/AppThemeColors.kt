package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView

object AppThemeColors {

    fun isDarkGoldTheme(context: Context): Boolean {
        return RemotePanelManager.getResellerId(context).equals("tv_fabio", ignoreCase = true)
    }

    fun isPereiraTheme(context: Context): Boolean {
        return RemotePanelManager.getResellerId(context).equals("pereira_tv", ignoreCase = true)
    }

    fun isGodinhoTheme(context: Context): Boolean {
        return RemotePanelManager.getResellerId(context).equals("godinho_tv", ignoreCase = true)
    }

    /**
     * Converte qualquer cor azul/ciano original da Eagle para a paleta do revendedor
     * (Preto Carbono & Dourado na tv_fabio; Ardósia Obsidiana & Azul Royal na pereira_tv;
     * Preto Obsidiana & Vermelho Carmesim na godinho_tv), mantendo 100% o azul original na app Master.
     */
    fun mapColor(context: Context, originalColor: Int): Int {
        if (isDarkGoldTheme(context)) return mapColorInternal(originalColor)
        if (isPereiraTheme(context)) return mapPereiraColorInternal(originalColor)
        if (isGodinhoTheme(context)) return mapGodinhoColorInternal(originalColor)
        return originalColor
    }

    fun mapGodinhoColorInternal(color: Int): Int {
        val alpha = color ushr 24
        val rgb = color and 0x00FFFFFF
        val mappedRgb = when (rgb) {
            // Fundos principais -> Preto-Obsidiana Rubi profundo
            0x061328, 0x071429, 0x071A2E, 0x080D16, 0x08090E, 0x091427, 0x020A17, 0x020914 -> 0x060405
            // Colunas laterais e barras superiores -> Carbono-Rubi escuro
            0x0B1322, 0x0B1424, 0x0C1422, 0x0C1628, 0x0D2038, 0x0E1522, 0x101827, 0x10203A, 0x101C30 -> 0x0D0608
            // Cartões e painéis -> Carvão Carmesim escuro
            0x101A2B, 0x111C2D, 0x121D2D, 0x152238, 0x162232, 0x172338, 0x19283F, 0x1A2639, 0x1E2836, 0x1E293B, 0x191B24 -> 0x15090C
            // Linhas divisórias, estados selecionados e bordas secundárias -> Bordô escuro
            0x1E3250, 0x1F2F44, 0x23324A, 0x263955, 0x27354A, 0x2B3D59, 0x2D3E56, 0x334155, 0x344967, 0x354B68, 0x2B4666 -> 0x2D1117
            // Fundos em Foco / Selecionados -> Vermelho Vinho / Rubi Intenso
            0x2A4365, 0x283E58, 0x365780 -> 0x4A1219
            // Botões principais -> Vermelho Carmesim GodinhoTV
            0x2B6CB0, 0x3182CE, 0x2563EB, 0x1D4ED8, 0x397DFF -> 0xDC2626
            // Destaques Ciano / Azul claro -> Vermelho Neon / Rubi Brilhante GodinhoTV
            0x42D6E8, 0x00E5FF, 0x62D7FF, 0x65D7E8, 0x64CFFF, 0x65A5FF, 0x73BFFF, 0x9BA8FF, 0xB49BFF -> 0xFF3B47
            // Subtítulos azulados -> Prata Cinza suave (como as letras de GODINHO)
            0x9EBBD7, 0x9BAAC0, 0xB7C4D6, 0x91A8BB, 0x8BA5BF, 0x8192A3 -> 0xB8A9AD
            // Amarelo de categorias especiais -> Dourado da Coroa GodinhoTV
            0xF5C56B -> 0xFBBF24
            else -> return color
        }
        return (alpha shl 24) or mappedRgb
    }

    fun mapPereiraColorInternal(color: Int): Int {
        val alpha = color ushr 24
        val rgb = color and 0x00FFFFFF
        val mappedRgb = when (rgb) {
            // Fundos principais -> Preto-Ardósia Obsidiana profundo
            0x061328, 0x071429, 0x071A2E, 0x080D16, 0x08090E, 0x091427 -> 0x050811
            // Colunas laterais e barras superiores -> Índigo Escuro
            0x0B1322, 0x0B1424, 0x0C1422, 0x0C1628, 0x0D2038, 0x0E1522, 0x101827, 0x10203A -> 0x090E1C
            // Cartões e painéis -> Azul Grafite escuro
            0x101A2B, 0x111C2D, 0x121D2D, 0x152238, 0x162232, 0x172338, 0x19283F, 0x1A2639, 0x1E2836, 0x1E293B -> 0x0F172A
            // Linhas divisórias, estados selecionados e bordas secundárias -> Azul Cobalto suave
            0x1E3250, 0x1F2F44, 0x23324A, 0x263955, 0x27354A, 0x2B3D59, 0x2D3E56, 0x334155, 0x344967, 0x354B68 -> 0x172A4A
            // Fundos em Foco / Selecionados -> Azul Royal Vibrante
            0x2A4365, 0x283E58, 0x365780 -> 0x163B73
            // Botões principais -> Azul Elétrico PereiraTV
            0x2B6CB0, 0x3182CE, 0x2563EB, 0x1D4ED8, 0x397DFF -> 0x0284C7
            // Destaques Ciano -> Azul Celeste PereiraTV
            0x42D6E8, 0x00E5FF, 0x62D7FF, 0x65D7E8, 0x64CFFF, 0x65A5FF, 0x73BFFF, 0x9BA8FF, 0xB49BFF -> 0x38BDF8
            // Amarelo de categorias especiais -> Azul Gelo brilhante
            0xF5C56B -> 0x38BDF8
            else -> return color
        }
        return (alpha shl 24) or mappedRgb
    }

    fun mapColorInternal(color: Int): Int {
        val alpha = color ushr 24
        val rgb = color and 0x00FFFFFF
        val mappedRgb = when (rgb) {
            // Fundos principais azul-marinho -> Preto Carbono profundo
            0x061328, 0x071429, 0x071A2E, 0x080D16, 0x08090E, 0x091427 -> 0x050507
            // Colunas laterais e barras superiores -> Preto Grafite
            0x0B1322, 0x0B1424, 0x0C1422, 0x0C1628, 0x0D2038, 0x0E1522, 0x101827, 0x10203A -> 0x0B0C10
            // Cartões e painéis -> Cinza Carbono escuro
            0x101A2B, 0x111C2D, 0x121D2D, 0x152238, 0x162232, 0x172338, 0x19283F, 0x1A2639, 0x1E2836, 0x1E293B -> 0x121318
            // Linhas divisórias, estados selecionados e bordas secundárias
            0x1E3250, 0x1F2F44, 0x23324A, 0x263955, 0x27354A, 0x2B3D59, 0x2D3E56, 0x334155, 0x344967, 0x354B68 -> 0x22232B
            // Fundos em Foco / Selecionados (Azul médio -> Castanho Dourado escuro)
            0x2A4365, 0x283E58, 0x365780 -> 0x2B200B
            // Botões principais azuis -> Dourado Âmbar
            0x2B6CB0, 0x3182CE, 0x2563EB, 0x1D4ED8, 0x397DFF -> 0xD97706
            // Destaques Ciano / Azul claro -> Dourado Fpinto TV
            0x42D6E8, 0x00E5FF, 0x62D7FF, 0x65D7E8, 0x64CFFF, 0x65A5FF, 0x73BFFF, 0x9BA8FF, 0xB49BFF -> 0xFBBF24
            // Subtítulos azulados -> Cinza Prata suave
            0x9EBBD7, 0x9BAAC0, 0xB7C4D6, 0x91A8BB, 0x8BA5BF, 0x8192A3 -> 0xA1A1AA
            else -> return color
        }
        return (alpha shl 24) or mappedRgb
    }

    private fun resolveMapper(context: Context): ((Int) -> Int)? {
        return when {
            isDarkGoldTheme(context) -> ::mapColorInternal
            isPereiraTheme(context) -> ::mapPereiraColorInternal
            isGodinhoTheme(context) -> ::mapGodinhoColorInternal
            else -> null
        }
    }

    fun applyToActivity(activity: Activity) {
        val mapper = resolveMapper(activity) ?: return
        val root = activity.window?.decorView ?: return
        applyToViewHierarchy(root, mapper)
    }

    fun applyToDialog(dialog: Dialog) {
        val mapper = resolveMapper(dialog.context) ?: return
        val root = dialog.window?.decorView ?: return
        applyToViewHierarchy(root, mapper)
    }

    fun applyToViewHierarchy(view: View?, mapper: (Int) -> Int = ::mapColorInternal) {
        if (view == null) return

        // 1. Background ColorDrawable
        when (val bg = view.background) {
            is ColorDrawable -> {
                val mapped = mapper(bg.color)
                if (mapped != bg.color) {
                    view.setBackgroundColor(mapped)
                }
            }
        }

        // 2. CardView background
        if (view is CardView) {
            val defaultColor = view.cardBackgroundColor.defaultColor
            val mapped = mapper(defaultColor)
            if (mapped != defaultColor) {
                view.setCardBackgroundColor(mapped)
            }
        }

        // 3. TextView text color
        if (view is TextView) {
            val currentText = view.currentTextColor
            val mapped = mapper(currentText)
            if (mapped != currentText) {
                view.setTextColor(mapped)
            }
        }

        // 4. ImageView tint
        if (view is ImageView) {
            val tint = view.imageTintList?.defaultColor
            if (tint != null) {
                val mapped = mapper(tint)
                if (mapped != tint) {
                    view.imageTintList = ColorStateList.valueOf(mapped)
                }
            }
        }

        // 5. ProgressBar tint
        if (view is ProgressBar) {
            view.progressTintList?.defaultColor?.let { c ->
                val mapped = mapper(c)
                if (mapped != c) view.progressTintList = ColorStateList.valueOf(mapped)
            }
            view.indeterminateTintList?.defaultColor?.let { c ->
                val mapped = mapper(c)
                if (mapped != c) view.indeterminateTintList = ColorStateList.valueOf(mapped)
            }
            view.progressBackgroundTintList?.defaultColor?.let { c ->
                val mapped = mapper(c)
                if (mapped != c) view.progressBackgroundTintList = ColorStateList.valueOf(mapped)
            }
        }

        // 6. Recursão nos filhos e listener para RecyclerViews / listas dinâmicas
        if (view is ViewGroup) {
            view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    applyToViewHierarchy(child, mapper)
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until view.childCount) {
                applyToViewHierarchy(view.getChildAt(i), mapper)
            }
        }
    }
}
