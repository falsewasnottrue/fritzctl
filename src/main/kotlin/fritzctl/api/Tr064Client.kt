package fritzctl.api

import fritzctl.auth.SessionStore
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * SOAP-Client für die Fritz!Box TR-064/UPnP-Schnittstelle.
 * Port: 49000 (HTTP).
 * Authentifizierung: HTTP-Digest (RFC 2617) mit Benutzername + SID als Passwort.
 * Die Digest-Challenge wird manuell berechnet, da Java's HttpClient-Authenticator
 * für Digest-Auth unzuverlässig ist.
 */
class Tr064Client(private val host: String, private val log: (String) -> Unit = {}) {

    private val baseUrl = "http://$host:49000"
    private val httpClient = HttpClient.newHttpClient()

    // -------------------------------------------------------------------------
    // Hosts
    // -------------------------------------------------------------------------

    /** Gibt alle bekannten Netzwerk-Hosts zurück. */
    fun getHosts(): List<NetHost> {
        val countXml = soap(
            service = "urn:dslforum-org:service:Hosts:1",
            controlUrl = "/upnp/control/hosts",
            action = "GetHostNumberOfEntries",
        )
        val count = extractText(countXml, "NewHostNumberOfEntries").toIntOrNull() ?: 0
        log("Anzahl Hosts: $count")

        return (0 until count).mapNotNull { index ->
            try {
                val xml = soap(
                    service = "urn:dslforum-org:service:Hosts:1",
                    controlUrl = "/upnp/control/hosts",
                    action = "GetGenericHostEntry",
                    params = "<NewIndex>$index</NewIndex>",
                )
                NetHost(
                    mac = extractText(xml, "NewMACAddress"),
                    ip = extractText(xml, "NewIPAddress"),
                    name = extractText(xml, "NewHostName"),
                    interfaceType = extractText(xml, "NewInterfaceType"),
                    active = extractText(xml, "NewActive").let { it == "1" || it == "true" },
                )
            } catch (e: Tr064Exception) {
                log("Host $index übersprungen: ${e.message}")
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // WAN
    // -------------------------------------------------------------------------

    /** Gibt den aktuellen WAN-Status zurück. */
    fun getWanStatus(): WanStatus {
        val linkXml = soap(
            service = "urn:dslforum-org:service:WANCommonInterfaceConfig:1",
            controlUrl = "/upnp/control/wancommonifconfig",
            action = "GetCommonLinkProperties",
        )
        val addonXml = soap(
            service = "urn:dslforum-org:service:WANCommonInterfaceConfig:1",
            controlUrl = "/upnp/control/wancommonifconfig",
            action = "GetAddonInfos",
        )

        data class ConnectionInfo(val ip: String?, val uptime: Long?)
        val connInfo = run {
            val candidates = listOf(
                "urn:dslforum-org:service:WANPPPConnection:1" to "/upnp/control/wanpppconn1",
                "urn:dslforum-org:service:WANIPConnection:1" to "/upnp/control/wanipconnection",
            )
            var result = ConnectionInfo(null, null)
            for ((service, url) in candidates) {
                try {
                    val ip = extractText(soap(service, url, "GetExternalIPAddress"), "NewExternalIPAddress")
                        .takeIf { it.isNotBlank() }
                    val uptime = extractText(soap(service, url, "GetStatusInfo"), "NewUptime").toLongOrNull()
                    result = ConnectionInfo(ip, uptime)
                    break
                } catch (e: Tr064Exception) {
                    log("$service nicht verfügbar: ${e.message}")
                }
            }
            result
        }

        return WanStatus(
            accessType = extractText(linkXml, "NewWANAccessType"),
            linkStatus = extractText(linkXml, "NewPhysicalLinkStatus"),
            upstreamMaxBps = extractText(linkXml, "NewLayer1UpstreamMaxBitRate").toLongOrNull(),
            downstreamMaxBps = extractText(linkXml, "NewLayer1DownstreamMaxBitRate").toLongOrNull(),
            upstreamCurrentBps = extractText(addonXml, "NewByteSendRate").toLongOrNull()?.times(8),
            downstreamCurrentBps = extractText(addonXml, "NewByteReceiveRate").toLongOrNull()?.times(8),
            externalIp = connInfo.ip,
            uptimeSeconds = connInfo.uptime,
        )
    }

    // -------------------------------------------------------------------------
    // WiFi
    // -------------------------------------------------------------------------

    /** Gibt WLAN-Konfigurationen (2,4 GHz, 5 GHz, Gast) zurück. */
    fun getWifiNetworks(): List<WifiNetwork> {
        val defaultBands = mapOf(1 to "2,4 GHz", 2 to "5 GHz", 3 to "Gast")
        return (1..3).mapNotNull { index ->
            try {
                val xml = soap(
                    service = "urn:dslforum-org:service:WLANConfiguration:$index",
                    controlUrl = "/upnp/control/wlanconfig$index",
                    action = "GetInfo",
                )
                val freqRaw = extractText(xml, "NewX_AVM-DE_FrequencyBand")
                val band = when {
                    freqRaw.startsWith("2") -> "2,4 GHz"
                    freqRaw.startsWith("5") -> "5 GHz"
                    index == 3 -> "Gast"
                    else -> defaultBands[index] ?: "WLAN $index"
                }
                WifiNetwork(
                    index = index,
                    ssid = extractText(xml, "NewSSID"),
                    enabled = extractText(xml, "NewEnable").let { it == "1" || it == "true" },
                    channel = extractText(xml, "NewChannel"),
                    standard = extractText(xml, "NewStandard"),
                    band = band,
                )
            } catch (e: Tr064Exception) {
                log("WLANConfiguration:$index nicht verfügbar: ${e.message}")
                null
            }
        }
    }

    /** Schaltet ein WLAN-Netz ein oder aus. index: 1=2,4 GHz, 2=5 GHz, 3=Gast. */
    fun setWifiEnabled(index: Int, enabled: Boolean) {
        soap(
            service = "urn:dslforum-org:service:WLANConfiguration:$index",
            controlUrl = "/upnp/control/wlanconfig$index",
            action = "SetEnable",
            params = "<NewEnable>${if (enabled) "1" else "0"}</NewEnable>",
        )
    }

    // -------------------------------------------------------------------------
    // Device Info
    // -------------------------------------------------------------------------

    /** Gibt Informationen über die Fritz!Box zurück. */
    fun getBoxInfo(): BoxInfo {
        val xml = soap(
            service = "urn:dslforum-org:service:DeviceInfo:1",
            controlUrl = "/upnp/control/deviceinfo",
            action = "GetInfo",
        )
        return BoxInfo(
            manufacturer = extractText(xml, "NewManufacturerName"),
            modelName = extractText(xml, "NewModelName"),
            serialNumber = extractText(xml, "NewSerialNumber"),
            firmwareVersion = extractText(xml, "NewSoftwareVersion"),
            hardwareVersion = extractText(xml, "NewHardwareVersion"),
            uptimeSeconds = extractText(xml, "NewUpTime").toLongOrNull(),
        )
    }

    /** Gibt das Systemprotokoll als Liste von Einträgen zurück (neueste zuerst). */
    fun getDeviceLog(): List<String> {
        val xml = soap(
            service = "urn:dslforum-org:service:DeviceInfo:1",
            controlUrl = "/upnp/control/deviceinfo",
            action = "GetDeviceLog",
        )
        return extractText(xml, "NewDeviceLog").lines().filter { it.isNotBlank() }.reversed()
    }

    // -------------------------------------------------------------------------
    // Calls
    // -------------------------------------------------------------------------

    /** Gibt die Anrufliste zurück. */
    fun getCallList(): List<CallEntry> {
        val xml = soap(
            service = "urn:dslforum-org:service:X_AVM-DE_OnTel:1",
            controlUrl = "/upnp/control/x_contact",
            action = "GetCallList",
        )
        val rawUrl = extractText(xml, "NewCallListURL")
        log("Anrufliste URL: $rawUrl")
        val listUrl = if (rawUrl.startsWith("http")) rawUrl else "http://$host$rawUrl"
        return parseCallList(fetch(listUrl))
    }

    private fun parseCallList(xml: String): List<CallEntry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()
        val nodes = doc.getElementsByTagName("Call")
        return (0 until nodes.length).map { i ->
            val el = nodes.item(i) as Element
            fun t(tag: String) = el.getElementsByTagName(tag).item(0)?.textContent?.trim() ?: ""
            CallEntry(
                type = t("Type").toIntOrNull() ?: 0,
                caller = t("Caller"),
                called = t("Called"),
                name = t("Name"),
                date = t("Date"),
                duration = t("Duration"),
                device = t("Device"),
            )
        }
    }

    // -------------------------------------------------------------------------
    // HTTP / SOAP Infrastruktur
    // -------------------------------------------------------------------------

    private fun soap(service: String, controlUrl: String, action: String, params: String = ""): String {
        val session = SessionStore.load()
            ?: throw Tr064Exception("Keine aktive Session. Bitte zuerst mit 'fritzctl auth login' anmelden.")
        if (session.sid == "0000000000000000") {
            throw Tr064Exception("Session-ID ist ungültig. Bitte erneut mit 'fritzctl auth login' anmelden.")
        }
        if (session.password.isBlank()) {
            throw Tr064Exception(
                "Session enthält kein Passwort (ältere Session). Bitte erneut mit 'fritzctl auth login' anmelden."
            )
        }

        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
                        xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:$action xmlns:u="$service">$params</u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val url = "$baseUrl$controlUrl"
        log("SOAP $action → $url")

        // Erster Versuch ohne Auth
        val firstResponse = sendPost(url, service, action, body, authHeader = null)

        if (firstResponse.statusCode() in 200..299) return firstResponse.body()

        if (firstResponse.statusCode() == 401) {
            val wwwAuth = firstResponse.headers().firstValue("WWW-Authenticate").orElse("")
            log("401 erhalten, berechne Digest-Auth (realm aus Challenge)")
            val authHeader = buildDigestHeader(
                username = session.username,
                password = session.password,
                wwwAuth = wwwAuth,
                method = "POST",
                uri = controlUrl,
            )
            val secondResponse = sendPost(url, service, action, body, authHeader)
            if (secondResponse.statusCode() !in 200..299) {
                throw Tr064Exception("HTTP ${secondResponse.statusCode()} für $action (nach Auth-Retry)")
            }
            return secondResponse.body()
        }

        throw Tr064Exception("HTTP ${firstResponse.statusCode()} für $action")
    }

    private fun fetch(url: String): String {
        val response = try {
            httpClient.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw Tr064Exception("Abruf von $url fehlgeschlagen: ${e.message}")
        }
        if (response.statusCode() !in 200..299) {
            throw Tr064Exception("HTTP ${response.statusCode()} beim Datei-Abruf")
        }
        return response.body()
    }

    private fun sendPost(
        url: String,
        service: String,
        action: String,
        body: String,
        authHeader: String?,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "\"$service#$action\"")
            .POST(HttpRequest.BodyPublishers.ofString(body))

        if (authHeader != null) builder.header("Authorization", authHeader)

        return try {
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw Tr064Exception("Verbindung zu $host:49000 fehlgeschlagen: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // HTTP Digest Auth (RFC 2617)
    // -------------------------------------------------------------------------

    private fun buildDigestHeader(
        username: String,
        password: String,
        wwwAuth: String,
        method: String,
        uri: String,
    ): String {
        fun param(key: String): String {
            val quoted = Regex("""$key="([^"]*)"""").find(wwwAuth)?.groupValues?.get(1)
            val unquoted = Regex("""\b$key=([^\s,]+)""").find(wwwAuth)?.groupValues?.get(1)
            return quoted ?: unquoted ?: ""
        }

        val realm = param("realm")
        val nonce = param("nonce")
        val opaque = param("opaque")
        val qop = param("qop")
        val algorithm = param("algorithm").ifBlank { "MD5" }

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")

        val nc = "00000001"
        val cnonce = md5("${System.currentTimeMillis()}").take(8)

        val responseHash = if (qop.contains("auth")) {
            md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        return buildString {
            append("Digest username=\"$username\"")
            append(", realm=\"$realm\"")
            append(", nonce=\"$nonce\"")
            append(", uri=\"$uri\"")
            append(", algorithm=$algorithm")
            if (qop.contains("auth")) {
                append(", qop=auth")
                append(", nc=$nc")
                append(", cnonce=\"$cnonce\"")
            }
            append(", response=\"$responseHash\"")
            if (opaque.isNotBlank()) append(", opaque=\"$opaque\"")
        }
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // -------------------------------------------------------------------------
    // XML-Hilfsmethode
    // -------------------------------------------------------------------------

    private fun extractText(xml: String, tag: String): String =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
            .getElementsByTagName(tag).item(0)?.textContent?.trim() ?: ""
}

class Tr064Exception(message: String) : Exception(message)
