package fritzctl

import com.github.ajalt.clikt.core.subcommands
import fritzctl.commands.device.DeviceCommand
import fritzctl.commands.network.NetworkCommand

fun main(args: Array<String>) {
    FritzCtl()
        .subcommands(DeviceCommand(), NetworkCommand())
        .main(args)
}
