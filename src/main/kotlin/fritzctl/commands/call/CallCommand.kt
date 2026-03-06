package fritzctl.commands.call

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class CallCommand : CliktCommand(name = "call") {

    override fun commandHelp(context: Context) = "Anrufliste anzeigen."

    init {
        subcommands(CallListCommand())
    }

    override fun run() = Unit
}
