package fritzctl.commands.auth

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.auth.FritzAuth
import fritzctl.auth.FritzAuthException
import fritzctl.auth.SessionStore
import kotlin.system.exitProcess

class LogoutCommand : CliktCommand(name = "logout") {

    override fun commandHelp(context: Context) = "Meldet sich von der Fritzbox ab."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val verbose = config?.verbose ?: false
        val output = config?.output ?: Output(null, terminal)

        val log: (String) -> Unit = { msg ->
            if (verbose) terminal.println(cyan("  [debug] $msg"))
        }

        val session = SessionStore.load()
        if (session == null) {
            output.warning("Keine aktive Session gefunden.")
            return
        }

        log("Session geladen: SID=${session.sid}, Host=${session.host}, User=${session.username}")

        if (verbose) {
            terminal.println(cyan("  [debug] Sende Logout für SID ${session.sid}"))
        } else if (!output.isMachineReadable) {
            terminal.print("Abmelden von ${session.host} (${session.username}) ...")
        }

        try {
            FritzAuth.logout(session.host, session.sid, log)
            SessionStore.clear()
            log("Lokale Session gelöscht: ${SessionStore.path()}")

            output.success("host" to session.host, "username" to session.username) {
                if (!verbose) terminal.print(" ")
                terminal.println(green("OK"))
                terminal.println("Erfolgreich abgemeldet.")
            }
        } catch (e: FritzAuthException) {
            // Lokale Session trotzdem löschen, da der Zustand unklar ist
            SessionStore.clear()
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }
}
