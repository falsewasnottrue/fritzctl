package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.api.AhaApiException
import fritzctl.api.AhaClient
import kotlin.system.exitProcess

class DeviceOnCommand : CliktCommand(name = "on") {

    override fun commandHelp(context: Context) = "Schaltet ein Gerät ein."

    private val ain by argument(
        name = "AIN",
        help = "AIN des Geräts, z. B. \"11630 0015376\"",
    )

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            AhaClient(config?.host ?: "fritz.box", log).switchOn(ain)
            output.success("ain" to ain) {
                terminal.println(green("Gerät $ain eingeschaltet."))
            }
        } catch (e: AhaApiException) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }
}
