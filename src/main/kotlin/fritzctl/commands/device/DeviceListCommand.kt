package fritzctl.commands.device

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.AhaApiException
import fritzctl.api.AhaClient
import fritzctl.api.AhaDevice
import kotlin.system.exitProcess

class DeviceListCommand : CliktCommand(name = "list") {

    override fun commandHelp(context: Context) = "Listet alle Smarthome-Geräte auf."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val client = AhaClient(config?.host ?: "fritz.box", log)
            val devices = client.getDeviceList()

            when (output.format) {
                OutputFormat.JSON -> println(devicesToJson(devices))
                OutputFormat.YAML -> println(devicesToYaml(devices))
                null -> printTable(terminal, devices)
            }
        } catch (e: AhaApiException) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printTable(terminal: Terminal, devices: List<AhaDevice>) {
        if (devices.isEmpty()) {
            terminal.println("Keine Geräte gefunden.")
            return
        }
        terminal.println(table {
            header { row("AIN", "Name", "Status", "Schalter", "Temperatur", "Leistung") }
            body {
                devices.forEach { d ->
                    row(
                        d.ain,
                        d.name,
                        if (d.present) green("online") else red("offline"),
                        when (d.switchState) {
                            true -> green("an")
                            false -> red("aus")
                            null -> "-"
                        },
                        d.temperatureCelsius?.let { "$it °C" } ?: "-",
                        d.powerMilliWatt?.let { "${it / 1000.0} W" } ?: "-",
                    )
                }
            }
        })
    }

    private fun devicesToJson(devices: List<AhaDevice>): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\",\n  \"devices\": [")
        devices.forEachIndexed { i, d ->
            if (i > 0) append(",")
            append("\n    ${deviceToJsonObject(d)}")
        }
        append("\n  ]\n}")
    }

    private fun deviceToJsonObject(d: AhaDevice): String = buildString {
        append("{")
        append("\"ain\": ${jsonStr(d.ain)}, ")
        append("\"name\": ${jsonStr(d.name)}, ")
        append("\"present\": ${d.present}, ")
        d.switchState?.let { append("\"switchState\": $it, ") }
        d.temperatureCelsius?.let { append("\"temperatureCelsius\": $it, ") }
        d.powerMilliWatt?.let { append("\"powerMilliWatt\": $it, ") }
        d.energyWh?.let { append("\"energyWh\": $it, ") }
        // remove trailing comma+space
        if (endsWith(", ")) delete(length - 2, length)
        append("}")
    }

    private fun devicesToYaml(devices: List<AhaDevice>): String = buildString {
        appendLine("status: ok")
        appendLine("devices:")
        devices.forEach { d ->
            appendLine("  - ain: ${yamlStr(d.ain)}")
            appendLine("    name: ${yamlStr(d.name)}")
            appendLine("    present: ${d.present}")
            d.switchState?.let { appendLine("    switchState: $it") }
            d.temperatureCelsius?.let { appendLine("    temperatureCelsius: $it") }
            d.powerMilliWatt?.let { appendLine("    powerMilliWatt: $it") }
            d.energyWh?.let { appendLine("    energyWh: $it") }
        }
    }.trimEnd()

    private fun jsonStr(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    private fun yamlStr(v: String): String {
        val needsQuoting = v.any { it in ":#{},[]|>&*!'\"%@`\n\r\t" } || v.isBlank()
        return if (needsQuoting) "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\"" else v
    }
}
