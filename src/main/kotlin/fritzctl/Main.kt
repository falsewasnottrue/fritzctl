package fritzctl

import com.github.ajalt.clikt.core.subcommands
import fritzctl.commands.auth.AuthCommand
import fritzctl.commands.device.DeviceCommand
import fritzctl.commands.net.NetCommand

fun main(args: Array<String>) {
    FritzCtl()
        .subcommands(AuthCommand(), DeviceCommand(), NetCommand())
        .main(args)
}
