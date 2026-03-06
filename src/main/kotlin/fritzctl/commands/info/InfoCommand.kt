package fritzctl.commands.info

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class InfoCommand : CliktCommand(name = "info") {

    override fun commandHelp(context: Context) =
        "Informationen über die Fritz!Box (Modell, Firmware, Systemprotokoll)."

    init {
        subcommands(InfoStatusCommand(), InfoLogCommand())
    }

    override fun run() = Unit
}
