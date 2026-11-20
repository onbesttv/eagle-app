package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de Controlo Parental (PIN) para proteção de conteúdos e categorias adultas (+18 / XXX).
 * Ativo exclusivamente nas variantes 'master' (Eagle) e 'eagle_tv' (Eagle TV).
 * PIN padrão de fábrica: 0000.
 * Suporta redefinição remota a partir do painel via ||PARENTAL_RESET:target=pin||.
 */
object ParentalControlManager {

    private const val TAG = "ParentalControl"
    private const val PREFS_NAME = "parental_control_prefs"
    private const val KEY_PIN = "parental_pin"
    const val DEFAULT_PIN = "0000"

    // Guarda categorias desbloqueadas durante a sessão atual da aplicação
    private val unlockedCategoryIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * Verifica se o Controlo Parental está ativo. Apenas para 'master' e 'eagle_tv'.
     */
    fun isParentalControlEnabled(context: Context): Boolean {
        val rid = RemotePanelManager.getResellerId(context).trim().lowercase()
        return rid == "master" || rid == "eagle_tv"
    }

    /**
     * Obtém o PIN atual configurado (ou 0000 por omissão).
     */
    fun getPin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN, DEFAULT_PIN)?.trim()?.ifEmpty { DEFAULT_PIN } ?: DEFAULT_PIN
    }

    /**
     * Guarda um novo PIN de 4 dígitos.
     */
    fun setPin(context: Context, newPin: String) {
        val cleanPin = newPin.trim()
        if (cleanPin.matches(Regex("""^\d{4}$"""))) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PIN, cleanPin)
                .apply()
            Log.d(TAG, "Novo PIN parental configurado com sucesso.")
        }
    }

    /**
     * Verifica se uma categoria corresponde a conteúdo para adultos (+18, XXX, Porn, etc.).
     */
    fun isAdultCategory(categoryName: String?): Boolean {
        if (categoryName.isNullOrBlank()) return false
        val clean = Normalizer.normalize(categoryName.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .trim()

        if (clean.contains("+18") || clean.contains("18+") || clean.contains("xxx")) {
            return true
        }

        val pattern = Regex(
            """(?:\b|_)(adulto|adultos|adult|erotico|erotica|eroticos|eroticas|porno|porn|playboy|venus|sextreme|redlight|hustler|brazzers|penthouse|for adult|para adultos)(?:\b|_)"""
        )
        return pattern.containsMatchIn(clean)
    }

    /**
     * Verifica se um conteúdo específico (canal, filme ou série) é adulto.
     */
    fun isAdultContent(categoryName: String?, title: String? = null): Boolean {
        if (isAdultCategory(categoryName)) return true
        if (!title.isNullOrBlank()) {
            val clean = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .trim()
            if (clean.contains("+18") || clean.contains("18+") || clean.contains("xxx")) {
                return true
            }
            val pattern = Regex(
                """(?:\b|_)(adulto|adultos|erotico|erotica|porno|porn|playboy|venus|sextreme|redlight|hustler|brazzers|penthouse)(?:\b|_)"""
            )
            return pattern.containsMatchIn(clean)
        }
        return false
    }

    fun isCategoryUnlocked(categoryId: String): Boolean {
        return unlockedCategoryIds.contains(categoryId)
    }

    fun unlockCategory(categoryId: String) {
        unlockedCategoryIds.add(categoryId)
    }

    fun clearUnlocked() {
        unlockedCategoryIds.clear()
    }

    /**
     * Processa a redefinição remota enviada pelo painel:
     * Tag: ||PARENTAL_RESET:target=PIN||
     * target pode ser 'all', ID do dispositivo, username ou lista separada por vírgulas.
     */
    fun applyRemoteReset(context: Context, rawAnnouncement: String?): Boolean {
        if (rawAnnouncement.isNullOrBlank() || !isParentalControlEnabled(context)) return false
        if (!rawAnnouncement.contains("||PARENTAL_RESET:")) return false

        val myDevId = RemotePanelManager.getDeviceId(context).lowercase()
        val myMac = RemotePanelManager.getDeviceMac(context).replace(":", "").replace("-", "").lowercase()
        val loginPrefs = context.getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
        val myUser = loginPrefs.getString("SAVED_USER", "")?.trim()?.lowercase().orEmpty()

        val regex = Regex("""\|\|PARENTAL_RESET:(.*?)=(.*?)\|\|""", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(rawAnnouncement)
        var updated = false

        for (m in matches) {
            val targetRaw = m.groupValues[1].trim().lowercase()
            val newPin = m.groupValues[2].trim()

            if (!newPin.matches(Regex("""^\d{4}$"""))) continue

            val targets = targetRaw.split(",", "|", ";").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val matchesMe = targets.isEmpty() || targets.any { t ->
                t == "all" ||
                t == "todos" ||
                t == "master" ||
                t == "eagle_tv" ||
                (myDevId.isNotEmpty() && (t == myDevId || t.contains(myDevId) || myDevId.contains(t))) ||
                (myMac.isNotEmpty() && (t == myMac || t.contains(myMac) || myMac.contains(t))) ||
                (myUser.isNotEmpty() && (t == myUser || t.contains(myUser) || myUser.contains(t)))
            }

            if (matchesMe) {
                setPin(context, newPin)
                updated = true
                Log.i(TAG, "PIN Parental atualizado remotamente para '$newPin' (target: $targetRaw)")
            }
        }
        return updated
    }

    /**
     * Apresenta o diálogo moderno de introdução de PIN com teclado no ecrã (suporta D-Pad da box)
     * e atalhos para teclas numéricas do comando de TV (0 a 9) e toque em ecrãs de telemóvel.
     */
    fun promptPin(
        activity: Activity,
        categoryName: String,
        onCorrect: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_parental_pin, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(false)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvPinDialogSubtitle)
        val tvError = view.findViewById<TextView>(R.id.tvPinError)
        val dot1 = view.findViewById<View>(R.id.pinDot1)
        val dot2 = view.findViewById<View>(R.id.pinDot2)
        val dot3 = view.findViewById<View>(R.id.pinDot3)
        val dot4 = view.findViewById<View>(R.id.pinDot4)
        val dots = listOf(dot1, dot2, dot3, dot4)

        val btnCancel = view.findViewById<Button>(R.id.btnPinCancel)
        val btnDel = view.findViewById<Button>(R.id.btnPinDel)
        val btnOk = view.findViewById<Button>(R.id.btnPinOk)

        val numButtons = mapOf(
            R.id.btnPin0 to "0",
            R.id.btnPin1 to "1",
            R.id.btnPin2 to "2",
            R.id.btnPin3 to "3",
            R.id.btnPin4 to "4",
            R.id.btnPin5 to "5",
            R.id.btnPin6 to "6",
            R.id.btnPin7 to "7",
            R.id.btnPin8 to "8",
            R.id.btnPin9 to "9"
        )

        val displayName = if (categoryName.isNotBlank()) categoryName else "Conteúdo Adulto"
        tvSubtitle.text = "Introduz o PIN para aceder a:\n$displayName"

        val currentInput = StringBuilder()

        fun updateDots() {
            val len = currentInput.length
            dots.forEachIndexed { idx, dot ->
                if (idx < len) {
                    dot.setBackgroundResource(R.drawable.bg_pin_dot_filled)
                } else {
                    dot.setBackgroundResource(R.drawable.bg_pin_dot_empty)
                }
            }
        }

        fun checkPin() {
            val correctPin = getPin(activity)
            if (currentInput.toString() == correctPin) {
                dialog.dismiss()
                onCorrect()
            } else {
                tvError.visibility = View.VISIBLE
                tvError.text = "Código PIN incorreto!"
                currentInput.clear()
                updateDots()

                // Ocultar mensagem de erro após 3 segundos
                Handler(Looper.getMainLooper()).postDelayed({
                    if (dialog.isShowing) {
                        tvError.visibility = View.GONE
                    }
                }, 3000L)
            }
        }

        fun appendDigit(d: String) {
            if (currentInput.length < 4) {
                currentInput.append(d)
                tvError.visibility = View.GONE
                updateDots()
                if (currentInput.length == 4) {
                    // Validação automática após o 4º dígito
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (dialog.isShowing && currentInput.length == 4) {
                            checkPin()
                        }
                    }, 120L)
                }
            }
        }

        fun deleteDigit() {
            if (currentInput.isNotEmpty()) {
                currentInput.deleteCharAt(currentInput.length - 1)
                tvError.visibility = View.GONE
                updateDots()
            }
        }

        // Configuração dos botões no ecrã
        numButtons.forEach { (btnId, digit) ->
            view.findViewById<Button>(btnId)?.setOnClickListener {
                appendDigit(digit)
            }
        }

        btnDel?.setOnClickListener { deleteDigit() }
        btnOk?.setOnClickListener {
            if (currentInput.length == 4) {
                checkPin()
            } else {
                tvError.visibility = View.VISIBLE
                tvError.text = "Introduz os 4 dígitos."
            }
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
            onCancel?.invoke()
        }

        dialog.setOnCancelListener {
            onCancel?.invoke()
        }

        // Intercetar comandos de comando de TV (teclas físicas 0-9, backspace, enter)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit("0"); true }
                    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit("1"); true }
                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit("2"); true }
                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit("3"); true }
                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit("4"); true }
                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit("5"); true }
                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit("6"); true }
                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit("7"); true }
                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit("8"); true }
                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit("9"); true }
                    KeyEvent.KEYCODE_DEL -> { deleteDigit(); true }
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (currentInput.length == 4) {
                            checkPin()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        dialog.dismiss()
                        onCancel?.invoke()
                        true
                    }
                    else -> false
                }
            } else false
        }

        try {
            dialog.show()
            // Focar o primeiro botão para facilitar navegação via D-pad em Android TV
            view.findViewById<Button>(R.id.btnPin1)?.post {
                view.findViewById<Button>(R.id.btnPin1)?.requestFocus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao apresentar diálogo de PIN: ${e.message}")
        }
    }
}
