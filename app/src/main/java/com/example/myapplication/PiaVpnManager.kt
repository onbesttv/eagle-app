package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import okhttp3.Credentials
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

object PiaVpnManager {

    enum class VpnStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }

    private var backend: Backend? = null
    private val tunnel = PiaTunnel("pia_wireguard")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(VpnStatus, String?) -> Unit>()

    private var status: VpnStatus = VpnStatus.DISCONNECTED
    private var statusMessage: String? = null
    private var connectedRegion: PiaRegion? = null
    private var protectedIp: String? = null
    private var connectionStartTime: Long = 0L

    private var cachedToken: String? = null
    private var cachedTokenExpiry: Long = 0L

    private val regionsCache = mutableListOf<PiaRegion>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Cliente HTTP para o endpoint /addKey na porta 1337 (usa certificado interno da PIA)
    private val insecureClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private class PiaTunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {
            when (newState) {
                Tunnel.State.UP -> updateState(VpnStatus.CONNECTED, null)
                Tunnel.State.DOWN -> {
                    if (status != VpnStatus.ERROR) {
                        updateState(VpnStatus.DISCONNECTED, null)
                    }
                }
                else -> {}
            }
        }
    }

    fun init(context: Context) {
        if (backend == null) {
            try {
                backend = GoBackend(context.applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getStatus(): VpnStatus = status
    fun getStatusMessage(): String? = statusMessage
    fun getConnectedRegion(): PiaRegion? = connectedRegion
    fun getProtectedIp(): String? = protectedIp
    fun getConnectionStartTime(): Long = connectionStartTime

    fun addListener(listener: (VpnStatus, String?) -> Unit) {
        listeners.add(listener)
        listener(status, statusMessage)
    }

    fun removeListener(listener: (VpnStatus, String?) -> Unit) {
        listeners.remove(listener)
    }

    private fun updateState(newStatus: VpnStatus, message: String?) {
        status = newStatus
        statusMessage = message
        if (newStatus == VpnStatus.CONNECTED) {
            if (connectionStartTime == 0L) {
                connectionStartTime = System.currentTimeMillis()
            }
        } else if (newStatus == VpnStatus.DISCONNECTED || newStatus == VpnStatus.ERROR) {
            connectionStartTime = 0L
            if (newStatus == VpnStatus.DISCONNECTED) {
                protectedIp = null
            }
        }
        mainHandler.post {
            listeners.forEach { it(newStatus, message) }
        }
    }

    fun checkPermission(activity: Activity): Intent? {
        return GoBackend.VpnService.prepare(activity)
    }

    fun connect(context: Context, region: PiaRegion) {
        if (status == VpnStatus.CONNECTING || status == VpnStatus.CONNECTED) return

        init(context)
        updateState(VpnStatus.CONNECTING, "A estabelecer ligação a ${region.name}…")
        connectedRegion = region

        thread {
            try {
                val username = PiaVpnConfig.getUsername(context)
                val password = PiaVpnConfig.getPassword(context)

                if (username.isBlank() || password.isBlank()) {
                    throw IllegalStateException("Utilizador ou palavra-passe da PIA VPN não configurados.")
                }

                // Passo 1: Obter Token da PIA
                updateState(VpnStatus.CONNECTING, "A autenticar na PIA VPN…")
                val token = getOrFetchToken(username, password)

                // Passo 2: Gerar chaves WireGuard
                updateState(VpnStatus.CONNECTING, "A negociar túnel WireGuard…")
                val keyPair = KeyPair()
                val privKey = keyPair.privateKey.toBase64()
                val pubKey = keyPair.publicKey.toBase64()

                // Passo 3: Registar chave no servidor PIA (/addKey na porta 1337)
                val encodedPubKey = URLEncoder.encode(pubKey, "UTF-8")
                val encodedToken = URLEncoder.encode(token, "UTF-8")
                val addKeyUrl = "https://${region.serverIp}:1337/addKey?pubkey=$encodedPubKey&pt=$encodedToken"

                val req = Request.Builder().url(addKeyUrl).build()
                val resp = insecureClient.newCall(req).execute()
                val respBody = resp.body?.string().orEmpty()

                if (!resp.isSuccessful) {
                    throw IllegalStateException("Erro ao registar chave no servidor (${resp.code}): $respBody")
                }

                val json = JSONObject(respBody)
                if (json.optString("status") != "OK") {
                    val msg = json.optString("message", "Falha na resposta do servidor PIA")
                    throw IllegalStateException(msg)
                }

                val peerIp = json.getString("peer_ip")
                val serverKey = json.getString("server_key")
                val serverPort = json.optInt("server_port", 1337)
                val dnsServers = mutableListOf<String>()
                val dnsArray = json.optJSONArray("dns_servers")
                if (dnsArray != null) {
                    for (i in 0 until dnsArray.length()) {
                        dnsServers.add(dnsArray.getString(i))
                    }
                }

                protectedIp = peerIp

                // Passo 4: Construir Configuração WireGuard
                val ifaceBuilder = Interface.Builder()
                    .addAddress(InetNetwork.parse(if (peerIp.contains("/")) peerIp else "$peerIp/32"))
                    .parsePrivateKey(privKey)

                if (dnsServers.isNotEmpty()) {
                    for (dns in dnsServers) {
                        try {
                            ifaceBuilder.addDnsServer(InetAddress.getByName(dns))
                        } catch (_: Exception) {}
                    }
                } else {
                    try {
                        ifaceBuilder.addDnsServer(InetAddress.getByName("10.0.0.243"))
                    } catch (_: Exception) {}
                }

                val peerBuilder = Peer.Builder()
                    .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                    .setEndpoint(InetEndpoint.parse("${region.serverIp}:$serverPort"))
                    .parsePublicKey(serverKey)

                val wgConfig = Config.Builder()
                    .setInterface(ifaceBuilder.build())
                    .addPeer(peerBuilder.build())
                    .build()

                // Passo 5: Iniciar Túnel
                updateState(VpnStatus.CONNECTING, "A ativar interface de rede segura…")
                val be = backend ?: GoBackend(context.applicationContext).also { backend = it }
                be.setState(tunnel, Tunnel.State.UP, wgConfig)
                updateState(VpnStatus.CONNECTED, null)

            } catch (e: Exception) {
                e.printStackTrace()
                updateState(VpnStatus.ERROR, e.localizedMessage ?: "Erro desconhecido ao ligar à VPN")
            }
        }
    }

    fun disconnect(context: Context, onComplete: (() -> Unit)? = null) {
        if (status == VpnStatus.DISCONNECTED) {
            onComplete?.invoke()
            return
        }
        updateState(VpnStatus.DISCONNECTING, "A desligar VPN…")
        thread {
            try {
                init(context)
                backend?.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                updateState(VpnStatus.DISCONNECTED, null)
                if (onComplete != null) {
                    mainHandler.post { onComplete() }
                }
            }
        }
    }

    fun resetCachedToken() {
        cachedToken = null
        cachedTokenExpiry = 0L
    }

    private fun getOrFetchToken(user: String, pass: String): String {
        val now = System.currentTimeMillis()
        if (!cachedToken.isNullOrBlank() && now < cachedTokenExpiry) {
            return cachedToken!!
        }

        // Tenta endpoint 1: POST /api/client/v2/token (Form Data)
        try {
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username", user)
                .addFormDataPart("password", pass)
                .build()

            val req = Request.Builder()
                .url("https://www.privateinternetaccess.com/api/client/v2/token")
                .post(form)
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            if (res.isSuccessful) {
                val json = JSONObject(body)
                val token = json.optString("token")
                if (token.isNotBlank()) {
                    cachedToken = token
                    cachedTokenExpiry = now + TimeUnit.HOURS.toMillis(23)
                    return token
                }
            }
        } catch (_: Exception) {}

        // Tenta endpoint 2: Basic Auth /gtoken/generateToken
        val basic = Credentials.basic(user, pass)
        val req2 = Request.Builder()
            .url("https://www.privateinternetaccess.com/gtoken/generateToken")
            .header("Authorization", basic)
            .build()

        val res2 = httpClient.newCall(req2).execute()
        val body2 = res2.body?.string().orEmpty()

        if (!res2.isSuccessful) {
            val errJson = try { JSONObject(body2) } catch (_: Exception) { null }
            val errMsg = errJson?.optString("message") ?: if (res2.code == 401) "Acesso Negado (401)" else "HTTP ${res2.code}"
            resetCachedToken()
            throw IllegalStateException("Acesso Negado na PIA VPN ($errMsg). A conta '$user' foi rejeitada pelos servidores oficiais da PIA. Os utilizadores requerem a letra 'p' seguida de 7 dígitos (ex: p1234567).")
        }

        val json2 = JSONObject(body2)
        if (json2.optString("status") == "ERROR") {
            val msg = json2.optString("message", "Credenciais incorretas")
            resetCachedToken()
            throw IllegalStateException("Autenticação PIA falhou: $msg. Confirma os dados da subscrição.")
        }

        val token = json2.getString("token")
        cachedToken = token
        cachedTokenExpiry = now + TimeUnit.HOURS.toMillis(23)
        return token
    }

    fun getRegions(callback: (List<PiaRegion>) -> Unit) {
        if (regionsCache.isNotEmpty()) {
            callback(regionsCache)
            return
        }

        thread {
            try {
                val req = Request.Builder()
                    .url("https://serverlist.piaservers.net/vpninfo/servers/v6")
                    .build()
                val res = httpClient.newCall(req).execute()
                val body = res.body?.string().orEmpty()
                val end = body.lastIndexOf('}')
                if (end > 0) {
                    val cleanJson = body.substring(0, end + 1)
                    val root = JSONObject(cleanJson)
                    val regionsArray = root.optJSONArray("regions")
                    if (regionsArray != null) {
                        val parsed = mutableListOf<PiaRegion>()
                        for (i in 0 until regionsArray.length()) {
                            val r = regionsArray.getJSONObject(i)
                            val servers = r.optJSONObject("servers")
                            val wgArr = servers?.optJSONArray("wg")
                            if (wgArr != null && wgArr.length() > 0) {
                                val wgObj = wgArr.getJSONObject(0)
                                val ip = wgObj.optString("ip")
                                val cn = wgObj.optString("cn")
                                val country = r.optString("country")
                                val name = r.optString("name")
                                val id = r.optString("id")
                                val isSo = id.contains("streaming") || id.endsWith("-so") || name.contains("Streaming", ignoreCase = true)
                                if (ip.isNotBlank()) {
                                    parsed.add(
                                        PiaRegion(
                                            id = id,
                                            name = name,
                                            countryCode = country,
                                            flagEmoji = PiaRegion.flagForCountry(country),
                                            serverIp = ip,
                                            hostname = r.optString("dns", cn),
                                            isStreamingOptimized = isSo
                                        )
                                    )
                                }
                            }
                        }
                        if (parsed.isNotEmpty()) {
                            // Coloca Portugal e países de topo no início
                            val ordered = mutableListOf<PiaRegion>()
                            val topIds = listOf("pt", "pt-so", "spain", "es-valencia", "france", "uk", "uk_manchester", "nl_netherlands-so", "nl_amsterdam", "de-frankfurt", "de_berlin", "swiss", "italy", "us-newjersey", "br")
                            topIds.forEach { tid ->
                                parsed.find { it.id == tid }?.let { ordered.add(it) }
                            }
                            parsed.forEach { p ->
                                if (ordered.none { it.id == p.id }) ordered.add(p)
                            }
                            regionsCache.clear()
                            regionsCache.addAll(ordered)
                            mainHandler.post { callback(regionsCache) }
                            return@thread
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback para lista pré-definida
            if (regionsCache.isEmpty()) {
                regionsCache.addAll(PiaRegion.defaultRegions())
            }
            mainHandler.post { callback(regionsCache) }
        }
    }
}
