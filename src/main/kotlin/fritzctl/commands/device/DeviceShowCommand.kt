package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

class DeviceShowCommand : CliktCommand(name = "show") {

    override fun commandHelp(context: Context) = "Zeigt Details zu einem bestimmten Gerät."

    private val name: String by option("--name", "-n", help = "Name des Geräts").required()

    override fun run() {
        // Platzhalterdaten – später durch echte Fritzbox-API ersetzen
        echo("Gerät: $name")
        echo("IP:    192.168.1.42")
        echo("MAC:   AA:BB:CC:DD:EE:FF")
        echo("WLAN:  2.4 GHz")
    }
}
