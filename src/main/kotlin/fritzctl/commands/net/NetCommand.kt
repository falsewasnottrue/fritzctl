package fritzctl.commands.net

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class NetCommand : CliktCommand(name = "net") {

    override fun commandHelp(context: Context) = "Netzwerk-Informationen abrufen."

    init {
        subcommands(
            NetHostsCommand(),
            NetWanCommand(),
        )
    }

    override fun run() = Unit
}
