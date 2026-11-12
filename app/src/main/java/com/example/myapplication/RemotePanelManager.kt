package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

object RemotePanelManager {
    private const val SUPABASE_URL = "https://myreymyqsnikjdhnstjn.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_Jsb0b0Kcf5-bv6GCUFu3KA_wO1G9HjM"

    /**
     * ID do Revendedor desta App (Modelo 2 - White-Label / Rebrand).
     * - "master" = A tua App Oficial principal.
     * - Quando venderes a app a um revendedor, basta mudar para o ID que criaste para ele no Painel (ex: "revenda_carlos").
     */
    const val RESELLER_ID = "master"

    fun getResellerId(context: Context): String {
        return try {
            context.getString(R.string.reseller_id).trim().lowercase().ifEmpty { RESELLER_ID }
        } catch (_: Exception) {
            RESELLER_ID
        }
    }

    private const val PREFS = "remote_panel_prefs"
    private const val KEY_DEVICE_ID = "DEVICE_ID"
    private const val KEY_IS_BLOCKED = "IS_BLOCKED"
    private const val KEY_BLOCK_REASON = "BLOCK_REASON"
    private const val KEY_MAINTENANCE = "MAINTENANCE_MODE"
    private const val KEY_ANNOUNCEMENT = "ANNOUNCEMENT"
    private const val KEY_LAST_SHOWN_ANNOUNCEMENT = "LAST_SHOWN_ANNOUNCEMENT"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeBlockDialog: AlertDialog? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) return cached

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        val finalId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString().replace("-", "").take(16)
        }
        prefs.edit().putString(KEY_DEVICE_ID, finalId).apply()
        return finalId
    }

    fun getDeviceMac(context: Context): String {
        val rawId = getDeviceId(context).replace("-", "").lowercase()
        val clean = if (rawId.length >= 12) rawId.take(12) else rawId.padEnd(12, '0')
        return clean.chunked(2).joinToString(":").uppercase()
    }

    fun getDeviceKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var key = prefs.getString("DEVICE_KEY", null)
        if (key.isNullOrBlank()) {
            val randomNum = (100000..999999).random()
            key = randomNum.toString()
            prefs.edit().putString("DEVICE_KEY", key).apply()
        }
        return key
    }

    fun getRawAnnouncement(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("RAW_ANNOUNCEMENT", "").orEmpty()
    }

    fun isDeviceActivatedInPanel(context: Context): Boolean {
        val raw = getRawAnnouncement(context).lowercase()
        val myMac = getDeviceMac(context).replace(":", "").replace("-", "").lowercase()
        val myId = getDeviceId(context).replace(":", "").replace("-", "").lowercase()
        val actPart = raw.substringAfter("||activations:", "").substringBefore("||")
        return actPart.contains(myMac) || actPart.contains(myId)
    }

    fun applyActivationIfPresent(context: Context, rawAnnouncement: String): Boolean {
        val actPart = rawAnnouncement.substringAfter("||ACTIVATIONS:", "").substringBefore("||")
        if (actPart.isBlank()) return false
        val myId = getDeviceId(context).lowercase()
        val myMac = getDeviceMac(context).replace(":", "").lowercase()
        val entries = actPart.split("|")
        for (entry in entries) {
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) {
                val dev = parts[0].trim().replace(":", "").lowercase()
                if (dev == myId || dev == myMac) {
                    val creds = parts[1].trim().split(":", limit = 3)
                    if (creds.size >= 2) {
                        val user = creds[0].trim()
                        val pass = creds[1].trim()
                        val dns = creds.getOrNull(2)?.trim()?.ifBlank { null }
                        if (user.isNotBlank() && pass.isNotBlank()) {
                            val prefs = context.getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
                            val loggedOutUser = prefs.getString("LOGGED_OUT_USER", "")
                            if (prefs.getBoolean("USER_LOGGED_OUT", false) && user.equals(loggedOutUser, ignoreCase = true)) {
                                return false
                            }
                            prefs.edit().remove("USER_LOGGED_OUT").remove("LOGGED_OUT_USER").apply()
                            val appContext = context.applicationContext
                            IptvServiceConfig.initialize(appContext)
                            val activeSrv = IptvServiceConfig.activeServiceId(appContext)
                            IptvServiceConfig.saveCredentials(appContext, activeSrv, user, pass)
                            if (!dns.isNullOrBlank()) {
                                IptvServiceConfig.updateRemoteUrls(appContext, dns, "")
                            }
                            prefs.edit()
                                .putString("SAVED_USER", user)
                                .putString("SAVED_PASS", pass)
                                .apply()
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    fun isBlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_BLOCKED, false) || prefs.getBoolean(KEY_MAINTENANCE, false)
    }

    fun getBlockMessage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isMaint = prefs.getBoolean(KEY_MAINTENANCE, false)
        if (isMaint) {
            val notice = prefs.getString(KEY_ANNOUNCEMENT, "").orEmpty().trim()
            return if (notice.isNotBlank()) notice else "O serviço encontra-se em manutenção temporária. Por favor, tenta novamente dentro de momentos."
        }
        return prefs.getString(KEY_BLOCK_REASON, null)?.takeIf { it.isNotBlank() }
            ?: "Acesso suspenso pelo administrador."
    }

    fun sync(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        IptvServiceConfig.initialize(appContext)
        val activeService = IptvServiceConfig.activeServiceId(appContext)
        var username = IptvServiceConfig.username(appContext, activeService)
        if (username.isBlank()) {
            val loginPrefs = appContext.getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
            username = loginPrefs.getString("SAVED_USER", "").orEmpty()
        }

        val payload = JSONObject().apply {
            put("p_device_id", getDeviceId(appContext))
            put("p_username", username)
            put("p_service", activeService)
            put("p_device_name", getDeviceName())
            put("p_app_version", getAppVersion(appContext))
            put("p_reseller_id", getResellerId(appContext))
        }

        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/rpc/app_sync")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onComplete?.invoke(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string().orEmpty()
                var success = false
                if (response.isSuccessful && raw.isNotBlank()) {
                    try {
                        val json = JSONObject(raw)
                        val bestUrl = json.optString("best_url", "").trim()
                        val best2Url = json.optString("best2_url", "").trim()
                        val rawAnnouncement = json.optString("announcement", "").trim()
                        applyActivationIfPresent(appContext, rawAnnouncement)
                        val announcementParts = rawAnnouncement.split("||VPN:", limit = 2)
                        val announcement = announcementParts[0].trim()
                        if (announcementParts.size > 1) {
                            val vpnRaw = announcementParts[1].trim()
                            val killSplit = vpnRaw.split("||VPN_KILL:", limit = 2)
                            val vpnBeforeKill = killSplit[0].trim()
                            if (killSplit.size > 1) {
                                val killTs = killSplit[1].trim().takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
                                PiaVpnConfig.applyRemoteVpnKillIfNeeded(appContext, killTs)
                            }

                            val usersSplit = vpnBeforeKill.split("||VPN_USERS:", limit = 2)
                            val vpnBeforeUsers = usersSplit[0].trim()
                            if (usersSplit.size > 1) {
                                val usersPart = usersSplit[1].trim()
                                val modeAndUsers = usersPart.split("|", limit = 2)
                                val mode = modeAndUsers.getOrNull(0)?.trim()?.lowercase().orEmpty().ifBlank { "restricted" }
                                val usersCsv = modeAndUsers.getOrNull(1)?.trim().orEmpty()
                                PiaVpnConfig.setVpnUserAccessConfig(appContext, mode, usersCsv)
                            }

                            val authParts = vpnBeforeUsers.split("||VPN_AUTH:", limit = 2)
                            val regionsCsv = authParts[0].trim()
                            PiaVpnConfig.setAllowedRegionsCsv(appContext, regionsCsv)

                            if (authParts.size > 1) {
                                val creds = authParts[1].trim().split(":", limit = 2)
                                if (creds.size == 2 && creds[0].isNotBlank() && creds[1].isNotBlank()) {
                                    val newU = creds[0].trim()
                                    val newP = creds[1].trim()
                                    if (PiaVpnConfig.getUsername(appContext) != newU || PiaVpnConfig.getPassword(appContext) != newP) {
                                        PiaVpnConfig.setUsername(appContext, newU)
                                        PiaVpnConfig.setPassword(appContext, newP)
                                        PiaVpnManager.resetCachedToken()
                                    }
                                }
                            }
                        } else {
                            PiaVpnConfig.setAllowedRegionsCsv(appContext, null)
                        }

                        if (!PiaVpnConfig.isVpnAllowedForCurrentUser(appContext) &&
                            PiaVpnManager.getStatus() != PiaVpnManager.VpnStatus.DISCONNECTED
                        ) {
                            PiaVpnManager.disconnect(appContext)
                        }
                        val maintenance = json.optBoolean("maintenance_mode", false)
                        val blocked = json.optBoolean("is_blocked", false)
                        val blockReason = json.optString("block_reason", "Acesso suspenso pelo administrador.").trim()

                        IptvServiceConfig.updateRemoteUrls(appContext, bestUrl, best2Url)

                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean(KEY_IS_BLOCKED, blocked)
                            .putString(KEY_BLOCK_REASON, blockReason)
                            .putBoolean(KEY_MAINTENANCE, maintenance)
                            .putString(KEY_ANNOUNCEMENT, announcement)
                            .putString("RAW_ANNOUNCEMENT", rawAnnouncement)
                            .apply()

                        success = true
                    } catch (_: Exception) {
                    }
                }
                mainHandler.post { onComplete?.invoke(success) }
            }
        })
    }

    fun syncAndEnforce(activity: Activity) {
        sync(activity) {
            if (!activity.isFinishing && !activity.isDestroyed) {
                enforceOnUi(activity)
            }
        }
    }

    fun enforceOnUi(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        if (activity is MainActivity) {
            activity.atualizarEstadoBotaoVpn()
        } else if (activity is EagleTvActivity) {
            activity.atualizarEstadoServidorEVpn()
        } else if (activity is VpnActivity && !PiaVpnConfig.isVpnAllowedForCurrentUser(activity)) {
            activity.finish()
            return true
        }
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val blocked = prefs.getBoolean(KEY_IS_BLOCKED, false)
        val maintenance = prefs.getBoolean(KEY_MAINTENANCE, false)

        if (blocked || maintenance) {
            showBlockOrMaintenanceDialog(activity, maintenance)
            return true
        } else {
            activeBlockDialog?.takeIf { it.isShowing }?.dismiss()
            activeBlockDialog = null
        }

        val announcement = prefs.getString(KEY_ANNOUNCEMENT, "").orEmpty().trim()
        val lastShown = prefs.getString(KEY_LAST_SHOWN_ANNOUNCEMENT, "").orEmpty().trim()
        if (announcement.isNotBlank() && announcement != lastShown) {
            prefs.edit().putString(KEY_LAST_SHOWN_ANNOUNCEMENT, announcement).apply()
            AlertDialog.Builder(activity)
                .setTitle("Aviso Importante")
                .setMessage(announcement)
                .setPositiveButton("OK", null)
                .show()
        } else if (announcement.isBlank() && lastShown.isNotBlank()) {
            prefs.edit().remove(KEY_LAST_SHOWN_ANNOUNCEMENT).apply()
        }

        return false
    }

    private fun showBlockOrMaintenanceDialog(activity: Activity, isMaintenance: Boolean) {
        if (activeBlockDialog?.isShowing == true) return
        val title = if (isMaintenance) "Serviço em Manutenção" else "Acesso Indisponível"
        val message = getBlockMessage(activity) + "\n\nID do Dispositivo: ${getDeviceId(activity)}"

        activeBlockDialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Verificar Novamente") { dialog, _ ->
                dialog.dismiss()
                activeBlockDialog = null
                sync(activity) {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        enforceOnUi(activity)
                    }
                }
            }
            .setNegativeButton("Sair") { _, _ ->
                activity.finishAffinity()
            }
            .create()
        activeBlockDialog?.show()
    }
}
