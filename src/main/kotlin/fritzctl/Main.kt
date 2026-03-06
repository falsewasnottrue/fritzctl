package fritzctl

import com.github.ajalt.clikt.core.subcommands
import fritzctl.commands.auth.AuthCommand
import fritzctl.commands.call.CallCommand
import fritzctl.commands.device.DeviceCommand
import fritzctl.commands.info.InfoCommand
import fritzctl.commands.net.NetCommand
import fritzctl.commands.wifi.WifiCommand

fun main(args: Array<String>) {
    FritzCtl()
        .subcommands(AuthCommand(), DeviceCommand(), NetCommand(), WifiCommand(), CallCommand(), InfoCommand())
        .main(args)
}
