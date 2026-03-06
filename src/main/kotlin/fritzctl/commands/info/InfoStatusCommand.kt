package fritzctl.commands.info

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.terminal.Terminal
import fritzctl.FritzConfig
import fritzctl.Output
import fritzctl.OutputFormat
import fritzctl.api.BoxInfo
import fritzctl.api.Tr064Client
import fritzctl.api.Tr064Exception
import kotlin.system.exitProcess

class InfoStatusCommand : CliktCommand(name = "status") {

    override fun commandHelp(context: Context) =
        "Zeigt Modell, Firmware-Version, Seriennummer und Uptime der Fritz!Box."

    override fun run() {
        val terminal = Terminal()
        val config = currentContext.findObject<FritzConfig>()
        val output = config?.output ?: Output(null, terminal)
        val verbose = config?.verbose ?: false
        val log: (String) -> Unit = { if (verbose) terminal.println(cyan("  [debug] $it")) }

        try {
            val info = Tr064Client(config?.host ?: "fritz.box", log).getBoxInfo()

            when (output.format) {
                OutputFormat.JSON -> println(toJson(info))
                OutputFormat.YAML -> println(toYaml(info))
                null -> printHuman(terminal, info)
            }
        } catch (e: Tr064Exception) {
            output.error(e.message ?: "Unbekannter Fehler")
            exitProcess(1)
        }
    }

    private fun printHuman(terminal: Terminal, i: BoxInfo) {
        terminal.println("Hersteller:    ${i.manufacturer}")
        terminal.println("Modell:        ${i.modelName}")
        terminal.println("Seriennummer:  ${i.serialNumber}")
        terminal.println("Firmware:      ${i.firmwareVersion}")
        terminal.println("Hardware:      ${i.hardwareVersion}")
        i.uptimeSeconds?.let { terminal.println("Uptime:        ${formatUptime(it)}") }
    }

    private fun toJson(i: BoxInfo): String = buildString {
        appendLine("{")
        append("  \"status\": \"ok\"")
        append(",\n  \"manufacturer\": ${q(i.manufacturer)}")
        append(",\n  \"modelName\": ${q(i.modelName)}")
        append(",\n  \"serialNumber\": ${q(i.serialNumber)}")
        append(",\n  \"firmwareVersion\": ${q(i.firmwareVersion)}")
        append(",\n  \"hardwareVersion\": ${q(i.hardwareVersion)}")
        i.uptimeSeconds?.let { append(",\n  \"uptimeSeconds\": $it") }
        append("\n}")
    }

    private fun toYaml(i: BoxInfo): String = buildString {
        appendLine("status: ok")
        appendLine("manufacturer: ${i.manufacturer}")
        appendLine("modelName: ${i.modelName}")
        appendLine("serialNumber: ${i.serialNumber}")
        appendLine("firmwareVersion: ${i.firmwareVersion}")
        appendLine("hardwareVersion: ${i.hardwareVersion}")
        i.uptimeSeconds?.let { appendLine("uptimeSeconds: $it") }
    }.trimEnd()

    private fun formatUptime(s: Long): String {
        val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (h > 0 || d > 0) append("${h}h ")
            append("${m}min")
        }
    }

    private fun q(v: String) = "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
