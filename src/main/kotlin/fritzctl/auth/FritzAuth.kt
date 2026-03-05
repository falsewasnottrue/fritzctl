package fritzctl.auth

import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

data class SessionXml(
    val sid: String,
    val challenge: String,
    val blockTime: Int,
)

class FritzAuthException(message: String) : Exception(message)

object FritzAuth {

    private val httpClient = HttpClient.newHttpClient()

    fun getSessionInfo(host: String, log: (String) -> Unit = {}): SessionXml {
        val url = "http://$host/login_sid.lua?version=2"
        log("GET $url")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            log("HTTP ${response.statusCode()}")
            log("Antwort-Body:\n${response.body()}")
            parseSessionXml(response.body())
        } catch (e: FritzAuthException) {
            throw e
        } catch (e: Exception) {
            throw FritzAuthException("Verbindung zur Fritzbox unter '$host' fehlgeschlagen: ${e.message}")
        }
    }

    fun login(host: String, username: String, password: String, log: (String) -> Unit = {}): String {
        log("=== Schritt 1: Challenge anfordern ===")
        val sessionInfo = getSessionInfo(host, log)
        log("Challenge:  ${sessionInfo.challenge}")
        log("BlockTime:  ${sessionInfo.blockTime}s")
        log("Aktuelle SID: ${sessionInfo.sid}")

        if (sessionInfo.blockTime > 0) {
            throw FritzAuthException("Anmeldung blockiert. Nächster Versuch in ${sessionInfo.blockTime} Sekunden möglich.")
        }

        val challenge = sessionInfo.challenge
        log("")
        log("=== Schritt 2: Response berechnen ===")

        val response = if (challenge.startsWith("2\$")) {
            log("Verfahren: PBKDF2 (Fritz!OS >= 7.24)")
            val parts = challenge.split("$")
            log("  iter1 = ${parts[1]}, salt1 = ${parts[2]}")
            log("  iter2 = ${parts[3]}, salt2 = ${parts[4]}")
            val r = calculatePbkdf2Response(challenge, password, log)
            log("  Response: $r")
            r
        } else {
            log("Verfahren: MD5-Fallback (Fritz!OS < 7.24)")
            val r = calculateMd5Response(challenge, password, log)
            log("  Response: $r")
            r
        }

        log("")
        log("=== Schritt 3: Login-POST senden ===")
        val postUrl = "http://$host/login_sid.lua?version=2"
        log("POST $postUrl")
        log("  username=${username}")
        log("  response=${response}")

        val sid = postLogin(host, username, response, log)
        log("Erhaltene SID: $sid")

        if (sid == "0000000000000000") {
            log("SID ist leer (0000000000000000) → Anmeldung abgelehnt")
            val newInfo = getSessionInfo(host, log)
            if (newInfo.blockTime > 0) {
                throw FritzAuthException(
                    "Anmeldung fehlgeschlagen. Nächster Versuch in ${newInfo.blockTime} Sekunden möglich."
                )
            }
            throw FritzAuthException("Anmeldung fehlgeschlagen. Benutzername oder Kennwort falsch.")
        }

        return sid
    }

    fun logout(host: String, sid: String, log: (String) -> Unit = {}) {
        val url = "http://$host/login_sid.lua?version=2"
        log("POST $url")
        log("  logout=1")
        log("  sid=$sid")
        val body = "logout=1&sid=${urlEncode(sid)}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            log("HTTP ${response.statusCode()}")
            log("Antwort-Body:\n${response.body()}")
        } catch (e: Exception) {
            throw FritzAuthException("Abmeldung fehlgeschlagen: ${e.message}")
        }
    }

    private fun postLogin(
        host: String,
        username: String,
        response: String,
        log: (String) -> Unit,
    ): String {
        val body = "username=${urlEncode(username)}&response=${urlEncode(response)}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://$host/login_sid.lua?version=2"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return try {
            val httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            log("HTTP ${httpResponse.statusCode()}")
            log("Antwort-Body:\n${httpResponse.body()}")
            parseSessionXml(httpResponse.body()).sid
        } catch (e: FritzAuthException) {
            throw e
        } catch (e: Exception) {
            throw FritzAuthException("Login-Request fehlgeschlagen: ${e.message}")
        }
    }

    // Challenge format: 2$<iter1>$<salt1>$<iter2>$<salt2>
    // salt1 and salt2 are hex-encoded byte arrays.
    // hash1    = PBKDF2-HMAC-SHA256(password_utf8, fromHex(salt1), iter1)
    // response = salt2 + "$" + PBKDF2-HMAC-SHA256(hash1, fromHex(salt2), iter2).hex()
    internal fun calculatePbkdf2Response(
        challenge: String,
        password: String,
        log: (String) -> Unit = {},
    ): String {
        val parts = challenge.split("$")
        if (parts.size != 5 || parts[0] != "2") {
            throw FritzAuthException("Ungültiges PBKDF2-Challenge-Format: $challenge")
        }
        val iter1 = parts[1].toIntOrNull()
            ?: throw FritzAuthException("Ungültiger iter1-Wert: ${parts[1]}")
        val salt1 = parts[2]
        val iter2 = parts[3].toIntOrNull()
            ?: throw FritzAuthException("Ungültiger iter2-Wert: ${parts[3]}")
        val salt2 = parts[4]

        val hash1 = pbkdf2HmacSha256(
            password = password.toByteArray(Charsets.UTF_8),
            salt = fromHex(salt1),
            iterations = iter1,
        )
        log("  hash1 = PBKDF2-HMAC-SHA256(password, hex(\"$salt1\"), $iter1)")
        log("       => 0x${hash1.toHexString()}")

        val hash2 = pbkdf2HmacSha256(
            password = hash1,
            salt = fromHex(salt2),
            iterations = iter2,
        )
        log("  hash2 = PBKDF2-HMAC-SHA256(hash1, hex(\"$salt2\"), $iter2)")
        log("       => 0x${hash2.toHexString()}")

        return "$salt2\$${hash2.toHexString()}"
    }

    // response = challenge + "-" + MD5(UTF-16LE(challenge + "-" + password)).hex()
    // Characters with codepoint > 255 are replaced by '.' before encoding.
    internal fun calculateMd5Response(
        challenge: String,
        password: String,
        log: (String) -> Unit = {},
    ): String {
        val input = "$challenge-$password"
        val sanitized = input.map { c -> if (c.code > 255) '.' else c }.joinToString("")
        if (sanitized != input) {
            log("  Hinweis: Sonderzeichen (codepoint > 255) wurden durch '.' ersetzt")
            log("  Original:   $input")
            log("  Bereinigt:  $sanitized")
        }
        log("  MD5-Eingabe (UTF-16LE): \"$sanitized\"")
        val bytes = sanitized.toByteArray(Charset.forName("UTF-16LE"))
        val md5 = MessageDigest.getInstance("MD5").digest(bytes)
        log("  MD5-Hash: ${md5.toHexString()}")
        return "$challenge-${md5.toHexString()}"
    }

    // RFC 2898 PBKDF2 with HMAC-SHA256, output length fixed to 32 bytes (256 bit).
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))

        // T_1 = U_1 XOR U_2 XOR ... XOR U_iterations  (single 32-byte block)
        // U_1 = HMAC(password, salt || 0x00000001)
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val result = u.copyOf()

        // U_i = HMAC(password, U_{i-1})
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }

        return result
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun urlEncode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun parseSessionXml(xml: String): SessionXml {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            doc.documentElement.normalize()

            val sid = doc.getElementsByTagName("SID").item(0)?.textContent?.trim()
                ?: "0000000000000000"
            val challenge = doc.getElementsByTagName("Challenge").item(0)?.textContent?.trim()
                ?: ""
            val blockTime = doc.getElementsByTagName("BlockTime").item(0)?.textContent?.trim()
                ?.toIntOrNull() ?: 0

            SessionXml(sid, challenge, blockTime)
        } catch (e: FritzAuthException) {
            throw e
        } catch (e: Exception) {
            throw FritzAuthException("Fehler beim Parsen der Fritzbox-Antwort: ${e.message}")
        }
    }
}
