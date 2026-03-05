package fritzctl.auth

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class FritzAuthTest {

    // -------------------------------------------------------------------------
    // PBKDF2
    // Testvektoren aus der AVM-Dokumentation "Anmeldung am FRITZ!Box Webinterface"
    // -------------------------------------------------------------------------

    @Test
    fun `PBKDF2 - Testvektor aus AVM-Dokumentation`() {
        val challenge = "2\$10000\$5A1711\$2000\$5A1722"
        val password = "1example!"
        val expected = "5A1722\$1798a1672bca7c6463d6b245f82b53703b0f50813401b03e4045a5861e689adb"

        assertEquals(expected, FritzAuth.calculatePbkdf2Response(challenge, password))
    }

    @Test
    fun `PBKDF2 - Challenge muss mit 2$ beginnen`() {
        assertThrows<FritzAuthException> {
            FritzAuth.calculatePbkdf2Response("2\$10000\$salt1\$2000", "passwort")
        }
    }

    @Test
    fun `PBKDF2 - Ungültiger iter1-Wert wirft Exception`() {
        val ex = assertThrows<FritzAuthException> {
            FritzAuth.calculatePbkdf2Response("2\$abc\$salt1\$2000\$salt2", "passwort")
        }
        assertEquals("Ungültiger iter1-Wert: abc", ex.message)
    }

    @Test
    fun `PBKDF2 - Ungültiger iter2-Wert wirft Exception`() {
        val ex = assertThrows<FritzAuthException> {
            FritzAuth.calculatePbkdf2Response("2\$10000\$salt1\$xyz\$salt2", "passwort")
        }
        assertEquals("Ungültiger iter2-Wert: xyz", ex.message)
    }

    @Test
    fun `PBKDF2 - Falsche Anzahl Segmente wirft Exception`() {
        assertThrows<FritzAuthException> {
            FritzAuth.calculatePbkdf2Response("2\$10000\$salt1\$2000", "passwort")
        }
    }

    @Test
    fun `PBKDF2 - Response enthält salt2 als Präfix`() {
        val challenge = "2\$10000\$5A1711\$2000\$5A1722"
        val response = FritzAuth.calculatePbkdf2Response(challenge, "1example!")
        assert(response.startsWith("5A1722\$")) {
            "Response muss mit salt2 beginnen, war: $response"
        }
    }

    // Leeres Passwort wird nicht getestet: HMAC-SHA256 erlaubt keinen leeren Schlüssel (Java-Sicherheitsrichtlinie),
    // und eine Fritzbox akzeptiert ohnehin kein leeres Passwort.

    // -------------------------------------------------------------------------
    // MD5
    // Testvektoren aus der AVM-Dokumentation "Anmeldung am FRITZ!Box Webinterface"
    // -------------------------------------------------------------------------

    @Test
    fun `MD5 - Testvektor aus AVM-Dokumentation (Umlaut)`() {
        val challenge = "1234567z"
        val password = "äbc"
        val expected = "1234567z-9e224a41eeefa284df7bb0f26c2913e2"

        assertEquals(expected, FritzAuth.calculateMd5Response(challenge, password))
    }

    @Test
    fun `MD5 - Response beginnt mit challenge`() {
        val response = FritzAuth.calculateMd5Response("abcdef12", "passwort")
        assert(response.startsWith("abcdef12-")) {
            "Response muss mit der Challenge beginnen, war: $response"
        }
    }

    @Test
    fun `MD5 - Response enthält 32-stelligen Hexadezimal-Hash`() {
        val response = FritzAuth.calculateMd5Response("abcdef12", "passwort")
        val hash = response.removePrefix("abcdef12-")
        assertEquals(32, hash.length)
        assert(hash.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Hash enthält ungültige Zeichen: $hash"
        }
    }

    @Test
    fun `MD5 - Zeichen mit codepoint über 255 werden durch Punkt ersetzt`() {
        val challenge = "test1234"
        // '€' hat codepoint 8364 (> 255) und wird durch '.' ersetzt
        val responseWithEuro = FritzAuth.calculateMd5Response(challenge, "€")
        val responseWithDot  = FritzAuth.calculateMd5Response(challenge, ".")
        assertEquals(responseWithDot, responseWithEuro)
    }

    @Test
    fun `MD5 - Zeichen mit codepoint unter 256 werden nicht ersetzt`() {
        val challenge = "test1234"
        val responseA = FritzAuth.calculateMd5Response(challenge, "ä") // codepoint 228
        val responseB = FritzAuth.calculateMd5Response(challenge, ".")
        // 'ä' (228) soll NICHT durch '.' ersetzt werden → andere Hashes
        assert(responseA != responseB)
    }

    @Test
    fun `MD5 - Gleiches Input ergibt gleichen Output (Determinismus)`() {
        val r1 = FritzAuth.calculateMd5Response("challenge1", "passwort")
        val r2 = FritzAuth.calculateMd5Response("challenge1", "passwort")
        assertEquals(r1, r2)
    }

    @Test
    fun `MD5 - Unterschiedliche Challenges ergeben unterschiedliche Responses`() {
        val r1 = FritzAuth.calculateMd5Response("challenge1", "passwort")
        val r2 = FritzAuth.calculateMd5Response("challenge2", "passwort")
        assert(r1 != r2)
    }
}
