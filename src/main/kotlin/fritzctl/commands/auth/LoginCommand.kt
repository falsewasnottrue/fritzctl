package fritzctl.commands.auth

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.auth.FritzAuth
import fritzctl.auth.FritzAuthException
import fritzctl.auth.Session
import fritzctl.auth.SessionStore
import kotlin.system.exitProcess

class LoginCommand : CliktCommand(name = "login") {

    override fun commandHelp(context: Context) = "Meldet sich an der Fritzbox an."

    private val usernameOpt: String? by option("--user", "-u", help = "Benutzername (Standard: \$FB_USER)")
    private val passwordOpt: String? by option("--password", "-p", help = "Passwort (Standard: \$FB_PASSWORD)")

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val host = config?.host ?: "fritz.box"
        val verbose = config?.verbose ?: false
        val output = config?.output ?: Output(null, terminal)

        val log: (String) -> Unit = { msg ->
            if (verbose) terminal.println(cyan("  [debug] $msg"))
        }

        val username = System.getenv("FB_USER")
            ?: usernameOpt
            ?: terminal.prompt("Benutzername")!!

        val password = System.getenv("FB_PASSWORD")
            ?: passwordOpt
            ?: terminal.prompt("Passwort", hideInput = true)!!

        if (verbose) {
            terminal.println(cyan("  [debug] Starte Login für Benutzer '$username' auf $host"))
        } else if (!output.isMachineReadable) {
            terminal.print("Verbinde mit $host ...")
        }

        try {
            val sid = FritzAuth.login(host, username, password, log)
            val sessionFile = SessionStore.path()
            SessionStore.save(Session(sid = sid, host = host, username = username))

            output.success(
                "host" to host,
                "username" to username,
                "sid" to sid,
                "sessionFile" to sessionFile,
            ) {
                if (!verbose) terminal.print(" ")
                terminal.println(green("OK"))
                terminal.println("Angemeldet als '$username'.")
                terminal.println("Session gespeichert in: $sessionFile")
            }
        } catch (e: FritzAuthException) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }
}
