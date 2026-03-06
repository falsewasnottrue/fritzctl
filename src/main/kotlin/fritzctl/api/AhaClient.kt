package fritzctl.api

import fritzctl.auth.SessionStore
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.xml.parsers.DocumentBuilderFactory

/**
 * HTTP-Client für die AVM AHA-HTTP-Schnittstelle.
 * Endpunkt: http(s)://<host>/webservices/homeautoswitch.lua
 * Authentifizierung: ?sid=<sid> als Query-Parameter.
 * Kompatibel mit Fritz!OS ab 6.x.
 */
class AhaClient(private val host: String, private val log: (String) -> Unit = {}) {

    private val httpClient = HttpClient.newHttpClient()
    private val baseUrl = "http://$host/webservices/homeautoswitch.lua"

    // -------------------------------------------------------------------------
    // Geräteliste
    // -------------------------------------------------------------------------

    /** Gibt alle bekannten Smarthome-Geräte zurück. */
    fun getDeviceList(): List<AhaDevice> {
        val xml = command("getdevicelistinfos")
        log("XML-Antwort (${xml.length} Zeichen)")
        return parseDeviceList(xml)
    }

    // -------------------------------------------------------------------------
    // Schaltbefehle
    // -------------------------------------------------------------------------

    /** Schaltet ein Gerät ein. */
    fun switchOn(ain: String) { command("setswitchon", ain) }

    /** Schaltet ein Gerät aus. */
    fun switchOff(ain: String) { command("setswitchoff", ain) }

    /** Wechselt den Schaltzustand eines Geräts. */
    fun switchToggle(ain: String) { command("setswitchtoggle", ain) }

    // -------------------------------------------------------------------------
    // HTTP-Hilfsmethoden
    // -------------------------------------------------------------------------

    private fun command(cmd: String, ain: String? = null): String {
        val sid = sid()
        val url = buildString {
            append("$baseUrl?sid=$sid&switchcmd=$cmd")
            if (ain != null) append("&ain=${URLEncoder.encode(ain, "UTF-8")}")
        }
        log("GET $url")

        val response = try {
            httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
        } catch (e: Exception) {
            throw AhaApiException("Verbindung zu $host fehlgeschlagen: ${e.message}")
        }

        if (response.statusCode() !in 200..299) {
            throw AhaApiException("HTTP ${response.statusCode()}")
        }
        val body = response.body().trim()
        if (body == "inval") {
            throw AhaApiException("Ungültige Anfrage. AIN \"$ain\" nicht gefunden oder Gerät unterstützt diesen Befehl nicht.")
        }
        return body
    }

    private fun sid(): String {
        val session = SessionStore.load()
            ?: throw AhaApiException(
                "Keine aktive Session. Bitte zuerst mit 'fritzctl auth login' anmelden."
            )
        if (session.sid == "0000000000000000") {
            throw AhaApiException(
                "Session-ID ist ungültig. Bitte erneut mit 'fritzctl auth login' anmelden."
            )
        }
        return session.sid
    }

    // -------------------------------------------------------------------------
    // XML-Parser
    // -------------------------------------------------------------------------

    private fun parseDeviceList(xml: String): List<AhaDevice> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()

        val nodes = doc.getElementsByTagName("device")
        return (0 until nodes.length).map { parseDevice(nodes.item(it) as Element) }
    }

    private fun parseDevice(el: Element): AhaDevice {
        fun text(tag: String): String? =
            el.getElementsByTagName(tag).item(0)?.textContent?.trim()

        fun childText(parentTag: String, childTag: String): String? =
            (el.getElementsByTagName(parentTag).item(0) as? Element)
                ?.getElementsByTagName(childTag)?.item(0)?.textContent?.trim()

        val switchState = childText("switch", "state")
            ?.let { if (it == "1") true else if (it == "0") false else null }

        val temperatureCelsius = childText("temperature", "celsius")
            ?.toIntOrNull()?.let { it / 10.0 }

        val powerMilliWatt = childText("powermeter", "power")?.toIntOrNull()
        val energyWh = childText("powermeter", "energy")?.toIntOrNull()

        return AhaDevice(
            ain = el.getAttribute("identifier"),
            name = text("name") ?: "",
            present = text("present") == "1",
            functionbitmask = el.getAttribute("functionbitmask").toIntOrNull() ?: 0,
            switchState = switchState,
            temperatureCelsius = temperatureCelsius,
            powerMilliWatt = powerMilliWatt,
            energyWh = energyWh,
        )
    }
}

class AhaApiException(message: String) : Exception(message)
