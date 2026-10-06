package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

object AppUpdateConfig {
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_GITHUB_OWNER = "github_owner"
    private const val KEY_GITHUB_REPO = "github_repo"

    // Valores padrão
    const val DEFAULT_GITHUB_OWNER = "onbesttv"
    const val DEFAULT_GITHUB_REPO = "eagle-app"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGithubOwner(context: Context): String {
        return getPrefs(context).getString(KEY_GITHUB_OWNER, DEFAULT_GITHUB_OWNER) ?: DEFAULT_GITHUB_OWNER
    }

    fun getGithubRepo(context: Context): String {
        return getPrefs(context).getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO
    }

    fun setGithubRepoInfo(context: Context, owner: String, repo: String) {
        getPrefs(context).edit()
            .putString(KEY_GITHUB_OWNER, owner.trim())
            .putString(KEY_GITHUB_REPO, repo.trim())
            .apply()
    }

    fun getLatestReleaseApiUrl(context: Context): String {
        val owner = getGithubOwner(context)
        val repo = getGithubRepo(context)
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }
}
