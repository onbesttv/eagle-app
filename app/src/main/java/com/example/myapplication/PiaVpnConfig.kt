package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

object PiaVpnConfig {

    private const val PREFS_NAME = "pia_vpn_prefs"
    private const val KEY_USERNAME = "pia_username"
    private const val KEY_PASSWORD = "pia_password"
    private const val KEY_REGION_ID = "pia_region_id"
    private const val KEY_AUTO_CONNECT = "pia_auto_connect"

    // Credenciais pré-configuradas da PIA VPN
    const val DEFAULT_USERNAME = "p6255571"
    const val DEFAULT_PASSWORD = "EqwvpMqb6e"
    const val DEFAULT_REGION_ID = "pt"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getUsername(context: Context): String {
        val user = getPrefs(context).getString(KEY_USERNAME, "")
        if (user.isNullOrBlank() || user == "p625571") {
            getPrefs(context).edit().putString(KEY_USERNAME, DEFAULT_USERNAME).apply()
            return DEFAULT_USERNAME
        }
        return user
    }

    fun setUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun getPassword(context: Context): String {
        val pass = getPrefs(context).getString(KEY_PASSWORD, "")
        return if (!pass.isNullOrBlank()) pass else DEFAULT_PASSWORD
    }

    fun setPassword(context: Context, password: String) {
        getPrefs(context).edit().putString(KEY_PASSWORD, password.trim()).apply()
    }

    fun getSelectedRegionId(context: Context): String {
        return getPrefs(context).getString(KEY_REGION_ID, DEFAULT_REGION_ID) ?: DEFAULT_REGION_ID
    }

    fun setSelectedRegionId(context: Context, regionId: String) {
        getPrefs(context).edit().putString(KEY_REGION_ID, regionId).apply()
    }

    fun isAutoConnect(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CONNECT, false)
    }

    fun setAutoConnect(context: Context, autoConnect: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CONNECT, autoConnect).apply()
    }
}
