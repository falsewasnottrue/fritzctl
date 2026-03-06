package fritzctl.commands.wifi

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import kotlin.system.exitProcess

class WifiOnCommand : CliktCommand(name = "on") {

    override fun commandHelp(context: Context) =
        "Schaltet das WLAN ein. Mit --guest wird das Gastnetz eingeschaltet."

    private val guest by option("--guest", "-g", help = "Gastnetz (WLAN 3) statt Haupt-WLAN").flag()

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val client = Tr064Client(config?.host ?: "fritz.box", log)
            if (guest) {
                client.setWifiEnabled(3, true)
                output.success("network" to "guest") { terminal.println(green("Gastnetz eingeschaltet.")) }
            } else {
                // Haupt-WLAN: Configs 1 (2,4 GHz) und 2 (5 GHz)
                client.setWifiEnabled(1, true)
                try { client.setWifiEnabled(2, true) } catch (e: Tr064Exception) {
                    log("5-GHz-Band nicht verfügbar: ${e.message}")
                }
                output.success("network" to "main") { terminal.println(green("WLAN eingeschaltet.")) }
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }
}
