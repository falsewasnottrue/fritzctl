package fritzctl

import com.github.ajalt.clikt.core.subcommands
import fritzctl.commands.auth.AuthCommand
import fritzctl.commands.device.DeviceCommand

fun main(args: Array<String>) {
    FritzCtl()
        .subcommands(AuthCommand(), DeviceCommand())
        .main(args)
}
