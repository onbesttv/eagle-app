package com.example.myapplication

import android.content.Context

/** Stores the independent server and account settings for BEST and BEST2. */
object IptvServiceConfig {
    const val BEST = "best"
    const val BEST2 = "best2"

    private const val PREFS = "iptv_login_prefs"
    private const val KEY_ACTIVE = "ACTIVE_SERVICE"
    private const val KEY_INITIALIZED = "MULTI_SERVICE_V1"
    private const val BEST_URL = "https://allrevplay.online:443"
    private const val BEST2_URL = "http://gd.tugabest.xyz:8080"
    private const val BEST_USER = "BEST_USER"
    private const val BEST_PASS = "BEST_PASS"
    private const val BEST2_USER = "BEST2_USER"
    private const val BEST2_PASS = "BEST2_PASS"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Limpar credenciais de teste antigas caso ainda existam no dispositivo
        if (prefs.getString(BEST2_USER, "") == "889248" && prefs.getString(BEST2_PASS, "") == "7772") {
            prefs.edit().remove(BEST2_USER).remove(BEST2_PASS).apply()
        }

        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val legacyUser = prefs.getString("SAVED_USER", "").orEmpty()
            val legacyPass = prefs.getString("SAVED_PASS", "").orEmpty()
            val legacyService = prefs.getString("SAVED_SERVICE", "").orEmpty()
            val migrateToBest2 = legacyService == BEST2
            if (legacyUser.isNotBlank() && legacyPass.isNotBlank() && legacyUser != "889248") {
                if (migrateToBest2) {
                    prefs.edit().putString(BEST2_USER, legacyUser).putString(BEST2_PASS, legacyPass).apply()
                } else {
                    prefs.edit().putString(BEST_USER, legacyUser).putString(BEST_PASS, legacyPass).apply()
                }
            }

            val initialService = if (migrateToBest2 && legacyUser.isNotBlank()) BEST2 else BEST
            prefs.edit()
                .putString(KEY_ACTIVE, prefs.getString(KEY_ACTIVE, initialService) ?: initialService)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
        }

        val active = activeServiceId(context)
        syncLegacyCredentials(context, active)
    }

    fun activeServiceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE, BEST)?.takeIf { it == BEST2 } ?: BEST
    }

    fun baseUrl(context: Context, serviceId: String = activeServiceId(context)): String =
        if (serviceId == BEST2) BEST2_URL else BEST_URL

    fun username(context: Context, serviceId: String = activeServiceId(context)): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(if (serviceId == BEST2) BEST2_USER else BEST_USER, "").orEmpty()
    }

    fun password(context: Context, serviceId: String = activeServiceId(context)): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(if (serviceId == BEST2) BEST2_PASS else BEST_PASS, "").orEmpty()
    }

    fun select(context: Context, serviceId: String) {
        val validService = if (serviceId == BEST2) BEST2 else BEST
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE, validService)
            .putString("SAVED_SERVICE", validService)
            .apply()
        syncLegacyCredentials(context, validService)
    }

    fun saveCredentials(context: Context, serviceId: String, username: String, password: String) {
        val validService = if (serviceId == BEST2) BEST2 else BEST
        val userKey = if (validService == BEST2) BEST2_USER else BEST_USER
        val passKey = if (validService == BEST2) BEST2_PASS else BEST_PASS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(userKey, username)
            .putString(passKey, password)
            .putString(KEY_ACTIVE, validService)
            .putString("SAVED_SERVICE", validService)
            .putString("SAVED_USER", username)
            .putString("SAVED_PASS", password)
            .apply()
    }

    private fun syncLegacyCredentials(context: Context, serviceId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val username = username(context, serviceId)
        val password = password(context, serviceId)
        prefs.edit()
            .putString("SAVED_SERVICE", serviceId)
            .putString("SAVED_USER", username)
            .putString("SAVED_PASS", password)
            .apply()
    }
}
