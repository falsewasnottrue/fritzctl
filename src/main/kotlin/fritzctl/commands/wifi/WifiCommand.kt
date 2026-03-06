package fritzctl.commands.wifi

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class WifiCommand : CliktCommand(name = "wifi") {

    override fun commandHelp(context: Context) = "WLAN-Netze verwalten und Verbindungen anzeigen."

    init {
        subcommands(
            WifiStatusCommand(),
            WifiClientsCommand(),
            WifiOnCommand(),
            WifiOffCommand(),
        )
    }

    override fun run() = Unit
}
