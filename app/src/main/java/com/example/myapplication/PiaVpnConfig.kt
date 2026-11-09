package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

object PiaVpnConfig {

    private const val PREFS_NAME = "pia_vpn_prefs"
    private const val KEY_USERNAME = "pia_username"
    private const val KEY_PASSWORD = "pia_password"
    private const val KEY_REGION_ID = "pia_region_id"
    private const val KEY_AUTO_CONNECT = "pia_auto_connect"
    private const val KEY_ALLOWED_REGIONS = "pia_allowed_regions_csv"
    private const val KEY_VPN_ACCESS_MODE = "pia_vpn_access_mode"
    private const val KEY_VPN_ALLOWED_USERS = "pia_vpn_allowed_users_csv"

    // Credenciais Próprias da PIA VPN (App Master / Eagle e Godinho TV)
    const val MASTER_USERNAME = "p7426501"
    const val MASTER_PASSWORD = "on4q5FT9vx"

    // Credenciais de Revenda da PIA VPN (tv_fabio / Fpinto)
    const val FPINTO_USERNAME = "p6255571"
    const val FPINTO_PASSWORD = "EqwvpMqb6e"

    // Compatibilidade reversa
    const val DEFAULT_USERNAME = MASTER_USERNAME
    const val DEFAULT_PASSWORD = MASTER_PASSWORD
    const val DEFAULT_REGION_ID = "pt"
    const val DEFAULT_ALLOWED_REGIONS = "pt,spain,france,uk,nl_amsterdam,de-frankfurt,swiss,italy"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun isTvFabio(context: Context): Boolean {
        return RemotePanelManager.getResellerId(context).equals("tv_fabio", ignoreCase = true)
    }

    fun getDefaultUsername(context: Context): String {
        return if (isTvFabio(context)) FPINTO_USERNAME else MASTER_USERNAME
    }

    fun getDefaultPassword(context: Context): String {
        return if (isTvFabio(context)) FPINTO_PASSWORD else MASTER_PASSWORD
    }

    fun getUsername(context: Context): String {
        val defaultUser = getDefaultUsername(context)
        val prefs = getPrefs(context)
        val user = prefs.getString(KEY_USERNAME, "")
        // Migração automática se a app for Master ou Godinho TV e ainda tiver o utilizador antigo do Fpinto
        if (user.isNullOrBlank() || user == "p625571" || (!isTvFabio(context) && user == FPINTO_USERNAME)) {
            prefs.edit().putString(KEY_USERNAME, defaultUser).apply()
            PiaVpnManager.resetCachedToken()
            return defaultUser
        }
        return user
    }

    fun setUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun getPassword(context: Context): String {
        val defaultPass = getDefaultPassword(context)
        val prefs = getPrefs(context)
        val pass = prefs.getString(KEY_PASSWORD, "")
        if (pass.isNullOrBlank() || (!isTvFabio(context) && pass == FPINTO_PASSWORD)) {
            prefs.edit().putString(KEY_PASSWORD, defaultPass).apply()
            return defaultPass
        }
        return pass
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

    fun getAllowedRegionIds(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_ALLOWED_REGIONS, null)?.trim()
        val csv = if (raw.isNullOrBlank()) DEFAULT_ALLOWED_REGIONS else raw
        return csv.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }

    fun setAllowedRegionsCsv(context: Context, csv: String?) {
        if (!csv.isNullOrBlank()) {
            getPrefs(context).edit().putString(KEY_ALLOWED_REGIONS, csv.trim()).apply()
        } else {
            getPrefs(context).edit().remove(KEY_ALLOWED_REGIONS).apply()
        }
    }

    fun setVpnUserAccessConfig(context: Context, mode: String?, usersCsv: String?) {
        val cleanMode = mode?.trim()?.lowercase().orEmpty().ifBlank { "restricted" }
        val cleanUsers = usersCsv?.trim()?.lowercase().orEmpty()
        getPrefs(context).edit()
            .putString(KEY_VPN_ACCESS_MODE, cleanMode)
            .putString(KEY_VPN_ALLOWED_USERS, cleanUsers)
            .apply()
    }

    fun applyRemoteVpnKillIfNeeded(context: Context, killTimestamp: Long): Boolean {
        if (killTimestamp <= 0L) return false
        val prefs = getPrefs(context)
        val lastKill = prefs.getLong("pia_last_vpn_kill_ts", 0L)
        if (killTimestamp > lastKill) {
            prefs.edit()
                .putLong("pia_last_vpn_kill_ts", killTimestamp)
                .putBoolean(KEY_AUTO_CONNECT, false)
                .apply()
            PiaVpnManager.forceDisconnect(context)
            return true
        }
        return false
    }

    fun isVpnAllowedForCurrentUser(context: Context): Boolean {
        val resId = RemotePanelManager.getResellerId(context).trim().lowercase()
        val hasPerUserVpnControl = (resId == "master" || resId == "godinho_tv")
        if (!hasPerUserVpnControl) {
            return true
        }
        val prefs = getPrefs(context)
        IptvServiceConfig.initialize(context)
        val activeSrv = IptvServiceConfig.activeServiceId(context)
        val srvUser = IptvServiceConfig.username(context, activeSrv).trim().lowercase()
        val legacyUser = context.getSharedPreferences("iptv_login_prefs", Context.MODE_PRIVATE)
            .getString("SAVED_USER", "")?.trim()?.lowercase().orEmpty()
        val currentUser = if (srvUser.isNotBlank()) srvUser else legacyUser
        if (currentUser.isBlank()) {
            return false
        }
        val allowedUsers = prefs.getString(KEY_VPN_ALLOWED_USERS, "")
            .orEmpty()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        return allowedUsers.contains(currentUser) || (legacyUser.isNotBlank() && allowedUsers.contains(legacyUser))
    }
}
