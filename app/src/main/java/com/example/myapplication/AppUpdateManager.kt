package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppUpdateManager {

    private const val UPDATE_PREFS = "app_update_prefs"
    private const val KEY_UPDATE_IN_PROGRESS_TAG = "update_in_progress_tag"

    private var isLifecycleRegistered = false
    private var pendingReleaseForPermission: ReleaseInfo? = null
    private var pendingApkForInstall: Pair<File, String?>? = null
    private var activeUpdateDialog: AlertDialog? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    data class ReleaseInfo(
        val tagName: String,
        val releaseName: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val apkFileName: String,
        val apkSizeBytes: Long
    )

    fun getAppVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    fun getAppVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * Verifica atualizações no GitHub.
     * @param activity A atividade atual
     * @param manual Se true, mostra feedback visual imediato (loading, diálogos informativos).
     */
    fun checkForUpdates(activity: AppCompatActivity, manual: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed) return
        ensureLifecycleListener(activity)

        var loadingDialog: AlertDialog? = null
        if (manual) {
            loadingDialog = showLoadingDialog(activity, "A verificar atualizações…")
        }

        activity.lifecycleScope.launch {
            val apiUrl = AppUpdateConfig.getLatestReleaseApiUrl(activity)
            val currentVersion = getAppVersionName(activity)
            val resellerId = RemotePanelManager.getResellerId(activity)

            val result = withContext(Dispatchers.IO) {
                fetchLatestRelease(apiUrl, resellerId)
            }

            if (activity.isFinishing || activity.isDestroyed) return@launch
            loadingDialog?.dismiss()

            when (result) {
                is ReleaseResult.Success -> {
                    val release = result.release
                    if (isNewerVersion(release.tagName, currentVersion)) {
                        showUpdateAvailableDialog(activity, release)
                    } else if (manual) {
                        showUpToDateDialog(activity, currentVersion)
                    }
                }
                is ReleaseResult.UpToDate -> {
                    if (manual) {
                        showUpToDateDialog(activity, currentVersion)
                    }
                }
                is ReleaseResult.Error -> {
                    if (manual) {
                        showErrorDialog(activity, result.message)
                    }
                }
            }
        }
    }

    private sealed class ReleaseResult {
        data class Success(val release: ReleaseInfo) : ReleaseResult()
        object UpToDate : ReleaseResult()
        data class Error(val message: String) : ReleaseResult()
    }

    private fun fetchLatestRelease(apiUrl: String, resellerId: String): ReleaseResult {
        return try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "MyApplication-OTA-Updater")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            val statusCode = response.code
            val bodyString = response.body?.string().orEmpty()

            if (statusCode == 404) {
                // Nenhuma release publicada ainda significa que o app está na versão atual
                return ReleaseResult.UpToDate
            }

            if (!response.isSuccessful) {
                return ReleaseResult.Error("Não foi possível verificar atualizações de momento. Tenta mais tarde.")
            }

            // Procura nas releases mais recentes a primeira que contenha o APK exclusivo desta variante (eagle.apk ou <reseller_id>.apk)
            val expectedApkName = if (resellerId == "master") "eagle.apk" else "${resellerId}.apk"
            val isEagleTv = (resellerId == "eagle_tv")
            val releasesArray = if (bodyString.trimStart().startsWith("[")) {
                org.json.JSONArray(bodyString)
            } else {
                org.json.JSONArray().put(JSONObject(bodyString))
            }

            for (rIndex in 0 until releasesArray.length()) {
                val json = releasesArray.getJSONObject(rIndex)
                if (json.optBoolean("draft", false)) continue

                val rawTagName = json.optString("tag_name", "").trim()
                val tagName = rawTagName.substringBefore("-")
                val releaseName = json.optString("name", tagName).trim()
                val body = json.optString("body", "").trim()

                // Se for Eagle TV (MyTvOnline), as suas releases usam a versão v1.x, v2.x, v3.x.
                // Ignora releases da app principal Eagle XCIPTV que têm tag v4.x, v5.x, v6.x, v7.x!
                if (isEagleTv) {
                    val cleanTag = tagName.removePrefix("v").removePrefix("V")
                    val major = cleanTag.split(".").firstOrNull()?.toIntOrNull() ?: 0
                    if (major >= 4) {
                        continue
                    }
                }

                val assetsArray = json.optJSONArray("assets") ?: continue

                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.equals(expectedApkName, ignoreCase = true)) {
                        val apkUrl = asset.optString("browser_download_url", "")
                        val apkSize = asset.optLong("size", 0L)
                        if (apkUrl.isNotBlank()) {
                            return ReleaseResult.Success(
                                ReleaseInfo(
                                    tagName = tagName,
                                    releaseName = if (releaseName.isBlank()) tagName else releaseName,
                                    releaseNotes = body,
                                    apkDownloadUrl = apkUrl,
                                    apkFileName = name,
                                    apkSizeBytes = apkSize
                                )
                            )
                        }
                    }
                }
            }

            ReleaseResult.UpToDate
        } catch (e: Exception) {
            ReleaseResult.Error("Não foi possível verificar atualizações de momento.\nVerifica a ligação à Internet e tenta novamente.")
        }
    }

    /**
     * Compara números de versão como "v1.2" ou "1.2.0" contra "1.0".
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        try {
            val cleanRemote = remoteTag.removePrefix("v").removePrefix("V").trim()
            val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()

            val remoteParts = cleanRemote.split(".").mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            // Se forem idênticos numericamente, não é mais recente
            return false
        } catch (_: Exception) {
            return !remoteTag.equals(currentVersion, ignoreCase = true)
        }
    }

    private fun showLoadingDialog(activity: AppCompatActivity, text: String): AlertDialog {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 24))
            background = activity.getDrawable(R.drawable.bg_auth_panel)
        }

        val progressBar = ProgressBar(activity).apply {
            isIndeterminate = true
        }
        root.addView(progressBar, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))

        val statusView = TextView(activity).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 14), 0, 0)
        }
        root.addView(statusView)

        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .setCancelable(false)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.65f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun showUpToDateDialog(activity: AppCompatActivity, currentVersion: String) {
        val panel = createDialogContainer(activity)

        val title = createDialogHeader(activity, "ATUALIZAÇÕES")
        panel.addView(title)

        val message = TextView(activity).apply {
            text = "A aplicação já está na versão mais recente (v$currentVersion).\nNão há novas atualizações disponíveis."
            setTextColor(0xFFD2DDE7.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 12), 0, dp(activity, 20))
        }
        panel.addView(message)

        val dialog = AlertDialog.Builder(activity)
            .setView(panel)
            .setPositiveButton("OK", null)
            .create()

        dialog.show()
        styleDialogWindow(dialog, activity)
    }

    private fun showErrorDialog(activity: AppCompatActivity, errorMessage: String) {
        val panel = createDialogContainer(activity)

        val title = createDialogHeader(activity, "ATUALIZAÇÕES")
        panel.addView(title)

        val message = TextView(activity).apply {
            text = errorMessage
            setTextColor(0xFFE2EAF2.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 10), 0, dp(activity, 18))
        }
        panel.addView(message)

        val dialog = AlertDialog.Builder(activity)
            .setView(panel)
            .setPositiveButton("OK", null)
            .create()

        dialog.show()
        styleDialogWindow(dialog, activity)
    }

    private fun ensureLifecycleListener(activity: AppCompatActivity) {
        if (isLifecycleRegistered) return
        isLifecycleRegistered = true
        activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(act: Activity) {
                if (act !is AppCompatActivity || act.isFinishing || act.isDestroyed) return

                val pendingInstall = pendingApkForInstall
                if (pendingInstall != null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || act.packageManager.canRequestPackageInstalls()) {
                        val file = pendingInstall.first
                        val tag = pendingInstall.second
                        pendingApkForInstall = null
                        if (file.exists() && file.length() > 0) {
                            promptInstallApk(act, file, tag)
                            return
                        }
                    }
                }

                val pendingRelease = pendingReleaseForPermission
                if (pendingRelease != null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || act.packageManager.canRequestPackageInstalls()) {
                        pendingReleaseForPermission = null
                        startDownloadAndInstall(act, pendingRelease)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun getTargetApkFile(context: Context, release: ReleaseInfo): File {
        val destinationDir = File(context.cacheDir, "updates")
        if (!destinationDir.exists()) destinationDir.mkdirs()
        return File(destinationDir, "eagle-${release.tagName}.apk")
    }

    private fun isApkAlreadyDownloaded(context: Context, release: ReleaseInfo): Boolean {
        val targetApk = getTargetApkFile(context, release)
        return targetApk.exists() && targetApk.length() > 0 &&
                (release.apkSizeBytes <= 0 || targetApk.length() == release.apkSizeBytes)
    }

    private fun cleanOldUpdates(context: Context, currentTag: String) {
        try {
            val destinationDir = File(context.cacheDir, "updates")
            if (destinationDir.exists() && destinationDir.isDirectory) {
                destinationDir.listFiles()?.forEach { file ->
                    if (file.name != "eagle-$currentTag.apk") {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun showUnknownSourcesPermissionDialog(activity: AppCompatActivity, release: ReleaseInfo) {
        val panel = createDialogContainer(activity)
        val title = createDialogHeader(activity, "AUTORIZAÇÃO NECESSÁRIA")
        panel.addView(title)

        val message = TextView(activity).apply {
            text = "Para atualizar a aplicação, é necessário autorizar a instalação de atualizações por esta app nas Definições do sistema.\n\nApós ativar 'Permitir desta fonte', regressa à app para concluir."
            setTextColor(0xFFD2DDE7.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 8), 0, dp(activity, 18))
        }
        panel.addView(message)

        val dialog = AlertDialog.Builder(activity)
            .setView(panel)
            .setNegativeButton("CANCELAR", null)
            .setPositiveButton("DEFINIÇÕES") { _, _ ->
                pendingReleaseForPermission = release
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    activity.startActivity(intent)
                }
            }
            .create()

        dialog.show()
        styleDialogWindow(dialog, activity)
    }

    private fun showUpdateAvailableDialog(activity: AppCompatActivity, release: ReleaseInfo) {
        if (activeUpdateDialog?.isShowing == true) return
        ensureLifecycleListener(activity)
        val panel = createDialogContainer(activity)

        val title = createDialogHeader(activity, "NOVA ATUALIZAÇÃO DISPONÍVEL!")
        panel.addView(title)

        val isDownloaded = isApkAlreadyDownloaded(activity, release)

        val versionText = TextView(activity).apply {
            text = "Nova Versão: ${release.tagName}"
            setTextColor(0xFF42D6E8.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 6), 0, dp(activity, 14))
        }
        panel.addView(versionText)

        val positiveBtnText = if (isDownloaded) "INSTALAR AGORA" else "ATUALIZAR AGORA"

        val dialog = AlertDialog.Builder(activity)
            .setView(panel)
            .setNegativeButton("MAIS TARDE") { d, _ ->
                d.dismiss()
                activeUpdateDialog = null
            }
            .setPositiveButton(positiveBtnText) { _, _ ->
                activeUpdateDialog = null
                startDownloadAndInstall(activity, release)
            }
            .setOnDismissListener {
                activeUpdateDialog = null
            }
            .create()

        activeUpdateDialog = dialog
        dialog.show()
        styleDialogWindow(dialog, activity)
    }

    private fun startDownloadAndInstall(activity: AppCompatActivity, release: ReleaseInfo) {
        ensureLifecycleListener(activity)

        // 1. Verificar permissão de instalação de fontes desconhecidas no Android 8.0+ ANTES de descarregar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                showUnknownSourcesPermissionDialog(activity, release)
                return
            }
        }

        val targetApk = getTargetApkFile(activity, release)

        // 2. Se o ficheiro já foi descarregado com sucesso anteriormente, instala diretamente sem voltar a descarregar!
        if (isApkAlreadyDownloaded(activity, release)) {
            promptInstallApk(activity, targetApk, release.tagName)
            return
        }

        // Limpar ficheiros antigos de atualizações anteriores para libertar espaço
        cleanOldUpdates(activity, release.tagName)

        // 3. Mostra o diálogo de progresso de download
        val panel = createDialogContainer(activity)
        val title = createDialogHeader(activity, "A DESCARREGAR ATUALIZAÇÃO")
        panel.addView(title)

        val versionLabel = TextView(activity).apply {
            text = "${release.tagName} • ${formatFileSize(release.apkSizeBytes)}"
            setTextColor(0xFF8BA5BF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(activity, 12))
        }
        panel.addView(versionLabel)

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = release.apkSizeBytes <= 0
            max = 100
            progress = 0
        }
        panel.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 20)))

        val progressText = TextView(activity).apply {
            text = "A iniciar download…"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(activity, 10), 0, dp(activity, 16))
        }
        panel.addView(progressText)

        var downloadJob: Job? = null

        val dialog = AlertDialog.Builder(activity)
            .setView(panel)
            .setNegativeButton("CANCELAR") { _, _ ->
                downloadJob?.cancel()
            }
            .setCancelable(false)
            .create()

        dialog.show()
        styleDialogWindow(dialog, activity)

        downloadJob = activity.lifecycleScope.launch {
            val destinationDir = File(activity.cacheDir, "updates")
            if (!destinationDir.exists()) destinationDir.mkdirs()
            val tempApk = File(destinationDir, "eagle-${release.tagName}.apk.tmp")

            val success = withContext(Dispatchers.IO) {
                downloadFile(
                    url = release.apkDownloadUrl,
                    tempFile = tempApk,
                    targetFile = targetApk,
                    expectedSize = release.apkSizeBytes,
                    onProgress = { downloadedBytes, totalBytes, percent ->
                        activity.runOnUiThread {
                            if (dialog.isShowing) {
                                if (totalBytes > 0) {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = percent
                                    progressText.text = "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)} ($percent%)"
                                } else {
                                    progressBar.isIndeterminate = true
                                    progressText.text = "${formatFileSize(downloadedBytes)} descarregados…"
                                }
                            }
                        }
                    }
                )
            }

            if (activity.isFinishing || activity.isDestroyed) return@launch
            dialog.dismiss()

            if (success) {
                promptInstallApk(activity, targetApk, release.tagName)
            } else {
                Toast.makeText(activity, "Falha ao descarregar a atualização. Tenta novamente.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadFile(
        url: String,
        tempFile: File,
        targetFile: File,
        expectedSize: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percent: Int) -> Unit
    ): Boolean {
        if (tempFile.exists()) tempFile.delete()
        if (targetFile.exists()) targetFile.delete()

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyApplication-OTA-Updater")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            val contentLength = if (body.contentLength() > 0) body.contentLength() else expectedSize

            inputStream = body.byteStream()
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(64 * 1024)
            var totalRead = 0L
            var lastUpdate = 0L

            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 150) { // Atualiza UI a cada ~150ms
                    lastUpdate = now
                    val percent = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100) else 0
                    onProgress(totalRead, contentLength, percent)
                }
            }
            outputStream.flush()
            outputStream.close()
            outputStream = null

            // Se conhecemos o tamanho esperado e não recebemos tudo, o download foi interrompido
            if (contentLength > 0 && totalRead < contentLength) {
                if (tempFile.exists()) tempFile.delete()
                return false
            }

            onProgress(totalRead, contentLength, 100)

            // Renomeia o temporário para o ficheiro final do APK
            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            return targetFile.exists() && targetFile.length() > 0
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            return false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Inicia a instalação do APK através do PackageInstaller do Android.
     */
    fun promptInstallApk(activity: AppCompatActivity, apkFile: File, releaseTag: String? = null) {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            Toast.makeText(activity, "Ficheiro APK não encontrado para instalação.", Toast.LENGTH_SHORT).show()
            return
        }

        // No Android 8.0+ (Oreo), precisamos de verificar se a app tem permissão para instalar APKs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                pendingApkForInstall = Pair(apkFile, releaseTag)
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Autorização Necessária")
                    .setMessage("Para atualizar automaticamente, é necessário autorizar a instalação de atualizações por esta aplicação nas Definições do sistema.")
                    .setPositiveButton("DEFINIÇÕES") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            }
                            activity.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            activity.startActivity(intent)
                        }
                    }
                    .setNegativeButton("CANCELAR", null)
                    .create()

                dialog.show()
                styleDialogWindow(dialog, activity)
                return
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Conceder permissão explícita de leitura a todos os componentes instaladores de pacotes
            val resInfoList = activity.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                activity.grantUriPermission(pkgName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // O instalador pode terminar o processo da app. Gravar sincronamente antes
            // de o abrir evita repetir o mesmo aviso se o APK ainda arrancar com a versão antiga.
            if (!releaseTag.isNullOrBlank()) {
                activity.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_UPDATE_IN_PROGRESS_TAG, releaseTag)
                    .commit()
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Erro ao abrir o instalador: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createDialogContainer(activity: Context): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 14))
            background = activity.getDrawable(R.drawable.bg_auth_panel)
        }
    }

    private fun createDialogHeader(activity: Context, text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 16f
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(activity, 8))
        }
    }

    private fun styleDialogWindow(dialog: AlertDialog, activity: Context) {
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val screenWidth = activity.resources.displayMetrics.widthPixels
        dialog.window?.setLayout((screenWidth * 0.72f).toInt().coerceAtMost(screenWidth - dp(activity, 48)), ViewGroup.LayoutParams.WRAP_CONTENT)
        AppThemeColors.applyToDialog(dialog)
        val btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        btnPos?.setTextColor(AppThemeColors.mapColor(activity, 0xFF42D6E8.toInt()))
        btnNeg?.setTextColor(AppThemeColors.mapColor(activity, 0xFF91A8BB.toInt()))
        btnPos?.post { btnPos.requestFocus() }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
