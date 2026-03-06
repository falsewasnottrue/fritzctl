package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.AhaApiException
import fritzctl.api.AhaClient
import fritzctl.api.AhaDevice
import kotlin.system.exitProcess

class DeviceGetCommand : CliktCommand(name = "get") {

    override fun commandHelp(context: Context) =
        "Zeigt Details zu einem Gerät (Name, Status, Temperatur, Leistung)."

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
            val client = AhaClient(config?.host ?: "fritz.box", log)
            val device = client.getDeviceList().find { it.ain == ain }
                ?: throw AhaApiException("Gerät mit AIN \"$ain\" nicht gefunden.")

            when (output.format) {
                OutputFormat.JSON -> println(deviceToJson(device))
                OutputFormat.YAML -> println(deviceToYaml(device))
                null -> printHuman(terminal, device)
            }
        } catch (e: AhaApiException) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printHuman(terminal: Terminal, d: AhaDevice) {
        terminal.println("${bold("AIN:")}         ${d.ain}")
        terminal.println("${bold("Name:")}        ${d.name}")
        terminal.println("${bold("Status:")}      ${if (d.present) green("online") else red("offline")}")
        if (d.switchState != null) {
            terminal.println("${bold("Schalter:")}    ${if (d.switchState) green("an") else red("aus")}")
        }
        d.temperatureCelsius?.let { terminal.println("${bold("Temperatur:")}  $it °C") }
        d.powerMilliWatt?.let { terminal.println("${bold("Leistung:")}    ${it / 1000.0} W") }
        d.energyWh?.let { terminal.println("${bold("Energie:")}     $it Wh") }
    }

    private fun deviceToJson(d: AhaDevice): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\"")
        append(",\n  \"ain\": ${jsonStr(d.ain)}")
        append(",\n  \"name\": ${jsonStr(d.name)}")
        append(",\n  \"present\": ${d.present}")
        d.switchState?.let { append(",\n  \"switchState\": $it") }
        d.temperatureCelsius?.let { append(",\n  \"temperatureCelsius\": $it") }
        d.powerMilliWatt?.let { append(",\n  \"powerMilliWatt\": $it") }
        d.energyWh?.let { append(",\n  \"energyWh\": $it") }
        append("\n}")
    }

    private fun deviceToYaml(d: AhaDevice): String = buildString {
        appendLine("status: ok")
        appendLine("ain: ${yamlStr(d.ain)}")
        appendLine("name: ${yamlStr(d.name)}")
        appendLine("present: ${d.present}")
        d.switchState?.let { appendLine("switchState: $it") }
        d.temperatureCelsius?.let { appendLine("temperatureCelsius: $it") }
        d.powerMilliWatt?.let { appendLine("powerMilliWatt: $it") }
        d.energyWh?.let { appendLine("energyWh: $it") }
    }.trimEnd()

    private fun jsonStr(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun yamlStr(v: String): String {
        val needsQuoting = v.any { it in ":#{},[]|>&*!'\"%@`\n\r\t" } || v.isBlank()
        return if (needsQuoting) "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else v
    }
}
