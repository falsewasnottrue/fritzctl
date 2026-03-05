package fritzctl.auth

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

data class Session(
    val sid: String,
    val host: String,
    val username: String,
)

object SessionStore {

    private val sessionFile: File
        get() {
            val dir = File(System.getProperty("user.home"), ".fritzctl")
            dir.mkdirs()
            return File(dir, "session")
        }

    fun save(session: Session) {
        sessionFile.writeText(
            """
            sid=${session.sid}
            host=${session.host}
            username=${session.username}
            """.trimIndent()
        )
        // Restrict file permissions to owner only (rw-------)
        try {
            Files.setPosixFilePermissions(
                sessionFile.toPath(),
                PosixFilePermissions.fromString("rw-------"),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows does not support POSIX permissions
        }
    }

    fun load(): Session? {
        val file = sessionFile
        if (!file.exists()) return null
        return try {
            val props = file.readLines()
                .filter { "=" in it }
                .associate { line ->
                    val idx = line.indexOf("=")
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
            Session(
                sid = props["sid"] ?: return null,
                host = props["host"] ?: return null,
                username = props["username"] ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        sessionFile.delete()
    }

    fun path(): String = sessionFile.absolutePath
}
