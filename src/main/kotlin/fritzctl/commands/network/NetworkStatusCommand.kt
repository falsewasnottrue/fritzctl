package fritzctl.commands.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal

class NetworkStatusCommand : CliktCommand(name = "status") {

    override fun commandHelp(context: Context) = "Zeigt den aktuellen Verbindungsstatus."

    override fun run() {
        val terminal = Terminal()

        // Platzhalterdaten – später durch echte Fritzbox-API ersetzen
        terminal.println(green("Verbindung aktiv"))
        terminal.println("Extern-IP:   93.184.216.34")
        terminal.println("Uptime:      3d 14h 22m")
        terminal.println("Down:        250 Mbit/s")
        terminal.println("Up:           40 Mbit/s")
    }
}
