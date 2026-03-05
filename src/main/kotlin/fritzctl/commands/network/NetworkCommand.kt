package fritzctl.commands.network

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class NetworkCommand : CliktCommand(name = "network") {

    override fun commandHelp(context: Context) = "Befehle rund um Netzwerk und Verbindung."

    init {
        subcommands(NetworkStatusCommand())
    }

    override fun run() = Unit
}
