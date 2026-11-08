package com.example.myapplication

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.abs

class PdfViewerActivity : BaseActivity() {

    companion object {
        const val EXTRA_PDF_URL = "EXTRA_PDF_URL"
        const val EXTRA_NEWSPAPER_TITLE = "EXTRA_NEWSPAPER_TITLE"
    }

    private lateinit var scrollViewPdf: ScrollView
    private lateinit var ivPdfPage: ImageView
    private lateinit var layoutTopBar: LinearLayout
    private lateinit var tvPdfTitle: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var tvControlsHint: TextView
    private lateinit var btnBackPdf: ImageButton
    private lateinit var btnPrevPage: ImageButton
    private lateinit var btnNextPage: ImageButton
    private lateinit var btnZoomToggle: Button

    private lateinit var layoutPdfLoading: LinearLayout
    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvLoadingProgress: TextView
    private lateinit var btnCancelDownload: Button

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var downloadCall: Call? = null

    private var pfd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentBitmap: Bitmap? = null

    private var currentPageIndex = 0
    private var totalPages = 0
    private var currentZoomIndex = 0
    private val zoomLevels = floatArrayOf(1.0f, 1.6f, 2.3f)

    private val autoHideTopBarRunnable = Runnable {
        layoutTopBar.animate().alpha(0f).setDuration(250).withEndAction {
            layoutTopBar.visibility = View.GONE
        }.start()
        tvControlsHint.animate().alpha(0f).setDuration(300).withEndAction {
            tvControlsHint.visibility = View.GONE
        }.start()
    }

    // Gesto de Swipe para ecrãs táteis
    private var touchStartX = 0f
    private var touchStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        scrollViewPdf = findViewById(R.id.scrollViewPdf)
        ivPdfPage = findViewById(R.id.ivPdfPage)
        layoutTopBar = findViewById(R.id.layoutTopBar)
        tvPdfTitle = findViewById(R.id.tvPdfTitle)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        tvControlsHint = findViewById(R.id.tvControlsHint)
        btnBackPdf = findViewById(R.id.btnBackPdf)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        btnZoomToggle = findViewById(R.id.btnZoomToggle)

        layoutPdfLoading = findViewById(R.id.layoutPdfLoading)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)
        tvLoadingProgress = findViewById(R.id.tvLoadingProgress)
        btnCancelDownload = findViewById(R.id.btnCancelDownload)

        val title = intent.getStringExtra(EXTRA_NEWSPAPER_TITLE) ?: "Jornal"
        val pdfUrl = intent.getStringExtra(EXTRA_PDF_URL).orEmpty()
        tvPdfTitle.text = title

        btnBackPdf.setOnClickListener { finish() }
        btnPrevPage.setOnClickListener { prevPage(); resetAutoHide() }
        btnNextPage.setOnClickListener { nextPage(); resetAutoHide() }
        btnZoomToggle.setOnClickListener { cycleZoom(); resetAutoHide() }
        btnCancelDownload.setOnClickListener {
            downloadCall?.cancel()
            finish()
        }

        ivPdfPage.setOnClickListener {
            toggleTopBar()
        }

        setupSwipeDetector()
        openPdfSource(pdfUrl)
        resetAutoHide()
    }

    private fun setupSwipeDetector() {
        scrollViewPdf.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    if (abs(deltaX) > 160 && abs(deltaX) > abs(deltaY) * 1.5) {
                        if (deltaX < 0) nextPage() else prevPage()
                        resetAutoHide()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun toggleTopBar() {
        mainHandler.removeCallbacks(autoHideTopBarRunnable)
        if (layoutTopBar.visibility == View.VISIBLE) {
            layoutTopBar.visibility = View.GONE
        } else {
            layoutTopBar.visibility = View.VISIBLE
            layoutTopBar.alpha = 1f
            resetAutoHide()
        }
    }

    private fun resetAutoHide() {
        mainHandler.removeCallbacks(autoHideTopBarRunnable)
        layoutTopBar.visibility = View.VISIBLE
        layoutTopBar.alpha = 1f
        mainHandler.postDelayed(autoHideTopBarRunnable, 4500)
    }

    private fun openPdfSource(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "Link do PDF não disponível.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            val cacheKey = hashString(url) + ".pdf"
            val cacheDir = File(cacheDir, "newspapers").apply { mkdirs() }
            val cachedFile = File(cacheDir, cacheKey)

            if (cachedFile.exists() && cachedFile.length() > 10_000) {
                initRenderer(cachedFile)
            } else {
                downloadPdf(url, cachedFile)
            }
        } else {
            val localFile = File(url)
            if (localFile.exists()) {
                initRenderer(localFile)
            } else {
                Toast.makeText(this, "Ficheiro local não encontrado.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun downloadPdf(url: String, destFile: File) {
        layoutPdfLoading.visibility = View.VISIBLE
        tvLoadingStatus.text = "A descarregar ${tvPdfTitle.text}..."
        tvLoadingProgress.text = "A estabelecer ligação segura..."

        val request = Request.Builder().url(url).build()
        downloadCall = client.newCall(request)

        downloadCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                runOnUiThread {
                    layoutPdfLoading.visibility = View.GONE
                    Toast.makeText(this@PdfViewerActivity, "Erro ao descarregar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        layoutPdfLoading.visibility = View.GONE
                        Toast.makeText(this@PdfViewerActivity, "Servidor de jornais indisponível (HTTP ${response.code})", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return
                }

                val body = response.body ?: return
                val totalBytes = body.contentLength()
                val tempFile = File(destFile.parentFile, destFile.name + ".tmp")

                try {
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            var downloadedBytes: Long = 0
                            var lastProgressUpdate = System.currentTimeMillis()

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 150) {
                                    lastProgressUpdate = now
                                    val mbDownloaded = downloadedBytes.toDouble() / (1024 * 1024)
                                    val mbTotal = totalBytes.toDouble() / (1024 * 1024)
                                    val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0

                                    runOnUiThread {
                                        if (totalBytes > 0) {
                                            tvLoadingProgress.text = String.format("%.1f MB / %.1f MB (%d%%)", mbDownloaded, mbTotal, percent)
                                        } else {
                                            tvLoadingProgress.text = String.format("%.1f MB descarregados", mbDownloaded)
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (tempFile.renameTo(destFile)) {
                        runOnUiThread {
                            layoutPdfLoading.visibility = View.GONE
                            initRenderer(destFile)
                        }
                    } else {
                        throw IOException("Falha ao gravar PDF em cache.")
                    }
                } catch (e: Exception) {
                    if (call.isCanceled()) return
                    runOnUiThread {
                        layoutPdfLoading.visibility = View.GONE
                        Toast.makeText(this@PdfViewerActivity, "Falha no download do jornal.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        })
    }

    private fun initRenderer(file: File) {
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pfd!!)
            totalPages = pdfRenderer!!.pageCount
            currentPageIndex = 0
            renderPage(currentPageIndex)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Não foi possível abrir este jornal.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun renderPage(pageIndex: Int) {
        val renderer = pdfRenderer ?: return
        if (pageIndex < 0 || pageIndex >= totalPages) return

        try {
            currentPage?.close()
            currentPage = renderer.openPage(pageIndex)

            val displayMetrics = resources.displayMetrics
            val baseWidth = (displayMetrics.widthPixels * 0.95).toInt()
            val zoom = zoomLevels[currentZoomIndex]
            val targetWidth = (baseWidth * zoom).toInt().coerceAtLeast(600)
            val aspectRatio = currentPage!!.height.toFloat() / currentPage!!.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            currentBitmap?.recycle()
            currentBitmap = bitmap

            ivPdfPage.setImageBitmap(bitmap)
            tvPageIndicator.text = "Pág. ${pageIndex + 1} / $totalPages"
            btnZoomToggle.text = "${(zoom * 100).toInt()}%"

            // Volta ao topo ao mudar de página
            scrollViewPdf.scrollTo(0, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun nextPage() {
        if (currentPageIndex + 1 < totalPages) {
            currentPageIndex++
            renderPage(currentPageIndex)
        } else {
            Toast.makeText(this, "Fim do jornal", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prevPage() {
        if (currentPageIndex - 1 >= 0) {
            currentPageIndex--
            renderPage(currentPageIndex)
        }
    }

    private fun cycleZoom() {
        currentZoomIndex = (currentZoomIndex + 1) % zoomLevels.size
        renderPage(currentPageIndex)
        Toast.makeText(this, "Zoom: ${(zoomLevels[currentZoomIndex] * 100).toInt()}%", Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        resetAutoHide()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                nextPage()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                prevPage()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                cycleZoom()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                scrollViewPdf.smoothScrollBy(0, -180)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                scrollViewPdf.smoothScrollBy(0, 180)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoHideTopBarRunnable)
        downloadCall?.cancel()
        try {
            currentPage?.close()
            pdfRenderer?.close()
            pfd?.close()
            currentBitmap?.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
