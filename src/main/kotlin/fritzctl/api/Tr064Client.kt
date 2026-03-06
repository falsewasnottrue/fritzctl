package fritzctl.api

import fritzctl.auth.SessionStore
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.xml.parsers.DocumentBuilderFactory

/**
 * SOAP-Client für die Fritz!Box TR-064/UPnP-Schnittstelle.
 * Port: 49000 (HTTP), 49443 (HTTPS).
 * Authentifizierung: HTTP-Digest mit Benutzername + SID als Passwort (Fritz!OS ≥ 7.25).
 */
class Tr064Client(private val host: String, private val log: (String) -> Unit = {}) {

    private val baseUrl = "http://$host:49000"

    // -------------------------------------------------------------------------
    // Öffentliche API
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

        // Externe IP + Uptime: PPPoE (DSL) oder IP-basiert (Kabel/Glasfaser) ausprobieren
        data class ConnectionInfo(val ip: String?, val uptime: Long?)
        val connInfo = run {
            val candidates = listOf(
                "urn:dslforum-org:service:WANPPPConnection:1" to "/upnp/control/wanpppconn1",
                "urn:dslforum-org:service:WANIPConnection:1" to "/upnp/control/wanipconnection",
            )
            var result = ConnectionInfo(null, null)
            for ((service, url) in candidates) {
                try {
                    val ipXml = soap(service, url, "GetExternalIPAddress")
                    val ip = extractText(ipXml, "NewExternalIPAddress").takeIf { it.isNotBlank() }
                    val statusXml = soap(service, url, "GetStatusInfo")
                    val uptime = extractText(statusXml, "NewUptime").toLongOrNull()
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
            // GetAddonInfos liefert Bytes/s → in Bits/s umrechnen
            upstreamCurrentBps = extractText(addonXml, "NewByteSendRate").toLongOrNull()?.times(8),
            downstreamCurrentBps = extractText(addonXml, "NewByteReceiveRate").toLongOrNull()?.times(8),
            externalIp = connInfo.ip,
            uptimeSeconds = connInfo.uptime,
        )
    }

    // -------------------------------------------------------------------------
    // SOAP-Hilfsmethoden
    // -------------------------------------------------------------------------

    private fun soap(service: String, controlUrl: String, action: String, params: String = ""): String {
        val session = SessionStore.load()
            ?: throw Tr064Exception("Keine aktive Session. Bitte zuerst mit 'fritzctl auth login' anmelden.")
        if (session.sid == "0000000000000000") {
            throw Tr064Exception("Session-ID ist ungültig. Bitte erneut mit 'fritzctl auth login' anmelden.")
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

        // Fritz!OS ≥ 7.25 akzeptiert SID als Passwort für HTTP-Digest-Auth
        val httpClient = HttpClient.newBuilder()
            .authenticator(object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(session.username, session.sid.toCharArray())
            })
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "\"$service#$action\"")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw Tr064Exception("Verbindung zu $host:49000 fehlgeschlagen: ${e.message}")
        }

        if (response.statusCode() !in 200..299) {
            throw Tr064Exception("HTTP ${response.statusCode()} für $action")
        }
        return response.body()
    }

    private fun extractText(xml: String, tag: String): String =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
            .getElementsByTagName(tag).item(0)?.textContent?.trim() ?: ""
}

class Tr064Exception(message: String) : Exception(message)
