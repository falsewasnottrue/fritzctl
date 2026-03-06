package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands

class DeviceCommand : CliktCommand(name = "device") {

    override fun commandHelp(context: Context) = "Smarthome-Geräte verwalten."

    init {
        subcommands(
            DeviceListCommand(),
            DeviceGetCommand(),
            DeviceOnCommand(),
            DeviceOffCommand(),
            DeviceToggleCommand(),
        )
    }

    override fun run() = Unit
}
