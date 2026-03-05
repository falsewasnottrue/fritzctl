package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class DeviceCommand : CliktCommand(name = "device") {

    override fun commandHelp(context: Context) = "Befehle rund um verbundene Geräte."

    init {
        subcommands(DeviceListCommand(), DeviceShowCommand())
    }

    override fun run() = Unit
}
