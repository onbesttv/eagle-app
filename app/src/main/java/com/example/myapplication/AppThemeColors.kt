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
        return RemotePanelManager.getResellerId(context) != "master"
    }

    /**
     * Converte qualquer cor azul/ciano original da Eagle para a paleta Preto Carbono & Dourado
     * quando a app pertence a um revendedor (ex: tv_fabio), mantendo 100% o azul original na app Master.
     */
    fun mapColor(context: Context, originalColor: Int): Int {
        if (!isDarkGoldTheme(context)) return originalColor
        return mapColorInternal(originalColor)
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

    fun applyToActivity(activity: Activity) {
        if (!isDarkGoldTheme(activity)) return
        val root = activity.window?.decorView ?: return
        applyToViewHierarchy(root)
    }

    fun applyToDialog(dialog: Dialog) {
        if (!isDarkGoldTheme(dialog.context)) return
        val root = dialog.window?.decorView ?: return
        applyToViewHierarchy(root)
    }

    fun applyToViewHierarchy(view: View?) {
        if (view == null) return

        // 1. Background ColorDrawable
        when (val bg = view.background) {
            is ColorDrawable -> {
                val mapped = mapColorInternal(bg.color)
                if (mapped != bg.color) {
                    view.setBackgroundColor(mapped)
                }
            }
        }

        // 2. CardView background
        if (view is CardView) {
            val defaultColor = view.cardBackgroundColor.defaultColor
            val mapped = mapColorInternal(defaultColor)
            if (mapped != defaultColor) {
                view.setCardBackgroundColor(mapped)
            }
        }

        // 3. TextView text color
        if (view is TextView) {
            val currentText = view.currentTextColor
            val mapped = mapColorInternal(currentText)
            if (mapped != currentText) {
                view.setTextColor(mapped)
            }
        }

        // 4. ImageView tint
        if (view is ImageView) {
            val tint = view.imageTintList?.defaultColor
            if (tint != null) {
                val mapped = mapColorInternal(tint)
                if (mapped != tint) {
                    view.imageTintList = ColorStateList.valueOf(mapped)
                }
            }
        }

        // 5. ProgressBar tint
        if (view is ProgressBar) {
            view.progressTintList?.defaultColor?.let { c ->
                val mapped = mapColorInternal(c)
                if (mapped != c) view.progressTintList = ColorStateList.valueOf(mapped)
            }
            view.indeterminateTintList?.defaultColor?.let { c ->
                val mapped = mapColorInternal(c)
                if (mapped != c) view.indeterminateTintList = ColorStateList.valueOf(mapped)
            }
            view.progressBackgroundTintList?.defaultColor?.let { c ->
                val mapped = mapColorInternal(c)
                if (mapped != c) view.progressBackgroundTintList = ColorStateList.valueOf(mapped)
            }
        }

        // 6. Recursão nos filhos e listener para RecyclerViews / listas dinâmicas
        if (view is ViewGroup) {
            view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    applyToViewHierarchy(child)
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until view.childCount) {
                applyToViewHierarchy(view.getChildAt(i))
            }
        }
    }
}
