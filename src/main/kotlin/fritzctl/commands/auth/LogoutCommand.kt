package fritzctl.commands.auth

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.auth.FritzAuth
import fritzctl.auth.FritzAuthException
import fritzctl.auth.SessionStore
import kotlin.system.exitProcess

class LogoutCommand : CliktCommand(name = "logout") {

    override fun commandHelp(context: Context) = "Meldet sich von der Fritzbox ab."

    override fun run() {
        val terminal = Terminal()
        val verbose = currentContext.findObject<FritzConfig>()?.verbose ?: false

        val log: (String) -> Unit = { msg ->
            if (verbose) terminal.println(cyan("  [debug] $msg"))
        }

        val session = SessionStore.load()
        if (session == null) {
            terminal.println(yellow("Keine aktive Session gefunden."))
            return
        }

        log("Session geladen: SID=${session.sid}, Host=${session.host}, User=${session.username}")

        try {
            if (verbose) {
                terminal.println(cyan("  [debug] Sende Logout für SID ${session.sid}"))
            } else {
                terminal.print("Abmelden von ${session.host} (${session.username}) ...")
            }

            FritzAuth.logout(session.host, session.sid, log)
            SessionStore.clear()
            log("Lokale Session gelöscht: ${SessionStore.path()}")

            if (!verbose) terminal.print(" ")
            terminal.println(green("OK"))
            terminal.println("Erfolgreich abgemeldet.")
        } catch (e: FritzAuthException) {
            if (!verbose) terminal.print(" ")
            terminal.println(red("Fehler: ${e.message}"))
            // Lokale Session trotzdem löschen, da der Zustand unklar ist
            SessionStore.clear()
            exitProcess(1)
        }
    }
}
